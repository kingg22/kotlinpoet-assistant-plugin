package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * `%name:X` → `%X`
 *
 * No live template — conversion is deterministic.
 * Caret lands just before the closing quote of the format string.
 */
class ConvertToRelativePlaceholderQuickFix(placeholders: List<PlaceholderSpec>) : AbstractMixedStyleFix(placeholders) {

    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.mixed.convert.to.relative")

    override fun buildRewrite(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): Pair<String, List<TemplateAnchor>>? {
        val named = placeholders.filter { it.binding is PlaceholderSpec.PlaceholderBinding.Named }
        if (named.isEmpty()) return null
        val transforms = named.mapNotNull { p ->
            val r = p.span.singleRangeOrNull() ?: return@mapNotNull null
            Triple(r.first, r.last + 1, "%${p.kind.value}")
        }
        return Pair(applyReversedTransforms(formatArg, transforms), emptyList())
    }

    override fun afterRewrite(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        freshFormatArg: KtStringTemplateExpression,
        anchors: List<TemplateAnchor>,
    ) {
        val end = freshFormatArg.textRange.endOffset - 1
        editor.caretModel.moveToOffset(end.coerceAtLeast(freshFormatArg.textRange.startOffset))
    }
}
