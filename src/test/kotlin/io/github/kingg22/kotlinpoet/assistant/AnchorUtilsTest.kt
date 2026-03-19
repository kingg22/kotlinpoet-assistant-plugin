package io.github.kingg22.kotlinpoet.assistant

import com.intellij.openapi.util.TextRange
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.buildAnchorsForPlaceholders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pure unit tests for [buildAnchorsForPlaceholders] and `tokenText``.
 *
 * Both functions are PSI-free: [buildAnchorsForPlaceholders] accepts a plain [String]
 * for the element text, so the full anchor-building pipeline can be verified without
 * the IntelliJ Platform.
 *
 * ## Structure
 *
 * - [TokenTextReconstruction] — verifies `tokenText` for every binding/kind combination.
 * - [AnchorBuilding] — verifies [buildAnchorsForPlaceholders] range and default value output.
 * - [MultiOccurrence] — critical tests for the `searchFrom` advancement when the same
 *   token appears more than once.
 * - [EdgeCases] — empty input, multi-segment spans, token not found, precondition check.
 */
class AnchorUtilsTest {

    // ── DSL helpers ────────────────────────────────────────────────────────────

    private fun relative(kind: FormatKind): PlaceholderSpec =
        PlaceholderSpec(kind, PlaceholderBinding.Relative, TextSpan.of(0..1))

    private fun positional(kind: FormatKind, index: Int): PlaceholderSpec =
        PlaceholderSpec(kind, PlaceholderBinding.Positional(index), TextSpan.of(0..1))

    private fun named(kind: FormatKind, name: String): PlaceholderSpec =
        PlaceholderSpec(kind, PlaceholderBinding.Named(name), TextSpan.of(0..1))

    /** Multi-range span — should be skipped by buildAnchorsForPlaceholders. */
    private fun multiSpan(kind: FormatKind): PlaceholderSpec =
        PlaceholderSpec(kind, PlaceholderBinding.Relative, TextSpan(listOf(0..1, 5..6)))

    private fun anchors(
        placeholders: List<PlaceholderSpec>,
        elementText: String,
        defaults: List<String> = placeholders.indices.map { "default$it" },
        vars: List<String> = placeholders.indices.map { "var$it" },
    ) = buildAnchorsForPlaceholders(placeholders, elementText, defaults, vars)

    @Nested
    inner class TokenTextReconstruction {

        @Test
        fun `relative L`() {
            assertEquals("%L", relative(FormatKind.LITERAL).tokenText())
        }

        @Test
        fun `relative S`() {
            assertEquals("%S", relative(FormatKind.STRING).tokenText())
        }

        @Test
        fun `relative T`() {
            assertEquals("%T", relative(FormatKind.TYPE).tokenText())
        }

        @Test
        fun `relative N`() {
            assertEquals("%N", relative(FormatKind.NAME).tokenText())
        }

        @Test
        fun `relative M`() {
            assertEquals("%M", relative(FormatKind.MEMBER).tokenText())
        }

        @Test
        fun `relative P`() {
            assertEquals("%P", relative(FormatKind.STRING_TEMPLATE).tokenText())
        }

        @Test
        fun `positional index 1 L`() {
            assertEquals("%1L", positional(FormatKind.LITERAL, 1).tokenText())
        }

        @Test
        fun `positional index 2 S`() {
            assertEquals("%2S", positional(FormatKind.STRING, 2).tokenText())
        }

        @Test
        fun `positional multi-digit index 12 T`() {
            assertEquals("%12T", positional(FormatKind.TYPE, 12).tokenText())
        }

        @Test
        fun `named food colon L`() {
            assertEquals("%food:L", named(FormatKind.LITERAL, "food").tokenText())
        }

        @Test
        fun `named count colon S`() {
            assertEquals("%count:S", named(FormatKind.STRING, "count").tokenText())
        }

        @Test
        fun `named with underscore and digits`() {
            assertEquals("%my_type123:T", named(FormatKind.TYPE, "my_type123").tokenText())
        }

        @Test
        fun `named uppercase name round-trips`() {
            assertEquals("%Food:L", named(FormatKind.LITERAL, "Food").tokenText())
        }
    }

    @Nested
    inner class AnchorBuilding {

        @Test
        fun `single relative token inside quoted string`() {
            // formatArg.text for "hello %L" → "\"hello %L\""
            val result = anchors(
                listOf(relative(FormatKind.LITERAL)),
                elementText = "\"hello %L\"",
                defaults = listOf("%name0:L"),
                vars = listOf("name0"),
            )

            assertEquals(1, result.size)
            val anchor = result.first()
            assertEquals(TextRange(7, 9), anchor.rangeInElement) // %L at index 7, length 2
            assertEquals("name0", anchor.variableName)
            assertEquals("%name0:L", anchor.defaultValue)
        }

        @Test
        fun `single named token inside quoted string`() {
            // formatArg.text for "%food:L" → "\"%food:L\""
            val result = anchors(
                listOf(named(FormatKind.LITERAL, "food")),
                elementText = "\"%food:L\"",
                defaults = listOf("%name0:L"),
                vars = listOf("name0"),
            )

            assertEquals(1, result.size)
            val anchor = result.first()
            assertEquals(TextRange(1, 8), anchor.rangeInElement) // %food:L = 7 chars at index 1
            assertEquals("%name0:L", anchor.defaultValue)
        }

        @Test
        fun `single positional token`() {
            // formatArg.text for "result %2S end" → "\"result %2S end\""
            val result = anchors(
                listOf(positional(FormatKind.STRING, 2)),
                elementText = "\"result %2S end\"",
                defaults = listOf("%1S"),
                vars = listOf("idx0"),
            )

            assertEquals(1, result.size)
            assertEquals(TextRange(8, 11), result.first().rangeInElement) // %2S = 3 chars at index 8
        }

        @Test
        fun `two distinct tokens are located independently`() {
            // formatArg.text for "val %S = %L\n" → "\"val %S = %L\\n\""
            val result = anchors(
                listOf(relative(FormatKind.STRING), relative(FormatKind.LITERAL)),
                elementText = "\"val %S = %L\\n\"",
                defaults = listOf("%name0:S", "%name1:L"),
                vars = listOf("name0", "name1"),
            )

            assertEquals(2, result.size)
            assertEquals(TextRange(5, 7), result[0].rangeInElement) // %S at index 5
            assertEquals(TextRange(10, 12), result[1].rangeInElement) // %L at index 10
        }

        @Test
        fun `three distinct kinds each located correctly`() {
            // formatArg.text for "%T %N %L" → "\"%T %N %L\""
            val result = anchors(
                listOf(
                    relative(FormatKind.TYPE),
                    relative(FormatKind.NAME),
                    relative(FormatKind.LITERAL),
                ),
                elementText = "\"%T %N %L\"",
                defaults = listOf("%name0:T", "%name1:N", "%name2:L"),
                vars = listOf("name0", "name1", "name2"),
            )

            assertEquals(3, result.size)
            assertEquals(TextRange(1, 3), result[0].rangeInElement) // %T
            assertEquals(TextRange(4, 6), result[1].rangeInElement) // %N
            assertEquals(TextRange(7, 9), result[2].rangeInElement) // %L
        }

        @Test
        fun `raw triple-quoted string offset is correct`() {
            // formatArg.text for """hello %L""" → "\"\"\"hello %L\"\"\""  (3+5+2+3 = 13 chars)
            val result = anchors(
                listOf(relative(FormatKind.LITERAL)),
                elementText = "\"\"\"hello %L\"\"\"",
                defaults = listOf("%name0:L"),
                vars = listOf("name0"),
            )

            assertEquals(1, result.size)
            assertEquals(TextRange(9, 11), result.first().rangeInElement) // %L after """hello (9 chars)
        }

        @Test
        fun `anchor range length equals token text length`() {
            val placeholders = listOf(
                relative(FormatKind.LITERAL), // %L  = 2 chars
                positional(FormatKind.STRING, 3), // %3S = 3 chars
                named(FormatKind.TYPE, "myType"), // %myType:T = 9 chars
            )
            val elementText = "\"%L %3S %myType:T\""
            val result = anchors(placeholders, elementText)

            assertEquals(3, result.size)
            assertEquals(2, result[0].rangeInElement.length) // %L
            assertEquals(3, result[1].rangeInElement.length) // %3S
            assertEquals(9, result[2].rangeInElement.length) // %myType:T
        }
    }

    @Nested
    inner class MultiOccurrence {
        @Test
        fun `two identical relative tokens get distinct positions`() {
            // formatArg.text for "%L %L" → "\"%L %L\""
            val result = anchors(
                listOf(relative(FormatKind.LITERAL), relative(FormatKind.LITERAL)),
                elementText = "\"%L %L\"",
                defaults = listOf("%name0:L", "%name1:L"),
                vars = listOf("name0", "name1"),
            )

            assertEquals(2, result.size)
            assertEquals(TextRange(1, 3), result[0].rangeInElement) // first %L
            assertEquals(TextRange(4, 6), result[1].rangeInElement) // second %L — not re-matched
            assertTrue(result[0].rangeInElement.startOffset < result[1].rangeInElement.startOffset)
        }

        @Test
        fun `three identical relative tokens all get distinct positions`() {
            // formatArg.text for "%L %L %L" → "\"%L %L %L\""
            val result = anchors(
                listOf(
                    relative(FormatKind.LITERAL),
                    relative(FormatKind.LITERAL),
                    relative(FormatKind.LITERAL),
                ),
                elementText = "\"%L %L %L\"",
            )

            assertEquals(3, result.size)
            assertEquals(TextRange(1, 3), result[0].rangeInElement)
            assertEquals(TextRange(4, 6), result[1].rangeInElement)
            assertEquals(TextRange(7, 9), result[2].rangeInElement)
        }

        @Test
        fun `mixed named and relative same kind - searchFrom prevents duplicate match`() {
            // formatArg.text for "%L %name:L %L" → "\"%L %name:L %L\""
            // Placeholders in document order: %L(1), %name:L(4), %L(12)
            val result = anchors(
                listOf(
                    relative(FormatKind.LITERAL),
                    named(FormatKind.LITERAL, "name"),
                    relative(FormatKind.LITERAL),
                ),
                elementText = "\"%L %name:L %L\"",
                defaults = listOf("%name0:L", "%name1:L", "%name2:L"),
                vars = listOf("name0", "name1", "name2"),
            )

            assertEquals(3, result.size)
            assertEquals(TextRange(1, 3), result[0].rangeInElement) // first %L
            assertEquals(TextRange(4, 11), result[1].rangeInElement) // %name:L (7 chars)
            assertEquals(TextRange(12, 14), result[2].rangeInElement) // second %L — not re-matched at 1
        }

        @Test
        fun `positional re-numbering produces correct consecutive positions`() {
            // formatArg.text for "%2S %1L" (positional out of order) → "\"%2S %1L\""
            // Both need to be located independently.
            val result = anchors(
                listOf(
                    positional(FormatKind.STRING, 2),
                    positional(FormatKind.LITERAL, 1),
                ),
                elementText = "\"%2S %1L\"",
                defaults = listOf("%1S", "%2L"),
                vars = listOf("idx0", "idx1"),
            )

            assertEquals(2, result.size)
            assertEquals(TextRange(1, 4), result[0].rangeInElement) // %2S = 3 chars
            assertEquals(TextRange(5, 8), result[1].rangeInElement) // %1L = 3 chars
        }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `empty placeholder list returns empty anchors`() {
            val result =
                anchors(emptyList(), elementText = "\"hello world\"", defaults = emptyList(), vars = emptyList())
            assertTrue(result.isEmpty())
        }

        @Test
        fun `placeholder with multi-segment span is skipped`() {
            val result = anchors(
                listOf(multiSpan(FormatKind.LITERAL)),
                elementText = "\"%L\"",
            )
            assertTrue(result.isEmpty(), "Multi-segment span placeholder must be skipped")
        }

        @Test
        fun `token not found in element text returns no anchor for that placeholder`() {
            // %S is not present in the text — should be skipped gracefully.
            val result = anchors(
                listOf(relative(FormatKind.STRING)),
                elementText = "\"hello %L\"",
                defaults = listOf("%name0:S"),
                vars = listOf("name0"),
            )
            assertTrue(result.isEmpty(), "Token not found must produce no anchor")
        }

        @Test
        fun `token not found does not prevent subsequent tokens from being found`() {
            // %S missing, but %L is present — %L anchor must still be produced.
            val result = anchors(
                listOf(
                    relative(FormatKind.STRING), // not in text — skipped
                    relative(FormatKind.LITERAL), // present — must be found
                ),
                elementText = "\"hello %L\"",
                defaults = listOf("%name0:S", "%name1:L"),
                vars = listOf("name0", "name1"),
            )
            assertEquals(1, result.size)
            assertNotNull(result.firstOrNull())
            assertEquals("%name1:L", result.first().defaultValue)
        }

        @Test
        fun `mismatched list sizes throw IllegalStateException`() {
            assertThrows<IllegalStateException> {
                buildAnchorsForPlaceholders(
                    placeholders = listOf(relative(FormatKind.LITERAL)),
                    elementText = "\"%L\"",
                    defaultValues = listOf("a", "b"), // size 2 != 1
                    variableNames = listOf("v0"),
                )
            }
        }

        @Test
        fun `anchor ranges are non-overlapping for adjacent tokens`() {
            // formatArg.text for "%L%S" (no space) → "\"%L%S\""
            val result = anchors(
                listOf(relative(FormatKind.LITERAL), relative(FormatKind.STRING)),
                elementText = "\"%L%S\"",
                defaults = listOf("%name0:L", "%name1:S"),
                vars = listOf("name0", "name1"),
            )
            assertEquals(2, result.size)
            val (a0, a1) = result
            // Ranges must not overlap.
            assertFalse(
                a0.rangeInElement.intersectsStrict(a1.rangeInElement),
                "Adjacent token anchors must not overlap: ${a0.rangeInElement} vs ${a1.rangeInElement}",
            )
            assertEquals(TextRange(1, 3), a0.rangeInElement) // %L
            assertEquals(TextRange(3, 5), a1.rangeInElement) // %S immediately after
        }

        @Test
        fun `control symbols in element text do not confuse token search`() {
            // formatArg.text for "«%L»" → "\"«%L»\""
            val result = anchors(
                listOf(relative(FormatKind.LITERAL)),
                elementText = "\"«%L»\"",
                defaults = listOf("%name0:L"),
                vars = listOf("name0"),
            )
            assertEquals(1, result.size)
            // «  is a 3-byte UTF-8 char but a single Char in Kotlin String (U+00AB).
            // "\"«%L»\"" → index 0=", 1=«, 2=%, 3=L, 4=», 5="
            assertEquals(TextRange(2, 4), result.first().rangeInElement)
        }
    }
}
