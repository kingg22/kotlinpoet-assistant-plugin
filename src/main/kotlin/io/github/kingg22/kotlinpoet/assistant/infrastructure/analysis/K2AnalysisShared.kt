package io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis

import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtValueArgument

fun extractMapEntry(entryExpr: KtExpression?): Pair<String, KtExpression?>? {
    // En Kotlin, "key to value" es una llamada infix a la función 'to'
    // PSI structure: KtBinaryExpression (si es infix) o KtCallExpression (si es constructor) o KtDotQualifiedExpression (si es .to())
    return when (entryExpr) {
        is KtBinaryExpression -> {
            val keyExpr = entryExpr.left
            val keyVal = keyExpr?.evaluateString() ?: return null
            keyVal to entryExpr.right
        }

        is KtCallExpression -> {
            if (entryExpr.calleeExpression?.text != "Pair") return null
            val arguments: List<KtValueArgument> = entryExpr.valueArguments
            val keyExpr = arguments.getOrNull(0)?.getArgumentExpression()
            val keyVal = keyExpr?.evaluateString() ?: return null
            val valueExpr = arguments.getOrNull(1)?.getArgumentExpression()
            keyVal to valueExpr
        }

        is KtDotQualifiedExpression -> {
            val callExpression = entryExpr.selectorExpression as? KtCallExpression ?: return null
            val callName = callExpression.calleeExpression?.text ?: return null
            if (callName != "to") return null
            val keyExpr = entryExpr.receiverExpression
            val keyVal = keyExpr.evaluateString() ?: return null
            val arguments: List<KtValueArgument> = callExpression.valueArguments
            if (arguments.size != 1) return null
            val valueExpr = arguments.getOrNull(0)?.getArgumentExpression()
            keyVal to valueExpr
        }

        else -> null
    }
}

fun resolveNamedTarget(mapExpression: KtExpression?, name: String): KtExpression? {
    val call = mapExpression as? KtCallExpression ?: return null
    for (entryArg in call.valueArguments) {
        val entryExpr = entryArg.getArgumentExpression()
        val (key, valueExpr) = extractMapEntry(entryExpr) ?: continue
        if (key == name) return valueExpr
    }
    return null
}

private fun KtExpression.evaluateString(): String? =
    KotlinAnalysisBridges.instance.evaluateConstant(this)?.value as? String
