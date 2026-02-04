package io.github.kingg22.kotlinpoet.assistant.domain.model.validation

interface FormatValidator {
    fun validate(context: BoundContext): List<FormatProblem>
}
