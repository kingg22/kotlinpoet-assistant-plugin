package io.github.kingg22.kotlinpoet.assistant

import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.FormatStyle
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.ParserIssueKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParser
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParserImpl
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatTextSegment
import io.github.kingg22.kotlinpoet.assistant.domain.text.SegmentKind
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Comprehensive tests for [StringFormatParserImpl].
 *
 * ## Structure
 * - [HappyPath] — correct inputs, correct outputs (black box)
 * - [WarningLevelIssues] — non-blocking diagnostics (black box)
 * - [BlockingErrors] — blocking errors diagnostic (black box)
 * - [StyleDetection] — `FormatStyle` enum correctness (black box)
 * - [SpanAccuracy] — absolute PSI offset mapping (white box)
 * - [DynamicSegments] — DYNAMIC segment skipping (white box)
 * - [StateFlagInvariant] — style flags only set on valid placeholders (white box)
 * - [Safety] — no infinite loop, no crash, no state corruption (stress)
 */
class StringFormatParserImplTest {

    private val parser: StringFormatParser = StringFormatParserImpl()

    // ── Test DSL ───────────────────────────────────────────────────────────────

    /**
     * Creates a single-segment [FormatText] where logical index == PSI offset.
     * `text("hello")` → segment("hello", range=0..4, LITERAL)
     */
    private fun text(raw: String, kind: SegmentKind = SegmentKind.LITERAL): FormatText = if (raw.isEmpty()) {
        FormatText(emptyList())
    } else {
        FormatText(listOf(FormatTextSegment(raw, 0 until raw.length, kind)))
    }

    /**
     * Multi-segment helper. Each pair is (text, absoluteStart).
     * Range end = absoluteStart + text.length - 1.
     */
    private fun text(vararg segments: Pair<String, Int>): FormatText =
        FormatText(segments.map { (t, start) -> FormatTextSegment(t, start until start + t.length) })

    private fun parse(raw: String, isNamedStyle: Boolean = false): FormatStringModel =
        parser.parse(text(raw), isNamedStyle)

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Nested
    inner class HappyPath {

        @Test
        fun `empty string produces empty model`() {
            val model = parser.parse(FormatText(emptyList()))
            assertTrue(model.placeholders.isEmpty())
            assertTrue(model.controlSymbols.isEmpty())
            assertTrue(model.errors.isEmpty())
            assertTrue(model.warnings.isEmpty())
            assertEquals(FormatStyle.None, model.style)
        }

        @Test
        fun `plain literal text has no placeholders`() {
            val model = parse("hello world")
            assertTrue(model.placeholders.isEmpty())
            assertTrue(model.errors.isEmpty())
            assertTrue(model.warnings.isEmpty())
        }

        // ── All 6 relative placeholder kinds ─────────────────────────────────

        @ParameterizedTest(name = "relative placeholder %{0} is recognised")
        @ValueSource(strings = ["L", "S", "T", "N", "M", "P"])
        fun `relative placeholder kinds are all recognised`(kindChar: String) {
            val model = parse("%$kindChar")
            assertEquals(1, model.placeholders.size)
            assertEquals(checkNotNull(FormatKind.fromChar(kindChar.first())), model.placeholders.first().kind)
            assertEquals(PlaceholderBinding.Relative, model.placeholders.first().binding)
            assertTrue(model.errors.isEmpty())
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `multiple relative placeholders preserve order`() {
            val model = parse("%L %S %T")
            assertEquals(3, model.placeholders.size)
            assertEquals(FormatKind.LITERAL, model.placeholders[0].kind)
            assertEquals(FormatKind.STRING, model.placeholders[1].kind)
            assertEquals(FormatKind.TYPE, model.placeholders[2].kind)
        }

        @Test
        fun `back-to-back relative placeholders are parsed correctly`() {
            val model = parse("%L%S%N")
            assertEquals(3, model.placeholders.size)
        }

        // ── Positional ────────────────────────────────────────────────────────

        @Test
        fun `positional single-digit index`() {
            val model = parse("%1L")
            assertEquals(1, model.placeholders.size)
            val binding = model.placeholders.first().binding as PlaceholderBinding.Positional
            assertEquals(1, binding.index1Based)
        }

        @Test
        fun `positional multi-digit index`() {
            val model = parse("%12S")
            val binding = model.placeholders.first().binding as PlaceholderBinding.Positional
            assertEquals(12, binding.index1Based)
        }

        @Test
        fun `positional out-of-order indices are parsed independently`() {
            val model = parse("%2L %1S")
            assertEquals(2, model.placeholders.size)
            val p1 = model.placeholders[0].binding as PlaceholderBinding.Positional
            val p2 = model.placeholders[1].binding as PlaceholderBinding.Positional
            assertEquals(2, p1.index1Based)
            assertEquals(1, p2.index1Based)
        }

        // ── Named ─────────────────────────────────────────────────────────────

        @Test
        fun `named placeholder single name`() {
            val model = parse("%food:L", isNamedStyle = true)
            assertEquals(1, model.placeholders.size)
            val binding = model.placeholders.first().binding as PlaceholderBinding.Named
            assertEquals("food", binding.name)
            assertEquals(FormatKind.LITERAL, model.placeholders.first().kind)
        }

        @Test
        fun `named placeholder with digits and underscores`() {
            val model = parse("%food_item123:S", isNamedStyle = true)
            val binding = model.placeholders.first().binding as PlaceholderBinding.Named
            assertEquals("food_item123", binding.name)
        }

        @Test
        fun `named placeholder uppercase name passes parsing`() {
            // Parsing accepts any \w+ — the NamedArgumentCaseValidator enforces lowercase.
            val model = parse("%Food:L", isNamedStyle = true)
            assertEquals(1, model.placeholders.size)
            assertTrue(model.errors.isEmpty(), "Parser should not reject uppercase names")
        }

        @Test
        fun `multiple named placeholders`() {
            val model = parse("%count:L %food:S", isNamedStyle = true)
            assertEquals(2, model.placeholders.size)
        }

        // ── Control symbols ───────────────────────────────────────────────────

        @Test
        fun `percent-percent produces LITERAL_PERCENT control symbol`() {
            val model = parse("%%")
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.LITERAL_PERCENT, model.controlSymbols.first().type)
            assertTrue(model.placeholders.isEmpty())
        }

        @Test
        fun `indent control symbol`() {
            val model = parse("⇥")
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.INDENT, model.controlSymbols.first().type)
        }

        @Test
        fun `outdent control symbol`() {
            val model = parse("⇤")
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.OUTDENT, model.controlSymbols.first().type)
        }

        @Test
        fun `statement begin control symbol`() {
            val model = parse("«")
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.STATEMENT_BEGIN, model.controlSymbols.first().type)
        }

        @Test
        fun `statement end control symbol`() {
            val model = parse("»")
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.STATEMENT_END, model.controlSymbols.first().type)
        }

        @Test
        fun `space-or-newline control symbol`() {
            val model = parse("♢")
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.SPACE_OR_NEW_LINE, model.controlSymbols.first().type)
        }

        @Test
        fun `non-wrapping space control symbol`() {
            val model = parse("·")
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.SPACE, model.controlSymbols.first().type)
        }

        @Test
        fun `all control symbols in one string`() {
            val model = parse("%% ⇥ ⇤ « » ♢ ·")
            assertEquals(7, model.controlSymbols.size)
            assertTrue(model.placeholders.isEmpty())
        }

        @Test
        fun `control symbols and placeholders coexist`() {
            val model = parse("«%L»")
            assertEquals(1, model.placeholders.size)
            assertEquals(2, model.controlSymbols.size)
        }

        @Test
        fun `percent-percent followed by placeholder`() {
            val model = parse("%%%L") // %% → control, %L → placeholder
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.LITERAL_PERCENT, model.controlSymbols.first().type)
            assertEquals(1, model.placeholders.size)
            assertEquals(FormatKind.LITERAL, model.placeholders.first().kind)
        }
    }

    // ── Warning-level issues (non-blocking) ───────────────────────────────────
    // Currently nothing
    @Nested
    inner class WarningLevelIssues

    // ── Blocking errors  ────────────────────────────────────

    @Nested
    inner class BlockingErrors {

        @Test
        fun `dangling percent at end of string produces warning`() {
            val model = parse("hello %")
            assertEquals(0, model.placeholders.size)
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
            assertTrue(model.warnings.isEmpty(), "Dangling % must NOT be a warning")
        }

        @Test
        fun `dangling percent at start of string`() {
            val model = parse("% hello")
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
        }

        @Test
        fun `dangling percent in middle allows other placeholders to parse`() {
            val model = parse("%L % %S")
            assertEquals(2, model.placeholders.size, "Valid placeholders around dangling % still parsed")
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `percent followed by space is dangling`() {
            val model = parse("% value")
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `percent followed by digit but no type char is dangling`() {
            // "%1" alone — tryParsePositional fails because no format char follows the digit.
            val model = parse("%1")
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
        }

        @Test
        fun `unknown placeholder type produces warning and is not added`() {
            val model = parse("%Z")
            assertEquals(0, model.placeholders.size, "Unknown kind must not be added to placeholders")
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.UNKNOWN_PLACEHOLDER_TYPE, model.errors.first().kind)
            assertTrue(model.warnings.isEmpty(), "%Z must NOT be a warning")
        }

        @Test
        fun `unknown positional type is also non-blocking`() {
            val model = parse("%1Z")
            assertEquals(0, model.placeholders.size)
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.UNKNOWN_PLACEHOLDER_TYPE, model.errors.first().kind)
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `valid placeholder after unknown type is still recognised`() {
            val model = parse("%Z %L")
            assertEquals(1, model.placeholders.size)
            assertEquals(FormatKind.LITERAL, model.placeholders.first().kind)
            assertEquals(1, model.errors.size)
        }

        @Test
        fun `invalid positional index zero produces warning`() {
            val model = parse("%0L")
            assertEquals(0, model.placeholders.size)
            assertEquals(1, model.errors.size)
            val w = model.errors.first()
            // data is the format char for the quick fix
            assertEquals('L', w.data)
            assertTrue(model.warnings.isEmpty(), "%0L must NOT be a warning")
        }

        @Test
        fun `invalid positional index stores format char for quick fix`() {
            val model = parse("%0S")
            assertEquals(1, model.errors.size)
            assertEquals('S', model.errors.first().data)
        }

        @Test
        fun `valid placeholder after invalid index placeholder is parsed`() {
            val model = parse("%0L hello %1S")
            assertEquals(1, model.placeholders.size)
            assertEquals(FormatKind.STRING, model.placeholders.first().kind)
            assertEquals(1, model.errors.size)
        }

        @Test
        fun `warnings accumulate across multiple issues`() {
            val model = parse("% %Z %0L")
            assertEquals(3, model.errors.size)
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `warning target is TextSpanTarget`() {
            val model = parse("hello %")
            val target = model.errors.first().target
            assertTrue(target is ProblemTarget.TextSpanTarget, "Parser warnings use TextSpanTarget")
        }

        @Test
        fun `relative and positional mix is blocking`() {
            val model = parse("%L %1S")
            assertTrue(model.errors.isNotEmpty(), "Mixing relative and positional must be a blocking error")
            assertEquals(FormatStyle.Mixed, model.style)
        }

        @Test
        fun `relative and named mix is blocking`() {
            val model = parse("%L %food:S")
            assertTrue(model.errors.isNotEmpty())
        }

        @Test
        fun `positional and named mix is blocking`() {
            val model = parse("%1L %food:S")
            assertTrue(model.errors.isNotEmpty())
        }

        @Test
        fun `all three styles is blocking`() {
            val model = parse("%L %1S %food:T")
            assertTrue(model.errors.isNotEmpty())
        }

        @Test
        fun `forceNamed with relative style is blocking`() {
            val model = parse("%L hello", isNamedStyle = true)
            assertTrue(model.errors.isNotEmpty(), "addNamed with relative placeholders must be an error")
        }

        @Test
        fun `forceNamed with positional style is blocking`() {
            val model = parse("%1L", isNamedStyle = true)
            assertTrue(model.errors.isNotEmpty())
        }

        @Test
        fun `forceNamed with named style has no error`() {
            val model = parse("%food:L", isNamedStyle = true)
            assertTrue(model.errors.isEmpty())
            assertEquals(FormatStyle.Named, model.style)
        }

        @Test
        fun `forceNamed with no placeholders has no error`() {
            // addNamed("hello world", emptyMap()) is valid in KotlinPoet.
            val model = parse("hello world", isNamedStyle = true)
            assertTrue(model.errors.isEmpty(), "No placeholders with forceNamed is valid")
        }
    }

    // ── Style detection ────────────────────────────────────────────────────────

    @Nested
    inner class StyleDetection {

        @Test
        fun `no placeholders to None`() {
            assertEquals(FormatStyle.None, parse("hello").style)
        }

        @Test
        fun `only relative to Relative`() {
            assertEquals(FormatStyle.Relative, parse("%L %S").style)
        }

        @Test
        fun `only positional to Positional`() {
            assertEquals(FormatStyle.Positional, parse("%1L %2S").style)
        }

        @Test
        fun `only named to Named`() {
            assertEquals(FormatStyle.Named, parse("%food:L %count:S", isNamedStyle = true).style)
        }

        @Test
        fun `only control symbols to None`() {
            assertEquals(FormatStyle.None, parse("⇥ ⇤").style)
        }

        @Test
        fun `only invalid types to None (unknown kinds don't count)`() {
            val model = parse("%Z %Q")
            assertEquals(
                FormatStyle.None,
                model.style,
                "Unknown kinds must not influence style — they produce warnings only",
            )
        }

        @Test
        fun `mix of valid and invalid kinds uses only valid for style`() {
            val model = parse("%L %Z")
            // %L → relative, %Z → warning (not added). Style should be Relative, not Mixed.
            assertEquals(FormatStyle.Relative, model.style)
            assertEquals(1, model.errors.size) // one error for %Z
        }
    }

    // ── Span accuracy ──────────────────────────────────────────────────────────

    @Nested
    inner class SpanAccuracy {

        @Test
        fun `span of L in simple string starts at 0`() {
            val model = parse("%L")
            val span = model.placeholders.first().span
            assertTrue(span.isSingle())
            assertEquals(0, span.singleRangeOrNull()!!.first)
            assertEquals(1, span.singleRangeOrNull()!!.last) // inclusive: %=0, L=1
        }

        @Test
        fun `span of S offset by leading text`() {
            val model = parse("ab%S")
            val span = model.placeholders.first().span
            assertEquals(2, span.singleRangeOrNull()!!.first)
            assertEquals(3, span.singleRangeOrNull()!!.last)
        }

        @Test
        fun `span of positional 1L`() {
            val model = parse("%1L")
            val span = model.placeholders.first().span
            assertEquals(0, span.singleRangeOrNull()!!.first)
            assertEquals(2, span.singleRangeOrNull()!!.last) // %=0, 1=1, L=2
        }

        @Test
        fun `span of named food of L`() {
            // "%food:L" has 7 chars: %, f, o, o, d, :, L → 0..6
            val model = parse("%food:L")
            val span = model.placeholders.first().span
            assertEquals(0, span.singleRangeOrNull()!!.first)
            assertEquals(6, span.singleRangeOrNull()!!.last)
        }

        @Test
        fun `span uses absolute PSI offsets across segments`() {
            // Segment 1: "A%L" at PSI offsets 10..12
            // Segment 2: "B" at PSI offsets 20..20
            val multiSegment = FormatText(
                listOf(
                    FormatTextSegment("A%L", 10..12),
                    FormatTextSegment("B", 20..20),
                ),
            )
            val model = parser.parse(multiSegment)
            assertEquals(1, model.placeholders.size)
            val span = model.placeholders.first().span
            assertTrue(span.isSingle())
            // %L is at logical indices 1-2, mapping to PSI offsets 11..12
            assertEquals(11, span.singleRangeOrNull()!!.first)
            assertEquals(12, span.singleRangeOrNull()!!.last)
        }

        @Test
        fun `warning span for dangling percent is correct`() {
            val model = parse("hi %") // % is at index 3
            assertEquals(1, model.errors.size)
            val target = model.errors.first().target as ProblemTarget.TextSpanTarget
            val range = target.span.singleRangeOrNull()!!
            assertEquals(3, range.first)
            assertEquals(3, range.last) // single char span
        }

        @Test
        fun `control symbol span is correct`() {
            val model = parse("%%")
            val span = model.controlSymbols.first().span
            assertEquals(0, span.singleRangeOrNull()!!.first)
            assertEquals(1, span.singleRangeOrNull()!!.last)
        }
    }

    // ── Dynamic segment handling ───────────────────────────────────────────────

    @Nested
    inner class DynamicSegments {

        @Test
        fun `placeholder inside dynamic segment is not parsed`() {
            val t = FormatText(
                listOf(
                    FormatTextSegment("%L", 0..1, SegmentKind.DYNAMIC),
                ),
            )
            val model = parser.parse(t)
            assertTrue(model.placeholders.isEmpty(), "Dynamic segments must be skipped entirely")
        }

        @Test
        fun `placeholder in literal segment next to dynamic is parsed`() {
            val t = FormatText(
                listOf(
                    FormatTextSegment("%L", 0..1, SegmentKind.LITERAL),
                    FormatTextSegment("\$name", 2..6, SegmentKind.DYNAMIC),
                    FormatTextSegment("%S", 7..8, SegmentKind.LITERAL),
                ),
            )
            val model = parser.parse(t)
            assertEquals(2, model.placeholders.size)
        }

        @Test
        fun `only dynamic segment produces empty model`() {
            val t = FormatText(listOf(FormatTextSegment("\$x", 0..1, SegmentKind.DYNAMIC)))
            val model = parser.parse(t)
            assertTrue(model.placeholders.isEmpty())
            assertTrue(model.errors.isEmpty())
            assertTrue(model.warnings.isEmpty())
        }
    }

    // ── Style flag invariant (white box) ──────────────────────────────────────

    @Nested
    inner class StateFlagInvariant {

        @Test
        fun `unknown type does not set hasRelative`() {
            // %Z alone: unknown type → warning. Style should be None, not Relative.
            val model = parse("%Z")
            assertFalse(
                model.style == FormatStyle.Relative,
                "Unknown type char must not set hasRelative",
            )
            assertEquals(FormatStyle.None, model.style)
        }

        @Test
        fun `unknown positional type does not set hasPositional`() {
            val model = parse("%1Z")
            assertEquals(FormatStyle.None, model.style)
        }

        @Test
        fun `unknown named type does not set hasNamed`() {
            val model = parse("%food:Z", isNamedStyle = true)
            assertEquals(FormatStyle.None, model.style)
        }

        @Test
        fun `valid relative after unknown does not produce mix error`() {
            // %Z (unknown, doesn't set hasRelative) then %L (valid, sets hasRelative).
            // No mix because unknown never influenced style.
            val model = parse("%Z %L")
            assertTrue(model.errors.isNotEmpty(), "Unknown kind must not cause spurious mix error")
            assertTrue(model.warnings.isEmpty(), "Unknown kind must not cause spurious mix warnings")
            assertEquals(FormatStyle.Relative, model.style)
        }
    }

    // ── Safety & stress ────────────────────────────────────────────────────────

    @Nested
    inner class Safety {

        @Test
        fun `single percent char never crashes`() {
            val model = parse("%")
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
        }

        @Test
        fun `three percent chars produce one escape and one dangling`() {
            // "%%%" → LITERAL_PERCENT control (consumes 2) + dangling % (index 2)
            val model = parse("%%%")
            assertEquals(1, model.controlSymbols.size)
            assertEquals(SymbolType.LITERAL_PERCENT, model.controlSymbols.first().type)
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
        }

        @Test
        fun `four percent chars produce two escape controls`() {
            val model = parse("%%%%")
            assertEquals(2, model.controlSymbols.size)
            assertTrue(model.placeholders.isEmpty())
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `string of only percent signs never hangs`() {
            // 100 % chars: 50 pairs → 50 LITERAL_PERCENT controls
            val model = parse("%".repeat(100))
            assertEquals(50, model.controlSymbols.size)
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `odd number of percent signs has one dangling warning`() {
            val model = parse("%".repeat(101))
            assertEquals(1, model.errors.size)
            assertEquals(50, model.controlSymbols.size)
        }

        @Test
        fun `very long format string with many placeholders`() {
            val raw = (1..200).joinToString(" ") { "%L" }
            val model = parse(raw)
            assertEquals(200, model.placeholders.size)
            assertTrue(model.errors.isEmpty())
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `very long named format string`() {
            val raw = (1..50).joinToString(" ") { i -> "%arg$i:L" }
            val model = parse(raw, isNamedStyle = true)
            assertEquals(50, model.placeholders.size)
            assertTrue(model.errors.isEmpty())
        }

        @Test
        fun `percent followed by non-letter non-digit symbol`() {
            val model = parse("%!")
            // tryParseNamed: no ':', returns false
            // tryParsePositional: peek() = '!', not digit, returns false
            // tryParseRelative: peek() = '!', not letter, returns false
            // fallback: dangling percent warning, cursor advances past '%'
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
        }

        @Test
        fun `empty named name like percent-colon-L does not crash`() {
            // "%:L" → tryParseNamed: NAMED_ARGUMENT_PATTERN requires [\w_]+ (at least 1 char)
            // → regex won't match → tryParsePositional: peek() = ':', not digit → false
            // → tryParseRelative: peek() = ':', not letter → false
            // → fallback: dangling %
            val model = parse("%:L")
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
        }

        @Test
        fun `percent at very end of multi-char string`() {
            val raw = "a".repeat(1000) + "%"
            val model = parse(raw)
            assertEquals(1, model.errors.size)
            assertEquals(ParserIssueKind.DANGLING_PERCENT, model.errors.first().kind)
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `mixed control symbols and valid placeholders in long string`() {
            val raw = "⇥%L⇤%S«%T»"
            val model = parse(raw)
            assertEquals(3, model.placeholders.size)
            assertEquals(4, model.controlSymbols.size)
            assertTrue(model.errors.isEmpty())
            assertTrue(model.warnings.isEmpty())
        }

        @Test
        fun `parsing is idempotent for same input`() {
            val raw = "%L hello %1S"
            val m1 = parse(raw)
            val m2 = parse(raw)
            // Mixing relative and positional → blocking error. Both runs must agree.
            assertEquals(m1.errors.size, m2.errors.size)
            assertEquals(m1.style, m2.style)
        }

        @Test
        fun `parse never throws for arbitrary strings`() {
            val inputs = listOf(
                "", "%", "%%", "%%%", "%Z", "%0L", "%1", "% ", "%:L",
                "%food:", "%!@#", "«»«»", "·♢⇥⇤", "%L%1L%food:L",
                "\u0000", "\uFFFF", "%\uFFFF", "a".repeat(10_000),
            )
            for (input in inputs) {
                assertNotNull(parse(input)) // must not throw
            }
        }
    }

    // ── FormatProblem.data payloads ────────────────────────────────────────────

    @Nested
    inner class DataPayloads {

        @Test
        fun `DANGLING_PERCENT data is the enum constant`() {
            val w = parse("hi %").errors.first()
            assertEquals(null, w.data)
        }

        @Test
        fun `UNKNOWN_PLACEHOLDER_TYPE data is the enum constant`() {
            val w = parse("%Z").errors.first()
            assertEquals(null, w.data)
        }

        @Test
        fun `INVALID_POSITIONAL_INDEX data is the format Char`() {
            // For %0L, data is overridden to 'L' for the quick fix
            val w = parse("%0S").errors.first()
            assertEquals('S', w.data, "data should be the format char so FixPositionalIndexQuickFix can use it")
        }

        @Test
        fun `INVALID_POSITIONAL_INDEX data for type T is T`() {
            val w = parse("%0T").errors.first()
            assertEquals('T', w.data)
        }
    }
}
