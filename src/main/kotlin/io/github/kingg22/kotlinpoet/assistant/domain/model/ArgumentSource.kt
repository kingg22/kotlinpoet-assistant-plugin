package io.github.kingg22.kotlinpoet.assistant.domain.model

/**
 * Defines the source of argument.
 *
 * Can be [vararg][VarArgs] or a [Map][NamedMap]
 */
sealed interface ArgumentSource {
    /** Equivalent to `vararg` argument */
    @JvmInline
    value class VarArgs(val arguments: List<ArgumentValue>) : ArgumentSource

    /** Equivalent to named arguments stored in a [Map] */
    data class NamedMap(val entries: Map<String, ArgumentValue>, val isComplete: Boolean) : ArgumentSource
}
