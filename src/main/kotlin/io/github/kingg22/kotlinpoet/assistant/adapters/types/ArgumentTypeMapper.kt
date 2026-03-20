package io.github.kingg22.kotlinpoet.assistant.adapters.types

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType

/**
 * Utility to map K2 Analysis API types to our Domain ArgumentType.
 */
object ArgumentTypeMapper {
    @JvmStatic
    fun KaSession.map(type: KaType?): ArgumentType {
        if (type == null) return ArgumentType.NullType

        if (type is KaClassType) {
            val fqName = type.classId.asSingleFqName().asString()

            // 1. Check for String
            if (fqName == "kotlin.String") return ArgumentType.StringType

            // 2. Check for Primitives (Int, Boolean, etc.)
            if (fqName in primitiveTypes) return ArgumentType.Primitive(fqName)

            // 3. Capture class type + supertypes to allow hierarchy checks
            val supertypes = type.allSupertypes
                .mapNotNull { superType ->
                    (superType as? KaClassType)?.classId?.asSingleFqName()?.asString()
                }
                .toSet()
            return ArgumentType.Class(fqName, supertypes)
        }

        return ArgumentType.Unknown("Complex or unresolved type: $type")
    }
}

private val primitiveTypes = setOf(
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Boolean",
    "kotlin.Double",
    "kotlin.Float",
)
