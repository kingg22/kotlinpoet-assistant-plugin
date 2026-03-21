package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan

/**
 * The complete, locally-computed contribution of a single KotlinPoet builder call
 * to the eventual `CodeBlock` output.
 *
 * ## Locality guarantee
 *
 * A [MethodEmissionContribution] is computed from **one** `KtCallExpression` in isolation —
 * it does not depend on the accumulated [EmissionState] of the chain leading up to it.
 * This makes it safe to cache per-call and invalidate independently.
 *
 * ## Composition
 *
 * The chain analysis layer composes contributions by:
 * 1. Walking backward in PSI to collect predecessor contributions.
 * 2. Folding [EmissionState.apply] over each [stateDelta] in document order.
 * 3. Concatenating [parts] to build the approximated output.
 *
 * @param methodName The KotlinPoet builder method name (e.g. `"addStatement"`, `"add"`).
 * @param semantics The classified [MethodSemantics] of this call.
 * @param parts Ordered [EmittedPart]s representing what this call contributes to the output.
 *        Includes both the format-string content and any implicit structural parts
 *        injected by the method (e.g., the `«`/`»` in `addStatement`).
 * @param stateDelta The [EmissionStateDelta] representing the state transitions this call
 *        produces. Used for chain validation without materializing the full output.
 * @param resolvability Summary of how completely the arguments were resolved.
 * @param callSpan Absolute file offsets of the `KtCallExpression` in the source file.
 *        Used for tool window navigation and cache invalidation.
 */
data class MethodEmissionContribution(
    val methodName: String,
    val semantics: MethodSemantics,
    val parts: List<EmittedPart>,
    val stateDelta: EmissionStateDelta,
    val resolvability: ContributionResolvability,
    val callSpan: TextSpan,
) {
    /** True when every placeholder argument was resolved to a concrete value. */
    val isFullyResolved: Boolean
        get() = resolvability is ContributionResolvability.FullyResolved

    /** True when this call's emission state transitions can produce a [ChainViolation]. */
    val hasStatefulTransitions: Boolean
        get() = stateDelta.events.isNotEmpty()

    /**
     * Renders a human-readable approximation of the text this call emits.
     *
     * - Resolved placeholders show their concrete value.
     * - Unresolved placeholders show `[%L?]`, `[%T?]`, etc.
     * - Implicit control symbols are included.
     * - `%%` is rendered as `%` (the actual emitted character).
     * - Nested CodeBlock contributions are recursively expanded.
     *
     * This is an **approximation** — the real output depends on runtime state
     * (imports, indent context, etc.). The plugin is transparent about this.
     */
    fun approximateText(): String = buildString {
        for (part in parts) {
            append(part.approxText())
        }
    }

    /**
     * Renders only the parts that came from this call explicitly (excluding implicit parts).
     * Useful to show what the user actually wrote vs what was injected by the method.
     */
    fun explicitText(): String = buildString {
        for (part in parts.filter { it.origin is EmissionOrigin.ExplicitCall }) {
            append(part.approxText())
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────────

private fun EmittedPart.approxText(): String = when (this) {
    is EmittedPart.FormatLiteral -> text

    is EmittedPart.ResolvedPlaceholder -> resolvedText

    // Show the original placeholder token — no brackets, no `?`
    // The tool window row already distinguishes resolved vs unresolved by color
    is EmittedPart.UnresolvedPlaceholder -> "[${placeholder.tokenText()}]"

    is EmittedPart.ControlSymbolPart -> when (symbolType.value) {
        "%%" -> "%"

        // Statement markers are invisible in the raw approximation — the renderer
        // (ChainTextRenderer) handles indentation-aware rendering of «/»
        "«", "»" -> ""

        else -> symbolType.value
    }

    is EmittedPart.NestedCodeBlockPart -> {
        val nested = nestedContributions.joinToString("") { it.approximateText() }
        // Fall back to the token text so the preview shows %L, not "[CodeBlock]"
        nested.ifEmpty { "[${placeholder.tokenText()}]" }
    }
}
