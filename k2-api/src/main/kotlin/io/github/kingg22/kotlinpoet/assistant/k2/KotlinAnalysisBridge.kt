package io.github.kingg22.kotlinpoet.assistant.k2

import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Version-neutral facade over the Kotlin K2 Analysis API.
 *
 * The Analysis API is **binary incompatible** between IDE generations:
 * `KaSession` members were extension functions up to the 252–262 line and became top-level
 * functions with context parameters afterwards. Plugin code therefore never touches `KaSession`
 * directly; every call goes through this interface, which has one implementation per API flavour
 * (`k2-legacy` and `k2-modern`). Only plain PSI / JDK / Kotlin-name types cross this boundary.
 *
 * Every method opens its own `analyze {}` session and must be called inside a read action.
 */
interface KotlinAnalysisBridge {
    /** Resolves [element] to a single callable member call, or `null` when unresolved or ambiguous. */
    fun resolveCall(element: KtElement): ResolvedCallInfo?

    /** Type of [expression], or `null` when it has none. Supertypes are only computed on demand. */
    fun typeOf(expression: KtExpression, withSupertypes: Boolean = false): TypeInfo?

    /** Constant-evaluates [expression], or `null` when it is not a constant. */
    fun evaluateConstant(expression: KtExpression): ConstantInfo?
}
