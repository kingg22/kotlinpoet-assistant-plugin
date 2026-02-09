package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

fun KaSession.resolveStringOrNull(ktExpression: KtExpression?): String? = when (ktExpression) {
    null -> null

    // References to variable or constant is not supported
    is KtReferenceExpression -> null

    // Simple extraction for literals TODO proper handling of entries
    is KtStringTemplateExpression -> ktExpression.entries.joinToString("") { it.text }

    // Try to solve an extension function
    is KtDotQualifiedExpression -> {
        val receiverExpression = ktExpression.receiverExpression
        resolveStringOrNull(receiverExpression)
    }

    // TODO support KtBinaryExpression like string concatenation
    else -> {
        // SLOW path
        val kaConstantValue = ktExpression.evaluate()

        if (kaConstantValue is KaConstantValue.StringValue) {
            kaConstantValue.value
        } else {
            null
        }
    }
}
