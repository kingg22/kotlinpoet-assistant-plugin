package io.github.kingg22.kotlinpoet.assistant.domain.validation

import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.ExtraArgumentValidator
import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.MissingArgumentValidator
import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.TypeMismatchValidator

object FormatValidatorRegistry {
    @PublishedApi
    internal val validators: List<FormatValidator> = listOf(
        MissingArgumentValidator(),
        ExtraArgumentValidator(),
        TypeMismatchValidator(),
    )

    @Suppress("NOTHING_TO_INLINE")
    @JvmStatic
    inline fun validate(boundContext: BoundContext): List<FormatProblem> =
        validators.flatMap { it.validate(boundContext) }
}
