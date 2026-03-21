package io.github.kingg22.kotlinpoet.assistant.infrastructure.chain

import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator.findChain
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator.findPredecessorCall
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator.findSuccessorCall
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator.walkBackward
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * All KotlinPoet `CodeBlock.Builder` method names that the plugin understands.
 *
 * The tool window uses this set directly (instead of `Constants.KOTLINPOET_CALLS`) so
 * that calls like `.build()`, `.indent()`, `.endControlFlow()`, `.builder()` are
 * recognized even though they are not in the narrower inspection/completion set.
 */
internal val KNOWN_BUILDER_CALLS: Set<String> = setOf(
    "add", "addCode", "addNamed", "addStatement",
    "beginControlFlow", "nextControlFlow", "endControlFlow",
    "indent", "unindent",
    "addKdoc",
    "build",
    "builder", "of",
)

/**
 * Names of DSL functions whose lambda body contains sequential `CodeBlock.Builder` calls.
 *
 * Inside such a lambda, builder calls do NOT form a dot-chain — they are sequential
 * statements with an implicit `CodeBlock.Builder` receiver. The navigator handles this
 * separately from the dot-chain case.
 */
private val KNOWN_DSL_BUILDERS: Set<String> = setOf(
    "buildCodeBlock",
    "apply",
    "also",
    "run",
    "with",
)

/**
 * Walks a KotlinPoet builder method chain in PSI.
 *
 * ## Two chain shapes
 *
 * ### Dot-chain
 * ```kotlin
 * CodeBlock.builder().add("fmt", arg).addStatement("stmt").build()
 * ```
 * Each call is the `selectorExpression` of a `KtDotQualifiedExpression`.
 *
 * ### Lambda body (DSL)
 * ```kotlin
 * buildCodeBlock {
 *     add("fmt", arg)
 *     addStatement("stmt")
 * }
 * ```
 * Calls are sequential statements inside a lambda — no dot-chain between them.
 *
 * ## Complexity
 *
 * - [findPredecessorCall], [findSuccessorCall]: **O(1)** — single PSI parent traversal.
 * - [walkBackward], [findChain]: **O(n)** bounded by [maxSteps], each step O(1).
 *
 * All methods are **PSI-only** — no Analysis API, no read-action requirement beyond
 * normal PSI access rules (caller must already be in a read action).
 */
object CodeBlockPsiNavigator {

    /**
     * The primary entry point. Returns all calls in the chain that contains [call],
     * in emission order (oldest first, [call] last).
     *
     * Handles both dot-chains and `buildCodeBlock { }` lambda bodies transparently.
     */
    fun findChain(call: KtCallExpression, maxSteps: Int = 50): List<KtCallExpression> {
        // 1. Try dot-chain (the common case)
        val dotChainPredecessor = findPredecessorCall(call)
        if (dotChainPredecessor != null) {
            return fullChainEndingAt(call, maxSteps)
        }

        // 2. Try lambda body (buildCodeBlock DSL)
        val lambdaChain = findLambdaChain(call)
        if (lambdaChain != null) return lambdaChain

        // 3. Standalone call (CodeBlock.of(...), or single builder call)
        return listOf(call)
    }

    /**
     * Returns the `KtCallExpression` immediately preceding [call] in a dot-chain,
     * or `null` if [call] is the first element or the chain is not a dot-chain.
     *
     * O(1) — single PSI parent lookup.
     */
    fun findPredecessorCall(call: KtCallExpression): KtCallExpression? {
        val enclosingDot = call.parent as? KtDotQualifiedExpression ?: return null
        if (enclosingDot.selectorExpression !== call) return null
        return enclosingDot.receiverExpression.terminalCall()
    }

    /**
     * Returns the `KtCallExpression` immediately following [call] in a dot-chain,
     * or `null` if [call] is the last visible element.
     *
     * O(1) — single PSI grandparent lookup.
     */
    fun findSuccessorCall(call: KtCallExpression): KtCallExpression? {
        val enclosingDot = call.parent as? KtDotQualifiedExpression ?: return null
        val parentDot = enclosingDot.parent as? KtDotQualifiedExpression ?: return null
        if (parentDot.receiverExpression !== enclosingDot) return null
        return parentDot.selectorExpression as? KtCallExpression
    }

    /**
     * Walks backward from [call] up to [maxSteps] steps in a dot-chain.
     *
     * Returns predecessors from **oldest to most recent** (exclusive of [call]).
     * Stops when:
     * - The predecessor method name is not in [KNOWN_BUILDER_CALLS].
     * - The PSI structure is broken (variable reference, conditional, etc.).
     * - [maxSteps] is reached.
     */
    fun walkBackward(call: KtCallExpression, maxSteps: Int = 50): List<KtCallExpression> {
        val result = ArrayDeque<KtCallExpression>(minOf(maxSteps, 16))
        var current = findPredecessorCall(call)
        var steps = 0
        while (current != null && steps < maxSteps) {
            val callee = current.calleeExpression?.text ?: break
            if (callee !in KNOWN_BUILDER_CALLS) break
            result.addFirst(current)
            current = findPredecessorCall(current)
            steps++
        }
        return result
    }

    /**
     * Full dot-chain ending at [call]: `walkBackward(call) + listOf(call)`.
     */
    fun fullChainEndingAt(call: KtCallExpression, maxSteps: Int = 50): List<KtCallExpression> =
        walkBackward(call, maxSteps) + listOf(call)
}

// ── Lambda body traversal ──────────────────────────────────────────────────

/**
 * Detects whether [call] is a direct statement inside a `buildCodeBlock { }` lambda
 * (or other known DSL builder) and returns **all sibling calls** in emission order.
 *
 * The calls are filtered to only include those with known builder call names.
 * Returns `null` if [call] is not inside a recognized DSL lambda.
 *
 * ## PSI structure
 * ```
 * buildCodeBlock {          ← KtCallExpression (dslCall)
 *     addStatement(...)     ← KtCallExpression in KtBlockExpression.statements
 *     add(...)              ← KtCallExpression in KtBlockExpression.statements
 * }
 * ```
 * The lambda can be either a `KtValueArgument` (explicit) or `KtLambdaArgument`
 * (trailing lambda without parentheses — the common case for `buildCodeBlock`).
 */
private fun findLambdaChain(call: KtCallExpression): List<KtCallExpression>? {
    // Walk up: call → KtBlockExpression → KtFunctionLiteral → KtLambdaExpression
    val blockExpr = call.parent as? KtBlockExpression ?: return null
    val funcLiteral = blockExpr.parent as? KtFunctionLiteral ?: return null
    val lambdaExpr = funcLiteral.parent as? KtLambdaExpression ?: return null

    // The lambda can be a trailing lambda (KtLambdaArgument) or a normal argument
    val dslCall = when (val lambdaParent = lambdaExpr.parent) {
        is KtLambdaArgument -> {
            // Trailing lambda: lambdaParent.parent is the KtCallExpression
            lambdaParent.parent as? KtCallExpression
        }

        is KtValueArgument -> {
            val argList = lambdaParent.parent as? KtValueArgumentList ?: return null
            argList.parent as? KtCallExpression
        }

        else -> null
    } ?: return null

    val dslName = dslCall.calleeExpression?.text ?: return null
    if (dslName !in KNOWN_DSL_BUILDERS) return null

    // Collect all direct KtCallExpression statements in the lambda body
    return blockExpr.statements
        .filterIsInstance<KtCallExpression>()
        .filter { (it.calleeExpression?.text ?: "") in KNOWN_BUILDER_CALLS }
}

// ── Private PSI helpers ────────────────────────────────────────────────────────

/**
 * Returns the terminal `KtCallExpression` of an expression in a dot-chain.
 *
 * For `CodeBlock.builder()`:
 * - The PSI is `KtDotQualifiedExpression(receiver=CodeBlock, selector=builder())`
 * - `terminalCall()` on the dot-qual returns `builder()` (the selector).
 *
 * For a bare `KtCallExpression`: returns it directly.
 */
private fun KtExpression.terminalCall(): KtCallExpression? = when (this) {
    is KtCallExpression -> this
    is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression
    else -> null
}
