package io.github.kingg22.kotlinpoet.assistant.domain.validation.validators

import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatValidator
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget

class MissingArgumentValidator : FormatValidator {
    override fun validate(bound: List<BoundPlaceholder>, arguments: ArgumentSource): List<FormatProblem> {
        // Skip named arguments when the map isn't fully resolved.
        if (bound.any { it.placeholder.binding is PlaceholderSpec.PlaceholderBinding.Named }) {
            val named = arguments as? ArgumentSource.NamedMap
            if (named == null || !named.isComplete) return emptyList()
        }

        return bound.filter { it.argument == null }.map { boundPlaceholder: BoundPlaceholder ->
            FormatProblem(
                severity = ProblemSeverity.ERROR,
                message = KPoetAssistantBundle.getMessage(
                    "argument.format.missing",
                    boundPlaceholder.placeholder.kind.value,
                ),
                target = ProblemTarget.Placeholder(boundPlaceholder.placeholder.span),
            )
        }
    }
}
