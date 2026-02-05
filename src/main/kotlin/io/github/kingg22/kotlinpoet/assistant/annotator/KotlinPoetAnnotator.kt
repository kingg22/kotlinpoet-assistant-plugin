package io.github.kingg22.kotlinpoet.assistant.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.domain.model.binding.BindingEngineResolver
import io.github.kingg22.kotlinpoet.assistant.domain.model.extractor.FormatContextExtractorRegistry
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.BoundContext
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.FormatValidatorRegistry
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.ProblemTarget
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtCallExpression

class KotlinPoetAnnotator
@JvmOverloads
constructor(
    private val bindingResolver: BindingEngineResolver = BindingEngineResolver,
    private val extractorRegistry: FormatContextExtractorRegistry = FormatContextExtractorRegistry,
    private val validatorRegistry: FormatValidatorRegistry = FormatValidatorRegistry,
) : Annotator {
    private val logger = thisLogger()

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KtCallExpression) return

        try {
            // 1. Extraemos el contexto (Modelos de dominio)
            val callContext = extractorRegistry.extract(element) ?: return

            // 2. Renderizar errores de parseo (ej: mezcla inválida, sintaxis rota)
            if (callContext.format.errors.isNotEmpty()) {
                callContext.format.errors.forEach { it.renderProblem(element, holder) }
                return // Si el formato está roto, no intentamos validar argumentos
            }

            // 3. Lógica de Binding y Validación
            val bindingEngine = bindingResolver.forStyle(callContext.format.style)
            val boundContext = bindingEngine.bind(callContext.format, callContext.arguments)
            val problems = validatorRegistry.validate(BoundContext(boundContext))

            problems.forEach { it.renderProblem(element, holder) }
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            // Fail-safe para no romper el editor si el binding falla inesperadamente
            logger.error("Error binding format or validate Kotlinpoet", e)
        }
    }

    /** Helper para renderizar reportes */
    @VisibleForTesting
    fun FormatProblem.renderProblem(element: PsiElement, holder: AnnotationHolder) {
        val textRange = target.toTextRange(element)

        // Validación de seguridad para evitar "Range must be inside element"
        // Solo dibujamos si el rango calculado es válido y tiene sentido
        if (textRange.endOffset > element.containingFile.textLength) return

        val builder = when (severity) {
            ProblemSeverity.INFORMATION -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            ProblemSeverity.WARNING -> holder.newAnnotation(HighlightSeverity.WARNING, message)
            ProblemSeverity.ERROR -> holder.newAnnotation(HighlightSeverity.ERROR, message)
        }

        // TODO add domain builder problem to provide more info like quick fix, fix, text attributes, etc
        builder.range(textRange).create()
    }

    @VisibleForTesting
    fun ProblemTarget.toTextRange(element: PsiElement): TextRange = when (this) {
        ProblemTarget.Call -> element.textRange

        // Rango de toda la llamada
        is ProblemTarget.TextRange,
        // Si tienes un target para Placeholders ya bound:
        is ProblemTarget.Placeholder,
        -> {
            val start = range.first
            val end = range.last + 1
            TextRange(start, end)
        }
    }
}
