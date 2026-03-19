package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding.Named
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding.Positional
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding.Relative
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Converts placeholders to positional style: `%name:X` / `%X` → `%1X`, `%2X`, …
 *
 * Uses Path B of [AbstractMixedStyleFix] (live template). Each placeholder token becomes
 * an editable template variable pre-filled with `%1X`, `%2X`, … The user can adjust the
 * indices before committing with Tab/Enter.
 *
 * On cancel: caret moves inside the closing `)` of the argument list.
 */
class ConvertToPositionalPlaceholderQuickFix(placeholders: List<PlaceholderSpec>) :
    AbstractMixedStyleFix(placeholders) {

    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.mixed.convert.to.positional")

    override fun buildAnchors(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): List<TemplateAnchor> {
        val toConvert = placeholders.filter {
            it.binding is Named || it.binding is Relative || it.binding is Positional
        }

        val varNames = toConvert.indices.map { "idx$it" }
        val defaults = toConvert.mapIndexed { idx, p -> "%${idx + 1}${p.kind.value}" }

        return buildAnchorsForPlaceholders(toConvert, formatArg, defaults, varNames)
    }

    override fun afterRewrite(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        freshFormatArg: KtStringTemplateExpression,
        committed: Boolean,
    ) {
        val argListEnd = call.valueArgumentList?.textRange?.endOffset ?: return
        editor.caretModel.moveToOffset(argListEnd - 1)
    }
}
