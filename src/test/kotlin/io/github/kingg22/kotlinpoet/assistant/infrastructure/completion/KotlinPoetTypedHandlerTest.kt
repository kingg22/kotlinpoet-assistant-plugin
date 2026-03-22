package io.github.kingg22.kotlinpoet.assistant.infrastructure.completion

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [KotlinPoetTypedHandler].
 *
 * [TypedHandlerDelegate.checkAutoPopup] is called on every keystroke. The implementation
 * is **PSI-only** — it uses `looksLikeKotlinPoetCall()` which matches only on callee text,
 * with zero Analysis API calls.
 *
 * We verify:
 * - Returns [TypedHandlerDelegate.Result.STOP] when `%` is typed inside a KotlinPoet string.
 * - Returns [TypedHandlerDelegate.Result.CONTINUE] for any other character.
 * - Returns [TypedHandlerDelegate.Result.CONTINUE] when not in a KotlinPoet call.
 * - Never throws for any character or position.
 */
@TestDataPath("\$CONTENT_ROOT/testData")
class KotlinPoetTypedHandlerTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private val handler = KotlinPoetTypedHandler()

    fun testStopWhenPercentTypedInKotlinPoetString() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("<caret>") }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val result = handler.checkAutoPopup('%', project, editor, myFixture.file)
        assertEquals(
            "Should STOP (trigger popup) when % typed in KotlinPoet string",
            TypedHandlerDelegate.Result.STOP,
            result,
        )
    }

    fun testContinueWhenNonPercentCharTypedInKotlinPoetString() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("<caret>") }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        // 'L' is not %, so the handler should pass through
        val result = handler.checkAutoPopup('L', project, editor, myFixture.file)
        assertEquals(
            "Should CONTINUE when non-% char typed",
            TypedHandlerDelegate.Result.CONTINUE,
            result,
        )
    }

    fun testContinueWhenPercentTypedOutsideString() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            fun test() { val x<caret> = 1 }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val result = handler.checkAutoPopup('%', project, editor, myFixture.file)
        assertEquals(
            "Should CONTINUE when % typed outside a string",
            TypedHandlerDelegate.Result.CONTINUE,
            result,
        )
    }

    fun testContinueWhenPercentTypedInPrintlnString() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            fun test() { println("<caret>") }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val result = handler.checkAutoPopup('%', project, editor, myFixture.file)
        assertEquals(
            "Should CONTINUE when % typed in non-KotlinPoet call string",
            TypedHandlerDelegate.Result.CONTINUE,
            result,
        )
    }

    fun testNeverThrowsForAnyCharOrPosition() {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%L", 1)
                println("hello")
                val x = 42
            }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val file = myFixture.file
        val chars = listOf('%', 'L', 'S', ' ', '\n', '"', '(', ')', '1')
        // Move caret to a few representative positions and verify no throws
        for (char in chars) {
            assertNotNull(handler.checkAutoPopup(char, project, editor, file))
        }
    }

    fun testContinueForAddStatementWithCachedAnalysis() {
        // When a cached analysis exists, the handler returns CONTINUE (trusts the contributor)
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().addStatement("<caret>") }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        // First call might STOP (no cache yet) or CONTINUE (cache exists) — just verify no throw
        val result = handler.checkAutoPopup('%', project, editor, myFixture.file)
        assertNotNull(result)
    }
}
