package io.github.kingg22.kotlinpoet.assistant.application.usecase

import io.github.kingg22.kotlinpoet.assistant.domain.extractor.FormatContextExtractorRegistry
import org.jetbrains.kotlin.psi.KtCallExpression

fun KtCallExpression.isKotlinPoetCall(
    extractorRegistry: FormatContextExtractorRegistry = FormatContextExtractorRegistry,
): Boolean = extractorRegistry.extract(this) != null
