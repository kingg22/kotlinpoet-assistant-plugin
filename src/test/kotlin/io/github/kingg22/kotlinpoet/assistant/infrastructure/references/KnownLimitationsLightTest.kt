package io.github.kingg22.kotlinpoet.assistant.infrastructure.references

import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

@Suppress("UnstableApiUsage")
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
class KnownLimitationsLightTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    fun testReferenceInsideTrimIndentDoesNotResolve() {
        myFixture.configureByFiles("limitations/TrimIndent.kt", "stubs/KotlinPoet.kt")
        val file = myFixture.file
        val caretOffset = myFixture.caretOffset
        val element = file.findElementAt(caretOffset) ?: error("No PSI element at caret")
        val host = element.parentOfType<KtStringTemplateExpression>() ?: error("No string template at caret")

        val references = allowAnalysisOnEdt {
            KotlinPoetReferenceProvider().getReferences(host, PsiSymbolReferenceHints.offsetHint(1))
        }
        val caretInHost = caretOffset - host.textRange.startOffset
        val reference = references.firstOrNull { it.rangeInElement.containsOffset(caretInHost) }
        assertNull(reference)
    }
}
