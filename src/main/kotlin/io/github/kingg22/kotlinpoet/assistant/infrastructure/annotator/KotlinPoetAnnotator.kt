package io.github.kingg22.kotlinpoet.assistant.infrastructure.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.putCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRanges
import org.jetbrains.kotlin.psi.KtCallExpression

class KotlinPoetAnnotator :
    Annotator,
    DumbAware {
    private val logger = thisLogger()

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KtCallExpression) return

        try {
            // 1. Extraemos el contexto (Modelos de dominio) y se hace binding
            val kotlinPoetAnalysis = getCachedAnalysis(element)?.bind() ?: return
            putCachedAnalysis(element, kotlinPoetAnalysis)

            // 2. Renderizamos los highlights encontrados
            kotlinPoetAnalysis.controlSymbols.forEach { controlSymbol ->
                controlSymbol.span.toTextRanges().forEach { range ->
                    holder.highlight(
                        range,
                        key = KotlinPoetHighlightKeys.CONTROL_SYMBOL,
                    )
                }
            }
            kotlinPoetAnalysis.placeholders.forEach { placeholder ->
                placeholder.span.toTextRanges().forEach { range ->
                    holder.highlight(range, key = KotlinPoetHighlightKeys.PLACEHOLDER)
                }
            }
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            // Fail-safe para no romper el editor si el binding falla inesperadamente
            logger.error("Error trying to highlight KotlinPoet format string", e)
        }
    }
}

private fun AnnotationHolder.highlight(
    range: TextRange,
    key: TextAttributesKey,
    severity: HighlightSeverity = HighlightSeverity.INFORMATION,
) {
    newSilentAnnotation(severity)
        .textAttributes(key)
        .range(range)
        .create()
}
