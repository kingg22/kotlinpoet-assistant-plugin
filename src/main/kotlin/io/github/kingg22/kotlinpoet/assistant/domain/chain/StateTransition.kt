package io.github.kingg22.kotlinpoet.assistant.domain.chain

/**
 * A single state-machine event that changes the [EmissionState].
 *
 * Events are ordered. A single builder call can produce a **sequence** of transitions —
 * for example, `nextControlFlow` applies [DecrementIndent] then [IncrementIndent].
 * The order is critical: `DecrementIndent` when `indentLevel == 0` is a violation even
 * if a subsequent `IncrementIndent` would restore it.
 *
 * @see EmissionStateDelta
 * @see EmissionState.apply
 */
sealed interface StateTransition {
    /** Opens a statement scope (`«`). [EmissionState.statementLine] must be `-1`. */
    data object OpenStatement : StateTransition

    /** Closes a statement scope (`»`). [EmissionState.statementLine] must be `>= 0`. */
    data object CloseStatement : StateTransition

    /** Increases indentation level (`⇥`). [EmissionState.indentLevel] increments. */
    data object IncrementIndent : StateTransition

    /**
     * Decreases indentation level (`⇤`). [EmissionState.indentLevel] must be `> 0`;
     * otherwise produces [ChainViolation.NegativeIndent].
     */
    data object DecrementIndent : StateTransition
}
