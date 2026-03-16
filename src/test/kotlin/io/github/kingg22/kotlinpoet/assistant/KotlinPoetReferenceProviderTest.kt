package io.github.kingg22.kotlinpoet.assistant

import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.infrastructure.references.KotlinPoetArgumentSymbol
import io.github.kingg22.kotlinpoet.assistant.infrastructure.references.KotlinPoetPlaceholderReference
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.junit.Assert.assertNotEquals

@KaAllowAnalysisOnEdt
class KotlinPoetReferenceProviderTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "testData"

    fun testPlaceholderReferencesAreDistinct() {
        myFixture.configureByFiles("references/MultiplePlaceholders.kt", "stubs/KotlinPoet.kt")
        val file = myFixture.file
        val template = PsiTreeUtil.findChildOfType(file, KtStringTemplateExpression::class.java)
        assertNotNull(template)

        val text = file.text
        val elementStart = template!!.textRange.startOffset
        val nOffset = text.indexOf("%N") + 1 - elementStart
        val sOffset = text.indexOf("%S") + 1 - elementStart
        val nSymbol = resolveAtOffset(template, nOffset)
        val sSymbol = resolveAtOffset(template, sOffset)

        assertNotNull(nSymbol)
        assertNotNull(sSymbol)
        assertNotEquals(nSymbol!!.expression.text, sSymbol!!.expression.text)
        assertEquals("\"myVar\"", nSymbol.expression.text)
        assertEquals("\"myArg\"", sSymbol.expression.text)
    }

    fun testNamedMapOfReferenceResolvesToValue() {
        myFixture.configureByFiles("references/NamedMapOf.kt", "stubs/KotlinPoet.kt")
        val file = myFixture.file
        val template = PsiTreeUtil.findChildOfType(file, KtStringTemplateExpression::class.java)
        assertNotNull(template)

        val text = file.text
        val elementStart = template!!.textRange.startOffset
        val nameOffset = text.indexOf("%name") + 1 - elementStart
        val symbol = resolveAtOffset(template, nameOffset)
        assertNotNull(symbol)
        assertEquals("\"value\"", symbol!!.expression.text)
    }

    private fun resolveAtOffset(
        template: KtStringTemplateExpression,
        offsetInElement: Int,
    ): KotlinPoetArgumentSymbol? {
        val references = allowAnalysisOnEdt {
            PsiSymbolReferenceService.getService().getReferences(
                template,
                PsiSymbolReferenceHints.offsetHint(offsetInElement),
            )
        }
        val reference = references.filterIsInstance<KotlinPoetPlaceholderReference>()
            .firstOrNull { containsOffset(it.rangeInElement, offsetInElement) }
            ?: return null
        return reference.resolveReference().firstOrNull() as? KotlinPoetArgumentSymbol
    }

    private fun containsOffset(range: TextRange, offset: Int): Boolean = range.containsOffset(offset)
}
