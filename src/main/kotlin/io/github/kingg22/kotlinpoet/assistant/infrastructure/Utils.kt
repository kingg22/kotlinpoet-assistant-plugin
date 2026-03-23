package io.github.kingg22.kotlinpoet.assistant.infrastructure

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import io.github.kingg22.kotlinpoet.assistant.Constants
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Converts an [IntRange] to a [TextRange]
 *
 * Returns the [TextRange] representing the converted range, with the end position adjusted to be inclusive.
 */
fun IntRange.toTextRange(): TextRange = TextRange(first, last + 1)

fun TextSpan.toTextRanges(): List<TextRange> = ranges.map { it.toTextRange() }

fun ProblemTarget.toTextRanges(element: PsiElement): List<TextRange> = when (this) {
    ProblemTarget.Call -> listOf(element.textRange)
    is ProblemTarget.TextSpanTarget, is ProblemTarget.Placeholder, is ProblemTarget.Argument -> span.toTextRanges()
}

fun KtCallExpression.looksLikeKotlinPoetCall(): Boolean {
    return (calleeExpression?.text ?: return false) in Constants.KOTLINPOET_CALLS
}

internal fun String.unescaped(): String = StringUtil.unescapeStringCharacters(this)

private val associateNotNullToken = object {}

internal fun <T, K, V> Iterable<T>.associateNotNull(valueSelector: (T) -> Pair<K, V>?): Map<K, V> {
    @Suppress("UNCHECKED_CAST")
    return associate { valueSelector(it) ?: (associateNotNullToken to null) }
        .filterKeys { it != associateNotNullToken } as Map<K, V>
}
