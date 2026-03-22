package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.KotlinPoetTestDescriptor
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

/**
 * Tests for [KotlinPoetDocumentationLinkHandler].
 *
 * ## Scope
 *
 * - `resolveLink` returns `null` for a non-[KotlinPoetDocumentationTarget] target (fast path).
 * - `resolveLink` returns `null` for an unsupported URL scheme (non `psi_element://`).
 * - `resolveLink` does NOT return null for well-known, always-resolvable PSI elements
 *   like `kotlin.String` and `com.squareup.kotlinpoet.CodeBlock` when the real KotlinPoet
 *   library is on the classpath.
 *
 * We do NOT test `psi_element://` links that require Kotlin K2 symbol resolution against
 * classes that may not be in the test classpath (e.g., private KotlinPoet internals).
 */
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
class KotlinPoetDocumentationLinkHandlerTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinPoetTestDescriptor.projectDescriptor

    private val handler = KotlinPoetDocumentationLinkHandler()

    // ── Helper — build a KotlinPoetDocumentationTarget anchored to a real PSI element ──

    private fun buildTarget(kind: PlaceholderSpec.FormatKind): KotlinPoetDocumentationTarget {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", 1) }
            """.trimIndent(),
        )
        val token = DocToken.Placeholder(kind)
        return KotlinPoetDocumentationTarget(token, myFixture.file)
    }

    private fun buildControlTarget(type: SymbolType): KotlinPoetDocumentationTarget {
        myFixture.configureByText(
            "Test.kt",
            // language=kotlin
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("⇥%L") }
            """.trimIndent(),
        )
        return KotlinPoetDocumentationTarget(DocToken.Control(type), myFixture.file)
    }

    // ── Fast-path: non-KotlinPoet target ──────────────────────────────────────

    @Suppress("UnstableApiUsage")
    fun testReturnsNullForNonKotlinPoetDocumentationTarget() {
        // A DocumentationTarget that is NOT KotlinPoetDocumentationTarget
        // The handler should return null immediately
        val fakeTarget = object : DocumentationTarget {
            override fun createPointer() = Pointer.hardPointer(this)
            override fun computePresentation() = TargetPresentation.builder("fake").presentation()
        }
        val result = allowAnalysisOnEdt {
            handler.resolveLink(fakeTarget, "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}kotlin.String")
        }
        assertNull("Should return null for non-KotlinPoet target", result)
    }

    // ── Unsupported URL schemes ────────────────────────────────────────────────

    fun testReturnsNullForHttpUrl() {
        val target = buildTarget(PlaceholderSpec.FormatKind.LITERAL)
        val result = allowAnalysisOnEdt {
            handler.resolveLink(target, "https://square.github.io/kotlinpoet/")
        }
        assertNull("Should return null for https:// URL (not psi_element://)", result)
    }

    fun testReturnsNullForEmptyUrl() {
        val target = buildTarget(PlaceholderSpec.FormatKind.LITERAL)
        val result = allowAnalysisOnEdt {
            handler.resolveLink(target, "")
        }
        assertNull("Should return null for empty URL", result)
    }

    fun testReturnsNullForArbitraryUnknownScheme() {
        val target = buildTarget(PlaceholderSpec.FormatKind.STRING)
        val result = allowAnalysisOnEdt {
            handler.resolveLink(target, "unknown://some.class.Name")
        }
        assertNull("Should return null for unknown:// scheme", result)
    }

    // ── psi_element:// — well-known resolvable types ───────────────────────────

    fun testResolvesKotlinStringLink() {
        val target = buildTarget(PlaceholderSpec.FormatKind.STRING)
        val url = "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}kotlin.String"
        val result = allowAnalysisOnEdt { handler.resolveLink(target, url) }
        // kotlin.String is always available — should NOT return null
        assertNotNull(
            "psi_element://kotlin.String should resolve to a documentation target",
            result,
        )
    }

    fun testResolvesCodeBlockLink() {
        val target = buildTarget(PlaceholderSpec.FormatKind.LITERAL)
        val url = "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}com.squareup.kotlinpoet.CodeBlock"
        val result = allowAnalysisOnEdt { handler.resolveLink(target, url) }
        assertNotNull(
            "psi_element://com.squareup.kotlinpoet.CodeBlock should resolve (KotlinPoet on classpath)",
            result,
        )
    }

    fun testResolvesFunSpecLink() {
        val target = buildTarget(PlaceholderSpec.FormatKind.NAME)
        val url = "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}com.squareup.kotlinpoet.FunSpec"
        val result = allowAnalysisOnEdt { handler.resolveLink(target, url) }
        assertNotNull(
            "psi_element://com.squareup.kotlinpoet.FunSpec should resolve",
            result,
        )
    }

    fun testResolvesMemberNameLink() {
        val target = buildTarget(PlaceholderSpec.FormatKind.MEMBER)
        val url = "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}com.squareup.kotlinpoet.MemberName"
        val result = allowAnalysisOnEdt { handler.resolveLink(target, url) }
        assertNotNull(
            "psi_element://com.squareup.kotlinpoet.MemberName should resolve",
            result,
        )
    }

    fun testResolvesTypeNameLink() {
        val target = buildTarget(PlaceholderSpec.FormatKind.TYPE)
        val url = "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}com.squareup.kotlinpoet.TypeName"
        val result = allowAnalysisOnEdt { handler.resolveLink(target, url) }
        assertNotNull(
            "psi_element://com.squareup.kotlinpoet.TypeName should resolve",
            result,
        )
    }

    // ── psi_element:// — unresolvable types return null gracefully ─────────────

    fun testReturnsNullForUnresolvableClass() {
        val target = buildTarget(PlaceholderSpec.FormatKind.LITERAL)
        val url = "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}com.example.nonexistent.Ghost"
        val result = allowAnalysisOnEdt { handler.resolveLink(target, url) }
        // Must not throw — null is the correct result for an unresolvable class
        assertNull(
            "Unresolvable class should return null without throwing",
            result,
        )
    }

    fun testDoesNotThrowForMalformedPsiElementUrl() {
        val target = buildTarget(PlaceholderSpec.FormatKind.LITERAL)
        val malformed = DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
        // Just verify no exception is thrown
        val result = allowAnalysisOnEdt { handler.resolveLink(target, malformed) }
        // null or non-null — both are fine; what matters is no crash
        assertNotNull("Result should not matter — just must not throw", result ?: "null is fine")
    }

    // ── Control symbol target ──────────────────────────────────────────────────

    fun testResolvesCodeBlockLinkFromControlTarget() {
        // Control symbol doc includes a link to CodeBlock — verify it resolves
        val target = buildControlTarget(SymbolType.INDENT)
        val url = "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}com.squareup.kotlinpoet.CodeBlock"
        val result = allowAnalysisOnEdt { handler.resolveLink(target, url) }
        assertNotNull("CodeBlock link should resolve from control symbol target", result)
    }
}
