package io.github.kingg22.kotlinpoet.assistant.domain.model

import org.jetbrains.annotations.Range

/**
 * Holds an argument value.
 * @property index 1-based. null si es Named
 * @property name The name of the argument, null si es Relative/Positional
 * @property type The type of the argument
 */
@ConsistentCopyVisibility
data class ArgumentValue private constructor(val index: Int?, val name: String?, val type: ArgumentType) {
    init {
        require((index == null && name != null) || (index != null && name == null)) {
            "Either index or name must be null, not both"
        }
        require(index == null || index > 0) { "Index must be positive" }
    }

    companion object {
        @JvmStatic
        fun positionalOrRelative(
            index:
            @Range(from = 0, to = Long.MAX_VALUE)
            Int,
            type: ArgumentType,
        ): ArgumentValue = ArgumentValue(index, null, type)

        @JvmStatic
        fun named(name: String, type: ArgumentType): ArgumentValue = ArgumentValue(null, name, type)
    }
}
