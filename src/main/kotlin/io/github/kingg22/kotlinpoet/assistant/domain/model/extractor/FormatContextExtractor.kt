package io.github.kingg22.kotlinpoet.assistant.domain.model.extractor

import org.jetbrains.kotlin.psi.KtCallExpression

interface FormatContextExtractor {
    fun extract(call: KtCallExpression): KotlinPoetCallContext?
}
