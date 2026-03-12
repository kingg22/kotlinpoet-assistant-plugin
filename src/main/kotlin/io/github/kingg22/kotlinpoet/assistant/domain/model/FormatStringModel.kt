package io.github.kingg22.kotlinpoet.assistant.domain.model

import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem

/** Model a string with format specifiers. */
data class FormatStringModel(
    val text: FormatText,
    val style: FormatStyle,
    val placeholders: List<PlaceholderSpec>,
    val controlSymbols: List<ControlSymbol>,
    /** Earlier errors detected during parsing or extraction */
    val errors: List<FormatProblem> = emptyList(),
) {
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
