package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.currentClassLogger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.endOffset
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRanges
import org.jetbrains.kotlin.psi.KtCallExpression

inline fun registerProblem(
    problem: FormatProblem,
    element: KtCallExpression,
    holder: ProblemsHolder,
    crossinline fixes: (TextRange) -> Array<LocalQuickFix> = { emptyArray() },
) {
    problem.target.toTextRanges(element).forEach { textRange ->
        // Validación de seguridad para evitar "Range must be inside element"
        // Solo dibujamos si el rango calculado es válido y tiene sentido
        if (textRange.endOffset > element.containingFile.textLength || textRange.endOffset > element.endOffset) {
            currentClassLogger().warn("Text range out of bounds: $textRange for element: $element, problem: $problem")
            return@forEach
        }

        val highlightType = when (problem.severity) {
            ProblemSeverity.ERROR -> ProblemHighlightType.ERROR
            ProblemSeverity.WARNING -> ProblemHighlightType.WARNING
            ProblemSeverity.INFORMATION -> ProblemHighlightType.WEAK_WARNING
        }

        holder.registerProblem(
            element,
            problem.message,
            highlightType,
            textRange,
            *fixes(textRange),
        )
    }
}
