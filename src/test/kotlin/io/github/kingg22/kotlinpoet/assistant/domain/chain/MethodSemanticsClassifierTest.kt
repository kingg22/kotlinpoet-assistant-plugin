package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParser
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParserImpl
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatTextSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Unit tests for [MethodSemanticsClassifier].
 *
 * Uses [StringFormatParserImpl] to create real [FormatStringModel] instances
 */
class MethodSemanticsClassifierTest {

    // ── DSL helpers ────────────────────────────────────────────────────────────

    private val parser: StringFormatParser = StringFormatParserImpl()

    /** Creates a FormatText backed by a single literal segment starting at offset 0. */
    private fun text(raw: String): FormatText = FormatText(
        listOf(FormatTextSegment(raw, 0 until raw.length)),
    )

    /** Parses a raw format string into a [FormatStringModel]. */
    private fun model(raw: String): FormatStringModel = parser.parse(text(raw))

    // ── classify() ────────────────────────────────────────────────────────────

    @Nested
    inner class Classify {
        @ParameterizedTest(name = "classify(\"{0}\") = FormatCall")
        @MethodSource(
            "io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemanticsClassifierTest#formatCallNames",
        )
        fun `format call methods`(name: String) {
            assertInstanceOf(
                MethodSemantics.FormatCall::class.java,
                MethodSemanticsClassifier.classify(name),
            )
        }

        @Test
        fun `addStatement is StatementCall`() {
            assertEquals(MethodSemantics.StatementCall, MethodSemanticsClassifier.classify("addStatement"))
        }

        @Test
        fun `beginControlFlow is ControlFlowBegin`() {
            assertEquals(MethodSemantics.ControlFlowBegin, MethodSemanticsClassifier.classify("beginControlFlow"))
        }

        @Test
        fun `nextControlFlow is ControlFlowNext`() {
            assertEquals(MethodSemantics.ControlFlowNext, MethodSemanticsClassifier.classify("nextControlFlow"))
        }

        @Test
        fun `endControlFlow is ControlFlowEnd`() {
            assertEquals(MethodSemantics.ControlFlowEnd, MethodSemanticsClassifier.classify("endControlFlow"))
        }

        @Test
        fun `indent is IndentCall`() {
            assertEquals(MethodSemantics.IndentCall, MethodSemanticsClassifier.classify("indent"))
        }

        @Test
        fun `unindent is UnindentCall`() {
            assertEquals(MethodSemantics.UnindentCall, MethodSemanticsClassifier.classify("unindent"))
        }

        @Test
        fun `addKdoc is KdocCall`() {
            assertEquals(MethodSemantics.KdocCall, MethodSemanticsClassifier.classify("addKdoc"))
        }

        @Test
        fun `build is TerminalCall`() {
            assertEquals(MethodSemantics.TerminalCall, MethodSemanticsClassifier.classify("build"))
        }

        @Test
        fun `unknown method name produces UnknownCall`() {
            val result = MethodSemanticsClassifier.classify("myCustomMethod")
            assertInstanceOf(MethodSemantics.UnknownCall::class.java, result)
            assertEquals("myCustomMethod", (result as MethodSemantics.UnknownCall).name)
        }

        @Test
        fun `empty string produces UnknownCall`() {
            val result = MethodSemanticsClassifier.classify("")
            assertInstanceOf(MethodSemantics.UnknownCall::class.java, result)
        }
    }

    // ── computeDelta() — wrapper methods ──────────────────────────────────────

    @Nested
    inner class ComputeDeltaWrapperMethods {

        @Test
        fun `StatementCall delta is ForStatement`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.StatementCall, null)
            assertEquals(EmissionStateDelta.ForStatement, delta)
        }

        @Test
        fun `StatementCall delta is ForStatement regardless of format`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.StatementCall, model("%L"))
            assertEquals(EmissionStateDelta.ForStatement, delta)
        }

        @Test
        fun `ControlFlowBegin delta is ForControlFlowBegin`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.ControlFlowBegin, null)
            assertEquals(EmissionStateDelta.ForControlFlowBegin, delta)
        }

        @Test
        fun `ControlFlowNext delta is ForControlFlowNext`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.ControlFlowNext, null)
            assertEquals(EmissionStateDelta.ForControlFlowNext, delta)
            assertEquals(
                listOf(StateTransition.DecrementIndent, StateTransition.IncrementIndent),
                delta.events,
            )
        }

        @Test
        fun `ControlFlowEnd delta is ForControlFlowEnd`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.ControlFlowEnd, null)
            assertEquals(EmissionStateDelta.ForControlFlowEnd, delta)
            assertEquals(listOf(StateTransition.DecrementIndent), delta.events)
        }

        @Test
        fun `IndentCall delta is Indent`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.IndentCall, null)
            assertEquals(EmissionStateDelta.Indent, delta)
            assertEquals(listOf(StateTransition.IncrementIndent), delta.events)
        }

        @Test
        fun `UnindentCall delta is Unindent`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.UnindentCall, null)
            assertEquals(EmissionStateDelta.Unindent, delta)
            assertEquals(listOf(StateTransition.DecrementIndent), delta.events)
        }

        @Test
        fun `TerminalCall delta is Zero`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.TerminalCall, null)
            assertEquals(EmissionStateDelta.Zero, delta)
            assertTrue(delta.events.isEmpty())
        }

        @Test
        fun `UnknownCall delta is Zero`() {
            val delta = MethodSemanticsClassifier.computeDelta(
                MethodSemantics.UnknownCall("whatever"),
                model("%L"),
            )
            assertEquals(EmissionStateDelta.Zero, delta)
        }
    }

    // ── computeDelta() — FormatCall derives delta from format ─────────────────

    @Nested
    inner class ComputeDeltaFromFormat {

        @Test
        fun `FormatCall with null format returns Zero`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, null)
            assertEquals(EmissionStateDelta.Zero, delta)
        }

        @Test
        fun `FormatCall with plain text returns Zero`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, model("hello %L"))
            assertEquals(EmissionStateDelta.Zero, delta)
        }

        @Test
        fun `FormatCall with explicit statement wrap produces Open+Close`() {
            // add("«%L»") — user wrote the «»  explicitly
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, model("«%L»"))
            assertEquals(
                listOf(StateTransition.OpenStatement, StateTransition.CloseStatement),
                delta.events,
            )
        }

        @Test
        fun `FormatCall with only open statement produces just OpenStatement`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, model("«%L"))
            assertEquals(listOf(StateTransition.OpenStatement), delta.events)
        }

        @Test
        fun `FormatCall with only close statement produces just CloseStatement`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, model("%L»"))
            assertEquals(listOf(StateTransition.CloseStatement), delta.events)
        }

        @Test
        fun `FormatCall with explicit indent symbols`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, model("⇥%L⇤"))
            assertEquals(
                listOf(StateTransition.IncrementIndent, StateTransition.DecrementIndent),
                delta.events,
            )
        }

        @Test
        fun `FormatCall with percent-percent (LITERAL_PERCENT) produces no transition`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, model("100%%"))
            assertEquals(EmissionStateDelta.Zero, delta)
        }

        @Test
        fun `FormatCall with space-or-newline symbol produces no transition`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, model("hello♢world"))
            assertEquals(EmissionStateDelta.Zero, delta)
        }

        @Test
        fun `FormatCall with non-wrapping space produces no transition`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.FormatCall, model("hello·world"))
            assertEquals(EmissionStateDelta.Zero, delta)
        }

        @Test
        fun `FormatCall with mixed control symbols preserves order`() {
            // ⇥ then « then » then ⇤ — unusual but should preserve order
            val delta = MethodSemanticsClassifier.computeDelta(
                MethodSemantics.FormatCall,
                model("⇥«%L»⇤"),
            )
            assertEquals(
                listOf(
                    StateTransition.IncrementIndent,
                    StateTransition.OpenStatement,
                    StateTransition.CloseStatement,
                    StateTransition.DecrementIndent,
                ),
                delta.events,
            )
        }

        @Test
        fun `KdocCall derives delta from format like FormatCall`() {
            val delta = MethodSemanticsClassifier.computeDelta(MethodSemantics.KdocCall, model("«%L»"))
            assertEquals(
                listOf(StateTransition.OpenStatement, StateTransition.CloseStatement),
                delta.events,
            )
        }
    }

    // ── Round-trip: classify + computeDelta for common patterns ───────────────

    @Nested
    inner class RoundTrip {

        @Test
        fun `add with implicit statement wrap has zero delta (no explicit symbols)`() {
            val semantics = MethodSemanticsClassifier.classify("add")
            val delta = MethodSemanticsClassifier.computeDelta(semantics, model("%L"))
            assertEquals(EmissionStateDelta.Zero, delta)
        }

        @Test
        fun `addStatement always produces ForStatement delta regardless of format`() {
            val semantics = MethodSemanticsClassifier.classify("addStatement")
            // Even if the format contains ⇥ — addStatement's delta is fixed
            val delta = MethodSemanticsClassifier.computeDelta(semantics, model("⇥%L⇤"))
            assertEquals(EmissionStateDelta.ForStatement, delta)
            assertEquals(
                listOf(StateTransition.OpenStatement, StateTransition.CloseStatement),
                delta.events,
            )
        }

        @Test
        fun `beginControlFlow delta is always ForControlFlowBegin`() {
            val semantics = MethodSemanticsClassifier.classify("beginControlFlow")
            val delta = MethodSemanticsClassifier.computeDelta(semantics, model("if (%L)"))
            assertEquals(EmissionStateDelta.ForControlFlowBegin, delta)
        }
    }

    companion object {
        @JvmStatic
        fun formatCallNames(): Stream<Arguments> = Stream.of(
            Arguments.of("add"),
            Arguments.of("addCode"),
            Arguments.of("addNamed"),
            Arguments.of("of"),
        )
    }
}
