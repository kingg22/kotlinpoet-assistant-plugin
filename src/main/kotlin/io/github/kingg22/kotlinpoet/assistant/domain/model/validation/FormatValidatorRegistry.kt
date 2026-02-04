package io.github.kingg22.kotlinpoet.assistant.domain.model.validation

import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.validators.MissingArgumentValidator

object FormatValidatorRegistry {
    @PublishedApi
    internal val validators: List<FormatValidator> = listOf(MissingArgumentValidator())

    @Suppress("NOTHING_TO_INLINE")
    @JvmStatic
    inline fun validate(boundContext: BoundContext): List<FormatProblem> =
        validators.flatMap { it.validate(boundContext) }
}
