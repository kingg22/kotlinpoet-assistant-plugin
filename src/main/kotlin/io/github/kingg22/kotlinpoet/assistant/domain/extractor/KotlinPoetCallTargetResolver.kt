package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import io.github.kingg22.kotlinpoet.assistant.adapters.types.isKotlinPoetBuilder
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

object KotlinPoetCallTargetResolver {
    private val FORMAT_METHODS = setOf("add", "addNamed", "addStatement", "addCode", "beginControlFlow", "of")
    private val KDOC_METHODS = setOf("addKdoc")

    fun resolve(call: KtCallExpression): KotlinPoetCallTarget? = analyze(call) {
        val resolvedCall: KaCallableMemberCall<*, *>? = call.resolveToCall()?.singleCallOrNull()

        val methodName = resolvedCall?.partiallyAppliedSymbol?.symbol?.callableId?.callableName?.asString()
            ?: call.calleeExpression?.text.orEmpty()
        val receiverType = resolvedCall?.partiallyAppliedSymbol?.dispatchReceiver?.type
            ?: resolvedCall?.partiallyAppliedSymbol?.extensionReceiver?.type

        if (receiverType != null && isKotlinPoetTarget(methodName, receiverType)) {
            return@analyze KotlinPoetCallTarget(
                methodName = methodName,
                receiverFqName = receiverType.asFqNameOrNull(),
                isDelegated = false,
            )
        }

        if (resolvedCall == null) {
            val qualified = call.getQualifiedExpressionForSelector()
            val fallbackReceiver = qualified?.receiverExpression?.expressionType
            if (fallbackReceiver != null && isKotlinPoetTarget(methodName, fallbackReceiver)) {
                return@analyze KotlinPoetCallTarget(
                    methodName = methodName,
                    receiverFqName = fallbackReceiver.asFqNameOrNull(),
                    isDelegated = false,
                )
            }
            return@analyze null
        }

        val functionPsi = resolvedCall.partiallyAppliedSymbol.symbol.psi as? KtNamedFunction ?: return@analyze null
        val body = functionPsi.bodyExpression ?: return@analyze null

        val delegated = body.collectDescendantsOfType<KtCallExpression>()
            .firstOrNull { it.calleeExpression?.text in FORMAT_METHODS || it.calleeExpression?.text in KDOC_METHODS }
            ?: return@analyze null

        val delegatedResolved: KaCallableMemberCall<*, *> =
            delegated.resolveToCall()?.singleCallOrNull() ?: return@analyze null
        val delegatedReceiver = delegatedResolved.partiallyAppliedSymbol.dispatchReceiver?.type
            ?: delegatedResolved.partiallyAppliedSymbol.extensionReceiver?.type
        if (delegatedReceiver != null &&
            isKotlinPoetTarget(delegated.calleeExpression?.text.orEmpty(), delegatedReceiver)
        ) {
            return@analyze KotlinPoetCallTarget(
                methodName = delegated.calleeExpression?.text.orEmpty(),
                receiverFqName = delegatedReceiver.asFqNameOrNull(),
                isDelegated = true,
            )
        }

        return@analyze null
    }

    private fun KaType.asFqNameOrNull(): String? = (this as? KaClassType)?.classId?.asSingleFqName()?.asString()

    private fun isKotlinPoetTarget(methodName: String, receiverType: KaType): Boolean {
        if (receiverType.isKotlinPoetBuilder()) return true
        val fqName = receiverType.asFqNameOrNull()
        if (fqName?.startsWith("com.squareup.kotlinpoet.") == true) {
            return methodName in KDOC_METHODS || methodName in FORMAT_METHODS
        }
        return false
    }
}
