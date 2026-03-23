package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemantics.*
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemanticsClassifier.classify
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemanticsClassifier.computeDelta
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel

/**
 * Classifies KotlinPoet builder method names into [MethodSemantics] and computes
 * the corresponding [EmissionStateDelta].
 *
 * ## Separation of concerns
 *
 * - **[classify]** maps a method name to its semantic category. This is pure and requires
 *   no PSI — just the string name.
 *
 * - **[computeDelta]** maps a [MethodSemantics] + optional [FormatStringModel] to the
 *   ordered [EmissionStateDelta]. For wrapper methods ([MethodSemantics.StatementCall],
 *   [MethodSemantics.ControlFlowBegin], etc.) the delta is fixed. For raw format calls
 *   ([MethodSemantics.FormatCall]), the delta is derived from the control symbols in the
 *   parsed format string.
 *
 * ## Why format is optional in computeDelta
 *
 * At the point where [computeDelta] is called, the [FormatStringModel] may not yet be
 * cached. Passing `null` is safe — it produces [EmissionStateDelta.Zero], which is a
 * conservative default that will not produce false violations. The caller should supply
 * the format when available for accurate validation.
 */
object MethodSemanticsClassifier {

    /**
     * Classifies [methodName] into a [MethodSemantics].
     *
     * The receiver's fully qualified name is intentionally not required here —
     * [io.github.kingg22.kotlinpoet.assistant.infrastructure.extractor.KotlinPoetCallTargetResolver]
     * already filters for KotlinPoet calls before this is invoked.
     *
     * @param methodName The simple name of the KotlinPoet builder method.
     * @return The corresponding [MethodSemantics], or [MethodSemantics.UnknownCall] for
     *         unrecognized methods.
     */
    fun classify(methodName: String): MethodSemantics = when (methodName) {
        "addStatement" -> StatementCall
        "beginControlFlow" -> ControlFlowBegin
        "nextControlFlow" -> ControlFlowNext
        "endControlFlow" -> ControlFlowEnd
        "indent" -> IndentCall
        "unindent" -> UnindentCall
        "add", "addCode", "addNamed", "of" -> FormatCall
        "addKdoc" -> KdocCall
        "build" -> TerminalCall
        "builder", "buildCodeBlock" -> StartBuilder
        else -> UnknownCall(methodName)
    }

    /**
     * Computes the [EmissionStateDelta] for a given [MethodSemantics] and optional format model.
     *
     * For wrapper methods, the delta is **fixed** and does not depend on the format string.
     * For [MethodSemantics.FormatCall] and [MethodSemantics.KdocCall], the delta is derived
     * from the control symbols present in [format].
     *
     * @param semantics The classified semantics (from [classify]).
     * @param format The parsed format model for this call. May be `null` if not yet available
     *        or if the call has no format string (e.g., `endControlFlow`, `indent`).
     *        When `null` for a [MethodSemantics.FormatCall], returns [EmissionStateDelta.Zero].
     * @return The ordered [EmissionStateDelta] for this call.
     */
    fun computeDelta(semantics: MethodSemantics, format: FormatStringModel?): EmissionStateDelta = when (semantics) {
        StatementCall -> EmissionStateDelta.ForStatement
        ControlFlowBegin -> EmissionStateDelta.ForControlFlowBegin
        ControlFlowNext -> EmissionStateDelta.ForControlFlowNext
        ControlFlowEnd -> EmissionStateDelta.ForControlFlowEnd
        IndentCall -> EmissionStateDelta.Indent
        UnindentCall -> EmissionStateDelta.Unindent
        FormatCall, KdocCall -> format?.let { computeDeltaFromFormat(it) } ?: EmissionStateDelta.Zero
        TerminalCall, is UnknownCall, StartBuilder -> EmissionStateDelta.Zero
    }
}
// ── Private helpers ────────────────────────────────────────────────────────

/**
 * Derives an [EmissionStateDelta] from the control symbols in a [FormatStringModel].
 *
 * Only [ControlSymbol.SymbolType]s that affect [EmissionState] are mapped:
 * - `«` → [StateTransition.OpenStatement]
 * - `»` → [StateTransition.CloseStatement]
 * - `⇥` → [StateTransition.IncrementIndent]
 * - `⇤` → [StateTransition.DecrementIndent]
 *
 * Other symbols (`%%`, `·`, `♢`) produce no state transition.
 *
 * Control symbols are already in left-to-right document order as produced by
 * [io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParserImpl],
 * so no sorting is needed.
 */
private fun computeDeltaFromFormat(format: FormatStringModel): EmissionStateDelta {
    val transitions = mutableListOf<StateTransition>()
    for (control in format.controlSymbols) {
        val transition = control.type.toTransition() ?: continue
        transitions.add(transition)
    }
    return if (transitions.isEmpty()) {
        EmissionStateDelta.Zero
    } else {
        EmissionStateDelta(transitions)
    }
}

/**
 * Maps a [ControlSymbol.SymbolType] to its corresponding [StateTransition], or `null`
 * if the symbol has no effect on [EmissionState].
 */
private fun ControlSymbol.SymbolType.toTransition(): StateTransition? = when (this) {
    ControlSymbol.SymbolType.STATEMENT_BEGIN -> StateTransition.OpenStatement
    ControlSymbol.SymbolType.STATEMENT_END -> StateTransition.CloseStatement
    ControlSymbol.SymbolType.INDENT -> StateTransition.IncrementIndent
    ControlSymbol.SymbolType.OUTDENT -> StateTransition.DecrementIndent
    else -> null // LITERAL_PERCENT, SPACE, SPACE_OR_NEW_LINE → no state change
}
