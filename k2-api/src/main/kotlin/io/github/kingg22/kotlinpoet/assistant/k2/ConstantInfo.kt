package io.github.kingg22.kotlinpoet.assistant.k2

/**
 * Result of constant evaluation.
 *
 * @property value boxed constant (`String`, `Int`, `UInt`, ...); `null` for `null` and for erroneous constants.
 */
data class ConstantInfo(val value: Any?)
