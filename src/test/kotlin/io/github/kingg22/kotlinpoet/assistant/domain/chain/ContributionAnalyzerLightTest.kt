package io.github.kingg22.kotlinpoet.assistant.domain.chain

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.KotlinPoetTestDescriptor
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.ContributionAnalyzer
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.jupiter.api.Assertions.assertInstanceOf

/**
 * Integration tests for [io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.ContributionAnalyzer] using the IntelliJ light platform.
 *
 * These tests verify:
 * - Scalar argument resolution (string, int, class literal)
 * - Named map argument resolution via `addNamed`
 * - `NestedCodeBlockPart` construction when `%L` receives a CodeBlock expression
 * - Variable reference following for nested CodeBlocks
 */
@KaAllowAnalysisOnEdt
class ContributionAnalyzerLightTest : BasePlatformTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinPoetTestDescriptor.projectDescriptor

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun configureKotlin(@Language("kotlin") content: String): KtFile {
        myFixture.configureByText("Test.kt", content.trimIndent())
        return myFixture.file as KtFile
    }

    private fun KtFile.callsNamed(name: String): List<KtCallExpression> = collectDescendantsOfType<KtCallExpression>()
        .filter { it.calleeExpression?.text == name }

    private fun analyzeFirst(file: KtFile, methodName: String): MethodEmissionContribution? {
        val call = file.callsNamed(methodName).firstOrNull() ?: return null
        return allowAnalysisOnEdt { ContributionAnalyzer.analyze(call) }
    }

    // ── Structural calls ───────────────────────────────────────────────────────

    fun testIndentCallProducesControlSymbolPart() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().indent().build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "indent")
        assertNotNull(contribution)
        assertEquals(MethodSemantics.IndentCall, contribution!!.semantics)
        assertTrue(contribution.parts.any { it is EmittedPart.ControlSymbolPart })
    }

    fun testEndControlFlowProducesOutdentAndBrace() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().beginControlFlow("if (x)").endControlFlow().build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "endControlFlow")
        assertNotNull(contribution)
        val parts = contribution!!.parts
        assertTrue(parts.any { it is EmittedPart.ControlSymbolPart })
        assertTrue(
            parts.any {
                it is EmittedPart.FormatLiteral && it.text.contains('}')
            },
        )
    }

    // ── Scalar argument resolution ─────────────────────────────────────────────

    fun testAddWithStringLiteralResolves() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("val name = %S", "hello").build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "add")
        assertNotNull(contribution)
        val resolved = contribution!!.parts.filterIsInstance<EmittedPart.ResolvedPlaceholder>()
        assertEquals(1, resolved.size)
        assertEquals("\"hello\"", resolved[0].resolvedText)
    }

    fun testAddWithIntLiteralResolves() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("val x = %L", 42).build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "add")
        assertNotNull(contribution)
        val resolved = contribution!!.parts.filterIsInstance<EmittedPart.ResolvedPlaceholder>()
        assertEquals(1, resolved.size)
        assertEquals("42", resolved[0].resolvedText)
    }

    fun testAddWithClassLiteralResolves() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%T()", String::class).build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "add")
        assertNotNull(contribution)
        val resolved = contribution!!.parts.filterIsInstance<EmittedPart.ResolvedPlaceholder>()
        assertEquals(1, resolved.size)
        assertEquals("String", resolved[0].resolvedText)
    }

    fun testAddStatementWrapsWithImplicitStatementMarkers() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().addStatement("return %S", "result").build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "addStatement")
        assertNotNull(contribution)
        assertEquals(MethodSemantics.StatementCall, contribution!!.semantics)

        val controlParts = contribution.parts.filterIsInstance<EmittedPart.ControlSymbolPart>()
        assertTrue(controlParts.any { it.implicit && it.symbolType == SymbolType.STATEMENT_BEGIN })
        assertTrue(controlParts.any { it.implicit && it.symbolType == SymbolType.STATEMENT_END })
    }

    // ── Named map resolution ───────────────────────────────────────────────────

    fun testAddNamedResolvesStringMapValue() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder()
                    .addNamed("%food:L yummy", mapOf("food" to "tacos"))
                    .build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "addNamed")
        assertNotNull("addNamed contribution should not be null", contribution)
        val resolved = contribution!!.parts.filterIsInstance<EmittedPart.ResolvedPlaceholder>()
        assertEquals(1, resolved.size)
        // %L → raw value, no quotes
        assertEquals("tacos", resolved[0].resolvedText)
    }

    fun testAddNamedResolvesIntMapValue() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder()
                    .addNamed("%count:L items", mapOf("count" to 3))
                    .build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "addNamed")
        assertNotNull(contribution)
        val resolved = contribution!!.parts.filterIsInstance<EmittedPart.ResolvedPlaceholder>()
        assertEquals(1, resolved.size)
        assertEquals("3", resolved[0].resolvedText)
    }

    // ── Variable reference following ───────────────────────────────────────────

    fun testAddWithConstValResolves() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                val greeting = "Hello"
                CodeBlock.builder().add("%S", greeting).build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "add")
        assertNotNull(contribution)
        val resolved = contribution!!.parts.filterIsInstance<EmittedPart.ResolvedPlaceholder>()
        // "Hello" → StringLiteral → displayText = "\"Hello\""
        assertTrue(resolved.isNotEmpty())
        assertEquals("\"Hello\"", resolved[0].resolvedText)
    }

    // ── Nested CodeBlock ───────────────────────────────────────────────────────

    fun testAddWithInlineCodeBlockOfProducesNestedPart() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder()
                    .add("%L", CodeBlock.of("inner %S", "value"))
                    .build()
            }
        """,
        )
        // The outer .add() call
        val call = file.callsNamed("add")
            .firstOrNull { it.calleeExpression?.text == "add" } ?: return

        val contribution = allowAnalysisOnEdt { ContributionAnalyzer.analyze(call) }
        assertNotNull(contribution)
        val nested = contribution!!.parts.filterIsInstance<EmittedPart.NestedCodeBlockPart>()
        assertEquals("Expected one NestedCodeBlockPart", 1, nested.size)
        assertTrue(
            "Nested contributions should not be empty",
            nested[0].nestedContributions.isNotEmpty(),
        )
    }

    fun testAddWithInlineBuilderChainProducesNestedPart() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder()
                    .add("%L", CodeBlock.builder().add("nested").build())
                    .build()
            }
        """,
        )
        val outerAdd = file.callsNamed("add")
            .firstOrNull { call ->
                call.valueArguments.firstOrNull()?.getArgumentExpression()?.text?.contains("%L") == true
            } ?: return

        val contribution = allowAnalysisOnEdt { ContributionAnalyzer.analyze(outerAdd) }
        assertNotNull(contribution)
        val nested = contribution!!.parts.filterIsInstance<EmittedPart.NestedCodeBlockPart>()
        assertEquals(1, nested.size)
    }

    fun testAddWithVariableCodeBlockProducesNestedPart() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                val inner = CodeBlock.of("val x = %L", 1)
                CodeBlock.builder()
                    .add("%L", inner)
                    .build()
            }
        """,
        )
        val outerAdd = file.callsNamed("add")
            .firstOrNull { call ->
                call.valueArguments.firstOrNull()?.getArgumentExpression()?.text?.contains("%L") == true
            } ?: return

        val contribution = allowAnalysisOnEdt { ContributionAnalyzer.analyze(outerAdd) }
        assertNotNull(contribution)
        val nested = contribution!!.parts.filterIsInstance<EmittedPart.NestedCodeBlockPart>()
        assertEquals(
            "Variable reference to CodeBlock should produce NestedCodeBlockPart",
            1,
            nested.size,
        )
    }

    // ── controlFlowSuffix (via beginControlFlow) ───────────────────────────────

    fun testBeginControlFlowWithoutBraceAppendsBrace() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder()
                    .beginControlFlow("if (%L)", true)
                    .endControlFlow()
                    .build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "beginControlFlow")
        assertNotNull(contribution)
        val literals = contribution!!.parts.filterIsInstance<EmittedPart.FormatLiteral>()
        // The implicit suffix should contain "{"
        assertTrue(literals.any { it.text.contains('{') })
    }

    fun testBeginControlFlowWithBraceOnlyAppendsNewline() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder()
                    .beginControlFlow("list.forEach { element ->")
                    .endControlFlow()
                    .build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "beginControlFlow")
        assertNotNull(contribution)
        val literals = contribution!!.parts.filterIsInstance<EmittedPart.FormatLiteral>()
        // The implicit suffix should be "\n" only, not " {\n"
        val suffix = literals.lastOrNull { it.origin is EmissionOrigin.ImplicitFromMethod }
        assertNotNull(suffix)
        assertEquals("\n", suffix!!.text)
    }

    // ── Resolvability ──────────────────────────────────────────────────────────

    fun testFullyResolvedWhenAllArgsConstant() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("val x = %S", "hello").build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "add")
        assertNotNull(contribution)
        assertInstanceOf(ContributionResolvability.FullyResolved::class.java, contribution!!.resolvability)
    }

    fun testPartiallyResolvedWhenOneArgUnresolvable() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test(name: String) {
                CodeBlock.builder().add("%S %S", "known", name).build()
            }
        """,
        )
        val contribution = analyzeFirst(file, "add")
        assertNotNull(contribution)
        // "known" resolves, `name` (function parameter) does not
        assertInstanceOf(ContributionResolvability.PartiallyResolved::class.java, contribution!!.resolvability)
    }
}
