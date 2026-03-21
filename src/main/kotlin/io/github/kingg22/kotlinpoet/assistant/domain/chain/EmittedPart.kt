package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan

/**
 * A typed fragment of the text that a single KotlinPoet builder call contributes
 * to the final `CodeBlock`.
 *
 * Together, the `parts` of a [MethodEmissionContribution] describe **what** was emitted
 * and **where it came from** ([EmissionOrigin]). This enables the tool window to display
 * provenance at part-level granularity.
 *
 * ## Part ordering
 *
 * Parts within a [MethodEmissionContribution] are ordered in emission sequence — i.e., the
 * order they would appear in the final rendered `CodeBlock`. Implicit parts (injected by
 * wrapper methods) are interleaved at the correct position:
 *
 * For `addStatement("%N = %S", name, value)`:
 * ```
 * ControlSymbolPart(STATEMENT_BEGIN, implicit=true)   // implicit «
 * ResolvedPlaceholder(%N → "myVar")
 * FormatLiteral(" = ")
 * ResolvedPlaceholder(%S → "\"hello\"")
 * FormatLiteral("\n")                                  // from the \n before »
 * ControlSymbolPart(STATEMENT_END, implicit=true)      // implicit »
 * ```
 */
sealed interface EmittedPart {

    /** The [EmissionOrigin] of this specific part. */
    val origin: EmissionOrigin

    /**
     * A literal text segment from the format string (not a placeholder).
     * Example: `"val "`, `" = "`, `"\n"`, `"} else {\n"`.
     *
     * @param text The literal text as it would appear in the output.
     */
    data class FormatLiteral(val text: String, override val origin: EmissionOrigin) : EmittedPart

    /**
     * A placeholder whose argument was successfully resolved to a concrete text value.
     *
     * @param placeholder The parsed placeholder spec (kind, binding, source span).
     * @param resolvedText The concrete text the argument resolves to.
     *        For `%S`, this includes the wrapping quotes and escaping.
     *        For `%T`, this is the simple or fully-qualified type name.
     *        For `%L`, this is the raw literal value.
     * @param argSpan Absolute file offsets of the argument expression in the source,
     *        used for navigation from the tool window.
     */
    data class ResolvedPlaceholder(
        val placeholder: PlaceholderSpec,
        val resolvedText: String,
        val argSpan: TextSpan?,
        override val origin: EmissionOrigin,
    ) : EmittedPart

    /**
     * A placeholder whose argument could not be resolved.
     * The tool window displays this as `[%L?]`, `[%T?]`, etc.
     *
     * @param placeholder The parsed placeholder spec.
     * @param reason Why resolution failed.
     */
    data class UnresolvedPlaceholder(
        val placeholder: PlaceholderSpec,
        val reason: UnresolvedReason,
        override val origin: EmissionOrigin,
    ) : EmittedPart

    /**
     * A KotlinPoet control symbol (`«`, `»`, `⇥`, `⇤`, `%%`, `·`, `♢`).
     *
     * @param symbolType The type of control symbol.
     * @param implicit `true` if this symbol was injected by a wrapper method
     *        (e.g., the `«` added by `addStatement`), `false` if the user wrote it
     *        explicitly in the format string.
     */
    data class ControlSymbolPart(
        val symbolType: ControlSymbol.SymbolType,
        val implicit: Boolean,
        override val origin: EmissionOrigin,
    ) : EmittedPart

    /**
     * A `%L` placeholder whose argument is another `CodeBlock` (or a builder chain
     * that was recursively analyzed).
     *
     * @param placeholder The `%L` placeholder spec.
     * @param nestedContributions The ordered contributions from the nested CodeBlock chain.
     *        Empty if the nested chain could not be analyzed.
     * @param argSpan Absolute file offsets of the `CodeBlock` argument expression.
     */
    data class NestedCodeBlockPart(
        val placeholder: PlaceholderSpec,
        val nestedContributions: List<MethodEmissionContribution>,
        val argSpan: TextSpan?,
        override val origin: EmissionOrigin,
    ) : EmittedPart
}
