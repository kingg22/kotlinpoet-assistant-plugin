package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation.DocToken.Control
import io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation.DocToken.Placeholder
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class KotlinPoetDocumentationTargetProvider : DocumentationTargetProvider {
    private val logger = thisLogger()

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        try {
            if (file.language != KotlinLanguage.INSTANCE) return emptyList()

            val element = file.findElementAt(offset) ?: return emptyList()
            val template = element.getParentOfType<KtStringTemplateExpression>(false)
                ?: return emptyList()
            val call = template.getParentOfType<KtCallExpression>(false) ?: return emptyList()

            val format = getCachedAnalysis(call)?.format ?: return emptyList()

            val placeholder = format.placeholders.firstOrNull { it.span.contains(offset) }
            if (placeholder != null) {
                // contextElement = the string template: a valid KtElement for KDoc resolution scope
                return listOf(KotlinPoetDocumentationTarget(Placeholder(placeholder.kind), template))
            }

            val control = format.controlSymbols.firstOrNull { it.span.contains(offset) }
            if (control != null) {
                return listOf(KotlinPoetDocumentationTarget(Control(control.type), template))
            }

            val fallback = resolveControlSymbolFromSource(file, template, offset)
            if (fallback != null) {
                logger.warn("Control symbol not found in analysis, but found fallback of $fallback")
                return listOf(KotlinPoetDocumentationTarget(Control(fallback), template))
            }
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            logger.error("Error trying to provide documentation for KotlinPoet format string", e)
        }

        return emptyList()
    }
}

private fun resolveControlSymbolFromSource(
    file: PsiFile,
    template: KtStringTemplateExpression,
    offset: Int,
): SymbolType? {
    if (!template.textRange.contains(offset)) return null

    // FIXME use template.text instead
    val text = file.text
    val current = text.getOrNull(offset) ?: return null

    if (current == '%' && text.getOrNull(offset + 1) == '%') {
        return SymbolType.LITERAL_PERCENT
    }

    return SymbolType.fromString(current.toString())
}
