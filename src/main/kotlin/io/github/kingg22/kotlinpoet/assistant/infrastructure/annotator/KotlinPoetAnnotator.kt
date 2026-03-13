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
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.putCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRanges
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtCallExpression

class KotlinPoetAnnotator :
    Annotator,
    DumbAware {
    private val logger = thisLogger()

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KtCallExpression) return

        try {
            // 1. Extraemos el contexto (Modelos de dominio)
            var kotlinPoetAnalysis = getCachedAnalysis(element) ?: return

            // 2. Renderizar errores de parseo (ej: mezcla inválida, sintaxis rota)
            if (kotlinPoetAnalysis.haveFormatProblems) {
                kotlinPoetAnalysis.format.errors.forEach { it.renderProblem(element, holder) }
                return // Si el formato está roto, no intentamos validar argumentos
            }

            // 3. Lógica de Binding y Validación
            kotlinPoetAnalysis = kotlinPoetAnalysis.validate()
            putCachedAnalysis(element, kotlinPoetAnalysis)
            val boundContext = kotlinPoetAnalysis.bounds
            val problems = kotlinPoetAnalysis.problems

            problems.forEach { it.renderProblem(element, holder) }
            kotlinPoetAnalysis.controlSymbols.forEach { controlSymbol ->
                controlSymbol.span.toTextRanges().forEach { range ->
                    holder.highlight(
                        range,
                        KotlinPoetHighlightKeys.CONTROL_SYMBOL,
                    )
                }
            }
            boundContext.forEach { (placeholder, arg) ->
                placeholder.span.toTextRanges().forEach { range ->
                    holder.highlight(range, KotlinPoetHighlightKeys.PLACEHOLDER)
                }
                arg?.span?.toTextRanges()?.forEach { range ->
                    holder.highlight(range, KotlinPoetHighlightKeys.ARGUMENT)
                }
            }
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            // Fail-safe para no romper el editor si el binding falla inesperadamente
            logger.error("Error trying to highlight KotlinPoet format string", e)
        }
    }

    /** Helper para renderizar reportes */
    @VisibleForTesting
    fun FormatProblem.renderProblem(element: PsiElement, holder: AnnotationHolder) {
        val textRanges = target.toTextRanges(element)
        if (textRanges.isEmpty()) return

        textRanges.forEach { textRange ->
            // Validación de seguridad para evitar "Range must be inside element"
            // Solo dibujamos si el rango calculado es válido y tiene sentido
            if (textRange.endOffset > element.containingFile.textLength) {
                logger.warn("Text range out of bounds: $textRange for element: $element, problem: $this")
                return@forEach
            }

            val builder = when (severity) {
                ProblemSeverity.INFORMATION -> holder.newAnnotation(HighlightSeverity.INFORMATION, message)
                ProblemSeverity.WARNING -> holder.newAnnotation(HighlightSeverity.WARNING, message)
                ProblemSeverity.ERROR -> holder.newAnnotation(HighlightSeverity.ERROR, message)
            }

            // TODO add domain builder problem to provide more info like quick fix, fix, text attributes, etc
            builder.range(textRange).create()
        }
    }

    fun AnnotationHolder.highlight(
        range: TextRange,
        key: TextAttributesKey,
        severity: HighlightSeverity = HighlightSeverity.INFORMATION,
    ) {
        newSilentAnnotation(severity)
            .textAttributes(key)
            .range(range)
            .create()
    }
}
