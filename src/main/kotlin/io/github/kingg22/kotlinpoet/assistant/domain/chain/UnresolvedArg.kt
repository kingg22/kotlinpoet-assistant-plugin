package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec

/**
 * A placeholder in a format string that could not be resolved to a concrete value,
 * paired with the reason for the failure.
 *
 * @param placeholder The placeholder spec (kind, binding, span) from the parsed format.
 * @param reason Why resolution failed.
 */
data class UnresolvedArg(val placeholder: PlaceholderSpec, val reason: UnresolvedReason)
