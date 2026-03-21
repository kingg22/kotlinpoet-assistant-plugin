package io.github.kingg22.kotlinpoet.assistant.domain.chain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EmissionState] and [EmissionStateDelta] application.
 *
 * Covers:
 * - Initial state invariants
 * - Each [StateTransition] in isolation
 * - [ChainViolation] detection (double-open, close-without-open, negative-indent)
 * - Multi-event sequences (forStatement, controlFlow)
 * - Stopping at first violation in a sequence
 * - Composition of multiple apply() calls (chain simulation)
 */
class EmissionStateTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun delta(vararg events: StateTransition): EmissionStateDelta = EmissionStateDelta(events.toList())

    private fun EmissionState.applyExpectingSuccess(vararg events: StateTransition): EmissionState {
        val result = apply(delta(*events))
        assertInstanceOf(EmissionState.StateApplyResult.Success::class.java, result, "Expected Success but got $result")
        return (result as EmissionState.StateApplyResult.Success).newState
    }

    private fun EmissionState.applyExpectingFailure(
        vararg events: StateTransition,
    ): EmissionState.StateApplyResult.Failure {
        val result = apply(delta(*events))
        assertInstanceOf(EmissionState.StateApplyResult.Failure::class.java, result, "Expected Failure but got $result")
        return result as EmissionState.StateApplyResult.Failure
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Nested
    inner class InitialState {

        @Test
        fun `Initial state has statementLine -1`() {
            assertEquals(-1, EmissionState.Initial.statementLine)
        }

        @Test
        fun `Initial state has indentLevel 0`() {
            assertEquals(0, EmissionState.Initial.indentLevel)
        }

        @Test
        fun `Initial state is not in statement`() {
            assertFalse(EmissionState.Initial.isInStatement)
        }

        @Test
        fun `Empty delta on initial state returns same state`() {
            val result = EmissionState.Initial.apply(EmissionStateDelta.Zero)
            assertInstanceOf(EmissionState.StateApplyResult.Success::class.java, result)
            assertEquals(EmissionState.Initial, (result as EmissionState.StateApplyResult.Success).newState)
        }

        @Test
        fun `init rejects negative statementLine`() {
            assertThrows<IllegalArgumentException> {
                EmissionState(statementLine = -2, indentLevel = 0)
            }
        }

        @Test
        fun `init rejects negative indentLevel`() {
            assertThrows<IllegalArgumentException> {
                EmissionState(statementLine = -1, indentLevel = -1)
            }
        }
    }

    // ── OpenStatement ──────────────────────────────────────────────────────────

    @Nested
    inner class OpenStatementTransition {

        @Test
        fun `OpenStatement on initial state succeeds`() {
            val next = EmissionState.Initial.applyExpectingSuccess(StateTransition.OpenStatement)
            assertEquals(0, next.statementLine)
            assertTrue(next.isInStatement)
        }

        @Test
        fun `OpenStatement when already in statement produces DoubleOpenStatement`() {
            val inStatement = EmissionState(statementLine = 0, indentLevel = 0)
            val failure = inStatement.applyExpectingFailure(StateTransition.OpenStatement)
            assertInstanceOf(ChainViolation.DoubleOpenStatement::class.java, failure.violation)
            val v = failure.violation as ChainViolation.DoubleOpenStatement
            assertEquals(0, v.currentStatementLine)
            assertEquals(0, v.indentLevel)
        }

        @Test
        fun `DoubleOpenStatement preserves indentLevel in violation`() {
            val state = EmissionState(statementLine = 0, indentLevel = 3)
            val failure = state.applyExpectingFailure(StateTransition.OpenStatement)
            val v = failure.violation as ChainViolation.DoubleOpenStatement
            assertEquals(3, v.indentLevel)
        }

        @Test
        fun `stateBeforeViolation is the state prior to the offending transition`() {
            val inStatement = EmissionState(statementLine = 0, indentLevel = 2)
            val failure = inStatement.applyExpectingFailure(StateTransition.OpenStatement)
            assertEquals(inStatement, failure.stateBeforeViolation)
        }
    }

    // ── CloseStatement ─────────────────────────────────────────────────────────

    @Nested
    inner class CloseStatementTransition {

        @Test
        fun `CloseStatement when in statement succeeds`() {
            val inStatement = EmissionState(statementLine = 0, indentLevel = 1)
            val next = inStatement.applyExpectingSuccess(StateTransition.CloseStatement)
            assertEquals(-1, next.statementLine)
            assertFalse(next.isInStatement)
            assertEquals(1, next.indentLevel) // indent unchanged
        }

        @Test
        fun `CloseStatement when not in statement produces CloseWithoutOpenStatement`() {
            val failure = EmissionState.Initial.applyExpectingFailure(StateTransition.CloseStatement)
            assertInstanceOf(ChainViolation.CloseWithoutOpenStatement::class.java, failure.violation)
        }

        @Test
        fun `CloseWithoutOpenStatement preserves indentLevel`() {
            val state = EmissionState(statementLine = -1, indentLevel = 2)
            val failure = state.applyExpectingFailure(StateTransition.CloseStatement)
            val v = failure.violation as ChainViolation.CloseWithoutOpenStatement
            assertEquals(2, v.indentLevel)
        }
    }

    // ── IncrementIndent ────────────────────────────────────────────────────────

    @Nested
    inner class IncrementIndentTransition {

        @Test
        fun `IncrementIndent increases indentLevel by 1`() {
            val next = EmissionState.Initial.applyExpectingSuccess(StateTransition.IncrementIndent)
            assertEquals(1, next.indentLevel)
        }

        @Test
        fun `IncrementIndent applied three times gives level 3`() {
            val next = EmissionState.Initial
                .applyExpectingSuccess(StateTransition.IncrementIndent)
                .applyExpectingSuccess(StateTransition.IncrementIndent)
                .applyExpectingSuccess(StateTransition.IncrementIndent)
            assertEquals(3, next.indentLevel)
        }

        @Test
        fun `IncrementIndent does not change statementLine`() {
            val inStatement = EmissionState(statementLine = 0, indentLevel = 0)
            val next = inStatement.applyExpectingSuccess(StateTransition.IncrementIndent)
            assertEquals(0, next.statementLine)
        }
    }

    // ── DecrementIndent ────────────────────────────────────────────────────────

    @Nested
    inner class DecrementIndentTransition {

        @Test
        fun `DecrementIndent with level 1 reaches 0`() {
            val state = EmissionState(statementLine = -1, indentLevel = 1)
            val next = state.applyExpectingSuccess(StateTransition.DecrementIndent)
            assertEquals(0, next.indentLevel)
        }

        @Test
        fun `DecrementIndent with level 0 produces NegativeIndent`() {
            val failure = EmissionState.Initial.applyExpectingFailure(StateTransition.DecrementIndent)
            assertInstanceOf(ChainViolation.NegativeIndent::class.java, failure.violation)
            val v = failure.violation as ChainViolation.NegativeIndent
            assertEquals(0, v.currentLevel)
        }

        @Test
        fun `DecrementIndent does not change statementLine`() {
            val state = EmissionState(statementLine = 0, indentLevel = 2)
            val next = state.applyExpectingSuccess(StateTransition.DecrementIndent)
            assertEquals(0, next.statementLine)
            assertEquals(1, next.indentLevel)
        }
    }

    // ── Multi-event sequences ──────────────────────────────────────────────────

    @Nested
    inner class MultiEventSequences {

        @Test
        fun `ForStatement delta on initial state succeeds`() {
            val result = EmissionState.Initial.apply(EmissionStateDelta.ForStatement)
            assertInstanceOf(EmissionState.StateApplyResult.Success::class.java, result)
            val next = (result as EmissionState.StateApplyResult.Success).newState
            // addStatement: opens then closes → back to statementLine=-1
            assertEquals(-1, next.statementLine)
            assertEquals(0, next.indentLevel)
        }

        @Test
        fun `ForStatement delta when already in statement fails on first OpenStatement`() {
            val inStatement = EmissionState(statementLine = 0, indentLevel = 0)
            val failure = inStatement.applyExpectingFailure(
                StateTransition.OpenStatement,
                StateTransition.CloseStatement,
            )
            assertInstanceOf(ChainViolation.DoubleOpenStatement::class.java, failure.violation)
            // The state at violation is before OpenStatement was applied
            assertEquals(inStatement, failure.stateBeforeViolation)
        }

        @Test
        fun `ForControlFlowBegin increments indent`() {
            val result = EmissionState.Initial.apply(EmissionStateDelta.ForControlFlowBegin)
            assertInstanceOf(EmissionState.StateApplyResult.Success::class.java, result)
            assertEquals(1, (result as EmissionState.StateApplyResult.Success).newState.indentLevel)
        }

        @Test
        fun `ForControlFlowEnd decrements indent`() {
            val state = EmissionState(statementLine = -1, indentLevel = 1)
            val result = state.apply(EmissionStateDelta.ForControlFlowEnd)
            assertInstanceOf(EmissionState.StateApplyResult.Success::class.java, result)
            assertEquals(0, (result as EmissionState.StateApplyResult.Success).newState.indentLevel)
        }

        @Test
        fun `ForControlFlowEnd at indentLevel 0 fails`() {
            val failure = EmissionState.Initial.applyExpectingFailure(StateTransition.DecrementIndent)
            assertInstanceOf(ChainViolation.NegativeIndent::class.java, failure.violation)
        }

        @Test
        fun `ForControlFlowNext validates decrement first`() {
            // nextControlFlow = [DecrementIndent, IncrementIndent]
            // At indentLevel=0: fails on DecrementIndent
            val result = EmissionState.Initial.apply(EmissionStateDelta.ForControlFlowNext)
            assertInstanceOf(EmissionState.StateApplyResult.Failure::class.java, result)
            val failure = result as EmissionState.StateApplyResult.Failure
            assertInstanceOf(ChainViolation.NegativeIndent::class.java, failure.violation)
        }

        @Test
        fun `ForControlFlowNext at indentLevel 1 succeeds and keeps level 1`() {
            val state = EmissionState(statementLine = -1, indentLevel = 1)
            val result = state.apply(EmissionStateDelta.ForControlFlowNext)
            assertInstanceOf(EmissionState.StateApplyResult.Success::class.java, result)
            val next = (result as EmissionState.StateApplyResult.Success).newState
            // [-1, +1] → net 0
            assertEquals(1, next.indentLevel)
        }

        @Test
        fun `Stopping at first violation does not apply subsequent transitions`() {
            // Delta: [DecrementIndent (fails at 0), IncrementIndent]
            // Should stop after DecrementIndent fails; IncrementIndent must not be applied
            val initial = EmissionState.Initial // indentLevel = 0
            val failure = initial.applyExpectingFailure(
                StateTransition.DecrementIndent,
                StateTransition.IncrementIndent,
            )
            // stateBeforeViolation should be initial (not incremented)
            assertEquals(initial, failure.stateBeforeViolation)
            assertInstanceOf(ChainViolation.NegativeIndent::class.java, failure.violation)
        }
    }

    // ── Chain simulation ───────────────────────────────────────────────────────

    @Nested
    inner class ChainSimulation {

        @Test
        fun `Simulating beginControlFlow addStatement endControlFlow`() {
            // beginControlFlow → +indent
            val s1 = EmissionState.Initial.applyExpectingSuccess(StateTransition.IncrementIndent)
            assertEquals(1, s1.indentLevel)

            // addStatement inside → open + close statement
            val s2 = s1.applyExpectingSuccess(StateTransition.OpenStatement)
            val s3 = s2.applyExpectingSuccess(StateTransition.CloseStatement)
            assertEquals(-1, s3.statementLine)
            assertEquals(1, s3.indentLevel)

            // endControlFlow → -indent
            val s4 = s3.applyExpectingSuccess(StateTransition.DecrementIndent)
            assertEquals(EmissionState.Initial, s4)
        }

        @Test
        fun `Nested beginControlFlow increases indent twice`() {
            val s1 = EmissionState.Initial.applyExpectingSuccess(StateTransition.IncrementIndent)
            val s2 = s1.applyExpectingSuccess(StateTransition.IncrementIndent)
            assertEquals(2, s2.indentLevel)

            val s3 = s2.applyExpectingSuccess(StateTransition.DecrementIndent)
            assertEquals(1, s3.indentLevel)

            val s4 = s3.applyExpectingSuccess(StateTransition.DecrementIndent)
            assertEquals(EmissionState.Initial, s4)
        }

        @Test
        fun `Detecting unclosed statement at end of chain`() {
            // User wrote add("«%L") without a closing »
            val s1 = EmissionState.Initial.applyExpectingSuccess(StateTransition.OpenStatement)
            // s1.statementLine = 0 → statement is open at end of chain
            assertTrue(s1.isInStatement, "Statement should be open at end of chain")
        }

        @Test
        fun `Detecting extra endControlFlow`() {
            // User wrote endControlFlow() when indentLevel is already 0
            val failure = EmissionState.Initial.apply(EmissionStateDelta.ForControlFlowEnd)
            assertInstanceOf(EmissionState.StateApplyResult.Failure::class.java, failure)
            val v = (failure as EmissionState.StateApplyResult.Failure).violation
            assertInstanceOf(ChainViolation.NegativeIndent::class.java, v)
        }
    }
}

// ── Assertion helpers ──────────────────────────────────────────────────────────

private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.simpleName} but got ${e::class.simpleName}: ${e.message}")
    }
    throw AssertionError("Expected ${T::class.simpleName} but no exception was thrown")
}
