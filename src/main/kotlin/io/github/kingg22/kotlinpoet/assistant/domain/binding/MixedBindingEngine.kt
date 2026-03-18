package io.github.kingg22.kotlinpoet.assistant.domain.binding

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel

class MixedBindingEngine : BindingEngine {
    override fun bind(format: FormatStringModel, arguments: ArgumentSource): List<BoundPlaceholder> {
        require(format.style is FormatStringModel.FormatStyle.Mixed) { "Expected style Mixed, got ${format.style}" }
        return emptyList()
    }
}
