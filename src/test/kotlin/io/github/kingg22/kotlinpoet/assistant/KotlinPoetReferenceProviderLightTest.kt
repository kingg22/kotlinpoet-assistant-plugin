package io.github.kingg22.kotlinpoet.assistant

import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.infrastructure.references.KotlinPoetArgumentSymbol
import io.github.kingg22.kotlinpoet.assistant.infrastructure.references.KotlinPoetReferenceProvider
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

@KaAllowAnalysisOnEdt
class KotlinPoetReferenceProviderLightTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "testData"

    fun testReferenceResolvesInConcatenatedString() {
        myFixture.configureByFiles("references/ConcatFormat.kt", "stubs/KotlinPoet.kt")
        val file = myFixture.file
        val caretOffset = myFixture.caretOffset
        val element = file.findElementAt(caretOffset) ?: error("No PSI element at caret")
        val host = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java)
            ?: error("No string template at caret")

        val references = allowAnalysisOnEdt {
            KotlinPoetReferenceProvider().getReferences(host, PsiSymbolReferenceHints.offsetHint(1))
        }
        val caretInHost = caretOffset - host.textRange.startOffset
        val reference = references.firstOrNull { it.rangeInElement.containsOffset(caretInHost) }
        assertNotNull(reference)

        val resolved = reference!!.resolveReference()
        val symbol = resolved.single() as KotlinPoetArgumentSymbol
        assertEquals("name", symbol.expression.text)
    }
}
