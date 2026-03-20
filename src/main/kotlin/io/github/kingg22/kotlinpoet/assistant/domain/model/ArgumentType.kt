package io.github.kingg22.kotlinpoet.assistant.domain.model

/** The type of argument */
sealed interface ArgumentType {
    data object NullType : ArgumentType
    data object StringType : ArgumentType
    data class Primitive(val fqName: String) : ArgumentType
    data class Class(val fqName: String, val supertypes: Set<String>) : ArgumentType
    data class Unknown(val reason: String) : ArgumentType
}
