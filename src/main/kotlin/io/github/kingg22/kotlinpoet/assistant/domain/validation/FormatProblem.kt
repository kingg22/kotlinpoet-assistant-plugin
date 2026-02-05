package io.github.kingg22.kotlinpoet.assistant.domain.validation

import org.jetbrains.annotations.Nls

data class FormatProblem(
    val severity: ProblemSeverity,
    @param:Nls @field:Nls @get:Nls val message: String,
    val target: ProblemTarget,
)
