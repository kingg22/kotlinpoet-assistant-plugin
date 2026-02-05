package io.github.kingg22.kotlinpoet.assistant.domain.binding

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding

/**
 * Validaciones:
 * - clave inexistente
 * - claves no usadas
 * - naming rules (^[a-z][a-zA-Z0-9_]*$)
 */
class NamedBindingEngine : BindingEngine {
    override fun bind(format: FormatStringModel, arguments: ArgumentSource): List<BoundPlaceholder> {
        if (arguments !is ArgumentSource.NamedMap) return emptyList()
        val map = arguments.entries
        return format.placeholders.map { p ->
            require(p.binding is PlaceholderBinding.Named) { "Expected named binding" }
            val name = p.binding.name
            BoundPlaceholder(p, map[name])
        }
    }
}
