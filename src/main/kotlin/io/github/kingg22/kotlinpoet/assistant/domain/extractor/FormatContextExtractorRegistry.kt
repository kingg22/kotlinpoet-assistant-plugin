package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import org.jetbrains.kotlin.psi.KtCallExpression

object FormatContextExtractorRegistry {
    private val extractors: List<FormatContextExtractor> = listOf(
        NamedFormatExtractor(),
        VarargFormatExtractor(),
    )

    @JvmStatic
    fun extract(call: KtCallExpression, boundOffsetOfCall: Boolean = false): KotlinPoetCallContext? =
        extractors.firstNotNullOfOrNull { it.extract(call, boundOffsetOfCall) }
}
