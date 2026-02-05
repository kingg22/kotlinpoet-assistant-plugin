package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import org.jetbrains.kotlin.psi.KtCallExpression

sealed interface FormatContextExtractor {
    fun extract(call: KtCallExpression): KotlinPoetCallContext?
}
