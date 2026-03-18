package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * `%name:X` → `%1X`, `%2X`, … (1-based, in order of appearance in the format string).
 *
 * No live template — indices are deterministic.
 * Caret lands inside the closing `)` of the call's argument list.
 */
class ConvertToPositionalPlaceholderQuickFix(placeholders: List<PlaceholderSpec>) :
    AbstractMixedStyleFix(placeholders) {

    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.mixed.convert.to.positional")

    override fun buildRewrite(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): Pair<String, List<TemplateAnchor>>? {
        val named = placeholders.filter { it.binding is PlaceholderSpec.PlaceholderBinding.Named }
        if (named.isEmpty()) return null
        val transforms = named.mapIndexedNotNull { idx, p ->
            val r = p.span.singleRangeOrNull() ?: return@mapIndexedNotNull null
            Triple(r.first, r.last + 1, "%${idx + 1}${p.kind.value}")
        }
        if (transforms.isEmpty()) return null
        return Pair(applyReversedTransforms(formatArg, transforms), emptyList())
    }

    override fun afterRewrite(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        freshFormatArg: KtStringTemplateExpression,
        anchors: List<TemplateAnchor>,
    ) {
        val argListEnd = call.valueArgumentList?.textRange?.endOffset ?: return
        editor.caretModel.moveToOffset(argListEnd - 1)
    }
}
