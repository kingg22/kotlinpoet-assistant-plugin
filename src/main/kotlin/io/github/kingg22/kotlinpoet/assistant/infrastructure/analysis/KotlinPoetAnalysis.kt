package io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis

import io.github.kingg22.kotlinpoet.assistant.domain.binding.BindingEngineResolver
import io.github.kingg22.kotlinpoet.assistant.domain.extractor.KotlinPoetCallContext
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem

data class KotlinPoetAnalysis(
    val callContext: KotlinPoetCallContext,
    val bounds: List<BoundPlaceholder> = emptyList(),
    val problems: List<FormatProblem> = emptyList(),
    val isBound: Boolean = false,
    val isValidated: Boolean = false,
) {
    val argumentSource: ArgumentSource get() = callContext.arguments
    val format: FormatStringModel get() = callContext.format
    val formatText: FormatText get() = callContext.format.text
    val formatStyle: FormatStringModel.FormatStyle get() = callContext.format.style
    val placeholders: List<PlaceholderSpec> get() = format.placeholders
    val controlSymbols: List<ControlSymbol> get() = format.controlSymbols
    val formatProblems: List<FormatProblem> get() = format.errors
    val haveProblems: Boolean get() = problems.isNotEmpty()
    val haveFormatProblems: Boolean get() = formatProblems.isNotEmpty()

    fun bind(): KotlinPoetAnalysis = if (isBound) {
        this
    } else {
        val bindingEngine = BindingEngineResolver.forStyle(callContext.format.style)
        val boundPlaceholders = bindingEngine.bind(callContext.format, callContext.arguments)
        this.copy(bounds = boundPlaceholders, isBound = true)
    }
}
