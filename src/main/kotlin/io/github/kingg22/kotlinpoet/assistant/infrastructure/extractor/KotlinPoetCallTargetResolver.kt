package io.github.kingg22.kotlinpoet.assistant.infrastructure.extractor

import io.github.kingg22.kotlinpoet.assistant.Constants
import io.github.kingg22.kotlinpoet.assistant.domain.extractor.KotlinPoetCallTarget
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinAnalysisBridges
import io.github.kingg22.kotlinpoet.assistant.k2.TypeInfo
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

object KotlinPoetCallTargetResolver {
    @JvmStatic
    fun resolve(call: KtCallExpression): KotlinPoetCallTarget? = with(KotlinAnalysisBridges.instance) {
        val resolvedCall = resolveCall(call)

        val methodName = resolvedCall?.callableName ?: call.calleeExpression?.text.orEmpty()
        val receiverType = resolvedCall?.receiverType

        if (receiverType != null && !receiverType.isError && isKotlinPoetTarget(methodName, receiverType)) {
            return@with KotlinPoetCallTarget(
                methodName = methodName,
                receiverFqName = receiverType.asFqNameOrNull(),
                isDelegated = false,
            )
        }

        if (resolvedCall == null) {
            val qualified = call.getQualifiedExpressionForSelector()
            val fallbackReceiver = qualified?.receiverExpression?.let { typeOf(it) }

            // Guard: skip error types — they indicate K2 could not resolve the receiver
            // (e.g., unresolved reference, incomplete classpath in test environments).
            if (fallbackReceiver != null && !fallbackReceiver.isError &&
                isKotlinPoetTarget(methodName, fallbackReceiver)
            ) {
                return@with KotlinPoetCallTarget(
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
                    return@with KotlinPoetCallTarget(
                        methodName = methodName,
                        receiverFqName = null,
                        isDelegated = false,
                    )
                }
            }

            return@with null
        }

        val functionPsi = resolvedCall.symbolPsi as? KtNamedFunction ?: return@with null
        val body = functionPsi.bodyExpression ?: return@with null

        val delegated = body.collectDescendantsOfType<KtCallExpression>()
            .firstOrNull { it.calleeExpression?.text in FORMAT_METHODS || it.calleeExpression?.text in KDOC_METHODS }
            ?: return@with null

        val delegatedResolved = resolveCall(delegated) ?: return@with null
        val delegatedReceiver = delegatedResolved.receiverType
        if (delegatedReceiver != null && !delegatedReceiver.isError &&
            isKotlinPoetTarget(delegated.calleeExpression?.text.orEmpty(), delegatedReceiver)
        ) {
            return@with KotlinPoetCallTarget(
                methodName = delegated.calleeExpression?.text.orEmpty(),
                receiverFqName = delegatedReceiver.asFqNameOrNull(),
                isDelegated = true,
            )
        }

        return@with null
    }
}

private val FORMAT_METHODS = setOf("add", "addNamed", "addStatement", "addCode", "beginControlFlow", "of")
private val KDOC_METHODS = setOf("addKdoc")

private fun TypeInfo.asFqNameOrNull(): String? = classId?.asSingleFqName()?.asString()

private fun isKotlinPoetTarget(methodName: String, receiverType: TypeInfo): Boolean {
    if (receiverType.isKotlinPoetBuilder()) return true
    val fqName = receiverType.asFqNameOrNull()
    if (fqName?.startsWith("com.squareup.kotlinpoet.") == true) {
        return methodName in KDOC_METHODS || methodName in FORMAT_METHODS
    }
    return false
}

/** Checks if a given type matches any of the known KotlinPoet builders that delegate to CodeBlock. */
private fun TypeInfo.isKotlinPoetBuilder(): Boolean {
    val classId = classId ?: return false
    if (classId in Constants.ClassIds.ALL) return true
    val fqName = classId.asSingleFqName().asString()
    return fqName.startsWith("com.squareup.kotlinpoet.") && fqName.endsWith(".Builder")
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
