package io.github.kingg22.kotlinpoet.assistant.domain.validation

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder

data class BoundContext(val bound: List<BoundPlaceholder>, val arguments: ArgumentSource)
