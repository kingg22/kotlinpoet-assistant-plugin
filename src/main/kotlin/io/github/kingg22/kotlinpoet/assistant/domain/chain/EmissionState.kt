package io.github.kingg22.kotlinpoet.assistant.domain.chain

import org.jetbrains.annotations.VisibleForTesting

/**
 * The accumulated emission state at a given point in a CodeBlock builder chain.
 *
 * This models the subset of `CodeWriter`'s mutable state that is relevant for
 * static validation:
 *
 * - **`statementLine`** mirrors `CodeWriter.statementLine`:
 *   - `-1` = not inside a statement
 *   - `>= 0` = inside a statement (value = current line within that statement)
 *
 * - **`indentLevel`** mirrors `CodeWriter.indentLevel`.
 *
 * [EmissionState] is **immutable**. [apply] returns a new state or a [StateApplyResult.Failure]
 * on constraint violation — it never mutates.
 *
 * ## Composition model
 *
 * The state is never cached for a whole chain. It is recomputed on demand by composing
 * the cached [EmissionStateDelta]s of individual calls walking backward in PSI.
 *
 * @see EmissionStateDelta
 * @see StateApplyResult
 * @see ChainViolation
 */
data class EmissionState(
    /** `-1` when outside a statement scope; `>= 0` when inside. */
    val statementLine: Int,
    val indentLevel: Int,
) {
    init {
        require(statementLine >= -1) { "statementLine must be >= -1, was $statementLine" }
        require(indentLevel >= 0) { "indentLevel must be >= 0, was $indentLevel" }
    }

    val isInStatement: Boolean get() = statementLine >= 0

    companion object {
        /** The state at the very start of a builder chain — no nesting, no indentation. */
        @JvmStatic
        val Initial: EmissionState = EmissionState(statementLine = -1, indentLevel = 0)
    }

    /**
     * Applies all [StateTransition]s in [delta] in order, returning the resulting state
     * or the first [ChainViolation] encountered.
     *
     * Processing stops at the first violation; subsequent transitions are NOT applied.
     *
     * @param delta The delta to apply. An empty delta returns [StateApplyResult.Success] immediately.
     */
    fun apply(delta: EmissionStateDelta): StateApplyResult {
        if (delta.events.isEmpty()) return StateApplyResult.Success(this)
        var current = this
        for (transition in delta.events) {
            when (val step = current.applyOne(transition)) {
                is StateApplyResult.Success -> current = step.newState
                is StateApplyResult.Failure -> return step
            }
        }
        return StateApplyResult.Success(current)
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun applyOne(transition: StateTransition): StateApplyResult = when (transition) {
        StateTransition.OpenStatement -> {
            if (isInStatement) {
                StateApplyResult.Failure(
                    violation = ChainViolation.DoubleOpenStatement(
                        currentStatementLine = statementLine,
                        indentLevel = indentLevel,
                    ),
                    stateBeforeViolation = this,
                )
            } else {
                StateApplyResult.Success(copy(statementLine = 0))
            }
        }

        StateTransition.CloseStatement -> {
            if (!isInStatement) {
                StateApplyResult.Failure(
                    violation = ChainViolation.CloseWithoutOpenStatement(indentLevel = indentLevel),
                    stateBeforeViolation = this,
                )
            } else {
                StateApplyResult.Success(copy(statementLine = -1))
            }
        }

        StateTransition.IncrementIndent -> StateApplyResult.Success(copy(indentLevel = indentLevel + 1))

        StateTransition.DecrementIndent -> {
            if (indentLevel <= 0) {
                StateApplyResult.Failure(
                    violation = ChainViolation.NegativeIndent(currentLevel = indentLevel),
                    stateBeforeViolation = this,
                )
            } else {
                StateApplyResult.Success(copy(indentLevel = indentLevel - 1))
            }
        }
    }

    // ── Result type for EmissionState.apply ───────────────────────────────────────

    /**
     * The result of applying an [EmissionStateDelta] to an [EmissionState].
     *
     * @see EmissionState.apply
     */
    @VisibleForTesting
    sealed interface StateApplyResult {
        /**
         * All transitions in the delta were valid.
         * @param newState The state after all transitions were applied.
         */
        data class Success(val newState: EmissionState) : StateApplyResult

        /**
         * A [StateTransition] violated a constraint.
         * Processing stops at the first violation — later transitions in the same delta
         * are not applied.
         *
         * @param violation The specific constraint that was violated.
         * @param stateBeforeViolation The state at the moment the violation occurred
         *        (before the offending transition was applied).
         */
        data class Failure(val violation: ChainViolation, val stateBeforeViolation: EmissionState) : StateApplyResult
    }
}
