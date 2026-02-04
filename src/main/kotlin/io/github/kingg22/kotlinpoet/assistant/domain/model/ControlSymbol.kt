package io.github.kingg22.kotlinpoet.assistant.domain.model

/** Representa símbolos de control de KotlinPoet que no llevan argumentos. */
data class ControlSymbol(val type: SymbolType, val range: IntRange) {
    @JvmInline
    value class SymbolType private constructor(val value: String) {
        companion object {
            val LITERAL_PERCENT: SymbolType = SymbolType("%%")
            val SPACE_OR_NEW_LINE: SymbolType = SymbolType("♢")
            val SPACE: SymbolType = SymbolType("·")
            val INDENT: SymbolType = SymbolType("⇥")
            val OUTDENT: SymbolType = SymbolType("⇤")
            val STATEMENT_BEGIN: SymbolType = SymbolType("«")
            val STATEMENT_END: SymbolType = SymbolType("»")

            private val ALL = mapOf(
                "%%" to LITERAL_PERCENT,
                "♢" to SPACE_OR_NEW_LINE,
                "·" to SPACE,
                "⇥" to INDENT,
                "⇤" to OUTDENT,
                "«" to STATEMENT_BEGIN,
                "»" to STATEMENT_END,
            )

            @JvmStatic
            fun fromString(str: String): SymbolType? = ALL[str]
        }
    }
}
