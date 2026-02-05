package io.github.kingg22.kotlinpoet.assistant.domain.validation

interface FormatValidator {
    fun validate(context: BoundContext): List<FormatProblem>
}
