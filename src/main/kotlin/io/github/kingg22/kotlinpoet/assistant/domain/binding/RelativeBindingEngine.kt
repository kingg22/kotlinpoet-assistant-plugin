package io.github.kingg22.kotlinpoet.assistant.domain.binding

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding

/**
 * Validaciones:
 * - faltan args
 * - args sin usar
 */
class RelativeBindingEngine : BindingEngine {
    override fun bind(format: FormatStringModel, arguments: ArgumentSource): List<BoundPlaceholder> {
        if (arguments !is ArgumentSource.VarArgs) return emptyList()
        val args = arguments.arguments
        return format.placeholders.mapIndexed { i, p ->
            require(p.binding is PlaceholderBinding.Relative) { "Expected relative binding" }
            BoundPlaceholder(
                placeholder = p,
                argument = args.getOrNull(i),
            )
        }
    }
}
