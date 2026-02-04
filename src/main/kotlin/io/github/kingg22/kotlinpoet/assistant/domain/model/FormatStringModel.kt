package io.github.kingg22.kotlinpoet.assistant.domain.model

import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.ProblemTarget
import org.jetbrains.annotations.Contract

/** Model a string with format specifiers. */
data class FormatStringModel(
    val rawText: String,
    val style: FormatStyle,
    val placeholders: List<PlaceholderSpec>,
    val controlSymbols: List<ControlSymbol>,
    /** Earlier errors detected during parsing or extraction */
    val errors: List<FormatProblem> = emptyList(),
    val baseOffset: Int = 0,
) {
    @Contract(pure = true)
    fun withBaseOffset(offset: Int): FormatStringModel = this.copy(
        baseOffset = offset,
        // Re-mapeamos los rangos de los placeholders para que sean absolutos
        placeholders = placeholders.map { placeholderSpec ->
            placeholderSpec.copy(
                textRange =
                (placeholderSpec.textRange.first + offset)..(placeholderSpec.textRange.last + offset),
            )
        },
        // Re-mapeamos los errores
        errors = errors.map { err ->
            if (err.target is ProblemTarget.IntRangeTarget) {
                val r = err.target.range
                val newRange = (r.first + offset)..(r.last + offset)
                val newTarget = when (err.target) {
                    is ProblemTarget.TextRange -> ProblemTarget.TextRange(newRange)
                    is ProblemTarget.Placeholder -> ProblemTarget.Placeholder(newRange)
                    else -> error("Unreachable branch")
                }
                err.copy(target = newTarget)
            } else {
                err
            }
        },
    )

    /** Marker class for different types of format styles */
    sealed interface FormatStyle {
        data object Relative : FormatStyle
        data object Positional : FormatStyle
        data object Named : FormatStyle

        /** Se usa cuando conviven varios estilos (ej.: %L y %name:S) */
        data object Mixed : FormatStyle

        /** Cuando no hay ningún placeholder detectado */
        data object None : FormatStyle
    }
}
