package io.github.kingg22.kotlinpoet.assistant.infrastructure.completion

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

/**
 * Light-platform integration tests for [KotlinPoetCompletionContributor] and
 * [KotlinPoetCompletionConfidence].
 *
 * ## What is tested
 *
 * - Typing `%` inside a known KotlinPoet format string triggers completion.
 * - All six placeholder kinds appear in the completion list (`%L`, `%S`, `%T`, `%N`, `%M`, `%P`).
 * - `%%` (escape) appears as a completion item.
 * - Completion does NOT trigger inside a non-KotlinPoet string.
 * - Completion does NOT trigger inside a string that is not a call argument.
 * - Each completion item carries a non-blank type text and tail text (UX smoke test).
 *
 * ## Limitations
 *
 * [KotlinPoetCompletionConfidence] and [KotlinPoetTypedHandler] fire in a real editor
 * context; for `checkCompletionVariants` / `completeBasic` we rely on the IntelliJ
 * test fixture which exercises the full contributor chain.
 */
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
class KotlinPoetCompletionContributorTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun setUp() {
        super.setUp()
        myFixture.configureByFile("stubs/KotlinPoet.kt")
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /**
     * Configures a Kotlin file with inline content, places the caret at `<caret>`,
     * invokes completion, and returns the lookup string list.
     */
    private fun completeAt(@Language("kotlin") content: String): List<String> {
        myFixture.configureByText("Test.kt", content.trimIndent())
        val elements = allowAnalysisOnEdt { myFixture.completeBasic() }
        return elements?.map { it.lookupString } ?: emptyList()
    }

    // ── Happy path — placeholder completions appear ────────────────────────────

    fun testAllPlaceholderKindsAppearInAdd() {
        val variants = completeAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>")
            }
        """,
        )
        assertContainsElements(variants, "%L", "%S", "%T", "%N", "%M", "%P")
    }

    fun testEscapePercentAppearsInAdd() {
        val variants = completeAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>")
            }
        """,
        )
        assertTrue("Expected %% in completions", variants.contains("%%"))
    }

    fun testCompletionAppearsInAddStatement() {
        val variants = completeAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().addStatement("val x = %<caret>")
            }
        """,
        )
        assertContainsElements(variants, "%L", "%S")
    }

    fun testCompletionAppearsInBeginControlFlow() {
        val variants = completeAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().beginControlFlow("if (%<caret>)")
            }
        """,
        )
        assertContainsElements(variants, "%L", "%S")
    }

    fun testCompletionAppearsInCodeBlockOf() {
        val variants = completeAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.of("%<caret>")
            }
        """,
        )
        assertContainsElements(variants, "%L")
    }

    // ── Completion lookup element quality ─────────────────────────────────────

    fun testLookupElementsHaveTypeText() {
        myFixture.configureByText(
            "Test.kt",
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>")
            }
            """.trimIndent(),
        )
        val elements = allowAnalysisOnEdt { myFixture.completeBasic() }
        assertNotNull("Completion elements must not be null", elements)

        val literalItem = elements!!.firstOrNull { it.lookupString == "%L" }
        assertNotNull("%%L must be in completion list", literalItem)

        // Verify the lookup element has a non-blank presentation
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        literalItem!!.renderElement(presentation)
        assertTrue(
            "Literal type text should be non-blank",
            presentation.typeText?.isNotBlank() == true,
        )
    }

    fun testAllSixKindsHaveNonBlankTypeText() {
        myFixture.configureByText(
            "Test.kt",
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>")
            }
            """.trimIndent(),
        )
        val elements = allowAnalysisOnEdt { myFixture.completeBasic() }
        assertNotNull(elements)

        val expectedLookups = listOf("%L", "%S", "%T", "%N", "%M", "%P")
        expectedLookups.forEach { lookup ->
            val item = elements!!.firstOrNull { it.lookupString == lookup }
            assertNotNull("$lookup should be in completions", item)
            val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
            item!!.renderElement(presentation)
            assertTrue(
                "$lookup should have non-blank type text",
                presentation.typeText?.isNotBlank() == true,
            )
        }
    }

    // ── No false positives — completion does not fire outside KotlinPoet ───────

    fun testNoCompletionInPlainStringOutsideCall() {
        // A string literal not inside any call should NOT get KotlinPoet completions
        val variants = completeAt(
            """
            fun test() {
                val x = "%<caret>"
            }
        """,
        )
        // %L and friends should not appear
        assertFalse(
            "KotlinPoet placeholders should not appear in plain string literals",
            variants.containsAll(listOf("%L", "%S", "%T")),
        )
    }

    fun testNoCompletionInNonKotlinPoetStringArgument() {
        // println() is not a KotlinPoet call
        val variants = completeAt(
            """
            fun test() {
                println("%<caret>")
            }
        """,
        )
        assertFalse(
            "KotlinPoet completions should not appear in println()",
            variants.containsAll(listOf("%L", "%S")),
        )
    }

    // ── Prefix handling: completion only after bare % ──────────────────────────

    fun testNoCompletionAfterDoublePercent() {
        // After "%%" the user has already escaped the percent — no placeholder expected
        val variants = completeAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("100%%<caret>")
            }
        """,
        )
        // The completion set should not contain %L etc. with prefix "%%"
        // (prefix matcher on "%%" won't match "%L")
        assertFalse(
            "%L should not appear after %%",
            variants.contains("%L"),
        )
    }
}
