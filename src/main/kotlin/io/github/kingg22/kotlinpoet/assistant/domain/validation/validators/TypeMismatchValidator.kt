package io.github.kingg22.kotlinpoet.assistant.domain.validation.validators

import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentType
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatValidator
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget

class TypeMismatchValidator : FormatValidator {
    override fun validate(bound: List<BoundPlaceholder>, arguments: ArgumentSource): List<FormatProblem> =
        bound.flatMap { bound ->
            val argType = bound.argument?.type ?: return@flatMap emptyList()
            if (argType is ArgumentType.Unknown) return@flatMap emptyList()

            val expected = expectedTypeFor(bound.placeholder.kind) ?: return@flatMap emptyList()
            if (isCompatible(expected, argType)) return@flatMap emptyList()

            val message = KPoetAssistantBundle.getMessage(
                "argument.format.type.mismatch",
                bound.placeholder.kind.value,
                expected.label,
                argTypeLabel(argType),
            )

            buildList {
                add(
                    FormatProblem(
                        severity = expected.severity,
                        message = message,
                        target = ProblemTarget.Placeholder(bound.placeholder.span),
                    ),
                )
                bound.argument.span?.let { span ->
                    add(
                        FormatProblem(
                            severity = expected.severity,
                            message = message,
                            target = ProblemTarget.Argument(span),
                        ),
                    )
                }
            }
        }

    private fun expectedTypeFor(kind: FormatKind): Expected? = when (kind) {
        FormatKind.STRING,
        FormatKind.STRING_TEMPLATE,
        -> Expected.STRING

        FormatKind.TYPE -> Expected.TYPE

        FormatKind.MEMBER -> Expected.MEMBER

        FormatKind.NAME -> Expected.NAME

        else -> null
    }

    private fun argTypeLabel(type: ArgumentType): String = when (type) {
        ArgumentType.StringType -> "String"
        is ArgumentType.Primitive -> "Primitive"
        is ArgumentType.Class -> type.fqName
        is ArgumentType.Unknown -> "Unknown"
    }

    private fun isCompatible(expected: Expected, argType: ArgumentType): Boolean = when (expected) {
        Expected.STRING -> when (argType) {
            ArgumentType.StringType -> true
            is ArgumentType.Class -> argType.isOrExtends(FQ_CHAR_SEQUENCE) || argType.isOrExtends(FQ_CODE_BLOCK)
            else -> false
        }

        Expected.TYPE -> argType is ArgumentType.Class && argType.matchesAny(TYPE_ACCEPTED)

        Expected.MEMBER -> argType is ArgumentType.Class && argType.isOrExtends(FQ_MEMBER_NAME)

        Expected.NAME -> when (argType) {
            ArgumentType.StringType -> true
            is ArgumentType.Class -> argType.matchesAny(NAME_ACCEPTED)
            else -> false
        }
    }

    private enum class Expected(val label: String, val severity: ProblemSeverity) {
        STRING("String", ProblemSeverity.INFORMATION),
        TYPE("Type", ProblemSeverity.WARNING),
        MEMBER("MemberName", ProblemSeverity.WARNING),
        NAME("Name", ProblemSeverity.WARNING),
    }

    private companion object {
        private const val FQ_CHAR_SEQUENCE = "kotlin.CharSequence"
        private const val FQ_MEMBER_NAME = "com.squareup.kotlinpoet.MemberName"
        private const val FQ_CODE_BLOCK = "com.squareup.kotlinpoet.CodeBlock"
        private const val FQ_TYPE_NAME = "com.squareup.kotlinpoet.TypeName"
        private const val FQ_KCLASS = "kotlin.reflect.KClass"
        private const val FQ_JAVA_CLASS = "java.lang.Class"
        private const val FQ_JAVA_TYPE = "java.lang.reflect.Type"
        private const val FQ_TYPE_MIRROR = "javax.lang.model.type.TypeMirror"
        private const val FQ_ELEMENT = "javax.lang.model.element.Element"

        private const val FQ_PARAMETER_SPEC = "com.squareup.kotlinpoet.ParameterSpec"
        private const val FQ_PROPERTY_SPEC = "com.squareup.kotlinpoet.PropertySpec"
        private const val FQ_FUN_SPEC = "com.squareup.kotlinpoet.FunSpec"
        private const val FQ_TYPE_SPEC = "com.squareup.kotlinpoet.TypeSpec"
        private const val FQ_CONTEXT_PARAMETER = "com.squareup.kotlinpoet.ContextParameter"

        private val NAME_ACCEPTED = setOf(
            FQ_CHAR_SEQUENCE,
            FQ_MEMBER_NAME,
            FQ_PARAMETER_SPEC,
            FQ_PROPERTY_SPEC,
            FQ_FUN_SPEC,
            FQ_TYPE_SPEC,
            FQ_CONTEXT_PARAMETER,
        )

        private val TYPE_ACCEPTED = setOf(
            FQ_TYPE_NAME,
            FQ_KCLASS,
            FQ_JAVA_CLASS,
            FQ_JAVA_TYPE,
            FQ_TYPE_MIRROR,
            FQ_ELEMENT,
        )
    }

    private fun ArgumentType.Class.isOrExtends(fqName: String): Boolean =
        this.fqName == fqName || supertypes.contains(fqName)

    private fun ArgumentType.Class.matchesAny(fqNames: Set<String>): Boolean = fqNames.any { isOrExtends(it) }
}
