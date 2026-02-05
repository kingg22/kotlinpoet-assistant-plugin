package io.github.kingg22.kotlinpoet.assistant.domain.binding

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding

/**
 * Validaciones:
 * - índice fuera de rango
 * - args no usados
 */
class PositionalBindingEngine : BindingEngine {
    override fun bind(format: FormatStringModel, arguments: ArgumentSource): List<BoundPlaceholder> {
        if (arguments !is ArgumentSource.VarArgs) return emptyList()
        val args = arguments.arguments
        return format.placeholders.map { p ->
            require(p.binding is PlaceholderBinding.Positional) { "Expected positional binding" }
            val index = p.binding.index1Based - 1
            BoundPlaceholder(p, args.getOrNull(index))
        }
    }
}
