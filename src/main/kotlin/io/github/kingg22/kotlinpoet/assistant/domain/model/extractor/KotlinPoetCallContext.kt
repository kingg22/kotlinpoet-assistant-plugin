package io.github.kingg22.kotlinpoet.assistant.domain.model.extractor

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel

data class KotlinPoetCallContext(val format: FormatStringModel, val arguments: ArgumentSource)
