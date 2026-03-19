package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding.Positional
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding.Relative
import io.github.kingg22.kotlinpoet.assistant.domain.parser.NAMED_ARGUMENT_REGEX
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * `%X` / `%nX` → `%name:X` using a live template (Path B of [AbstractMixedStyleFix]).
 *
 * Each placeholder token becomes an editable template variable pre-filled with
 * `%name0:X`, `%name1:X`, … The template engine replaces the tokens in-place.
 *
 * After the name template commits, [afterRewrite] inserts map entries for any placeholder
 * names not already present in [existingNamedMap].
 */
class ConvertToNamedPlaceholderQuickFix(
    placeholders: List<PlaceholderSpec>,
    private val existingNamedMap: ArgumentSource.NamedMap? = null,
) : AbstractMixedStyleFix(placeholders) {

    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.mixed.convert.to.named")

    override fun buildRewrite(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): String? = null

    override fun buildAnchors(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): List<TemplateAnchor> {
        val toConvert = placeholders.filter { it.binding is Relative || it.binding is Positional }

        val varNames = toConvert.indices.map { "name$it" }
        val defaults = toConvert.mapIndexed { idx, p -> "%${varNames[idx]}:${p.kind.value}" }

        return buildAnchorsForPlaceholders(toConvert, formatArg, defaults, varNames)
    }

    override fun afterRewrite(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        freshFormatArg: KtStringTemplateExpression,
        committed: Boolean,
    ) {
        if (!committed) return
        smartInsertMapEntries(project, editor, call, freshFormatArg)
    }

    // ── Map-entry insertion ────────────────────────────────────────────────────
    @Suppress("DialogTitleCapitalization")
    private fun smartInsertMapEntries(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        postTemplateArg: KtStringTemplateExpression,
    ) {
        val finalNames = extractFinalPlaceholderNames(postTemplateArg.text)
        if (finalNames.isEmpty()) return

        val existingKeys: Set<String> = existingNamedMap?.entries?.keys ?: emptySet()
        val existingSpans: Map<String, Int> = existingNamedMap?.entries
            ?.mapNotNull { (k, v) -> v.span?.singleRangeOrNull()?.first?.let { k to it } }
            ?.toMap()
            ?: emptyMap()

        val newNames = finalNames.filter { it !in existingKeys }
        val existingHits = finalNames.filter { it in existingKeys }

        if (newNames.isEmpty()) {
            val offset = existingHits.mapNotNull { existingSpans[it] }.minOrNull()
            if (offset != null) editor.caretModel.moveToOffset(offset + 1)
            return
        }

        val mapArgExpr = call.valueArguments.getOrNull(1)?.getArgumentExpression()
        if (mapArgExpr == null) {
            val listEnd = call.valueArgumentList?.textRange?.endOffset ?: return
            editor.caretModel.moveToOffset(listEnd - 1)
            return
        }

        val mapCall = mapArgExpr as? KtCallExpression
        val isInlineMap = mapCall?.calleeExpression?.text in INLINE_MAP_BUILDERS
        if (!isInlineMap) {
            editor.caretModel.moveToOffset(mapArgExpr.textRange.startOffset)
            return
        }

        val factory = KtPsiFactory(project)
        val argList = mapCall!!.valueArgumentList ?: run {
            editor.caretModel.moveToOffset(mapCall.textRange.endOffset)
            return
        }

        WriteCommandAction.runWriteCommandAction(project, name, familyName, {
            newNames.forEachIndexed { idx, entryName ->
                argList.addArgument(factory.createArgument("\"$entryName\" to $SENTINEL_PREFIX$idx"))
            }
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        })

        val freshMapCall = call.valueArguments.getOrNull(1)?.getArgumentExpression()
            as? KtCallExpression ?: return

        val builder = TemplateBuilderImpl(freshMapCall)
        val mapText = freshMapCall.text
        var foundAny = false

        newNames.forEachIndexed { idx, _ ->
            val sentinel = "$SENTINEL_PREFIX$idx"
            val pos = mapText.indexOf(sentinel)
            if (pos < 0) return@forEachIndexed
            builder.replaceRange(
                TextRange(pos, pos + sentinel.length),
                "value$idx",
                ConstantNode("value"),
                true,
            )
            foundAny = true
        }

        if (!foundAny) return

        WriteCommandAction.runWriteCommandAction(project, name, familyName, {
            builder.run(editor, true)
        })
    }
}

private fun extractFinalPlaceholderNames(formatExprText: String): List<String> {
    val inner = formatExprText.removeSurrounding("\"\"\"").removeSurrounding("\"")
    return NAMED_ARGUMENT_REGEX.findAll(inner)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
}

private val INLINE_MAP_BUILDERS = setOf("mapOf", "mutableMapOf", "linkedMapOf", "hashMapOf")

private const val SENTINEL_PREFIX = "__kpv"
