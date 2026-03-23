package io.github.kingg22.kotlinpoet.assistant.infrastructure.extractor

import io.github.kingg22.kotlinpoet.assistant.domain.extractor.KotlinPoetCallContext
import org.jetbrains.kotlin.psi.KtCallExpression

interface FormatContextExtractor {
    fun extract(call: KtCallExpression): KotlinPoetCallContext?
}
