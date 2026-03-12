package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import io.github.kingg22.kotlinpoet.assistant.adapters.psi.PsiFormatTextExtractor
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText
import org.jetbrains.kotlin.psi.KtExpression

fun resolveFormatTextOrNull(ktExpression: KtExpression?): FormatText? = PsiFormatTextExtractor.extract(ktExpression)
