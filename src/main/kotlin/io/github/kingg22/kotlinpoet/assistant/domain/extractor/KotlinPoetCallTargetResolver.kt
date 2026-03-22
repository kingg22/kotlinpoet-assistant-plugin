package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import io.github.kingg22.kotlinpoet.assistant.Constants
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import kotlin.contracts.contract

object KotlinPoetCallTargetResolver {
    @JvmStatic
    fun resolve(call: KtCallExpression): KotlinPoetCallTarget? = analyze(call) {
        val resolvedCall: KaCallableMemberCall<*, *>? = call.resolveToCall()?.singleCallOrNull()

        val methodName = resolvedCall?.partiallyAppliedSymbol?.symbol?.callableId?.callableName?.asString()
            ?: call.calleeExpression?.text.orEmpty()
        val receiverType = resolvedCall?.partiallyAppliedSymbol?.dispatchReceiver?.type
            ?: resolvedCall?.partiallyAppliedSymbol?.extensionReceiver?.type

        if (receiverType != null && !receiverType.isError() && isKotlinPoetTarget(methodName, receiverType)) {
            return@analyze KotlinPoetCallTarget(
                methodName = methodName,
                receiverFqName = receiverType.asFqNameOrNull(),
                isDelegated = false,
            )
        }

        if (resolvedCall == null) {
            val qualified = call.getQualifiedExpressionForSelector()
            val fallbackReceiver = qualified?.receiverExpression?.expressionType

            // Guard: skip error types — they indicate K2 could not resolve the receiver
            // (e.g., unresolved reference, incomplete classpath in test environments).
            if (fallbackReceiver != null && !fallbackReceiver.isError() &&
                isKotlinPoetTarget(methodName, fallbackReceiver)
            ) {
                return@analyze KotlinPoetCallTarget(
                    methodName = methodName,
                    receiverFqName = fallbackReceiver.asFqNameOrNull(),
                    isDelegated = false,
                )
            }

            // Last-resort heuristic: receiver resolved to an error type but the method
            // name is a known KotlinPoet format method. Inspect the raw PSI text of the
            // receiver to check whether it looks like a KotlinPoet builder chain.
            // This handles cases like `FunSpec.builder("x").addStatement(...)` where K2
            // returns KaErrorType for the `.builder(...)` call result.
            if (methodName in FORMAT_METHODS || methodName in KDOC_METHODS) {
                val receiverText = qualified?.receiverExpression?.text.orEmpty()
                if (looksLikeKotlinPoetChain(receiverText)) {
                    return@analyze KotlinPoetCallTarget(
                        methodName = methodName,
                        receiverFqName = null,
                        isDelegated = false,
                    )
                }
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
        if (delegatedReceiver != null && !delegatedReceiver.isError() &&
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
}

private val FORMAT_METHODS = setOf("add", "addNamed", "addStatement", "addCode", "beginControlFlow", "of")
private val KDOC_METHODS = setOf("addKdoc")

private fun KaType.asFqNameOrNull(): String? = (this as? KaClassType)?.classId?.asSingleFqName()?.asString()

/**
 * `true` when this type is a [KaErrorType] — i.e., K2 could not resolve it.
 *
 * Used as a guard before all [isKotlinPoetTarget] calls so that unresolved/error
 * types are never mistakenly treated as valid KotlinPoet receiver types.
 */
private fun KaType.isError(): Boolean {
    contract { returns(true) implies (this@isError is KaErrorType) }
    return this is KaErrorType
}

private fun isKotlinPoetTarget(methodName: String, receiverType: KaType): Boolean {
    if (receiverType.isKotlinPoetBuilder()) return true
    val fqName = receiverType.asFqNameOrNull()
    if (fqName?.startsWith("com.squareup.kotlinpoet.") == true) {
        return methodName in KDOC_METHODS || methodName in FORMAT_METHODS
    }
    return false
}

/** Checks if a given type matches any of the known KotlinPoet builders that delegate to CodeBlock. */
private fun KaType.isKotlinPoetBuilder(): Boolean {
    if (this is KaClassType) {
        if (this.classId in Constants.ClassIds.ALL) return true
        val fqName = this.classId.asSingleFqName().asString()
        if (fqName.startsWith("com.squareup.kotlinpoet.") && fqName.endsWith(".Builder")) return true
    }
    return false
}

private val knownEntryPoints = setOf(
    "FunSpec",
    "CodeBlock",
    "TypeSpec",
    "PropertySpec",
    "FileSpec",
    "ParameterSpec",
    "AnnotationSpec",
)

/**
 * Heuristic used when K2 returns a [KaErrorType] for the receiver expression and
 * normal type-based resolution is unavailable.
 *
 * Inspects the **raw PSI text** of the receiver to detect known KotlinPoet entry-point
 * patterns. This covers the common case where a builder chain such as
 * `FunSpec.builder("foo").addStatement(...)` cannot have its receiver resolved as a
 * `KaClassType` — but the text still unambiguously identifies it as KotlinPoet.
 *
 * Only reached when `methodName` is already a known format/kdoc method, so false
 * positives (a non-KotlinPoet call that happens to share the name and a matching
 * receiver text) are extremely unlikely in practice.
 */
private fun looksLikeKotlinPoetChain(receiverText: String): Boolean {
    if (receiverText.isBlank()) return false
    return knownEntryPoints.any { receiverText.startsWith(it) } ||
        receiverText.contains("builder") ||
        receiverText.contains(".Builder")
}
