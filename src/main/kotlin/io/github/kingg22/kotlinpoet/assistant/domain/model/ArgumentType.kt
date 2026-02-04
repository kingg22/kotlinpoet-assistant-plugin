package io.github.kingg22.kotlinpoet.assistant.domain.model

/** The type of argument */
sealed interface ArgumentType {
    data object StringType : ArgumentType
    data object Primitive : ArgumentType
    data class Class(val fqName: String) : ArgumentType
    data class Unknown(val reason: String) : ArgumentType
}
