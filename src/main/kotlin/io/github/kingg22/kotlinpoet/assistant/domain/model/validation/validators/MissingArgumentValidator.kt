package io.github.kingg22.kotlinpoet.assistant.domain.model.validation.validators

import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.BoundContext
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.FormatValidator
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.model.validation.ProblemTarget

class MissingArgumentValidator : FormatValidator {
    override fun validate(context: BoundContext): List<FormatProblem> {
        // Skip named arguments because can be runtime and static analysis is not enabled to resolve it
        if (context.bound.any { it.placeholder.binding is PlaceholderSpec.PlaceholderBinding.Named }) {
            return emptyList()
        }

        return context.bound.filter { it.argument == null }.map { boundPlaceholder: BoundPlaceholder ->
            FormatProblem(
                severity = ProblemSeverity.ERROR,
                message = KPoetAssistantBundle.getMessage(
                    "argument.format.missing",
                    boundPlaceholder.placeholder.kind.value,
                ),
                target = ProblemTarget.Placeholder(boundPlaceholder.placeholder.textRange),
            )
        }
    }
}
