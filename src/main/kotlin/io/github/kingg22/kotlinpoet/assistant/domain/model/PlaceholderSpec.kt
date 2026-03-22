package io.github.kingg22.kotlinpoet.assistant.domain.model

import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan

data class PlaceholderSpec(val kind: FormatKind, val binding: PlaceholderBinding, val span: TextSpan) {
    /**
     * Reconstructs the original placeholder token text.
     *
     * Examples:
     * - Relative `%L`     → `"%L"`
     * - Positional `%2S`  → `"%2S"`
     * - Named `%food:L`   → `"%food:L"`
     */
    fun tokenText(): String = when (val b = binding) {
        is PlaceholderBinding.Relative -> "%${kind.value}"
        is PlaceholderBinding.Positional -> "%${b.index1Based}${kind.value}"
        is PlaceholderBinding.Named -> "%${b.name}:${kind.value}"
    }

    enum class FormatKind(val value: Char) {
        LITERAL('L'),
        STRING('S'),
        TYPE('T'),
        MEMBER('M'),
        NAME('N'),
        STRING_TEMPLATE('P'),
        ;

        companion object {
            @JvmStatic
            fun fromChar(char: Char): FormatKind? = entries.firstOrNull { it.value == char }
        }
    }

    /** Marker class and storage value for different types of placeholders */
    sealed interface PlaceholderBinding {
        /** Example: %L */
        data object Relative : PlaceholderBinding

        /** Example: %2L */
        data class Positional(val index1Based: Int) : PlaceholderBinding

        /** Example: %count:L */
        data class Named(val name: String) : PlaceholderBinding
    }
}
