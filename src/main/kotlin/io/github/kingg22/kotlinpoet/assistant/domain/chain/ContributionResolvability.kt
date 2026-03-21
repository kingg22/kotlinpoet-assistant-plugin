package io.github.kingg22.kotlinpoet.assistant.domain.chain

/**
 * Summarizes how completely the arguments of a [MethodEmissionContribution] were resolved.
 *
 * The plugin never silently ignores unresolved arguments — it reports them via this
 * sealed type so the UI can mark them appropriately.
 */
sealed interface ContributionResolvability {
    /** Every placeholder argument was resolved to a concrete value or CodeBlock expansion. */
    data object FullyResolved : ContributionResolvability

    /**
     * Some arguments were resolved and some were not.
     * @param unresolvedArgs The list of placeholders that could not be resolved,
     *        each paired with the reason.
     */
    data class PartiallyResolved(val unresolvedArgs: List<UnresolvedArg>) : ContributionResolvability

    /**
     * No meaningful resolution was possible for this call (e.g., the format string
     * itself is a variable reference, or the method semantics are [MethodSemantics.UnknownCall]).
     */
    data object Unresolvable : ContributionResolvability
}
