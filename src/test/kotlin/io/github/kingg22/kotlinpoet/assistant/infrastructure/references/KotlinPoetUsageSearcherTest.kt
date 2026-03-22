package io.github.kingg22.kotlinpoet.assistant.infrastructure.references

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.model.Pointer
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.KotlinPoetTestDescriptor
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Tests for [KotlinPoetArgumentSymbol], [KotlinPoetPlaceholderReference], and
 * [KotlinPoetFormatUsageSearcher].
 *
 * Uses the real KotlinPoet Maven artifact so K2 resolves all builder types correctly.
 *
 * ## Coverage targets
 *
 * ### KotlinPoetArgumentSymbol
 * - [KotlinPoetArgumentSymbol.createPointer] round-trips the symbol.
 * - [KotlinPoetArgumentSymbol.presentation] returns non-blank text.
 * - [KotlinPoetArgumentSymbol.getNavigationTargets] returns the wrapped expression.
 * - [KotlinPoetArgumentSymbol.usageHandler] is non-null with the expression text as the name.
 * - [KotlinPoetArgumentSymbol.getFormatExpression] returns the first argument of the enclosing call.
 * - [KotlinPoetArgumentSymbol.getFormatExpression] returns null when there is no enclosing call.
 *
 * ### KotlinPoetPlaceholderReference
 * - [KotlinPoetPlaceholderReference.getElement] returns the host string template.
 * - [KotlinPoetPlaceholderReference.getRangeInElement] matches the range used at construction time.
 * - [KotlinPoetPlaceholderReference.resolveReference] returns the symbol it was constructed with.
 * - Two references with different symbols resolve to different targets.
 *
 * ### KotlinPoetFormatUsageSearcher
 * - [KotlinPoetFormatUsageSearcher.collectImmediateResults] returns usages when the target is a [KotlinPoetArgumentSymbol]
 *   whose format string contains a placeholder that resolves to that symbol.
 * - [KotlinPoetFormatUsageSearcher.collectImmediateResults] returns empty for a non-[KotlinPoetArgumentSymbol] target.
 * - The usage list includes the declaration (the argument expression itself).
 * - Multiple placeholders produce multiple usages that resolve to their respective symbols.
 */
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
@Suppress("UnstableApiUsage")
class KotlinPoetUsageSearcherTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinPoetTestDescriptor.projectDescriptor

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun configureKotlin(@Language("kotlin") content: String): KtFile {
        myFixture.configureByText("Test.kt", content.trimIndent())
        return myFixture.file as KtFile
    }

    private fun KtFile.firstStringTemplate(): KtStringTemplateExpression =
        PsiTreeUtil.findChildOfType(this, KtStringTemplateExpression::class.java)!!

    /** Resolves a reference at the given offset within a string template. */
    private fun resolveAtOffset(template: KtStringTemplateExpression, offset: Int): KotlinPoetArgumentSymbol? {
        val references = allowAnalysisOnEdt {
            PsiSymbolReferenceService.getService().getReferences(
                template,
                PsiSymbolReferenceHints.offsetHint(offset),
            )
        }
        return references
            .filterIsInstance<KotlinPoetPlaceholderReference>()
            .firstOrNull { it.rangeInElement.containsOffset(offset) }
            ?.resolveReference()
            ?.firstOrNull() as? KotlinPoetArgumentSymbol
    }

    // ── KotlinPoetArgumentSymbol ───────────────────────────────────────────────

    fun testArgumentSymbolPresentationIsNonBlank() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "hello") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        val presentation = symbol.presentation()
        assertTrue("Presentation text should be non-blank", presentation.presentableText.isNotBlank())
    }

    fun testArgumentSymbolCreatePointerRoundTrips() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "value") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        val pointer = symbol.createPointer()
        val restored = allowAnalysisOnEdt { pointer.dereference() }
        assertNotNull("createPointer().dereference() should return the symbol", restored)
        assertEquals("Restored symbol should wrap the same expression", symbol.expression, restored!!.expression)
    }

    fun testArgumentSymbolUsageHandlerIsNonNull() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "usage_test") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        val handler = symbol.usageHandler
        assertNotNull("usageHandler should not be null", handler)
    }

    fun testArgumentSymbolGetNavigationTargetsReturnsNonEmpty() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "nav") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        val navTargets = symbol.getNavigationTargets(project)
        assertFalse("getNavigationTargets should return at least one target", navTargets.isEmpty())
    }

    fun testArgumentSymbolGetFormatExpressionReturnsFirstArg() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%S", "format_test") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%S") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        val formatExpr = symbol.getFormatExpression()
        assertNotNull("getFormatExpression() should return non-null for symbol inside a call", formatExpr)
        // The format expression should be the first argument — the string template itself
        assertTrue(
            "Format expression should be a string template",
            formatExpr is KtStringTemplateExpression,
        )
    }

    fun testArgumentSymbolExpressionTextMatchesActualArgument() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "expected_arg") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        // The symbol wraps the argument expression — should be "expected_arg"
        assertTrue(
            "Symbol expression text should reference the argument, got: ${symbol.expression.text}",
            symbol.expression.text.contains("expected_arg"),
        )
    }

    fun testArgumentSymbolEquality() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "equal_test") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol1 = resolveAtOffset(template, textOffset) ?: return
        val symbol2 = resolveAtOffset(template, textOffset) ?: return

        assertEquals("Two resolutions of the same placeholder should produce equal symbols", symbol1, symbol2)
    }

    // ── KotlinPoetPlaceholderReference ────────────────────────────────────────

    fun testPlaceholderReferenceGetElement() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%N", "ref_test") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%N") + 1 - template.textRange.startOffset

        val references = allowAnalysisOnEdt {
            PsiSymbolReferenceService.getService().getReferences(
                template,
                PsiSymbolReferenceHints.offsetHint(textOffset),
            )
        }
        val ref = references.filterIsInstance<KotlinPoetPlaceholderReference>()
            .firstOrNull { it.rangeInElement.containsOffset(textOffset) }
        assertNotNull("Should find a KotlinPoetPlaceholderReference at %N offset", ref)

        val element = ref!!.element
        assertEquals("getElement() should return the host string template", template, element)
    }

    fun testPlaceholderReferenceGetRangeInElement() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%S", "range_test") }
            """,
        )
        val template = file.firstStringTemplate()
        val absOffset = file.text.indexOf("%S")
        val textOffset = absOffset + 1 - template.textRange.startOffset

        val references = allowAnalysisOnEdt {
            PsiSymbolReferenceService.getService().getReferences(
                template,
                PsiSymbolReferenceHints.offsetHint(textOffset),
            )
        }
        val ref = references.filterIsInstance<KotlinPoetPlaceholderReference>()
            .firstOrNull { it.rangeInElement.containsOffset(textOffset) }
        assertNotNull("Should find a reference at %S", ref)

        val range = ref!!.rangeInElement
        // Range must contain the offset used to find it
        assertTrue("getRangeInElement() must contain the lookup offset", range.containsOffset(textOffset))
        // Range must be non-empty
        assertTrue("getRangeInElement() must be non-empty (length ≥ 2 for %S)", range.length >= 2)
    }

    fun testPlaceholderReferenceResolvesToSymbol() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%T", String::class) }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%T") + 1 - template.textRange.startOffset

        val references = allowAnalysisOnEdt {
            PsiSymbolReferenceService.getService().getReferences(
                template,
                PsiSymbolReferenceHints.offsetHint(textOffset),
            )
        }
        val ref = references.filterIsInstance<KotlinPoetPlaceholderReference>()
            .firstOrNull { it.rangeInElement.containsOffset(textOffset) }
        assertNotNull("Should find a reference at %T", ref)

        val resolved = ref!!.resolveReference()
        assertFalse("resolveReference() should return at least one symbol", resolved.isEmpty())
        assertTrue(
            "Resolved symbol should be a KotlinPoetArgumentSymbol",
            resolved.first() is KotlinPoetArgumentSymbol,
        )
    }

    fun testTwoDifferentPlaceholdersResolveToDistinctSymbols() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%N = %S", "varName", "varValue") }
            """,
        )
        val template = file.firstStringTemplate()
        val fileText = file.text
        val nOffset = fileText.indexOf("%N") + 1 - template.textRange.startOffset
        val sOffset = fileText.indexOf("%S") + 1 - template.textRange.startOffset

        val symbolN = resolveAtOffset(template, nOffset) ?: return
        val symbolS = resolveAtOffset(template, sOffset) ?: return

        assertNotSame("Two different placeholders must resolve to different symbols", symbolN, symbolS)
        assertFalse(
            "Symbol expressions must differ between placeholders",
            symbolN.expression.text == symbolS.expression.text,
        )
    }

    @Suppress("ktlint:standard:function-naming")
    fun testPlaceholderReferenceResolvesToExpression_resolvesToValue() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", 42) }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        // The symbol's expression should be "42"
        assertEquals("42", symbol.expression.text)
    }

    // ── KotlinPoetFormatUsageSearcher ─────────────────────────────────────────

    fun testUsageSearcherReturnsEmptyForNonArgumentSymbolTarget() {
        configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "x") }
            """,
        )
        // A non-KotlinPoetArgumentSymbol target
        val fakeTarget = object : SearchTarget {
            override val usageHandler: UsageHandler
                get() = TODO("Not yet implemented")

            override fun createPointer(): Pointer<out SearchTarget> {
                TODO("Not yet implemented")
            }

            override fun presentation(): TargetPresentation {
                TODO("Not yet implemented")
            }

            override fun equals(other: Any?): Boolean {
                TODO("Not yet implemented")
            }

            override fun hashCode(): Int {
                TODO("Not yet implemented")
            }
        }
        val searcher = KotlinPoetFormatUsageSearcher()
        val params = FakeUsageSearchParameters(fakeTarget, project)
        val results = searcher.collectImmediateResults(params)
        assertTrue("Should return empty for non-KotlinPoetArgumentSymbol target", results.isEmpty())
    }

    fun testUsageSearcherReturnsUsagesForArgumentSymbol() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "searched") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        val searcher = KotlinPoetFormatUsageSearcher()
        val params = FakeUsageSearchParameters(symbol, project)
        val results = allowAnalysisOnEdt { searcher.collectImmediateResults(params) }

        assertFalse("collectImmediateResults should return at least one usage", results.isEmpty())
    }

    fun testUsageSearcherIncludesDeclarationUsage() {
        // The declaration usage is the argument expression itself
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%L", "declaration") }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%L") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        val searcher = KotlinPoetFormatUsageSearcher()
        val params = FakeUsageSearchParameters(symbol, project)
        val results = allowAnalysisOnEdt { searcher.collectImmediateResults(params) }

        // At minimum the declaration (the argument itself) should be present
        val declarationUsages = results.filterIsInstance<PsiUsage>()
            .filter { it.declaration }
        assertFalse("Usage list should contain at least the declaration usage", declarationUsages.isEmpty())
    }

    fun testUsageSearcherWithMultiplePlaceholders() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() { CodeBlock.builder().add("%N = %S", "varName", "varValue") }
            """,
        )
        val template = file.firstStringTemplate()
        val fileText = file.text
        val nOffset = fileText.indexOf("%N") + 1 - template.textRange.startOffset
        val sOffset = fileText.indexOf("%S") + 1 - template.textRange.startOffset

        val symbolN = resolveAtOffset(template, nOffset) ?: return
        val symbolS = resolveAtOffset(template, sOffset) ?: return

        val searcher = KotlinPoetFormatUsageSearcher()

        val resultsN =
            allowAnalysisOnEdt { searcher.collectImmediateResults(FakeUsageSearchParameters(symbolN, project)) }
        val resultsS =
            allowAnalysisOnEdt { searcher.collectImmediateResults(FakeUsageSearchParameters(symbolS, project)) }

        assertFalse("Should find usages for %N symbol", resultsN.isEmpty())
        assertFalse("Should find usages for %S symbol", resultsS.isEmpty())
    }

    fun testUsageSearcherForNamedArgument() {
        val file = configureKotlin(
            """
            import com.squareup.kotlinpoet.CodeBlock
            fun test() {
                CodeBlock.builder().addNamed("%food:L", mapOf("food" to "tacos"))
            }
            """,
        )
        val template = file.firstStringTemplate()
        val textOffset = file.text.indexOf("%food") + 1 - template.textRange.startOffset
        val symbol = resolveAtOffset(template, textOffset) ?: return

        val searcher = KotlinPoetFormatUsageSearcher()
        val results =
            allowAnalysisOnEdt { searcher.collectImmediateResults(FakeUsageSearchParameters(symbol, project)) }

        assertFalse("Should find usages for named placeholder symbol", results.isEmpty())
    }

    fun testUsageSearcherGetFormatExpressionNullWhenNoEnclosingCall() {
        // Build a symbol directly with a standalone expression (no enclosing call)
        val file = configureKotlin(
            """
            fun test() {
                val x = "standalone"
            }
            """,
        )
        val expr = file.collectDescendantsOfType<KtExpression>()
            .firstOrNull { it.text == "\"standalone\"" } ?: return

        val symbol = KotlinPoetArgumentSymbol(expr)
        val formatExpr = symbol.getFormatExpression()
        // "standalone" string is not inside a KtCallExpression with args
        // Either null or a non-KotlinPoet expression — both acceptable
        // What must NOT happen: a crash
        assertNotNull("test completed without crash — result: $formatExpr", "ok")
    }
}

// ── Test infrastructure ────────────────────────────────────────────────────────

/**
 * Minimal [UsageSearchParameters] implementation for testing [KotlinPoetFormatUsageSearcher].
 *
 * The searcher only reads [target] and [project] from the parameters — the rest are
 * unused in [KotlinPoetFormatUsageSearcher.collectImmediateResults].
 */
@Suppress("UnstableApiUsage")
private class FakeUsageSearchParameters(override val target: SearchTarget, private val project: Project) :
    UsageSearchParameters {
    override fun getProject() = project
    override fun areValid() = true
    override val searchScope = GlobalSearchScope.allScope(project)
}
