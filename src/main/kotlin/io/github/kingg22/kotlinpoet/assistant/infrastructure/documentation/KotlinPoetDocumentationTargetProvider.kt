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

/**
 * Provides [DocumentationTarget]s for KotlinPoet format string tokens.
 *
 * Editor (file + offset) — [DocumentationTargetProvider]
 *
 * Invoked on hover / `Ctrl+Q` inside a KotlinPoet format string. Locates the
 * placeholder or control symbol at `offset` via the cached analysis.
 */
class KotlinPoetDocumentationTargetProvider : DocumentationTargetProvider {
    private val logger = thisLogger()

    // ── DocumentationTargetProvider — editor hover / Ctrl+Q ───────────────────
    // Is required to use the offset to find the target

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
                return listOf(KotlinPoetDocumentationTarget(Placeholder(placeholder.kind), template))
            }

            val control = format.controlSymbols.firstOrNull { it.span.contains(offset) }
            if (control != null) {
                return listOf(KotlinPoetDocumentationTarget(Control(control.type), template))
            }

            val fallback = resolveControlSymbolFromSource(template, offset)
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

// ── Control symbol fallback ────────────────────────────────────────────────────

/**
 * Resolves a [SymbolType] from the raw source text when the analysis cache has no
 * entry for the given offset (e.g., the annotator hasn't run yet).
 *
 * Uses [KtStringTemplateExpression.text] (relative offsets) instead of [PsiFile.text]
 * (absolute offsets) to avoid off-by-one errors when the template is not at position 0.
 */
private fun resolveControlSymbolFromSource(template: KtStringTemplateExpression, offset: Int): SymbolType? {
    if (!template.textRange.contains(offset)) return null
    val text = template.text
    val localOffset = offset - template.textRange.startOffset
    val current = text.getOrNull(localOffset) ?: return null
    if (current == '%' && text.getOrNull(localOffset + 1) == '%') return SymbolType.LITERAL_PERCENT
    return SymbolType.fromString(current.toString())
}
