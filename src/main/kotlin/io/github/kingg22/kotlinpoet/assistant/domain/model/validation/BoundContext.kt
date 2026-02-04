package io.github.kingg22.kotlinpoet.assistant.domain.model.validation

import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder

@JvmInline
value class BoundContext(val bound: List<BoundPlaceholder>)
