package io.github.kingg22.kotlinpoet.assistant.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.kingg22.kotlinpoet.assistant.domain.model.binding.BindingEngineResolver
import io.github.kingg22.kotlinpoet.assistant.domain.model.extractor.FormatContextExtractorRegistry
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.BoundContext
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.FormatValidatorRegistry
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.ProblemTarget
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

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

        // 1. Extraemos el contexto (Modelos de dominio)
        val callContext = extractorRegistry.extract(element) ?: return

        // 2. Localizamos el PSI del String de formato para calcular los offsets reales
        // Asumimos que es el primer argumento (estándar en KotlinPoet)
        val formatArg = element.valueArguments.firstOrNull()?.getArgumentExpression()

        // Calculamos el desplazamiento (shift)
        // Si es un Template String, el contenido empieza después de las comillas.
        val contentShift = if (formatArg is KtStringTemplateExpression) {
            // startOffset del PSI + longitud de la comilla de apertura (" o """)
            val quoteSize = if (formatArg.text.startsWith("\"\"\"")) 3 else 1
            formatArg.textRange.startOffset + quoteSize
        } else {
            // Si es una constante evaluada u otro tipo, usamos el rango del elemento completo como fallback
            // Ojo: esto podría no ser preciso si el extractor usó evaluate(), pero evita el crash.
            formatArg?.textRange?.startOffset ?: element.textRange.startOffset
        }

        // Helper para renderizar reportes
        fun renderProblem(problem: FormatProblem) {
            val textRange = when (val target = problem.target) {
                ProblemTarget.Call -> element.textRange

                // Rango de toda la llamada
                is ProblemTarget.TextRange,
                // Si tienes un target para Placeholders ya bound:
                is ProblemTarget.Placeholder,
                -> {
                    // AQUÍ ESTÁ EL FIX: Rango Relativo + Shift Absoluto
                    val start = target.range.first + contentShift
                    val end = target.range.last + 1 + contentShift
                    TextRange(start, end)
                }
            }

            // Validación de seguridad para evitar "Range must be inside element"
            // Solo dibujamos si el rango calculado es válido y tiene sentido
            if (textRange.endOffset > element.containingFile.textLength) return

            val builder = when (problem.severity) {
                ProblemSeverity.INFORMATION -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                ProblemSeverity.WARNING -> holder.newAnnotation(HighlightSeverity.WARNING, problem.message)
                ProblemSeverity.ERROR -> holder.newAnnotation(HighlightSeverity.ERROR, problem.message)
            }

            builder.range(textRange).create()
        }

        // 3. Renderizar errores de parseo (ej: mezcla inválida, sintaxis rota)
        if (callContext.format.errors.isNotEmpty()) {
            callContext.format.errors.forEach { renderProblem(it) }
            return // Si el formato está roto, no intentamos validar argumentos
        }

        // 4. Lógica de Binding y Validación
        try {
            val bindingEngine = bindingResolver.forStyle(callContext.format.style)
            val boundContext = bindingEngine.bind(callContext.format, callContext.arguments)
            val problems = validatorRegistry.validate(BoundContext(boundContext))

            problems.forEach { renderProblem(it) }
        } catch (e: Exception) {
            // Fail-safe para no romper el editor si el binding falla inesperadamente
            logger.error("Error binding format or validate Kotlinpoet", e)
        }
    }
}
