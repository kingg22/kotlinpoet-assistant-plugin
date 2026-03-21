package io.github.kingg22.kotlinpoet.assistant.domain.chain

/**
 * The result of successfully resolving an argument expression to displayable text.
 */
sealed interface ResolvedText {

    /** The displayable text as it would appear in the generated code. */
    val displayText: String

    /**
     * A string literal value. [value] is the raw unescaped content.
     * [displayText] wraps it in quotes.
     */
    data class StringLiteral(val value: String) : ResolvedText {
        override val displayText: String get() = "\"$value\""
    }

    /** A numeric literal (`Int`, `Long`, `Float`, `Double`). */
    data class NumberLiteral(val value: Number) : ResolvedText {
        override val displayText: String get() = value.toString()
    }

    /** A primitive value that isn't a number or string (e.g., `Boolean`, `Char`). */
    data class Primitive(override val displayText: String) : ResolvedText

    /**
     * A type reference resolved from a class literal (`SomeClass::class`).
     * [simpleName] is the unqualified class name.
     */
    data class TypeReference(val simpleName: String) : ResolvedText {
        override val displayText: String get() = simpleName
    }
}
