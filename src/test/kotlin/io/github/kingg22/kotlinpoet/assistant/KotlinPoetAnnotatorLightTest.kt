package io.github.kingg22.kotlinpoet.assistant

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

@KaAllowAnalysisOnEdt
class KotlinPoetAnnotatorLightTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "testData"

    fun testHighlightsValidRelativePlaceholders() {
        myFixture.configureByFiles("annotator/ValidRelative.kt", "stubs/KotlinPoet.kt")
        val highlights = allowAnalysisOnEdt { myFixture.doHighlighting() }
        val hasReferenceHighlight = highlights.any {
            it.severity == HighlightSeverity.INFORMATION &&
                it.forcedTextAttributesKey == DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE
        }
        assertTrue(hasReferenceHighlight)
    }

    fun testReportsMissingArgument() {
        myFixture.configureByFiles("annotator/MissingArgument.kt", "stubs/KotlinPoet.kt")
        val highlights = allowAnalysisOnEdt { myFixture.doHighlighting() }
        val hasError = highlights.any { it.severity == HighlightSeverity.ERROR }
        assertTrue(hasError)
    }
}
