package io.github.kingg22.kotlinpoet.assistant.domain.model

import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import org.jetbrains.annotations.Range

/**
 * Holds an argument value.
 * @property index 1-based. null si es Named
 * @property name The name of the argument, null si es Relative/Positional
 * @property type The type of the argument
 */
@ConsistentCopyVisibility
data class ArgumentValue private constructor(
    val index: Int?,
    val name: String?,
    val type: ArgumentType,
    val span: TextSpan?,
) {
    init {
        require((index == null && name != null) || (index != null && name == null)) {
            "Either index or name must be null, not both"
        }
        require(index == null || index > 0) { "Index must be positive" }
    }
    val isRelative: Boolean get() = index == null
    val isPositional: Boolean get() = index != null
    val isNamed: Boolean get() = name != null

    companion object {
        @JvmStatic
        fun positionalOrRelative(
            index:
            @Range(from = 0, to = Long.MAX_VALUE)
            Int,
            type: ArgumentType,
            span: TextSpan? = null,
        ): ArgumentValue = ArgumentValue(index, null, type, span)

        @JvmStatic
        fun named(name: String, type: ArgumentType, span: TextSpan? = null): ArgumentValue =
            ArgumentValue(null, name, type, span)
    }
}
