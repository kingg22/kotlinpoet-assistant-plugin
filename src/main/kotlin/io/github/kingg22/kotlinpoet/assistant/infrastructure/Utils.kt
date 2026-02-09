package io.github.kingg22.kotlinpoet.assistant.infrastructure

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.kingg22.kotlinpoet.assistant.Constants
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Converts an [IntRange] to a [TextRange], optionally applying a delta to shift
 * the start and end positions of the range.
 *
 * @param delta The value by which the start and end positions of the range are offset.
 * Defaults to 0, meaning no offset is applied.
 * @return A [TextRange] representing the converted range, with the end position adjusted
 * to be inclusive.
 */
fun IntRange.toTextRange(delta: Int = 0): TextRange = if (delta == 0) {
    TextRange(first, last + 1)
} else {
    TextRange(first + delta, last + delta + 1)
}

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
