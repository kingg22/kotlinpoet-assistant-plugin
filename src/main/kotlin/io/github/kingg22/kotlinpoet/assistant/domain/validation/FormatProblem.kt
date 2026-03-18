package io.github.kingg22.kotlinpoet.assistant.domain.validation

import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.ParserIssueKind
import org.jetbrains.annotations.Nls

data class FormatProblem(
    val severity: ProblemSeverity,
    @param:Nls @field:Nls @get:Nls val message: String,
    val target: ProblemTarget,
    val kind: ParserIssueKind? = null,
    val data: Any? = null,
)
