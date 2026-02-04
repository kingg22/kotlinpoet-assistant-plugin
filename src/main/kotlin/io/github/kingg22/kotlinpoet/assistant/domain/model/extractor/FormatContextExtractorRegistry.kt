package io.github.kingg22.kotlinpoet.assistant.domain.model.extractor

import org.jetbrains.kotlin.psi.KtCallExpression

object FormatContextExtractorRegistry {
    private val extractors: List<FormatContextExtractor> = listOf(
        NamedFormatExtractor(),
        VarargFormatExtractor(),
    )

    @JvmStatic
    fun extract(call: KtCallExpression): KotlinPoetCallContext? = extractors.firstNotNullOfOrNull { it.extract(call) }
}
