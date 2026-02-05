package io.github.kingg22.kotlinpoet.assistant.domain.binding

import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.FormatStyle
import kotlin.contracts.contract

object BindingEngineResolver {
    @JvmStatic
    fun forStyle(style: FormatStyle): BindingEngine {
        contract { returns() implies (style !is FormatStyle.Mixed) }
        return when (style) {
            FormatStyle.None -> NoneBindingEngine()
            FormatStyle.Relative -> RelativeBindingEngine()
            FormatStyle.Positional -> PositionalBindingEngine()
            FormatStyle.Named -> NamedBindingEngine()
            FormatStyle.Mixed -> error("Invalid mix of relative, positional or named placeholders")
        }
    }
}
