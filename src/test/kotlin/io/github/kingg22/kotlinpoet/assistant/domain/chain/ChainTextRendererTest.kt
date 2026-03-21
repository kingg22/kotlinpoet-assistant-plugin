package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [renderChain].
 *
 * All tests are PSI-free and IntelliJ-free. The [MethodEmissionContribution]s are
 * constructed directly from [EmittedPart]s.
 */
class ChainTextRendererTest {

    // ── DSL helpers ────────────────────────────────────────────────────────────

    private fun dummySpan(): TextSpan = TextSpan.of(0..1)
    private fun dummyOrigin(method: String = "add"): EmissionOrigin = EmissionOrigin.ExplicitCall(method, dummySpan())

    private fun literal(text: String, method: String = "add"): EmittedPart.FormatLiteral =
        EmittedPart.FormatLiteral(text, dummyOrigin(method))

    private fun resolved(placeholder: PlaceholderSpec, text: String): EmittedPart.ResolvedPlaceholder =
        EmittedPart.ResolvedPlaceholder(placeholder, text, null, dummyOrigin())

    private fun unresolved(placeholder: PlaceholderSpec) =
        EmittedPart.UnresolvedPlaceholder(placeholder, UnresolvedReason.EXTERNAL_VARIABLE, dummyOrigin())

    private fun control(type: ControlSymbol.SymbolType, implicit: Boolean = false): EmittedPart.ControlSymbolPart =
        EmittedPart.ControlSymbolPart(type, implicit, dummyOrigin())

    private fun contribution(
        methodName: String = "add",
        semantics: MethodSemantics = MethodSemantics.FormatCall,
        vararg parts: EmittedPart,
    ): MethodEmissionContribution = MethodEmissionContribution(
        methodName = methodName,
        semantics = semantics,
        parts = parts.toList(),
        stateDelta = EmissionStateDelta(emptyList()),
        resolvability = ContributionResolvability.FullyResolved,
        callSpan = dummySpan(),
    )

    private fun relSpec(): PlaceholderSpec = PlaceholderSpec(
        PlaceholderSpec.FormatKind.LITERAL,
        PlaceholderSpec.PlaceholderBinding.Relative,
        dummySpan(),
    )

    // ── Basic rendering ────────────────────────────────────────────────────────

    @Nested
    inner class BasicRendering {

        @Test
        fun `empty contributions produces empty string`() {
            assertEquals("", renderChain(emptyList()))
        }

        @Test
        fun `single literal is rendered as-is`() {
            val c = contribution(parts = arrayOf(literal("hello world")))
            assertEquals("hello world", renderChain(listOf(c)))
        }

        @Test
        fun `resolved placeholder is rendered as its resolved text`() {
            val c = contribution(parts = arrayOf(resolved(relSpec(), "\"hello\"")))
            assertEquals("\"hello\"", renderChain(listOf(c)))
        }

        @Test
        fun `unresolved placeholder renders as original token`() {
            val c = contribution(parts = arrayOf(unresolved(relSpec())))
            assertEquals("[%L]", renderChain(listOf(c)))
        }

        @Test
        fun `mixed literal and placeholder`() {
            val c = contribution(
                parts = arrayOf(
                    literal("val x = "),
                    resolved(relSpec(), "\"hello\""),
                ),
            )
            assertEquals("val x = \"hello\"", renderChain(listOf(c)))
        }

        @Test
        fun `multiple contributions are concatenated`() {
            val c1 = contribution(parts = arrayOf(literal("foo")))
            val c2 = contribution(parts = arrayOf(literal("bar")))
            assertEquals("foobar", renderChain(listOf(c1, c2)))
        }
    }

    // ── Newline handling ───────────────────────────────────────────────────────

    @Nested
    inner class NewlineHandling {

        @Test
        fun `newline in literal causes line break`() {
            val c = contribution(parts = arrayOf(literal("line1\nline2")))
            val result = renderChain(listOf(c))
            assertEquals("line1\nline2", result)
        }

        @Test
        fun `trailing newline is trimmed from result`() {
            val c = contribution(parts = arrayOf(literal("hello\n")))
            assertEquals("hello", renderChain(listOf(c)))
        }

        @Test
        fun `multiple trailing newlines are trimmed`() {
            val c = contribution(parts = arrayOf(literal("hello\n\n\n")))
            assertEquals("hello", renderChain(listOf(c)))
        }
    }

    // ── Control symbols ────────────────────────────────────────────────────────

    @Nested
    inner class ControlSymbols {

        @Test
        fun `statement begin is invisible`() {
            val c = contribution(
                parts = arrayOf(
                    control(ControlSymbol.SymbolType.STATEMENT_BEGIN, implicit = true),
                    literal("code"),
                    control(ControlSymbol.SymbolType.STATEMENT_END, implicit = true),
                ),
            )
            assertEquals("code", renderChain(listOf(c)))
        }

        @Test
        fun `statement end is invisible`() {
            val c = contribution(
                parts = arrayOf(
                    literal("foo"),
                    control(ControlSymbol.SymbolType.STATEMENT_END, implicit = true),
                ),
            )
            assertEquals("foo", renderChain(listOf(c)))
        }

        @Test
        fun `literal percent renders as percent sign`() {
            val c = contribution(
                parts = arrayOf(
                    literal("100"),
                    control(ControlSymbol.SymbolType.LITERAL_PERCENT),
                    literal(" done"),
                ),
            )
            assertEquals("100% done", renderChain(listOf(c)))
        }

        @Test
        fun `non-wrapping space renders as space`() {
            val c = contribution(
                parts = arrayOf(
                    literal("a"),
                    control(ControlSymbol.SymbolType.SPACE),
                    literal("b"),
                ),
            )
            assertEquals("a b", renderChain(listOf(c)))
        }

        @Test
        fun `space-or-newline renders as space`() {
            val c = contribution(
                parts = arrayOf(
                    literal("a"),
                    control(ControlSymbol.SymbolType.SPACE_OR_NEW_LINE),
                    literal("b"),
                ),
            )
            assertEquals("a b", renderChain(listOf(c)))
        }
    }

    // ── Indentation ────────────────────────────────────────────────────────────

    @Nested
    inner class Indentation {

        @Test
        fun `indent increases indentation for subsequent lines`() {
            val c1 = contribution(
                "beginControlFlow",
                parts = arrayOf(
                    literal("if (x) {\n"),
                    control(ControlSymbol.SymbolType.INDENT, implicit = true),
                ),
            )
            val c2 = contribution(
                "add",
                parts = arrayOf(literal("doSomething()\n")),
            )
            val result = renderChain(listOf(c1, c2))
            // "if (x) {\n" then "  doSomething()\n" (2-space indent)
            assertEquals("if (x) {\n  doSomething()", result)
        }

        @Test
        fun `outdent decreases indentation`() {
            val c1 = contribution(
                parts = arrayOf(
                    literal("if (x) {\n"),
                    control(ControlSymbol.SymbolType.INDENT, implicit = true),
                ),
            )
            val c2 = contribution(
                parts = arrayOf(
                    literal("body()\n"),
                ),
            )
            val c3 = contribution(
                parts = arrayOf(
                    control(ControlSymbol.SymbolType.OUTDENT, implicit = true),
                    literal("}\n"),
                ),
            )
            val result = renderChain(listOf(c1, c2, c3))
            assertEquals("if (x) {\n  body()\n}", result)
        }

        @Test
        fun `indent does not go below 0`() {
            val c = contribution(
                parts = arrayOf(
                    control(ControlSymbol.SymbolType.OUTDENT), // would go to -1
                    literal("text"),
                ),
            )
            // Should not throw, just clamp to 0
            val result = renderChain(listOf(c))
            assertEquals("text", result)
        }

        @Test
        fun `nested beginControlFlow and endControlFlow`() {
            val begin = contribution(
                "beginControlFlow",
                parts = arrayOf(
                    literal("if (a) {\n"),
                    control(ControlSymbol.SymbolType.INDENT, implicit = true),
                ),
            )
            val nested = contribution(
                "beginControlFlow",
                parts = arrayOf(
                    literal("if (b) {\n"),
                    control(ControlSymbol.SymbolType.INDENT, implicit = true),
                ),
            )
            val body = contribution("add", parts = arrayOf(literal("body()\n")))
            val endNested = contribution(
                "endControlFlow",
                parts = arrayOf(
                    control(ControlSymbol.SymbolType.OUTDENT, implicit = true),
                    literal("}\n"),
                ),
            )
            val end = contribution(
                "endControlFlow",
                parts = arrayOf(
                    control(ControlSymbol.SymbolType.OUTDENT, implicit = true),
                    literal("}\n"),
                ),
            )

            val result = renderChain(listOf(begin, nested, body, endNested, end))
            val expected = "if (a) {\n  if (b) {\n    body()\n  }\n}"
            assertEquals(expected, result)
        }
    }

    // ── addStatement simulation ────────────────────────────────────────────────

    @Nested
    inner class AddStatementSimulation {

        @Test
        fun `addStatement wrapping is invisible - only content shows`() {
            // addStatement("val x = %L", 42) produces:
            // [«][val x = ][42][\n][»]
            val c = contribution(
                "addStatement",
                MethodSemantics.StatementCall,
                control(ControlSymbol.SymbolType.STATEMENT_BEGIN, implicit = true),
                literal("val x = "),
                resolved(relSpec(), "42"),
                literal("\n"),
                control(ControlSymbol.SymbolType.STATEMENT_END, implicit = true),
            )
            assertEquals("val x = 42", renderChain(listOf(c)))
        }

        @Test
        fun `multiple addStatements on separate lines`() {
            fun stmt(content: String) = contribution(
                "addStatement",
                MethodSemantics.StatementCall,
                control(ControlSymbol.SymbolType.STATEMENT_BEGIN, implicit = true),
                literal(content),
                literal("\n"),
                control(ControlSymbol.SymbolType.STATEMENT_END, implicit = true),
            )
            val result = renderChain(listOf(stmt("val a = 1"), stmt("val b = 2")))
            assertEquals("val a = 1\nval b = 2", result)
        }
    }

    // ── Full chain simulation ──────────────────────────────────────────────────

    @Nested
    inner class FullChainSimulation {

        @Test
        fun `if block with one statement inside`() {
            val beginIf = contribution(
                "beginControlFlow",
                MethodSemantics.ControlFlowBegin,
                literal("if (condition)"),
                literal(" {\n"),
                control(ControlSymbol.SymbolType.INDENT, implicit = true),
            )
            val stmt = contribution(
                "addStatement",
                MethodSemantics.StatementCall,
                control(ControlSymbol.SymbolType.STATEMENT_BEGIN, implicit = true),
                literal("println(\"hello\")"),
                literal("\n"),
                control(ControlSymbol.SymbolType.STATEMENT_END, implicit = true),
            )
            val endIf = contribution(
                "endControlFlow",
                MethodSemantics.ControlFlowEnd,
                control(ControlSymbol.SymbolType.OUTDENT, implicit = true),
                literal("}\n"),
            )

            val result = renderChain(listOf(beginIf, stmt, endIf))
            val expected = "if (condition) {\n  println(\"hello\")\n}"
            assertEquals(expected, result)
        }

        @Test
        fun `return statement is rendered correctly`() {
            val c = contribution(
                "addStatement",
                MethodSemantics.StatementCall,
                control(ControlSymbol.SymbolType.STATEMENT_BEGIN, implicit = true),
                literal("return "),
                resolved(relSpec(), "result"),
                literal("\n"),
                control(ControlSymbol.SymbolType.STATEMENT_END, implicit = true),
            )
            assertEquals("return result", renderChain(listOf(c)))
        }
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    @Nested
    inner class EdgeCases {

        @Test
        fun `contribution with empty parts produces empty string`() {
            val c = contribution(parts = emptyArray())
            assertEquals("", renderChain(listOf(c)))
        }

        @Test
        fun `only whitespace contributions produce empty string after trim`() {
            val c = contribution(parts = arrayOf(literal("\n\n")))
            assertEquals("", renderChain(listOf(c)))
        }

        @Test
        fun `unresolved in middle of line`() {
            val c = contribution(
                parts = arrayOf(
                    literal("val x: "),
                    unresolved(
                        PlaceholderSpec(
                            PlaceholderSpec.FormatKind.TYPE,
                            PlaceholderSpec.PlaceholderBinding.Relative,
                            dummySpan(),
                        ),
                    ),
                    literal(" = null"),
                ),
            )
            assertEquals("val x: [%T] = null", renderChain(listOf(c)))
        }
    }
}
