package io.github.kingg22.kotlinpoet.assistant

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections.ExtraArgumentInspection
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections.MissingArgumentInspection
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections.NamedCaseInspection
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections.TypeMismatchInspection
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

/**
 * Light-platform integration tests for all four KotlinPoet local inspections.
 *
 * Test method naming follows the JUnit 3 convention required by [BasePlatformTestCase]
 * (`fun testXxx()`).
 *
 * Each test:
 * 1. Loads a `*.kt` file from `src/test/testData/inspections/` (plus the KotlinPoet stub).
 * 2. Enables the inspection under test.
 * 3. Runs highlighting and asserts at least one problem was found.
 * 4. For quick-fix tests, launches the fix and asserts the result matches `*.after.kt`.
 *
 * Quick-fix tests are named `doFixTest` and follow the pattern documented in
 * https://plugins.jetbrains.com/docs/intellij/code-inspections.html#inspection-test
 */
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
class KotlinPoetInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ─── Missing argument ──────────────────────────────────────────────────────

    fun testMissingArgumentIsReported() {
        myFixture.enableInspections(MissingArgumentInspection())
        myFixture.configureByFiles("inspections/MissingArgument.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt { myFixture.doHighlighting() }
        assertTrue(
            "Expected at least one problem for missing argument",
            problems.any { it.description != null },
        )
    }

    fun testMissingArgumentNoFalsePositiveWhenArgsPresent() {
        myFixture.enableInspections(MissingArgumentInspection())
        myFixture.configureByFiles("inspections/ValidNoProblems.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt {
            myFixture.doHighlighting()
        }.filter { it.inspectionToolId == "KotlinPoetMissingArgument" }
        assertTrue("Expected no missing-argument problems on valid code", problems.isEmpty())
    }

    // ─── Extra argument ────────────────────────────────────────────────────────

    fun testExtraRelativeArgumentIsReported() {
        myFixture.enableInspections(ExtraArgumentInspection())
        myFixture.configureByFiles("inspections/ExtraArgument.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt { myFixture.doHighlighting() }
        assertTrue(
            "Expected at least one problem for extra argument",
            problems.any { it.description != null },
        )
    }

    fun testExtraRelativeArgumentQuickFix() {
        myFixture.enableInspections(ExtraArgumentInspection())
        myFixture.configureByFiles("inspections/ExtraArgument.kt", "stubs/KotlinPoet.kt")
        allowAnalysisOnEdt { myFixture.doHighlighting() }

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.contains("Remove extra argument") }
        assertNotNull("RemoveExtraArgumentQuickFix should be available", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResultByFile("inspections/ExtraArgument.after.kt")
    }

    fun testExtraPositionalArgumentIsReported() {
        myFixture.enableInspections(ExtraArgumentInspection())
        myFixture.configureByFiles("inspections/ExtraArgumentPositional.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt { myFixture.doHighlighting() }
        assertTrue(
            "Expected at least one problem for extra positional argument",
            problems.any { it.description != null },
        )
    }

    fun testExtraPositionalArgumentQuickFix() {
        myFixture.enableInspections(ExtraArgumentInspection())
        myFixture.configureByFiles("inspections/ExtraArgumentPositional.kt", "stubs/KotlinPoet.kt")
        allowAnalysisOnEdt { myFixture.doHighlighting() }

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.contains("Remove extra argument") }
        assertNotNull("RemoveExtraArgumentQuickFix should be available for positional", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResultByFile("inspections/ExtraArgumentPositional.after.kt")
    }

    fun testExtraArgumentNoFalsePositiveWhenArgsMatch() {
        myFixture.enableInspections(ExtraArgumentInspection())
        myFixture.configureByFiles("inspections/ValidNoProblems.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt {
            myFixture.doHighlighting()
        }.filter { it.inspectionToolId == "KotlinPoetExtraArgument" }
        assertTrue("Expected no extra-argument problems on valid code", problems.isEmpty())
    }

    // ─── Type mismatch ─────────────────────────────────────────────────────────

    fun testTypeMismatchIsReported() {
        myFixture.enableInspections(TypeMismatchInspection())
        myFixture.configureByFiles("inspections/TypeMismatch.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt { myFixture.doHighlighting() }
        assertTrue(
            "Expected at least one type-mismatch problem",
            problems.any { it.description != null },
        )
    }

    fun testTypeMismatchNoFalsePositiveOnCompatibleTypes() {
        myFixture.enableInspections(TypeMismatchInspection())
        myFixture.configureByFiles("inspections/ValidNoProblems.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt {
            myFixture.doHighlighting()
        }.filter { it.inspectionToolId == "KotlinPoetTypeMismatch" }
        assertTrue("Expected no type-mismatch problems on valid code", problems.isEmpty())
    }

    // ─── Named argument case ───────────────────────────────────────────────────

    fun testNamedCaseIsReported() {
        myFixture.enableInspections(NamedCaseInspection())
        myFixture.configureByFiles("inspections/NamedCaseInvalid.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt { myFixture.doHighlighting() }
        assertTrue(
            "Expected at least one named-case problem for uppercase key",
            problems.any { it.description != null },
        )
    }

    fun testNamedCaseQuickFix() {
        myFixture.enableInspections(NamedCaseInspection())
        myFixture.configureByFiles("inspections/NamedCaseInvalid.kt", "stubs/KotlinPoet.kt")
        allowAnalysisOnEdt { myFixture.doHighlighting() }

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.contains("food") }
        assertNotNull("RenameToLowercaseQuickFix should be available", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResultByFile("inspections/NamedCaseInvalid.after.kt")
    }

    fun testNamedCaseNoFalsePositiveForLowercaseKeys() {
        myFixture.enableInspections(NamedCaseInspection())
        myFixture.configureByFiles("inspections/ValidNoProblems.kt", "stubs/KotlinPoet.kt")
        val problems = allowAnalysisOnEdt {
            myFixture.doHighlighting()
        }.filter { it.inspectionToolId == "KotlinPoetNamedCase" }
        assertTrue("Expected no named-case problems for valid lowercase keys", problems.isEmpty())
    }
}
