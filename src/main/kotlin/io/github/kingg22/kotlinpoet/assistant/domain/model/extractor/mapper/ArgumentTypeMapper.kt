package io.github.kingg22.kotlinpoet.assistant.domain.model.extractor.mapper

import io.github.kingg22.kotlinpoet.assistant.Constants
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType

/**
 * Utility to map K2 Analysis API types to our Domain ArgumentType.
 */
object ArgumentTypeMapper {
    @JvmStatic
    fun map(type: KaType?): ArgumentType {
        if (type == null) return ArgumentType.Unknown("Type is null")

        if (type is KaClassType) {
            val fqName = type.classId.asSingleFqName().asString()

            // 1. Check for String
            if (fqName == "kotlin.String") return ArgumentType.StringType

            // 2. Check for Primitives (Int, Boolean, etc.)
            if (fqName in primitiveTypes) return ArgumentType.Primitive

            // 3. Check for specific Class types
            // We capture the Fully Qualified Name to help the Validator later
            return ArgumentType.Class(fqName)
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

/** Checks if a given type matches any of the known KotlinPoet builders that delegate to CodeBlock. */
fun KaType.isKotlinPoetBuilder(): Boolean {
    // Check hierarchy or exact match
    if (this is KaClassType && this.classId in Constants.ClassIds.ALL) return true

    // TODO: Considerar verificar supertipos si KotlinPoet usa herencia para estos builders
    return false
}
