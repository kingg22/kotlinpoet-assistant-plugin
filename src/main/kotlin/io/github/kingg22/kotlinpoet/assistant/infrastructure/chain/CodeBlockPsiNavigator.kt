package io.github.kingg22.kotlinpoet.assistant.infrastructure.chain

import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import io.github.kingg22.kotlinpoet.assistant.domain.chain.BUILDER_METHOD_NAMES
import io.github.kingg22.kotlinpoet.assistant.domain.chain.DSL_BUILDER_NAMES
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator.findBuilderCallAt
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
 * Walks a KotlinPoet builder method chain in PSI.
 *
 * ## Two chain shapes
 *
 * ### Dot-chain
 * ```kotlin
 * CodeBlock.builder().add("fmt", arg).addStatement("stmt").build()
 * ```
 *
 * ### Lambda body (DSL)
 * ```kotlin
 * buildCodeBlock {
 *     add("fmt", arg)
 *     addStatement("stmt")
 * }
 * ```
 *
 * ## Custom delegating methods
 *
 * User-defined extension functions on `CodeBlock.Builder` that delegate to known
 * KotlinPoet methods are recognized if the **analysis cache already has an entry**
 * for that call (populated by the annotator / inspection pass).
 *
 * [findBuilderCallAt] checks the cache as a fallback so these methods are included
 * in chains when the annotator has already processed the file.
 *
 * ## Complexity
 *
 * - [findPredecessorCall], [findSuccessorCall]: **O(1)**.
 * - [walkBackward], [findChain]: **O(n)** bounded by `maxSteps`, each step O(1).
 *
 * All methods are **PSI-only** (no Analysis API). Caller must be in a read action.
 */
object CodeBlockPsiNavigator {

    /**
     * Primary entry point. Returns all calls in the chain that contains [call],
     * in emission order (oldest first, [call] last).
     *
     * Handles dot-chains, `buildCodeBlock { }` lambda bodies, and standalone `of()` calls.
     */
    @RequiresReadLock(generateAssertion = false)
    fun findChain(call: KtCallExpression, maxSteps: Int = 50): List<KtCallExpression> {
        val lambdaChain = findLambdaChain(call)
        if (lambdaChain != null) return lambdaChain
        if (findPredecessorCall(call) != null) return fullChainEndingAt(call, maxSteps)
        return listOf(call)
    }

    /**
     * Finds the `KtCallExpression` immediately preceding [call] in a dot-chain.
     * O(1) — single PSI parent lookup.
     */
    @RequiresReadLock(generateAssertion = false)
    fun findPredecessorCall(call: KtCallExpression): KtCallExpression? {
        val enclosingDot = call.parent as? KtDotQualifiedExpression ?: return null
        if (enclosingDot.selectorExpression !== call) return null
        return enclosingDot.receiverExpression.terminalCall()
    }

    /**
     * Finds the `KtCallExpression` immediately following [call] in a dot-chain.
     * O(1).
     */
    @RequiresReadLock(generateAssertion = false)
    fun findSuccessorCall(call: KtCallExpression): KtCallExpression? {
        val enclosingDot = call.parent as? KtDotQualifiedExpression ?: return null
        val parentDot = enclosingDot.parent as? KtDotQualifiedExpression ?: return null
        if (parentDot.receiverExpression !== enclosingDot) return null
        return parentDot.selectorExpression as? KtCallExpression
    }

    /**
     * Walks backward from [call] collecting predecessors (oldest → most recent),
     * exclusive of [call] itself.
     *
     * Stops when the callee is not a known builder method AND does not have a
     * cached analysis (which would indicate a recognized custom delegating method).
     */
    @RequiresReadLock(generateAssertion = false)
    fun walkBackward(call: KtCallExpression, maxSteps: Int = 50): List<KtCallExpression> {
        val result = ArrayDeque<KtCallExpression>(minOf(maxSteps, 16))
        var current = findPredecessorCall(call)
        var steps = 0
        while (current != null && steps < maxSteps) {
            if (!isRecognizedBuilderCall(current)) break
            result.addFirst(current)
            current = findPredecessorCall(current)
            steps++
        }
        return result
    }

    /** Returns `walkBackward(call) + listOf(call)`. */
    @RequiresReadLock(generateAssertion = false)
    fun fullChainEndingAt(call: KtCallExpression, maxSteps: Int = 50): List<KtCallExpression> =
        walkBackward(call, maxSteps) + listOf(call)

    /**
     * Finds the nearest KotlinPoet builder call at or above [element] in the PSI tree.
     *
     * Checks:
     * 1. Callee text is in [BUILDER_METHOD_NAMES] (fast, PSI-only).
     * 2. The call has a cached analysis (covers custom delegating methods after the
     *    annotator has run, without requiring a fresh K2 session).
     */
    @RequiresReadLock(generateAssertion = false)
    fun findBuilderCallAt(element: PsiElement): KtCallExpression? {
        var e: PsiElement? = element
        while (e != null) {
            if (e is KtCallExpression && isRecognizedBuilderCall(e)) return e
            e = e.parent
        }
        return null
    }
}

// ── Lambda body traversal ──────────────────────────────────────────────────

/**
 * Returns the sibling calls inside a `buildCodeBlock { }` lambda if [call] is one
 * of them, or `null` if [call] is not inside a recognized DSL builder lambda.
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
        is KtLambdaArgument -> lambdaParent.parent as? KtCallExpression
        is KtValueArgument -> (lambdaParent.parent as? KtValueArgumentList)?.parent as? KtCallExpression
        else -> null
    } ?: return null

    val dslName = dslCall.calleeExpression?.text ?: return null
    if (dslName !in DSL_BUILDER_NAMES) return null

    // Collect all direct KtCallExpression statements in the lambda body
    return blockExpr.statements
        .filterIsInstance<KtCallExpression>()
        .filter { isRecognizedBuilderCall(it) }
}

/**
 * Returns `true` if [call] is a recognized CodeBlock builder call:
 * - Its callee is in [BUILDER_METHOD_NAMES], **or**
 * - The analysis cache already has an entry for it (custom delegating method
 *   recognized by [io.github.kingg22.kotlinpoet.assistant.infrastructure.extractor.KotlinPoetCallTargetResolver]).
 *
 * The cache check uses `extractOnMissing = false` to avoid triggering K2 analysis
 * during the PSI walk — we only trust what is already known.
 */
private fun isRecognizedBuilderCall(call: KtCallExpression): Boolean {
    val callee = call.calleeExpression?.text ?: return false
    if (callee in BUILDER_METHOD_NAMES) return true
    // Cache check: covers custom extension methods that delegate to CodeBlock.Builder
    return getCachedAnalysis(call, extractOnMissing = false) != null
}

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
