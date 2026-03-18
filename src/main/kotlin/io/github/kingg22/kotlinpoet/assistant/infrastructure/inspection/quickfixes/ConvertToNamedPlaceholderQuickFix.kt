package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding.Positional
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding.Relative
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * `%X` / `%nX` → `%name:X` with an inline live-template for renaming each `name` field.
 *
 * Map argument handling (triggered after the template session via a second `invokeLater`):
 * - Inline `mapOf / mutableMapOf / linkedMapOf`: appends `"name" to TODO("value")` entries;
 *   caret moves to the first `TODO`.
 * - External variable or complex expression: caret moves to that argument.
 * - No second argument present: caret moves inside the closing `)` of the argument list.
 */
class ConvertToNamedPlaceholderQuickFix(placeholders: List<PlaceholderSpec>) : AbstractMixedStyleFix(placeholders) {

    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.mixed.convert.to.named")

    override fun buildRewrite(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): Pair<String, List<TemplateAnchor>>? {
        val toConvert = placeholders.filter { it.binding is Relative || it.binding is Positional }
        if (toConvert.isEmpty()) return null

        val fileStart = formatArg.textRange.startOffset
        val transforms = mutableListOf<Triple<Int, Int, String>>()
        val anchors = mutableListOf<TemplateAnchor>()
        var drift = 0

        toConvert.forEachIndexed { idx, placeholder ->
            val absRange = placeholder.span.singleRangeOrNull() ?: return@forEachIndexed
            val varName = "name$idx"
            val replacement = "%$varName:${placeholder.kind.value}"
            transforms.add(Triple(absRange.first, absRange.last + 1, replacement))

            // +1: skip the leading '%'; drift: account for length changes from prior replacements.
            val relStart = (absRange.first - fileStart) + 1 + drift
            val relEnd = relStart + varName.length
            anchors.add(TemplateAnchor(TextRange(relStart, relEnd), varName, varName))
            drift += replacement.length - (absRange.last + 1 - absRange.first)
        }

        return Pair(applyReversedTransforms(formatArg, transforms), anchors)
    }

    override fun afterRewrite(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        freshFormatArg: KtStringTemplateExpression,
        anchors: List<TemplateAnchor>,
    ) {
        super.afterRewrite(project, editor, call, freshFormatArg, anchors)

        // Map-entry insertion happens after the template session to avoid interfering with it.
        ApplicationManager.getApplication().invokeLater {
            tryInsertMapEntries(project, editor, call, anchors)
        }
    }

    private fun tryInsertMapEntries(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        anchors: List<TemplateAnchor>,
    ) {
        val mapArgExpr = call.valueArguments.getOrNull(1)?.getArgumentExpression()

        if (mapArgExpr == null) {
            val listEnd = call.valueArgumentList?.textRange?.endOffset ?: return
            editor.caretModel.moveToOffset(listEnd - 1)
            return
        }

        val mapCall = mapArgExpr as? KtCallExpression
        val isInlineMap = mapCall?.calleeExpression?.text in setOf("mapOf", "mutableMapOf", "linkedMapOf", "hashMapOf")

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
            anchors.forEachIndexed { idx, anchor ->
                argList.addArgument(factory.createArgument("\"${anchor.defaultValue}\" to \"value$idx\""))
            }
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        })

        ApplicationManager.getApplication().invokeLater {
            val firstTodo = call.text.indexOf("\"value0\"")
            if (firstTodo >= 0) {
                editor.caretModel.moveToOffset(call.textRange.startOffset + firstTodo)
            }
        }
    }
}
