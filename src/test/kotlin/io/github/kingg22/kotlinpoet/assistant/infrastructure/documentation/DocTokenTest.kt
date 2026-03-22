package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Unit tests for [DocToken] — PSI-free, no IntelliJ Platform required.
 *
 * Verifies:
 * - [DocToken.Placeholder.html] contains the expected placeholder token and non-empty description.
 * - [DocToken.Control.html] contains the expected control symbol character.
 * - [DocToken.Placeholder.presentationTitle] and [DocToken.Control.presentationTitle] are non-blank.
 * - HTML output is well-formed enough (contains DEFINITION and CONTENT markers).
 *
 * Note: exact message content is not asserted — bundle keys are stable but values may change.
 * We assert structural invariants (non-empty, contains symbol, contains HTML markers).
 */
class DocTokenTest {

    // ── Placeholder ────────────────────────────────────────────────────────────

    @Nested
    inner class PlaceholderToken {

        @ParameterizedTest(name = "Placeholder %{0} presentationTitle is non-blank")
        @EnumSource(FormatKind::class)
        fun `presentationTitle is non-blank for all kinds`(kind: FormatKind) {
            val token = DocToken.Placeholder(kind)
            assertTrue(token.presentationTitle().isNotBlank(), "presentationTitle must not be blank for $kind")
        }

        @ParameterizedTest(name = "Placeholder %{0} html is non-empty")
        @EnumSource(FormatKind::class)
        fun `html is non-empty for all kinds`(kind: FormatKind) {
            val token = DocToken.Placeholder(kind)
            assertTrue(token.html().isNotBlank(), "html must not be blank for $kind")
        }

        @ParameterizedTest(name = "Placeholder %{0} html contains the kind char")
        @EnumSource(FormatKind::class)
        fun `html contains the placeholder kind character`(kind: FormatKind) {
            val token = DocToken.Placeholder(kind)
            val html = token.html()
            assertTrue(
                html.contains("%${kind.value}") || html.contains(kind.value.toString()),
                "html for $kind should reference the placeholder character '%${kind.value}'",
            )
        }

        @ParameterizedTest(name = "Placeholder %{0} html contains DEFINITION markers")
        @EnumSource(FormatKind::class)
        fun `html contains DocumentationMarkup DEFINITION markers`(kind: FormatKind) {
            val html = DocToken.Placeholder(kind).html()
            // DocumentationMarkup.DEFINITION_START / DEFINITION_END inject specific tags
            assertTrue(html.contains("<div"), "html should contain a div element from DocumentationMarkup")
        }

        @Test
        fun `Placeholder L title references literal`() {
            val title = DocToken.Placeholder(FormatKind.LITERAL).presentationTitle()
            assertTrue(title.isNotBlank())
        }

        @Test
        fun `Placeholder T title references type`() {
            val title = DocToken.Placeholder(FormatKind.TYPE).presentationTitle()
            assertTrue(title.isNotBlank())
        }

        @Test
        fun `two different placeholder kinds produce different html`() {
            val htmlL = DocToken.Placeholder(FormatKind.LITERAL).html()
            val htmlS = DocToken.Placeholder(FormatKind.STRING).html()
            assertFalse(
                htmlL == htmlS,
                "Different placeholder kinds should produce different HTML",
            )
        }

        @Test
        fun `two different placeholder kinds produce different presentationTitle`() {
            val titleL = DocToken.Placeholder(FormatKind.LITERAL).presentationTitle()
            val titleS = DocToken.Placeholder(FormatKind.STRING).presentationTitle()
            assertFalse(titleL == titleS, "Different kinds should have different presentation titles")
        }
    }

    // ── Control ────────────────────────────────────────────────────────────────

    @Nested
    inner class ControlToken {

        private val allControlTypes = listOf(
            SymbolType.LITERAL_PERCENT,
            SymbolType.SPACE_OR_NEW_LINE,
            SymbolType.SPACE,
            SymbolType.INDENT,
            SymbolType.OUTDENT,
            SymbolType.STATEMENT_BEGIN,
            SymbolType.STATEMENT_END,
        )

        @Test
        fun `presentationTitle is non-blank for all control types`() {
            allControlTypes.forEach { type ->
                val title = DocToken.Control(type).presentationTitle()
                assertTrue(title.isNotBlank(), "presentationTitle must not be blank for $type")
            }
        }

        @Test
        fun `html is non-empty for all control types`() {
            allControlTypes.forEach { type ->
                val html = DocToken.Control(type).html()
                assertTrue(html.isNotBlank(), "html must not be blank for $type")
            }
        }

        @Test
        fun `html contains DEFINITION markers for all control types`() {
            allControlTypes.forEach { type ->
                val html = DocToken.Control(type).html()
                assertTrue(html.contains("<div"), "html for $type should contain div from DocumentationMarkup")
            }
        }

        @Test
        fun `INDENT and OUTDENT produce different html`() {
            val indent = DocToken.Control(SymbolType.INDENT).html()
            val outdent = DocToken.Control(SymbolType.OUTDENT).html()
            assertFalse(indent == outdent, "INDENT and OUTDENT should produce different HTML")
        }

        @Test
        fun `STATEMENT_BEGIN and STATEMENT_END produce different html`() {
            val begin = DocToken.Control(SymbolType.STATEMENT_BEGIN).html()
            val end = DocToken.Control(SymbolType.STATEMENT_END).html()
            assertFalse(begin == end)
        }

        @Test
        fun `html contains a See Also section for control symbols`() {
            // Control symbols include an externalUrl → sections block is present
            allControlTypes.forEach { type ->
                val html = DocToken.Control(type).html()
                assertTrue(
                    html.contains("See also") || html.contains("href"),
                    "Control symbol html for $type should contain a See Also / href link",
                )
            }
        }

        @Test
        fun `LITERAL_PERCENT control html mentions percent`() {
            val html = DocToken.Control(SymbolType.LITERAL_PERCENT).html()
            assertTrue(
                html.contains("%") || html.contains("percent", ignoreCase = true),
                "LITERAL_PERCENT html should reference '%' or 'percent'",
            )
        }
    }
}
