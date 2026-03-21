package io.github.kingg22.kotlinpoet.assistant.domain.chain

/**
 * The ordered sequence of [StateTransition]s that a single KotlinPoet builder call contributes.
 *
 * ## Key design property
 *
 * Deltas are computed **locally** — they depend only on [MethodSemantics] and the parsed
 * [io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel] of that specific call,
 * never on prior accumulated state. This makes them safe to cache independently per
 * `KtCallExpression`.
 *
 * ## Order matters
 *
 * [EmissionState.apply] processes [events] in sequence, stopping at the first [ChainViolation].
 * A delta with `[DecrementIndent, IncrementIndent]` can violate if `indentLevel == 0` at the
 * decrement — even though the net change is zero.
 *
 * @see MethodSemanticsClassifier.computeDelta
 * @see EmissionState.apply
 */
@JvmInline value class EmissionStateDelta(val events: List<StateTransition>) {

    companion object {
        /** No state change — plain text or unknown calls. */
        @JvmStatic
        val Zero: EmissionStateDelta = EmissionStateDelta(emptyList())

        /** Single `⇥` — `indent()` call or implicit from `beginControlFlow`. */
        @JvmStatic
        val Indent: EmissionStateDelta = EmissionStateDelta(listOf(StateTransition.IncrementIndent))

        /** Single `⇤` — `unindent()` call or implicit from `endControlFlow`. */
        @JvmStatic
        val Unindent: EmissionStateDelta = EmissionStateDelta(listOf(StateTransition.DecrementIndent))

        /**
         * `addStatement(format, *args)` — wraps the format with `«…»`.
         * Opens then immediately closes a statement scope.
         * Validates that no statement was already open when this call executes.
         */
        @JvmStatic
        val ForStatement: EmissionStateDelta = EmissionStateDelta(
            listOf(StateTransition.OpenStatement, StateTransition.CloseStatement),
        )

        /**
         * `beginControlFlow(format, *args)` — emits format + implicit ` {\n` then `⇥`.
         * Only the indent transition is tracked; the literal text comes from [EmittedPart].
         */
        @JvmStatic
        val ForControlFlowBegin: EmissionStateDelta = EmissionStateDelta(
            listOf(StateTransition.IncrementIndent),
        )

        /**
         * `nextControlFlow(format, *args)` — `⇤` then format + ` {\n` then `⇥`.
         * Net indent change is zero but the `DecrementIndent` validates indentLevel > 0.
         */
        @JvmStatic
        val ForControlFlowNext: EmissionStateDelta = EmissionStateDelta(
            listOf(StateTransition.DecrementIndent, StateTransition.IncrementIndent),
        )

        /**
         * `endControlFlow()` — `⇤` then `}\n`.
         * Validates that indentLevel > 0.
         */
        @JvmStatic
        val ForControlFlowEnd: EmissionStateDelta = EmissionStateDelta(
            listOf(StateTransition.DecrementIndent),
        )
    }
}
