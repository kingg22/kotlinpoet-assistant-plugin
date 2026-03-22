package io.github.kingg22.kotlinpoet.assistant.domain.chain

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.ControlSymbolPart
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.FormatLiteral
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.NestedCodeBlockPart
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.ResolvedPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.UnresolvedPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemantics.*
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Produces a [MethodEmissionContribution] from a single `KtCallExpression`.
 *
 * ## Data sources (in priority order)
 *
 * 1. **[getCachedAnalysis]** — format model + bound placeholders already computed by
 *    the annotator/inspections. If the cache is cold, `getCachedAnalysis` extracts on
 *    demand (`extractOnMissing = true` is the default).
 * 2. **[ArgumentTextResolver]** — K2 constant evaluation + variable reference following
 *    for simple scalar arguments.
 * 3. **Nested CodeBlock expansion** — when a `%L` argument is itself a CodeBlock (inline
 *    chain or variable reference), the chain is recursively analyzed and emitted as a
 *    [EmittedPart.NestedCodeBlockPart].
 *
 * ## Named map arguments
 *
 * For `addNamed` calls, the [ArgumentSource.NamedMap] in the cached analysis already
 * contains the resolved `ArgumentValue` entries (populated by [io.github.kingg22.kotlinpoet.assistant.domain.extractor.NamedFormatExtractor]).
 * Each `ArgumentValue.span` points to the value expression inside the `mapOf(...)` call.
 * [getArgExpression] uses a **span-exact** PSI lookup so the correct leaf expression is
 * found even when multiple entries share a map literal.
 *
 * ## Custom delegating methods
 *
 * Methods recognized by [io.github.kingg22.kotlinpoet.assistant.domain.extractor.KotlinPoetCallTargetResolver]
 * as delegating to a KotlinPoet call are handled transparently: `getCachedAnalysis`
 * returns the analysis with `renderHint.methodName` set to the delegated method name,
 * and [MethodSemanticsClassifier] classifies it normally.
 *
 * ## Threading
 *
 * Must be called from a **read action**.
 */
object ContributionAnalyzer {
    @RequiresReadLock(generateAssertion = false)
    @JvmStatic
    fun analyze(call: KtCallExpression): MethodEmissionContribution? {
        val analysis = getCachedAnalysis(call)
        // renderHint.methodName is the delegated method name for custom wrappers
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
        val parts = buildParts(call, methodName, semantics, boundAnalysis, argTexts, callSpan)
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
            ControlSymbolPart(SymbolType.INDENT, implicit = false, origin),
        )

        UnindentCall -> listOf(
            ControlSymbolPart(SymbolType.OUTDENT, implicit = false, origin),
        )

        ControlFlowEnd -> listOf(
            ControlSymbolPart(SymbolType.OUTDENT, implicit = true, implicitOrigin),
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
 * **VarArg** (relative/positional): `argument.index` is 1-based in the domain model;
 * `valueArguments[0]` is always the format string, so `valueArguments[index]` is the arg.
 *
 * **Named** (`addNamed`): the `ArgumentValue.span` inside the [ArgumentSource.NamedMap]
 * already points to the value expression in the `mapOf(...)` call (set by
 * [io.github.kingg22.kotlinpoet.assistant.domain.extractor.NamedFormatExtractor]).
 * [getArgExpression] uses an exact-span PSI lookup to find that expression precisely.
 */
private fun resolveArgTexts(
    call: KtCallExpression,
    analysis: KotlinPoetAnalysis,
): Map<PlaceholderSpec, ResolvedText?> {
    if (analysis.bounds.isEmpty()) return emptyMap()

    val result = mutableMapOf<PlaceholderSpec, ResolvedText?>()

    for (bound in analysis.bounds) {
        val argExpr = getArgExpression(call, bound) ?: continue
        result[bound.placeholder] = ArgumentTextResolver.resolve(argExpr)
    }

    return result
}

// ── Parts construction ─────────────────────────────────────────────────────────

private fun buildParts(
    call: KtCallExpression,
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
        StatementCall -> parts += ControlSymbolPart(SymbolType.STATEMENT_BEGIN, implicit = true, implicitOrigin)
        ControlFlowNext -> parts += ControlSymbolPart(SymbolType.OUTDENT, implicit = true, implicitOrigin)
        else -> {}
    }

    // ── Main format content ────────────────────────────────────────────────────
    parts += buildFormatParts(call, analysis, argTexts, origin)

    // ── Implicit closing symbols ───────────────────────────────────────────────
    when (semantics) {
        StatementCall -> {
            parts += FormatLiteral("\n", implicitOrigin)
            parts += ControlSymbolPart(SymbolType.STATEMENT_END, implicit = true, implicitOrigin)
        }

        ControlFlowBegin, ControlFlowNext -> {
            // Mirror KotlinPoet's withOpeningBrace(): if the format string already ends
            // with '{', only append '\n'; otherwise append ' {\n'.
            parts += FormatLiteral(controlFlowSuffix(analysis.format.text.asString()), implicitOrigin)
            parts += ControlSymbolPart(SymbolType.INDENT, implicit = true, implicitOrigin)
        }

        else -> {}
    }

    return parts
}

/**
 * Walks the format string left-to-right, emitting:
 * - Literal text segments between placeholders
 * - [EmittedPart.ResolvedPlaceholder] when the argument resolved to a scalar value
 * - [EmittedPart.NestedCodeBlockPart] when `%L` receives a CodeBlock expression
 * - [EmittedPart.UnresolvedPlaceholder] for everything else
 */
private fun buildFormatParts(
    call: KtCallExpression,
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

        if (idx > 0) parts += FormatLiteral(remaining.substring(0, idx), origin)

        val resolved = argTexts[bound.placeholder]
        val part = when {
            resolved != null -> ResolvedPlaceholder(
                placeholder = bound.placeholder,
                resolvedText = formatForKind(bound.placeholder, resolved),
                argSpan = bound.argument?.span,
                origin = origin,
            )

            // For %L only: try to expand a nested CodeBlock before giving up
            bound.placeholder.kind == FormatKind.LITERAL ->
                tryBuildNestedPart(call, bound, origin)
                    ?: UnresolvedPlaceholder(bound.placeholder, unresolvedReason(bound), origin)

            else -> UnresolvedPlaceholder(bound.placeholder, unresolvedReason(bound), origin)
        }
        parts += part
        remaining = remaining.substring(idx + token.length)
    }

    if (remaining.isNotEmpty()) parts += FormatLiteral(remaining, origin)
    return parts
}

// ── Nested CodeBlock expansion ─────────────────────────────────────────────────

/**
 * Attempts to build a [EmittedPart.NestedCodeBlockPart] for a `%L` placeholder whose
 * argument expression is — or resolves to — a CodeBlock builder chain.
 *
 * ## Detection
 *
 * 1. **Inline chain**: the argument is directly a dot-chain ending in a known builder call
 *    (`CodeBlock.builder()...build()`, `buildCodeBlock { }`, `CodeBlock.of(...)`).
 * 2. **Variable reference** (one hop): the argument is a `KtNameReferenceExpression` that
 *    resolves via K2 to a `val`/`var` whose initializer is an inline CodeBlock chain.
 *
 * Deeper indirection (variable pointing to another variable) is not followed to
 * avoid excessive K2 sessions; the result is [EmittedPart.UnresolvedPlaceholder] with
 * [UnresolvedReason.EXTERNAL_VARIABLE].
 *
 * @param maxDepth Guards against infinite recursion in pathological self-referential code.
 */
private fun tryBuildNestedPart(
    call: KtCallExpression,
    bound: BoundPlaceholder,
    origin: EmissionOrigin,
    maxDepth: Int = 3,
): NestedCodeBlockPart? {
    if (maxDepth <= 0) return null
    val argExpr = getArgExpression(call, bound) ?: return null
    val codeBlockExpr = resolveToCodeBlockExpr(argExpr) ?: return null
    val terminalCall = codeBlockExpr.terminalBuilderCall() ?: return null
    val chain = CodeBlockPsiNavigator.findChain(terminalCall)
    if (chain.isEmpty()) return null
    val nestedContributions = chain.mapNotNull { ContributionAnalyzer.analyze(it) }
    return NestedCodeBlockPart(
        placeholder = bound.placeholder,
        nestedContributions = nestedContributions,
        argSpan = bound.argument?.span,
        origin = origin,
    )
}

/**
 * Resolves [expr] to the `KtExpression` that is (or contains) a CodeBlock chain.
 *
 * Returns the expression itself if it is an inline chain, or the initializer of the
 * referenced property if it is a single-hop variable reference.
 */
private fun resolveToCodeBlockExpr(expr: KtExpression): KtExpression? {
    if (expr.isCodeBlockExpr()) return expr

    if (expr is KtNameReferenceExpression) {
        // K2 symbol resolution — must be in a read action (enforced by callers)
        val initializer: KtExpression? = analyze(expr) {
            val call = expr.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()
            val property = call?.partiallyAppliedSymbol?.symbol?.psi as? KtProperty
            property?.initializer
        }
        return initializer?.takeIf { it.isCodeBlockExpr() }
    }

    return null
}

// ── Argument expression lookup ─────────────────────────────────────────────────

/**
 * Returns the `KtExpression` for a bound placeholder's argument.
 *
 * **VarArg**: direct index into `valueArguments (args[0] = format, args[index] = arg)`.
 *
 * **Named**: span-exact PSI lookup via [findExpressionAtSpan] — finds the expression
 * whose text range **starts at** `argument.span.first` so we get the value expression
 * itself, not the surrounding `mapOf(...)` or `"key" to value` pair.
 */
private fun getArgExpression(call: KtCallExpression, bound: BoundPlaceholder): KtExpression? {
    val argument = bound.argument ?: return null
    return when {
        argument.index != null -> call.valueArguments.getOrNull(argument.index)?.getArgumentExpression()

        argument.name != null -> argument.span?.singleRangeOrNull()?.let { span ->
            findExpressionAtSpan(call.containingFile, span)
        }

        else -> null
    }
}

/**
 * Finds the smallest `KtExpression` whose text range **starts exactly** at [span].`first`
 * and ends within [span] bounds.
 *
 * This is more precise than walking up to the first `KtExpression` ancestor: for map
 * values like `"food" to "tacos"`, we want `"tacos"` (startOffset == span.first), not
 * the parent `KtBinaryExpression`.
 */
private fun findExpressionAtSpan(file: PsiFile, span: IntRange): KtExpression? {
    var psi = file.findElementAt(span.first)
    while (psi != null) {
        if (psi is KtExpression &&
            psi.textRange.startOffset == span.first &&
            psi.textRange.endOffset <= span.last + 1
        ) {
            return psi
        }
        psi = psi.parent
    }
    return null
}

// ── Resolvability summary ──────────────────────────────────────────────────────

private fun computeResolvability(parts: List<EmittedPart>): ContributionResolvability {
    val unresolved = parts.filterIsInstance<UnresolvedPlaceholder>()
        .map { UnresolvedArg(it.placeholder, it.reason) }
    return when {
        unresolved.isEmpty() -> ContributionResolvability.FullyResolved
        parts.none { it is ResolvedPlaceholder || it is NestedCodeBlockPart } -> ContributionResolvability.Unresolvable
        else -> ContributionResolvability.PartiallyResolved(unresolved)
    }
}

// ── Private helpers ────────────────────────────────────────────────────────────

/**
 * Mirrors KotlinPoet's `String.withOpeningBrace()`.
 * Scans the raw format string from the end: if `{` is found before `}`, the brace
 * is already present → only `\n` needed; otherwise → ` {\n`.
 */
private fun controlFlowSuffix(rawFormat: String): String {
    for (i in rawFormat.indices.reversed()) {
        if (rawFormat[i] == '{') return "\n"
        if (rawFormat[i] == '}') break
    }
    return " {\n"
}

// %name:L emits the raw name identifier without quotes
private fun formatForKind(placeholder: PlaceholderSpec, resolved: ResolvedText): String = when (placeholder.binding) {
    is PlaceholderBinding.Named ->
        if (placeholder.kind != FormatKind.STRING && placeholder.kind != FormatKind.STRING_TEMPLATE) {
            StringUtil.unquoteString(resolved.displayText)
        } else {
            resolved.displayText
        }

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

/**
 * Returns `true` if [this] expression is the root of an inline CodeBlock chain:
 * - A `KtCallExpression` whose callee is in [BUILDER_METHOD_NAMES] (e.g., `CodeBlock.of(...)`,
 *   `buildCodeBlock { }`)
 * - A `KtDotQualifiedExpression` whose selector is a known builder call (e.g., `.build()`)
 */
private fun KtExpression.isCodeBlockExpr(): Boolean = when (this) {
    is KtCallExpression -> {
        val callee = calleeExpression?.text ?: return false
        callee in BUILDER_METHOD_NAMES || callee in DSL_BUILDER_NAMES
    }

    is KtDotQualifiedExpression -> {
        val selector = selectorExpression as? KtCallExpression ?: return false
        (selector.calleeExpression?.text ?: "") in BUILDER_METHOD_NAMES
    }

    else -> false
}

/**
 * Returns the terminal `KtCallExpression` of an expression that is either already a call
 * or a dot-qualified expression ending in a call (e.g., `CodeBlock.builder()...build()`).
 */
private fun KtExpression.terminalBuilderCall(): KtCallExpression? = when (this) {
    is KtCallExpression -> this
    is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression
    else -> null
}
