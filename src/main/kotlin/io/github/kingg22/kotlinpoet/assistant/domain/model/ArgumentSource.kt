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
    @JvmInline
    value class NamedMap(val entries: Map<String, ArgumentValue>) : ArgumentSource
}
