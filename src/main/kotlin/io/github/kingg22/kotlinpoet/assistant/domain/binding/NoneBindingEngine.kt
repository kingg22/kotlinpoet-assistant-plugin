package io.github.kingg22.kotlinpoet.assistant.domain.binding

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel

class NoneBindingEngine : BindingEngine {
    override fun bind(format: FormatStringModel, arguments: ArgumentSource): List<BoundPlaceholder> {
        require(format.style is FormatStringModel.FormatStyle.None) { "Expected style None, got ${format.style}" }
        require(format.placeholders.isEmpty()) { "Expected no placeholders, got ${format.placeholders}" }
        return emptyList()
    }
}
