package io.github.kingg22.kotlinpoet.assistant.domain.validation.validators

import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatValidator
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget

class ExtraArgumentValidator : FormatValidator {
    override fun validate(bound: List<BoundPlaceholder>, arguments: ArgumentSource): List<FormatProblem> {
        val placeholders = bound.map { it.placeholder }
        if (placeholders.isEmpty()) return emptyList()

        return when (arguments) {
            is ArgumentSource.VarArgs -> {
                if (placeholders.any { it.binding is PlaceholderSpec.PlaceholderBinding.Relative }) {
                    val extra = arguments.arguments.drop(placeholders.size)
                    extra.mapIndexed { i, arg ->
                        buildArgumentProblem(
                            index1Based = placeholders.size + i + 1,
                            span = arg.span,
                        )
                    }
                } else if (placeholders.any { it.binding is PlaceholderSpec.PlaceholderBinding.Positional }) {
                    val used = placeholders.mapNotNull {
                        (it.binding as? PlaceholderSpec.PlaceholderBinding.Positional)?.index1Based
                    }.toSet()
                    arguments.arguments.mapIndexedNotNull { i, arg ->
                        val index1 = i + 1
                        if (index1 in used) null else buildArgumentProblem(index1, arg.span)
                    }
                } else {
                    emptyList()
                }
            }

            is ArgumentSource.NamedMap -> {
                if (!arguments.isComplete) return emptyList()
                val used = placeholders.mapNotNull {
                    (it.binding as? PlaceholderSpec.PlaceholderBinding.Named)?.name
                }.toSet()
                arguments.entries
                    .filterKeys { it !in used }
                    .mapNotNull { (name, arg) ->
                        buildNamedProblem(name, arg.span)
                    }
            }
        }
    }

    private fun buildArgumentProblem(index1Based: Int, span: TextSpan?): FormatProblem {
        val target = span?.let { ProblemTarget.Argument(it) } ?: ProblemTarget.Call
        return FormatProblem(
            severity = ProblemSeverity.ERROR,
            message = KPoetAssistantBundle.getMessage("argument.format.extra.index", index1Based),
            target = target,
        )
    }

    private fun buildNamedProblem(name: String, span: TextSpan?): FormatProblem {
        val target = span?.let { ProblemTarget.Argument(it) } ?: ProblemTarget.Call
        return FormatProblem(
            severity = ProblemSeverity.INFORMATION,
            message = KPoetAssistantBundle.getMessage("argument.format.extra.name", name),
            target = target,
        )
    }
}
