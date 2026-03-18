package io.github.kingg22.kotlinpoet.assistant.domain.validation.validators

import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatValidator
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget

val NAMED_ARG_LOWERCASE: Regex = Regex("[a-z]+[\\w_]*")

/**
 * Validates that named argument identifiers conform to KotlinPoet's required pattern:
 * `[a-z]+[\w_]*` — they must start with a lowercase ASCII letter.
 *
 * This mirrors the runtime check in `CodeBlock.addNamed`:
 * ```
 * require(LOWERCASE matches argument) { "argument '$argument' must start with a lowercase character" }
 * ```
 *
 * Two independent checks are performed:
 *
 * ### 1. Placeholder names (always validated)
 * Names embedded in the **format string** (e.g. `%Food:L`) are always available from the parser
 * regardless of whether the argument map was fully resolved. A bad placeholder name guarantees a
 * runtime crash, so we always report it. Target: [ProblemTarget.Placeholder].
 *
 * ### 2. Map keys (validated only when [ArgumentSource.NamedMap.isComplete])
 * Keys in the supplied [ArgumentSource.NamedMap] are only validated when the map is fully resolved.
 * When the map is a reference to an external variable the extractor cannot enumerate all keys,
 * so we suppress validation to avoid false positives. Target: [ProblemTarget.Argument].
 *
 * When both a placeholder and a map key have the same invalid name (the normal case), only one
 * problem is reported — pointing at the placeholder — to avoid noise.
 *
 * [FormatProblem.data] carries the offending name as a [String] so the inspection layer can
 * construct a quick-fix without re-parsing the message.
 */
class NamedArgumentCaseValidator : FormatValidator {

    override fun validate(bound: List<BoundPlaceholder>, arguments: ArgumentSource): List<FormatProblem> {
        val namedMap = arguments as? ArgumentSource.NamedMap ?: return emptyList()
        val problems = mutableListOf<FormatProblem>()

        // ── 1. Validate placeholder names from the format string ──────────────
        // Always safe to check: the parser extracted them from the literal format string.
        // Deduplicate by name in case the same invalid name appears in multiple placeholders.
        bound
            .mapNotNull { bp ->
                (bp.placeholder.binding as? PlaceholderSpec.PlaceholderBinding.Named)?.let { bp to it }
            }
            .distinctBy { (_, binding) -> binding.name }
            .filter { (_, binding) -> !NAMED_ARG_LOWERCASE.matches(binding.name) }
            .forEach { (bp, binding) ->
                problems += FormatProblem(
                    severity = ProblemSeverity.ERROR,
                    message = KPoetAssistantBundle.getMessage("named.argument.lowercase", binding.name),
                    target = ProblemTarget.Placeholder(bp.placeholder.span),
                    data = binding.name,
                )
            }

        // ── 2. Validate map keys only when the map is fully resolved ──────────
        // Skip when isComplete = false: the extractor couldn't enumerate all entries
        // (e.g. the map comes from an external variable reference).
        namedMap.entries
            .filterNot { (name, _) -> NAMED_ARG_LOWERCASE.matches(name) }
            .forEach { (name, arg) ->
                // Suppress if already reported via the placeholder check above.
                if (problems.none { it.data == name }) {
                    problems += FormatProblem(
                        severity = ProblemSeverity.WARNING,
                        message = KPoetAssistantBundle.getMessage("named.argument.lowercase", name),
                        target = arg.span?.let { ProblemTarget.Argument(it) } ?: ProblemTarget.Call,
                        data = name,
                    )
                }
            }

        return problems
    }
}
