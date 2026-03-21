package io.github.kingg22.kotlinpoet.assistant.infrastructure.chain

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.domain.chain.BUILDER_METHOD_NAMES
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Integration tests for [CodeBlockPsiNavigator] using the IntelliJ light platform.
 *
 * Tests use `configureByText` to create synthetic Kotlin files so no test data files
 * are needed. The KotlinPoet stub (`CodeBlock`, `FunSpec.Builder`) is inlined.
 */
@KaAllowAnalysisOnEdt
@TestDataPath("\$CONTENT_ROOT/testData")
@Suppress("ktlint:standard:function-naming")
class CodeBlockPsiNavigatorLightTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ── Dot-chain tests ────────────────────────────────────────────────────────

    fun testFindPredecessorCall_simpleChain() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("fmt", "arg").addStatement("stmt")
            }
        """,
            )

        val calls = file.builderCalls()
        val addStatement = calls.first { it.calleeExpression?.text == "addStatement" }
        calls.first { it.calleeExpression?.text == "add" }

        val predecessor = CodeBlockPsiNavigator.findPredecessorCall(addStatement)
        assertNotNull("addStatement should have a predecessor", predecessor)
        assertEquals("add", predecessor?.calleeExpression?.text)
    }

    fun testFindPredecessorCall_firstInChain_returnsNull() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("fmt")
            }
        """,
            )

        val builderCall = file.builderCalls()
            .first { it.calleeExpression?.text == "builder" }

        // builder() has no KtCallExpression predecessor (receiver is CodeBlock reference)
        val predecessor = CodeBlockPsiNavigator.findPredecessorCall(builderCall)
        assertNull("builder() should have no predecessor", predecessor)
    }

    fun testFindSuccessorCall_returnsNextInChain() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("fmt").addStatement("stmt")
            }
        """,
            )

        val add = file.builderCalls().first { it.calleeExpression?.text == "add" }
        val successor = CodeBlockPsiNavigator.findSuccessorCall(add)
        assertNotNull(successor)
        assertEquals("addStatement", successor?.calleeExpression?.text)
    }

    fun testWalkBackward_returnsAllPredecessors() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder()
                    .add("a")
                    .addStatement("b")
                    .addStatement("c")
            }
        """,
            )

        val lastStmt = file.builderCalls().last { it.calleeExpression?.text == "addStatement" }

        val predecessors = CodeBlockPsiNavigator.walkBackward(lastStmt)
        val names = predecessors.map { it.calleeExpression?.text }

        assertEquals("Expected 3 predecessors", 3, predecessors.size)
        assertEquals("builder", names[0])
        assertEquals("add", names[1])
        assertEquals("addStatement", names[2])
    }

    fun testFullChainEndingAt_includesCallItself() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("fmt").build()
            }
        """,
            )

        val build = file.builderCalls().first { it.calleeExpression?.text == "build" }
        val chain = CodeBlockPsiNavigator.fullChainEndingAt(build)

        assertEquals(3, chain.size)
        assertEquals("builder", chain[0].calleeExpression?.text)
        assertEquals("add", chain[1].calleeExpression?.text)
        assertEquals("build", chain[2].calleeExpression?.text)
    }

    fun testFindChain_dotChain() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder()
                    .addStatement("val x = %L", 42)
                    .build()
            }
        """,
            )

        val addStatement = file.builderCalls()
            .first { it.calleeExpression?.text == "addStatement" }

        val chain = CodeBlockPsiNavigator.findChain(addStatement)
        assertTrue("Chain should have at least 2 calls", chain.size >= 2)
        assertEquals("addStatement", chain.last().calleeExpression?.text)
    }

    // ── DSL (buildCodeBlock) tests ─────────────────────────────────────────────

    fun testFindChain_buildCodeBlockLambda_returnsAllStatements() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.buildCodeBlock
            fun test() {
                buildCodeBlock {
                    add("first")
                    addStatement("second")
                    add("third")
                }
            }
        """,
            )

        // Get the 'add("first")' call
        val addFirst = file.builderCalls().first { it.calleeExpression?.text == "add" }

        val chain = CodeBlockPsiNavigator.findChain(addFirst)

        assertEquals("Expected 3 calls from the lambda body", 3, chain.size)
        assertEquals("add", chain[0].calleeExpression?.text)
        assertEquals("addStatement", chain[1].calleeExpression?.text)
        assertEquals("add", chain[2].calleeExpression?.text)
    }

    fun testFindChain_buildCodeBlockLambda_fromMiddleCall_returnsAll() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.buildCodeBlock
            fun test() {
                buildCodeBlock {
                    beginControlFlow("if (%L)", true)
                    addStatement("body()")
                    endControlFlow()
                }
            }
        """,
            )

        val addStatement = file.builderCalls()
            .first { it.calleeExpression?.text == "addStatement" }

        val chain = CodeBlockPsiNavigator.findChain(addStatement)
        assertEquals(3, chain.size)
        assertEquals("beginControlFlow", chain[0].calleeExpression?.text)
        assertEquals("addStatement", chain[1].calleeExpression?.text)
        assertEquals("endControlFlow", chain[2].calleeExpression?.text)
    }

    fun testFindChain_standaloneOf_returnsSingleCall() {
        val file =
            configureKotlin(
                """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                val block = CodeBlock.of("val x = %L", 42)
            }
        """,
            )

        val ofCall = file.builderCalls().first { it.calleeExpression?.text == "of" }
        val chain = CodeBlockPsiNavigator.findChain(ofCall)

        assertEquals("CodeBlock.of() is a standalone single-call chain", 1, chain.size)
        assertEquals("of", chain[0].calleeExpression?.text)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun configureKotlin(@Language("kotlin") content: String): KtFile {
        myFixture.configureByFile("stubs/KotlinPoet.kt")
        myFixture.configureByText("Test.kt", content.trimIndent())
        return myFixture.file as KtFile
    }

    private fun KtFile.builderCalls() = collectDescendantsOfType<KtCallExpression>()
        .filter { (it.calleeExpression?.text ?: "") in BUILDER_METHOD_NAMES }
}
