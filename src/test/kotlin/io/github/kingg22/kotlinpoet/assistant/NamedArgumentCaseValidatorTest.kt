package io.github.kingg22.kotlinpoet.assistant

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentType
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.model.BoundPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.NAMED_ARG_LOWERCASE
import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.NamedArgumentCaseValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NamedArgumentCaseValidatorTest {

    private val validator = NamedArgumentCaseValidator()

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun boundWith(vararg names: String): List<BoundPlaceholder> = names.mapIndexed { i, name ->
        BoundPlaceholder(
            placeholder = PlaceholderSpec(
                FormatKind.LITERAL,
                PlaceholderBinding.Named(name),
                TextSpan.of(i * 2..(i * 2 + 1)),
            ),
            argument = ArgumentValue.named(name, ArgumentType.StringType),
        )
    }

    private fun namedMap(
        vararg pairs: Pair<String, ArgumentValue>,
        isComplete: Boolean = true,
    ): ArgumentSource.NamedMap = ArgumentSource.NamedMap(mapOf(*pairs), isComplete)

    private fun argValue(name: String) = ArgumentValue.named(name, ArgumentType.StringType)

    // ─── 1. Placeholder name validation (always active) ───────────────────────

    @Test
    fun `placeholder with uppercase name is reported`() {
        val bound = boundWith("Food")
        val args = namedMap("Food" to argValue("Food"))
        val problems = validator.validate(bound, args)

        assertEquals(1, problems.size, "Expected exactly one problem (deduplicated placeholder + key)")
        assertEquals(ProblemSeverity.WARNING, problems.first().severity)
        assertEquals("Food", problems.first().data)
        assertTrue(
            problems.first().target is ProblemTarget.Placeholder,
            "Placeholder target expected when both are invalid with the same name",
        )
    }

    @Test
    fun `placeholder with uppercase name reported even when map is incomplete`() {
        val bound = boundWith("Food")
        // isComplete = false → external variable map, keys are unknown
        val args = namedMap("Food" to argValue("Food"), isComplete = false)
        val problems = validator.validate(bound, args)

        // The map key is skipped (isComplete=false), but the placeholder name is still caught.
        assertEquals(1, problems.size)
        assertTrue(problems.first().target is ProblemTarget.Placeholder)
    }

    @Test
    fun `multiple invalid placeholder names each reported once`() {
        val bound = boundWith("Food", "Count")
        val args = namedMap("Food" to argValue("Food"), "Count" to argValue("Count"))
        val problems = validator.validate(bound, args)
        assertEquals(2, problems.size)
    }

    @Test
    fun `duplicate invalid placeholder name reported only once`() {
        // Same name appears twice in the format string (%Food:L ... %Food:L)
        val spec = PlaceholderSpec(FormatKind.LITERAL, PlaceholderBinding.Named("Food"), TextSpan.of(0..1))
        val bound = listOf(
            BoundPlaceholder(spec, argValue("Food")),
            BoundPlaceholder(spec, argValue("Food")),
        )
        val args = namedMap("Food" to argValue("Food"))
        val problems = validator.validate(bound, args)
        assertEquals(1, problems.size)
    }

    @Test
    fun `lowercase placeholder name is accepted`() {
        val bound = boundWith("food")
        val args = namedMap("food" to argValue("food"))
        assertTrue(validator.validate(bound, args).isEmpty())
    }

    // ─── 2. Map key validation (only when isComplete = true) ──────────────────

    @Test
    fun `invalid key in complete map reported when no matching placeholder`() {
        // Suppose the key is invalid but there's no placeholder with that name in bound.
        // This can happen if the format string uses a different (valid) placeholder name.
        val bound = boundWith("food") // placeholder is fine
        val args = namedMap(
            "food" to argValue("food"),
            "BadExtra" to argValue("BadExtra"), // no placeholder for this key
        )
        val problems = validator.validate(bound, args)
        // "BadExtra" is not in any placeholder → reported separately as Argument target
        assertEquals(1, problems.size)
        assertEquals("BadExtra", problems.first().data)
        assertTrue(
            problems.first().target is ProblemTarget.Argument ||
                problems.first().target == ProblemTarget.Call,
        )
    }

    @Test
    fun `invalid key in incomplete map is NOT reported`() {
        // bound has no named placeholders; map is incomplete → no validation at all
        val args = namedMap("BadKey" to argValue("BadKey"), isComplete = false)
        val problems = validator.validate(emptyList(), args)
        assertTrue(problems.isEmpty(), "Should suppress map-key validation when isComplete=false")
    }

    @Test
    fun `valid key in complete map not reported`() {
        val bound = boundWith("food")
        val args = namedMap("food" to argValue("food"))
        assertTrue(validator.validate(bound, args).isEmpty())
    }

    // ─── 3. VarArgs source is ignored ─────────────────────────────────────────

    @Test
    fun `varargs source produces no problems`() {
        val args = ArgumentSource.VarArgs(
            listOf(ArgumentValue.positionalOrRelative(1, ArgumentType.StringType)),
        )
        assertTrue(validator.validate(emptyList(), args).isEmpty())
    }

    // ─── 4. NAMED_ARG_LOWERCASE regex contract ─────────────────────────────────

    @Test
    fun `regex matches single lowercase char`() {
        assertTrue(NAMED_ARG_LOWERCASE.matches("a"))
    }

    @Test
    fun `regex accepts lowercase with digits suffix`() {
        assertTrue(NAMED_ARG_LOWERCASE.matches("food123"))
    }

    @Test
    fun `regex accepts lowercase with underscore suffix`() {
        assertTrue(NAMED_ARG_LOWERCASE.matches("food_item"))
    }

    @Test
    fun `regex accepts lowercase with mixed upper suffix`() {
        // "foodItem" starts lowercase → valid per KotlinPoet spec
        assertTrue(NAMED_ARG_LOWERCASE.matches("foodItem"))
    }

    @Test
    fun `regex rejects uppercase start`() {
        assertFalse(NAMED_ARG_LOWERCASE.matches("Food"))
    }

    @Test
    fun `regex rejects digit start`() {
        assertFalse(NAMED_ARG_LOWERCASE.matches("1food"))
    }

    @Test
    fun `regex rejects underscore start`() {
        assertFalse(NAMED_ARG_LOWERCASE.matches("_food"))
    }

    @Test
    fun `regex rejects empty string`() {
        assertFalse(NAMED_ARG_LOWERCASE.matches(""))
    }
}
