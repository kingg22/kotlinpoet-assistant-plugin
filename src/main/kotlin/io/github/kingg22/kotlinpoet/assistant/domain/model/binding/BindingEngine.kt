package io.github.kingg22.kotlinpoet.assistant.domain.model.binding

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel

sealed interface BindingEngine {
    fun bind(format: FormatStringModel, arguments: ArgumentSource): List<BoundPlaceholder>
}
