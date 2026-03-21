package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.chain.ArgumentTextResolver.resolve
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.ControlSymbolPart
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.FormatLiteral
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.ResolvedPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.UnresolvedPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemantics.*
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Produces a [MethodEmissionContribution] from a single `KtCallExpression`.
 *
 * ## Data sources (in priority order)
 *
 * 1. **Existing [KotlinPoetAnalysis] cache** — format model + bound placeholders.
 *    Populated by the annotator, completion, and reference providers during the normal
 *    editor pass. No re-parsing or re-extraction.
 * 2. **Argument text resolution** — [ArgumentTextResolver.resolve] per argument.
 *    Each call opens its own `analyze {}` session (one per argument, not one per call).
 * 3. **[MethodSemanticsClassifier]** — method classification + state delta.
 *
 * ## Inspection awareness
 *
 * If the cached analysis reports format-level parse errors (`haveFormatProblems`),
 * the contribution is marked as [ContributionResolvability.Unresolvable] and parts
 * include a `⚠` prefix. The tool window uses this to show a warning row.
 *
 * ## Threading
 *
 * Must be called from a **read action**.
 */
object ContributionAnalyzer {
    @JvmStatic
    fun analyze(call: KtCallExpression): MethodEmissionContribution? {
        val analysis = getCachedAnalysis(call)
        val methodName = analysis?.callContext?.renderHint?.methodName
            ?: call.calleeExpression?.text
            ?: return null

        val semantics = MethodSemanticsClassifier.classify(methodName)

        if (isStructuralOnly(semantics)) {
            return buildStructuralContribution(call, methodName, semantics)
        }

        val boundAnalysis = analysis?.bind() ?: return null

        // Surface format-level parse errors so the tool window can mark the row
        if (boundAnalysis.format.errors.isNotEmpty()) {
            return buildErrorContribution(call, methodName, semantics, boundAnalysis)
        }

        val argTexts = resolveArgTexts(call, boundAnalysis)
        val callSpan = call.toTextSpan()
        val parts = buildParts(methodName, semantics, boundAnalysis, argTexts, callSpan)
        val delta = MethodSemanticsClassifier.computeDelta(semantics, boundAnalysis.format)
        val resolvability = computeResolvability(parts)

        return MethodEmissionContribution(
            methodName = methodName,
            semantics = semantics,
            parts = parts,
            stateDelta = delta,
            resolvability = resolvability,
            callSpan = callSpan,
        )
    }
}

// ── Structural contributions (no format string) ────────────────────────────────

private fun buildStructuralContribution(
    call: KtCallExpression,
    methodName: String,
    semantics: MethodSemantics,
): MethodEmissionContribution {
    val callSpan = call.toTextSpan()
    val origin = EmissionOrigin.ExplicitCall(methodName, callSpan)
    val implicitOrigin = EmissionOrigin.ImplicitFromMethod(methodName, callSpan)

    val parts = when (semantics) {
        IndentCall -> listOf(
            ControlSymbolPart(ControlSymbol.SymbolType.INDENT, implicit = false, origin),
        )

        UnindentCall -> listOf(
            ControlSymbolPart(ControlSymbol.SymbolType.OUTDENT, implicit = false, origin),
        )

        ControlFlowEnd -> listOf(
            ControlSymbolPart(ControlSymbol.SymbolType.OUTDENT, implicit = true, implicitOrigin),
            FormatLiteral("}\n", implicitOrigin),
        )

        else -> emptyList()
    }

    return MethodEmissionContribution(
        methodName = methodName,
        semantics = semantics,
        parts = parts,
        stateDelta = MethodSemanticsClassifier.computeDelta(semantics, null),
        resolvability = ContributionResolvability.FullyResolved,
        callSpan = callSpan,
    )
}

// ── Error contribution (format parse errors present) ──────────────────────────

private fun buildErrorContribution(
    call: KtCallExpression,
    methodName: String,
    semantics: MethodSemantics,
    analysis: KotlinPoetAnalysis,
): MethodEmissionContribution {
    val callSpan = call.toTextSpan()
    val origin = EmissionOrigin.ExplicitCall(methodName, callSpan)
    val rawText = analysis.format.text.asString()
    return MethodEmissionContribution(
        methodName = methodName,
        semantics = semantics,
        parts = listOf(FormatLiteral("⚠ $rawText", origin)),
        stateDelta = MethodSemanticsClassifier.computeDelta(semantics, null),
        resolvability = ContributionResolvability.Unresolvable,
        callSpan = callSpan,
    )
}

// ── Argument text resolution ───────────────────────────────────────────────────

/**
 * Resolves each bound placeholder's argument to display text.
 *
 * For VarArg calls (`add`, `addStatement`, `beginControlFlow`, `of`, etc.):
 * - [BoundPlaceholder.argument.index] is 1-based and maps directly to
 *   `call.valueArguments[index]` (since `valueArguments[0]` is always the format string).
 *
 * For Named calls (`addNamed`):
 * - Falls back to span-based PSI lookup using [BoundPlaceholder.argument.span].
 *
 * Each argument opens its own `analyze {}` session via [ArgumentTextResolver.resolve].
 */
private fun resolveArgTexts(
    call: KtCallExpression,
    analysis: KotlinPoetAnalysis,
): Map<PlaceholderSpec, ResolvedText?> {
    if (analysis.bounds.isEmpty()) return emptyMap()

    val result = mutableMapOf<PlaceholderSpec, ResolvedText?>()
    val psiArgs = call.valueArguments

    for (bound in analysis.bounds) {
        val argument = bound.argument ?: continue

        val argExpr = when {
            // VarArg (relative + positional): 1-based index, PSI args[0] = format
            argument.index != null -> psiArgs.getOrNull(argument.index)?.getArgumentExpression()

            // Named: use span to locate the value expression inside the map literal
            argument.name != null -> argument.span?.singleRangeOrNull()?.let { span ->
                findExpressionAtOffset(call, span.first)
            }

            else -> null
        } ?: continue

        result[bound.placeholder] = ArgumentTextResolver.resolve(argExpr)
    }

    return result
}

// ── Parts construction ─────────────────────────────────────────────────────────

private fun buildParts(
    methodName: String,
    semantics: MethodSemantics,
    analysis: KotlinPoetAnalysis,
    argTexts: Map<PlaceholderSpec, ResolvedText?>,
    callSpan: TextSpan,
): List<EmittedPart> {
    val origin = EmissionOrigin.ExplicitCall(methodName, callSpan)
    val implicitOrigin = EmissionOrigin.ImplicitFromMethod(methodName, callSpan)
    val parts = mutableListOf<EmittedPart>()

    // ── Implicit opening symbols ───────────────────────────────────────────────
    when (semantics) {
        StatementCall ->
            parts += ControlSymbolPart(ControlSymbol.SymbolType.STATEMENT_BEGIN, implicit = true, implicitOrigin)

        ControlFlowNext ->
            parts += ControlSymbolPart(ControlSymbol.SymbolType.OUTDENT, implicit = true, implicitOrigin)

        else -> {}
    }

    // ── Main format content ────────────────────────────────────────────────────
    parts += buildFormatParts(analysis, argTexts, origin)

    // ── Implicit closing symbols ───────────────────────────────────────────────
    when (semantics) {
        StatementCall -> {
            parts += FormatLiteral("\n", implicitOrigin)
            parts += ControlSymbolPart(ControlSymbol.SymbolType.STATEMENT_END, implicit = true, implicitOrigin)
        }

        ControlFlowBegin, ControlFlowNext -> {
            // Mirror KotlinPoet's withOpeningBrace(): if the format string already ends
            // with '{', only append '\n'; otherwise append ' {\n'.
            val suffix = detectControlFlowSuffix(analysis.format.text.asString())
            parts += FormatLiteral(suffix, implicitOrigin)
            parts += ControlSymbolPart(ControlSymbol.SymbolType.INDENT, implicit = true, implicitOrigin)
        }

        else -> {}
    }

    return parts
}

/**
 * Walks the raw format string left-to-right, placing resolved or unresolved parts
 * for each placeholder and literal text between them.
 *
 * Control symbols that the user wrote explicitly in the format string (e.g., `«`, `»`,
 * `⇥`, `⇤`) remain as [EmittedPart.FormatLiteral] here. [ChainRenderer] handles
 * their visual effect during rendering.
 */
private fun buildFormatParts(
    analysis: KotlinPoetAnalysis,
    argTexts: Map<PlaceholderSpec, ResolvedText?>,
    origin: EmissionOrigin,
): List<EmittedPart> {
    val parts = mutableListOf<EmittedPart>()
    var remaining = analysis.format.text.asString()

    for (bound in analysis.bounds) {
        val token = bound.placeholder.tokenText()
        val idx = remaining.indexOf(token)
        if (idx < 0) continue

        if (idx > 0) {
            parts += FormatLiteral(remaining.substring(0, idx), origin)
        }

        val resolved = argTexts[bound.placeholder]
        parts += if (resolved != null) {
            ResolvedPlaceholder(
                placeholder = bound.placeholder,
                resolvedText = formatForKind(bound.placeholder.kind, resolved),
                argSpan = bound.argument?.span,
                origin = origin,
            )
        } else {
            UnresolvedPlaceholder(
                placeholder = bound.placeholder,
                reason = unresolvedReason(bound),
                origin = origin,
            )
        }

        remaining = remaining.substring(idx + token.length)
    }

    if (remaining.isNotEmpty()) {
        parts += FormatLiteral(remaining, origin)
    }

    return parts
}

// ── Resolvability summary ──────────────────────────────────────────────────────

private fun computeResolvability(parts: List<EmittedPart>): ContributionResolvability {
    val unresolved = parts.filterIsInstance<UnresolvedPlaceholder>()
        .map { UnresolvedArg(it.placeholder, it.reason) }
    return when {
        unresolved.isEmpty() -> ContributionResolvability.FullyResolved
        parts.none { it is ResolvedPlaceholder } -> ContributionResolvability.Unresolvable
        else -> ContributionResolvability.PartiallyResolved(unresolved)
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/**
 * Mirrors KotlinPoet's internal `String.withOpeningBrace()`:
 * scan the raw format string from the end — if `{` is found before `}`, the brace
 * is already present, so only `\n` is needed. Otherwise append ` {\n`.
 *
 * This is applied to the raw format string (including placeholder tokens like `%L`),
 * exactly as KotlinPoet does before argument substitution.
 */
private fun detectControlFlowSuffix(rawFormat: String): String {
    for (i in rawFormat.indices.reversed()) {
        if (rawFormat[i] == '{') return "\n"
        if (rawFormat[i] == '}') break
    }
    return " {\n"
}

// %N emits the raw name without quotes
private fun formatForKind(kind: PlaceholderSpec.FormatKind, resolved: ResolvedText): String = when (kind) {
    PlaceholderSpec.FormatKind.NAME -> (resolved as? ResolvedText.StringLiteral)?.value ?: resolved.displayText
    else -> resolved.displayText
}

private fun unresolvedReason(bound: BoundPlaceholder): UnresolvedReason = if (bound.argument == null) {
    UnresolvedReason.EXTERNAL_VARIABLE
} else {
    UnresolvedReason.NON_CONST_EXPRESSION
}

private fun isStructuralOnly(semantics: MethodSemantics): Boolean = when (semantics) {
    IndentCall, UnindentCall, ControlFlowEnd, StartBuilder, TerminalCall, is UnknownCall -> true
    ControlFlowBegin, ControlFlowNext, FormatCall, KdocCall, StatementCall -> false
}

private fun KtCallExpression.toTextSpan(): TextSpan = TextSpan.of(textRange.startOffset until textRange.endOffset)

private fun findExpressionAtOffset(call: KtCallExpression, offset: Int): KtExpression? {
    var psi = call.containingFile.findElementAt(offset)
    while (psi != null && psi !is KtExpression) psi = psi.parent
    return psi
}
