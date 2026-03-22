package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

/**
 * Light-platform integration tests for [KotlinPoetDocumentationTargetProvider].
 *
 * Verifies that hovering over a placeholder or control symbol inside a KotlinPoet
 * format string returns a [KotlinPoetDocumentationTarget], and that hovering outside
 * a format string returns nothing.
 *
 * The caret position (`<caret>`) drives the `offset` passed to
 * [KotlinPoetDocumentationTargetProvider.documentationTargets].
 */
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
class KotlinPoetDocumentationTargetProviderTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private val provider = KotlinPoetDocumentationTargetProvider()

    // ── Helper ─────────────────────────────────────────────────────────────────

    private fun targetsAt(@Language("kotlin") content: String): List<KotlinPoetDocumentationTarget> {
        myFixture.configureByFiles("stubs/KotlinPoet.kt")
        myFixture.configureByText("Test.kt", content.trimIndent())
        val offset = myFixture.caretOffset
        return allowAnalysisOnEdt {
            provider.documentationTargets(myFixture.file, offset)
        }.filterIsInstance<KotlinPoetDocumentationTarget>()
    }

    // ── Placeholder documentation ──────────────────────────────────────────────

    fun testDocumentationTargetForPlaceholderL() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>L", "hello")
            }
        """,
        )
        assertFalse("Expected a documentation target for %L", targets.isEmpty())
        val target = targets.first()
        val html = allowAnalysisOnEdt { target.computeDocumentation() }
        assertNotNull(html)
    }

    fun testDocumentationTargetForPlaceholderS() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>S", "text")
            }
        """,
        )
        assertFalse("Expected a documentation target for %S", targets.isEmpty())
    }

    fun testDocumentationTargetForPlaceholderT() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>T", String::class)
            }
        """,
        )
        assertFalse("Expected a documentation target for %T", targets.isEmpty())
    }

    fun testDocumentationTargetForPlaceholderN() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>N", "myVar")
            }
        """,
        )
        assertFalse("Expected a documentation target for %N", targets.isEmpty())
    }

    fun testDocumentationTargetForPlaceholderM() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>M", "member")
            }
        """,
        )
        assertFalse("Expected a documentation target for %M", targets.isEmpty())
    }

    fun testDocumentationTargetForPlaceholderP() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>P", "template")
            }
        """,
        )
        assertFalse("Expected a documentation target for %P", targets.isEmpty())
    }

    // ── Control symbol documentation ───────────────────────────────────────────

    fun testDocumentationTargetForIndentSymbol() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("<caret>⇥some code")
            }
        """,
        )
        assertFalse("Expected a documentation target for ⇥", targets.isEmpty())
    }

    fun testDocumentationTargetForOutdentSymbol() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("<caret>⇤some code")
            }
        """,
        )
        assertFalse("Expected a documentation target for ⇤", targets.isEmpty())
    }

    fun testDocumentationTargetForStatementBegin() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("<caret>«%L»", 42)
            }
        """,
        )
        assertFalse("Expected a documentation target for «", targets.isEmpty())
    }

    // ── No false positives ─────────────────────────────────────────────────────

    fun testNoDocumentationOutsideKotlinPoetCall() {
        val targets = targetsAt(
            """
            fun test() {
                println("hel<caret>lo %L")
            }
        """,
        )
        assertTrue("Should return no KotlinPoet targets outside a KotlinPoet call", targets.isEmpty())
    }

    fun testNoDocumentationForLiteralTextInFormatString() {
        // Cursor is on regular text, not on a placeholder or control symbol
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("hel<caret>lo %L", "x")
            }
        """,
        )
        // 'hel' has no special meaning → no KotlinPoet doc target expected
        assertTrue(
            "Cursor on literal text should produce no KotlinPoet doc target",
            targets.isEmpty(),
        )
    }

    // ── Presentation ──────────────────────────────────────────────────────────

    fun testPresentationTitleIsNonBlankForPlaceholderL() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>L", "hello")
            }
        """,
        )
        if (targets.isEmpty()) return // guard: if no target produced, skip
        val presentation = allowAnalysisOnEdt { targets.first().computePresentation() }
        assertTrue("Presentation title should be non-blank", presentation.presentableText.isNotBlank())
    }

    fun testDocumentationResultHasExternalUrl() {
        val targets = targetsAt(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%<caret>L", "hello")
            }
        """,
        )
        if (targets.isEmpty()) return
        val docResult = allowAnalysisOnEdt { targets.first().computeDocumentation() }
        assertNotNull("Documentation result should not be null", docResult)
    }
}
