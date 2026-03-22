package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.KotlinPoetTestDescriptor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

/**
 * Light-platform integration tests for [FormatSyntaxInspection].
 *
 * Each test follows the pattern:
 * 1. Load testData file + KotlinPoet stub
 * 2. Enable the inspection
 * 3. Assert at least one problem is highlighted
 * 4. For quick-fix tests: launch the fix and compare against `*.after.kt`
 */
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
class FormatSyntaxInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinPoetTestDescriptor.projectDescriptor

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(FormatSyntaxInspection())
    }

    // ─── Dangling % ───────────────────────────────────────────────────────────

    fun testDanglingPercentIsReported() {
        myFixture.configureByFiles("inspections/DanglingPercent.kt")
        val problems = allowAnalysisOnEdt { myFixture.doHighlighting() }
        assertTrue("Expected a dangling-percent warning", problems.any { it.description != null })
    }

    fun testDanglingPercentEscapeQuickFix() {
        myFixture.configureByFiles("inspections/DanglingPercent.kt")
        allowAnalysisOnEdt { myFixture.doHighlighting() }

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.contains("%%") }
        assertNotNull("Escape as %% fix should be available", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResultByFile("inspections/DanglingPercent.after.kt")
    }

    // ─── Unknown placeholder type ─────────────────────────────────────────────

    fun testUnknownTypeIsReported() {
        myFixture.configureByFiles("inspections/UnknownType.kt")
        val problems = allowAnalysisOnEdt { myFixture.doHighlighting() }
        assertTrue("Expected an unknown-type warning", problems.any { it.description != null })
    }

    fun testUnknownTypeRemoveQuickFix() {
        myFixture.configureByFiles("inspections/UnknownType.kt")
        allowAnalysisOnEdt { myFixture.doHighlighting() }

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.contains("Remove") }
        assertNotNull("Remove token fix should be available", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResultByFile("inspections/UnknownType.after.kt")
    }

    // ─── Invalid positional index ─────────────────────────────────────────────

    fun testInvalidIndexIsReported() {
        myFixture.configureByFiles("inspections/InvalidIndex.kt")
        val problems = allowAnalysisOnEdt { myFixture.doHighlighting() }
        assertTrue("Expected an invalid-index warning", problems.any { it.description != null })
    }

    fun testInvalidIndexFixQuickFix() {
        myFixture.configureByFiles("inspections/InvalidIndex.kt")
        allowAnalysisOnEdt { myFixture.doHighlighting() }

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.contains("Fix index") }
        assertNotNull("Fix index quick fix should be available", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResultByFile("inspections/InvalidIndex.after.kt")
    }

    // ─── No false positives on valid code ─────────────────────────────────────

    fun testNoWarningsOnValidRelativeFormat() {
        myFixture.configureByFiles("inspections/ValidNoProblems.kt")
        val problems = allowAnalysisOnEdt {
            myFixture.doHighlighting()
        }.filter { it.inspectionToolId == "KotlinPoetFormatSyntax" }
        assertTrue("Expected no format-syntax warnings on valid code", problems.isEmpty())
    }
}
