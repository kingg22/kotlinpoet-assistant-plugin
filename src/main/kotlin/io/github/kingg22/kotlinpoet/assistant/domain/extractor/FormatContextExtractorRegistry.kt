package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParserImpl
import org.jetbrains.kotlin.psi.KtCallExpression

object FormatContextExtractorRegistry {
    private val parser = StringFormatParserImpl()
    private val extractors: List<FormatContextExtractor> = listOf(
        NamedFormatExtractor(parser),
        VarargFormatExtractor(parser),
    )

    @JvmStatic
    fun extract(call: KtCallExpression): KotlinPoetCallContext? = extractors.firstNotNullOfOrNull { it.extract(call) }
}
