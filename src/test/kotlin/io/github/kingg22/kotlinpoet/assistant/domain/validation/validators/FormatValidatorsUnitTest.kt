package io.github.kingg22.kotlinpoet.assistant.domain.validation.validators

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentType
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormatValidatorsUnitTest {
    @Test
    fun extraArgsInRelativeAreReported() {
        val placeholder = PlaceholderSpec(FormatKind.STRING, PlaceholderBinding.Relative, TextSpan.of(0..1))
        val bound = listOf(
            BoundPlaceholder(placeholder, ArgumentValue.positionalOrRelative(1, ArgumentType.StringType)),
        )
        val args = ArgumentSource.VarArgs(
            listOf(
                ArgumentValue.positionalOrRelative(1, ArgumentType.StringType),
                ArgumentValue.positionalOrRelative(2, ArgumentType.StringType),
            ),
        )

        val problems = ExtraArgumentValidator().validate(bound, args)
        assertEquals(1, problems.size)
    }

    @Test
    fun extraArgsInPositionalAreReported() {
        val placeholder = PlaceholderSpec(FormatKind.STRING, PlaceholderBinding.Positional(2), TextSpan.of(0..1))
        val bound = listOf(
            BoundPlaceholder(placeholder, ArgumentValue.positionalOrRelative(2, ArgumentType.StringType)),
        )
        val args = ArgumentSource.VarArgs(
            listOf(
                ArgumentValue.positionalOrRelative(1, ArgumentType.StringType),
                ArgumentValue.positionalOrRelative(2, ArgumentType.StringType),
            ),
        )

        val problems = ExtraArgumentValidator().validate(bound, args)
        assertEquals(1, problems.size)
    }

    @Test
    fun extraKeysInNamedAreReported() {
        val placeholder = PlaceholderSpec(
            FormatKind.STRING,
            PlaceholderBinding.Named("a"),
            TextSpan.of(0..1),
        )
        val bound = listOf(
            BoundPlaceholder(placeholder, ArgumentValue.named("a", ArgumentType.StringType)),
        )
        val args = ArgumentSource.NamedMap(
            mapOf(
                "a" to ArgumentValue.named("a", ArgumentType.StringType),
                "b" to ArgumentValue.named("b", ArgumentType.StringType),
            ),
            isComplete = true,
        )

        val problems = ExtraArgumentValidator().validate(bound, args)
        assertEquals(1, problems.size)
    }

    @Test
    fun typeMismatchForStringIsReported() {
        val placeholder = PlaceholderSpec(FormatKind.STRING, PlaceholderBinding.Relative, TextSpan.of(0..1))
        val bound = listOf(
            BoundPlaceholder(
                placeholder,
                ArgumentValue.positionalOrRelative(1, ArgumentType.Primitive("kotlin.Boolean")),
            ),
        )
        val args = ArgumentSource.VarArgs(
            listOf(ArgumentValue.positionalOrRelative(1, ArgumentType.Primitive("kotlin.String"))),
        )

        val problems = TypeMismatchValidator().validate(bound, args)
        assertEquals(1, problems.size)
    }

    @Test
    fun typeMatchForStringDoesNotReport() {
        val placeholder = PlaceholderSpec(FormatKind.STRING, PlaceholderBinding.Relative, TextSpan.of(0..1))
        val bound = listOf(
            BoundPlaceholder(placeholder, ArgumentValue.positionalOrRelative(1, ArgumentType.StringType)),
        )
        val args = ArgumentSource.VarArgs(listOf(ArgumentValue.positionalOrRelative(1, ArgumentType.StringType)))

        val problems = TypeMismatchValidator().validate(bound, args)
        assertTrue(problems.isEmpty())
    }
}
