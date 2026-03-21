package io.github.kingg22.kotlinpoet.assistant.domain.chain

/**
 * A constraint violation detected while applying an [EmissionStateDelta] to an [EmissionState].
 *
 * Violations correspond exactly to the runtime exceptions thrown by
 * `CodeWriter` / `CodeBlock.Builder`:
 *
 * - [DoubleOpenStatement] → `"Can't open a new statement until the current statement is closed"`
 * - [CloseWithoutOpenStatement] → `"Can't close a statement that hasn't been opened"`
 * - [NegativeIndent] → `"cannot unindent N from M"` (CodeWriter.unindent)
 *
 * @see EmissionState.apply
 */
sealed interface ChainViolation {

    /**
     * `«` applied when a statement is already open ([EmissionState.statementLine] `>= 0`).
     *
     * @param currentStatementLine The `statementLine` value at the moment of violation.
     * @param indentLevel The indentation level at the moment of violation.
     */
    data class DoubleOpenStatement(val currentStatementLine: Int, val indentLevel: Int) : ChainViolation

    /**
     * `»` applied when no statement is open ([EmissionState.statementLine] `== -1`).
     *
     * @param indentLevel The indentation level at the moment of violation.
     */
    data class CloseWithoutOpenStatement(val indentLevel: Int) : ChainViolation

    /**
     * `⇤` applied when [EmissionState.indentLevel] is already `0`.
     *
     * @param currentLevel Always `0` when this violation is produced.
     */
    data class NegativeIndent(val currentLevel: Int) : ChainViolation
}
