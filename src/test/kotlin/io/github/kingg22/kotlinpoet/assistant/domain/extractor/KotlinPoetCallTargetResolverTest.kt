package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.KotlinPoetTestDescriptor
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Light-platform integration tests for [resolveFormatTextOrNull], [extractMapEntry] and [KotlinPoetCallTargetResolver].
 *
 * ## KaAnalysisUtils
 * - [extractMapEntry] correctly extracts key/value from `"key" to value` (infix),
 *   `"key".to(value)` (dot-qualified), and `Pair("key", value)` (constructor).
 * - Returns `null` for unsupported entry shapes.
 *
 * ## KotlinPoetCallTargetResolver
 * - Resolves direct KotlinPoet builder calls (`add`, `addStatement`, `addNamed`, `beginControlFlow`).
 * - Resolves calls on `CodeBlock.Builder`, `FunSpec.Builder`, and `FileSpec.Builder`.
 * - Returns `null` for non-KotlinPoet calls.
 * - Detects delegating methods (custom wrappers that internally call KotlinPoet).
 * - [KotlinPoetCallTarget.isDelegated] is `false` for direct calls, `true` for custom wrappers without a receiver type.
 */
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
@Suppress("ktlint:standard:function-naming")
class KotlinPoetCallTargetResolverTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinPoetTestDescriptor.projectDescriptor

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun configureKotlin(@Language("kotlin") content: String): KtFile {
        myFixture.configureByText("Test.kt", content.trimIndent())
        return myFixture.file as KtFile
    }

    private fun KtFile.callsNamed(name: String): List<KtCallExpression> = collectDescendantsOfType<KtCallExpression>()
        .filter { it.calleeExpression?.text == name }

    // ── KaAnalysisUtils.extractMapEntry ────────────────────────────────────────

    fun testExtractMapEntry_infixTo() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().addNamed("%food:L", mapOf("food" to "tacos"))
            }
        """,
        )
        val addNamed = file.callsNamed("addNamed").firstOrNull() ?: return
        val mapCall = addNamed.valueArguments.getOrNull(1)
            ?.getArgumentExpression() as? KtCallExpression ?: return
        val entryArg = mapCall.valueArguments.firstOrNull()?.getArgumentExpression() ?: return

        allowAnalysisOnEdt {
            analyze(addNamed) {
                val result = extractMapEntry(entryArg)
                assertNotNull("extractMapEntry should return non-null for infix 'to'", result)
                assertEquals("food", result!!.first)
                assertNotNull("Value expression should not be null", result.second)
            }
        }
    }

    fun testExtractMapEntry_dotQualifiedTo() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().addNamed("%count:L", mapOf("count".to(3)))
            }
        """,
        )
        val addNamed = file.callsNamed("addNamed").firstOrNull() ?: return
        val mapCall = addNamed.valueArguments.getOrNull(1)
            ?.getArgumentExpression() as? KtCallExpression ?: return
        val entryArg = mapCall.valueArguments.firstOrNull()?.getArgumentExpression() ?: return

        allowAnalysisOnEdt {
            analyze(addNamed) {
                val result = extractMapEntry(entryArg)
                assertNotNull("extractMapEntry should handle dot-qualified .to()", result)
                assertEquals("count", result?.first)
            }
        }
    }

    fun testExtractMapEntry_pairConstructor() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().addNamed("%item:L", mapOf(Pair("item", "value")))
            }
        """,
        )
        val addNamed = file.callsNamed("addNamed").firstOrNull() ?: return
        val mapCall = addNamed.valueArguments.getOrNull(1)
            ?.getArgumentExpression() as? KtCallExpression ?: return
        val entryArg = mapCall.valueArguments.firstOrNull()?.getArgumentExpression() ?: return

        allowAnalysisOnEdt {
            analyze(addNamed) {
                val result = extractMapEntry(entryArg)
                assertNotNull("extractMapEntry should handle Pair() constructor", result)
                assertEquals("item", result?.first)
            }
        }
    }

    fun testExtractMapEntry_nullReturnsNull() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%L", 1)
            }
        """,
        )
        val add = file.callsNamed("add").firstOrNull() ?: return
        allowAnalysisOnEdt {
            analyze(add) {
                val result = extractMapEntry(null)
                assertNull("null expression should return null", result)
            }
        }
    }

    // ── KotlinPoetCallTargetResolver ───────────────────────────────────────────

    fun testResolveDirectAddCallOnCodeBlockBuilder() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%L", 1)
            }
        """,
        )
        val add = file.callsNamed("add").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(add) }

        assertNotNull("Should resolve direct add() call", target)
        assertEquals("add", target!!.methodName)
        assertFalse("Direct call should not be delegated", target.isDelegated)
    }

    fun testResolveAddStatementOnCodeBlockBuilder() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().addStatement("val x = %L", 42)
            }
        """,
        )
        val call = file.callsNamed("addStatement").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        assertNotNull("Should resolve addStatement()", target)
        assertEquals("addStatement", target!!.methodName)
        assertFalse(target.isDelegated)
    }

    fun testResolveAddNamedCallOnCodeBlockBuilder() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().addNamed("%food:L", mapOf("food" to "tacos"))
            }
        """,
        )
        val call = file.callsNamed("addNamed").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        assertNotNull("Should resolve addNamed()", target)
        assertEquals("addNamed", target!!.methodName)
    }

    fun testResolveBeginControlFlowOnCodeBlockBuilder() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().beginControlFlow("if (%L)", true)
            }
        """,
        )
        val call = file.callsNamed("beginControlFlow").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        assertNotNull("Should resolve beginControlFlow()", target)
        assertEquals("beginControlFlow", target!!.methodName)
        assertFalse(target.isDelegated)
    }

    fun testResolveAddStatementOnFunSpecBuilder() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.FunSpec
            fun test() {
                FunSpec.builder("hello").addStatement("return %S", "hello")
            }
        """,
        )
        val call = file.callsNamed("addStatement").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        assertNotNull("Should resolve addStatement() on FunSpec.Builder", target)
        assertEquals("addStatement", target!!.methodName)
        assertFalse(target.isDelegated)
    }

    fun testResolveCodeBlockOf() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.of("%L", 1)
            }
        """,
        )
        val call = file.callsNamed("of").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        // CodeBlock.of is on CodeBlock.Companion — should resolve
        assertNotNull("Should resolve CodeBlock.of()", target)
    }

    fun testNullForNonKotlinPoetCall() {
        val file = configureKotlin(
            """
            fun test() {
                println("hello %L")
            }
        """,
        )
        val call = file.callsNamed("println").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        assertNull("println() should not resolve to a KotlinPoet target", target)
    }

    fun testNullForStringFormatCall() {
        val file = configureKotlin(
            """
            fun test() {
                String.format("%s", "x")
            }
        """,
        )
        val call = file.callsNamed("format").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        assertNull("String.format() should not resolve to a KotlinPoet target", target)
    }

    fun testReceiverFqNameIsKotlinPoetPackage() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().add("%L", 1)
            }
        """,
        )
        val call = file.callsNamed("add").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        assertNotNull(target)
        val fqName = target!!.receiverFqName
        assertNotNull("receiverFqName should not be null", fqName)
        assertTrue(
            "receiverFqName should be in com.squareup.kotlinpoet package",
            fqName!!.startsWith("com.squareup.kotlinpoet"),
        )
    }

    // ── Delegating methods ─────────────────────────────────────────────────────

    fun testDelegatingMethodIsDetected() {
        // A custom extension function that internally calls addStatement
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock

            fun addReturn(builder: CodeBlock.Builder, value: String): CodeBlock.Builder = builder.addStatement("return %S", value)

            fun test() {
                val builder = CodeBlock.builder()
                addReturn(builder, "hello")
            }
        """,
        )
        val call = file.callsNamed("addReturn").firstOrNull() ?: return
        val target = allowAnalysisOnEdt { KotlinPoetCallTargetResolver.resolve(call) }

        assertNotNull("Should resolve custom extension function", target)

        assertEquals("addStatement", target!!.methodName)
        // If resolved, it should be marked as delegated
        assertTrue("Custom wrapper resolved to a delegated call", target.isDelegated)
    }
}
