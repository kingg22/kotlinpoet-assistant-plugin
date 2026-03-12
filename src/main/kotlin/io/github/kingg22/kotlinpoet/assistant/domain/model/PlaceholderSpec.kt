package io.github.kingg22.kotlinpoet.assistant.domain.model

import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan

data class PlaceholderSpec(val kind: FormatKind, val binding: PlaceholderBinding, val span: TextSpan) {
    @JvmInline
    value class FormatKind private constructor(val value: Char) {
        companion object {
            val LITERAL: FormatKind = FormatKind('L')
            val STRING: FormatKind = FormatKind('S')
            val TYPE: FormatKind = FormatKind('T')
            val MEMBER: FormatKind = FormatKind('M')
            val NAME: FormatKind = FormatKind('N')
            val STRING_TEMPLATE: FormatKind = FormatKind('P')

            private val ALL = mapOf(
                'L' to LITERAL,
                'S' to STRING,
                'T' to TYPE,
                'M' to MEMBER,
                'N' to NAME,
                'P' to STRING_TEMPLATE,
            )

            @JvmStatic
            fun fromChar(char: Char): FormatKind? = ALL[char]
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
