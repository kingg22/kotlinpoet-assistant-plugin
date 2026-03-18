package io.github.kingg22.kotlinpoet.assistant.domain.validation

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder

interface FormatValidator {
    fun validate(bound: List<BoundPlaceholder>, arguments: ArgumentSource): List<FormatProblem>
}
