package io.github.kingg22.kotlinpoet.assistant.infrastructure

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.kingg22.kotlinpoet.assistant.Constants
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import org.jetbrains.kotlin.psi.KtCallExpression

fun IntRange.toTextRange(): TextRange = TextRange(first, last + 1)

fun ProblemTarget.toTextRange(element: PsiElement): TextRange = when (this) {
    ProblemTarget.Call -> element.textRange

    // Rango de toda la llamada
    is ProblemTarget.TextRange,
    // Si tienes un target para Placeholders ya bound:
    is ProblemTarget.Placeholder,
    -> range.toTextRange()
}

fun KtCallExpression.looksLikeKotlinPoetCall(): Boolean {
    return (calleeExpression?.text ?: return false) in Constants.KOTLINPOET_CALLS
}
