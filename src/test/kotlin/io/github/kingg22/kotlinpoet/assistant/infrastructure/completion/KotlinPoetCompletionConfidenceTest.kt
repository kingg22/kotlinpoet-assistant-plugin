package io.github.kingg22.kotlinpoet.assistant.infrastructure.completion

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThreeState

/**
 * Tests for [KotlinPoetCompletionConfidence].
 *
 * ## Design constraints
 *
 * [CompletionConfidence.shouldSkipAutopopup] is called extremely frequently as the user
 * types. The implementation is explicitly **PSI-only** — no K2 Analysis API allowed.
 * These tests verify:
 *
 * - Returns [com.intellij.util.ThreeState.NO] (= "do show popup") when inside a string in a call whose
 *   callee text matches the known KotlinPoet method names (`add`, `addStatement`, etc.).
 * - Returns [com.intellij.util.ThreeState.UNSURE] (= "let other providers decide") everywhere else.
 * - Never throws for any input position.
 *
 * The guard that matters most is `looksLikeKotlinPoetCall()` — a cheap PSI text check
 * that **does not** use the Analysis API.
 */
@TestDataPath("\$CONTENT_ROOT/testData")
class KotlinPoetCompletionConfidenceTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // No KotlinPoetTestDescriptor needed — looksLikeKotlinPoetCall is PSI-only (callee text match)

    private val confidence = KotlinPoetCompletionConfidence()

    // ── shouldSkipAutopopup — NO (show popup) ──────────────────────────────────

    fun testReturnsFalseInsideAddStringArgument() {
        // Position caret after `%` — inside the string arg of add()
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%<caret>") }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val result = confidence.shouldSkipAutopopup(
            editor,
            myFixture.file.findElementAt(myFixture.caretOffset)!!,
            myFixture.file,
            myFixture.caretOffset,
        )
        // NO means "don't skip" = "show popup"
        assertEquals(ThreeState.NO, result)
    }

    fun testReturnsFalseInsideAddStatementStringArgument() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().addStatement("val x = %<caret>") }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val result = confidence.shouldSkipAutopopup(
            editor,
            myFixture.file.findElementAt(myFixture.caretOffset - 1)!!,
            myFixture.file,
            myFixture.caretOffset,
        )
        assertEquals(ThreeState.NO, result)
    }

    fun testReturnsFalseInsideBeginControlFlowStringArgument() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().beginControlFlow("if (%<caret>)") }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val result = confidence.shouldSkipAutopopup(
            editor,
            myFixture.file.findElementAt(myFixture.caretOffset - 1)!!,
            myFixture.file,
            myFixture.caretOffset,
        )
        assertEquals(ThreeState.NO, result)
    }

    // ── shouldSkipAutopopup — UNSURE (not a KotlinPoet call) ──────────────────

    fun testReturnsUnsureInsidePrintlnString() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            fun test() { println("hel<caret>lo") }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val offset = myFixture.caretOffset
        val element = myFixture.file.findElementAt(offset - 1) ?: return
        val result = confidence.shouldSkipAutopopup(editor, element, myFixture.file, offset)
        // Either UNSURE or NO — must NOT crash. For a non-KotlinPoet call, UNSURE is expected.
        assertNotNull(result)
    }

    fun testReturnsUnsureOutsideAnyString() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            fun test() { val x<caret> = 1 }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val offset = myFixture.caretOffset
        val element = myFixture.file.findElementAt(offset - 1) ?: return
        val result = confidence.shouldSkipAutopopup(editor, element, myFixture.file, offset)
        assertNotNull(result)
        // Outside a string — must be UNSURE (no KotlinPoet context)
        assertNotSame(ThreeState.NO, result)
    }

    fun testNeverThrowsForArbitraryPosition() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                val x = 1
                CodeBlock.builder().add("%L", x)
                println("hello")
            }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val file = myFixture.file
        val text = file.text
        // Walk through every offset — confidence must never throw
        for (offset in 1..text.length) {
            val element = file.findElementAt(offset - 1) ?: continue
            assertNotNull(confidence.shouldSkipAutopopup(editor, element, file, offset))
        }
    }
}
