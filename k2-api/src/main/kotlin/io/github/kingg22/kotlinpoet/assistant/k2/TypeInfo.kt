package io.github.kingg22.kotlinpoet.assistant.k2

import org.jetbrains.kotlin.name.ClassId

/**
 * @property classId set when the type is a class type.
 * @property isError `true` when K2 could not resolve the type.
 * @property supertypeFqNames fully-qualified names of all class supertypes (only when requested).
 * @property description human readable form, for diagnostics only.
 */
data class TypeInfo(
    val classId: ClassId?,
    val isError: Boolean,
    val supertypeFqNames: Set<String>,
    val description: String,
)
