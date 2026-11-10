package io.github.kingg22.kotlinpoet.assistant.adapters.types

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentType
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinAnalysisBridges
import io.github.kingg22.kotlinpoet.assistant.k2.TypeInfo
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Utility to map K2 Analysis API types (via [TypeInfo]) to our Domain ArgumentType.
 */
object ArgumentTypeMapper {
    /** Resolves the type of [expression] and maps it. Must be called inside a read action. */
    @JvmStatic
    fun map(expression: KtExpression?): ArgumentType {
        val type = expression?.let { KotlinAnalysisBridges.instance.typeOf(it, withSupertypes = true) }
        return map(type)
    }

    @JvmStatic
    fun map(type: TypeInfo?): ArgumentType {
        if (type == null) return ArgumentType.NullType

        val classId = type.classId ?: return ArgumentType.Unknown("Complex or unresolved type: ${type.description}")
        val fqName = classId.asSingleFqName().asString()

        // 1. Check for String
        if (fqName == "kotlin.String") return ArgumentType.StringType

        // 2. Check for Primitives (Int, Boolean, etc.)
        if (fqName in primitiveTypes) return ArgumentType.Primitive(fqName)

        // 3. Capture class type + supertypes to allow hierarchy checks
        return ArgumentType.Class(fqName, type.supertypeFqNames)
    }
}

private val primitiveTypes = setOf(
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Boolean",
    "kotlin.Double",
    "kotlin.Float",
)
