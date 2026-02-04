package io.github.kingg22.kotlinpoet.assistant.domain.model

/**
 * A placeholder with an argument value if present, or null otherwise.
 *
 * Is the result of binding a format string to an argument source.
 */
data class BoundPlaceholder(val placeholder: PlaceholderSpec, val argument: ArgumentValue?)
