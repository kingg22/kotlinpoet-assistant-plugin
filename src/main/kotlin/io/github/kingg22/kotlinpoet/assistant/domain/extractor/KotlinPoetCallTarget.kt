package io.github.kingg22.kotlinpoet.assistant.domain.extractor

data class KotlinPoetCallTarget(val methodName: String, val receiverFqName: String?, val isDelegated: Boolean)
