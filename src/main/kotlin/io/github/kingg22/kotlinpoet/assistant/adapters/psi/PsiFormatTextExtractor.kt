package io.github.kingg22.kotlinpoet.assistant.adapters.psi

import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatTextSegment
import io.github.kingg22.kotlinpoet.assistant.domain.text.SegmentKind
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

object PsiFormatTextExtractor {
    fun extract(expression: KtExpression?): FormatText? {
        return when (expression) {
            null -> null

            is KtParenthesizedExpression -> extract(expression.expression)

            is KtStringTemplateExpression -> fromStringTemplate(expression)

            is KtBinaryExpression -> {
                if (expression.operationToken != KtTokens.PLUS) return null
                val left = extract(expression.left) ?: return null
                val right = extract(expression.right) ?: return null
                left + right
            }

            is KtDotQualifiedExpression -> null

            // String transformations change offsets; avoid unsafe mapping
            else -> null
        }
    }

    private fun fromStringTemplate(template: KtStringTemplateExpression): FormatText {
        val segments = template.entries.mapNotNull { entry ->
            val text = entry.text
            if (text.isEmpty()) return@mapNotNull null
            FormatTextSegment(
                text = text,
                range = entry.textRange.startOffset..<entry.textRange.endOffset,
                kind = entry.kind(),
            )
        }
        return FormatText(segments)
    }

    private fun KtStringTemplateEntry.kind(): SegmentKind = when (this) {
        is KtStringTemplateEntryWithExpression -> SegmentKind.DYNAMIC
        else -> SegmentKind.LITERAL
    }
}
