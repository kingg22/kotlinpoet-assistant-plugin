package io.github.kingg22.kotlinpoet.assistant.infrastructure.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.KotlinPoetTestDescriptor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
class KotlinPoetAnnotatorLightTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinPoetTestDescriptor.projectDescriptor

    fun testHighlightsValidRelativePlaceholders() {
        myFixture.configureByFiles("annotator/ValidRelative.kt")
        val highlights = allowAnalysisOnEdt { myFixture.doHighlighting() }
        val hasReferenceHighlight = highlights.any {
            it.severity == HighlightSeverity.INFORMATION &&
                it.forcedTextAttributesKey == KotlinPoetHighlightKeys.PLACEHOLDER
        }
        assertTrue(hasReferenceHighlight)
    }

    fun testHighlightsValidControlSymbols() {
        myFixture.configureByFiles("annotator/ValidControlSymbol.kt")
        val highlights = allowAnalysisOnEdt { myFixture.doHighlighting() }
        val hasReferenceHighlight = highlights.any {
            it.severity == HighlightSeverity.INFORMATION &&
                it.forcedTextAttributesKey == KotlinPoetHighlightKeys.CONTROL_SYMBOL
        }
        assertTrue(hasReferenceHighlight)
    }

    fun testAddKdocIsRecognized() {
        myFixture.configureByFile("annotator/AddKdoc.kt")
        val highlights = allowAnalysisOnEdt { myFixture.doHighlighting() }
        val hasReferenceHighlight = highlights.any {
            it.severity == HighlightSeverity.INFORMATION &&
                it.forcedTextAttributesKey == KotlinPoetHighlightKeys.PLACEHOLDER
        }
        assertTrue(hasReferenceHighlight)
    }
}
