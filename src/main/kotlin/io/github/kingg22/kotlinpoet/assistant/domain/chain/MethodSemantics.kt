package io.github.kingg22.kotlinpoet.assistant.domain.chain

/**
 * Classifies the **semantic role** of a KotlinPoet builder method call with respect
 * to the CodeBlock emission model.
 *
 * Each variant encodes both what text is emitted and what implicit state transitions
 * ([StateTransition]) occur (statement nesting, indentation). This is the bridge
 * between a raw method name and the emission simulation.
 *
 * ## Implicit emissions
 *
 * Several methods inject content that is **not** part of the user-supplied format string:
 *
 * | Method               | Implicit content                         |
 * |----------------------|------------------------------------------|
 * | `addStatement`       | `«` before, `\n»` after the format       |
 * | `beginControlFlow`   | ` {\n` after the format + `⇥`           |
 * | `nextControlFlow`    | `⇤` before, `} ` prefix, ` {\n⇥` after |
 * | `endControlFlow`     | `⇤` + `}\n`                              |
 * | `indent`             | `⇥`                                      |
 * | `unindent`           | `⇤`                                      |
 *
 * @see MethodSemanticsClassifier
 * @see EmissionStateDelta
 */
sealed interface MethodSemantics {

    /**
     * Direct format emission without any implicit wrapping.
     * Covers: `add()`, `addCode()`, `addNamed()`, `CodeBlock.of()`.
     * State transitions are derived entirely from control symbols in the format string.
     */
    data object FormatCall : MethodSemantics

    /**
     * Internally equivalent to:
     * ```
     * add("«") + add(format, *args) + add("\n»")
     * ```
     * Implicitly wraps the format with [StateTransition.OpenStatement] and
     * [StateTransition.CloseStatement].
     */
    data object StatementCall : MethodSemantics

    /**
     * Internally equivalent to:
     * ```
     * add(format.withOpeningBrace(), *args) + indent()
     * ```
     * Implicitly appends ` {\n` (or `\n` if a brace is already present) and applies
     * [StateTransition.IncrementIndent].
     */
    data object ControlFlowBegin : MethodSemantics

    /**
     * Internally equivalent to:
     * ```
     * unindent() + add("} " + format + " {\n", *args) + indent()
     * ```
     * Applies [StateTransition.DecrementIndent] then [StateTransition.IncrementIndent].
     */
    data object ControlFlowNext : MethodSemantics

    /**
     * Internally equivalent to:
     * ```
     * unindent() + add("}\n")
     * ```
     * No format string. Applies [StateTransition.DecrementIndent].
     */
    data object ControlFlowEnd : MethodSemantics

    /** Emits `⇥` — applies [StateTransition.IncrementIndent]. */
    data object IndentCall : MethodSemantics

    /** Emits `⇤` — applies [StateTransition.DecrementIndent]. */
    data object UnindentCall : MethodSemantics

    /**
     * Format emission in KDoc context (`addKdoc`).
     * Treated as [FormatCall] for state-transition purposes.
     */
    data object KdocCall : MethodSemantics

    /** A method that starts a builder chain. Covers `CodeBlock.builder()`. */
    data object StartBuilder : MethodSemantics

    /**
     * The builder produces a `CodeBlock` from this point.
     * Covers `build()` and semantically equivalent wrappers.
     *
     * **The plugin does NOT rely on detecting terminal calls to bound a chain.**
     * Chains are bounded by what is visible in PSI, not by a known terminal.
     */
    data object TerminalCall : MethodSemantics

    /**
     * A method the plugin does not know how to analyze.
     * Treated as a chain break — the walker stops at this call.
     */
    data class UnknownCall(val name: String) : MethodSemantics
}
