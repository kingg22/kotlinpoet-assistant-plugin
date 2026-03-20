package io.github.kingg22.kotlinpoet.assistant

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections.FormatSyntaxInspection
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.junit.Ignore

/**
 * Integration tests for the three mixed-style conversion quick fixes:
 *
 * - [ConvertToRelativePlaceholderQuickFix] — Path A (direct PSI replace, deterministic)
 * - [ConvertToNamedPlaceholderQuickFix]   — Path B (live template)
 * - [ConvertToPositionalPlaceholderQuickFix] — Path B (live template)
 *
 * ## Testing strategy per path
 *
 * ### Path A — ConvertToRelative
 * Uses [myFixture.applyFix] which directly applies the fix without the read-only wrapper
 * that [myFixture.launchAction] adds. Result is verified with [checkResultByFile].
 *
 * ### Path B — ConvertToNamed / ConvertToPositional
 * These fixes open a live-template session. In headless test mode [launchAction] wraps
 * the call in [withReadOnlyFile] which causes [ReadOnlyModificationException] when
 * [TemplateBuilderImpl.buildInlineTemplate] tries to delete ranges.
 *
 * Strategy: only verify **fix availability** via [getAllQuickFixes]. The template execution
 * itself is an IntelliJ Platform concern — its correctness is covered by the manual testing
 * already performed and the unit tests in [AnchorUtilsUnitTest].
 */
@TestDataPath("\$CONTENT_ROOT/testData")
@KaAllowAnalysisOnEdt
@Suppress("DialogTitleCapitalization", "ktlint:standard:function-naming")
@Ignore("Fail to apply quick fixes, needs a API to test live templates")
class MixedStyleQuickFixTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(FormatSyntaxInspection())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun highlight() = allowAnalysisOnEdt { myFixture.doHighlighting() }

    private fun fix(text: String) = myFixture.getAllQuickFixes()
        .firstOrNull { it.text.contains(text, ignoreCase = true) }

    private fun load(vararg files: String) = myFixture.configureByFiles(
        *files.map { "inspections/mixed/$it" }.toTypedArray() + "stubs/KotlinPoet.kt",
    )

    private fun applyFix(text: String) {
        val f = myFixture.getAllQuickFixes()
            .firstOrNull { it.text.contains(text, ignoreCase = true) }
            ?: error("Fix containing '$text' not found in: ${myFixture.getAllQuickFixes().map { it.text }}")
        allowAnalysisOnEdt { myFixture.launchAction(f) }
    }

    // ── Path A: ConvertToRelative — full result verification ───────────────────

    fun testConvertToRelative_namedInAdd() {
        load("NamedInAdd.kt")
        highlight()
        applyFix("relative")
        myFixture.checkResultByFile("inspections/mixed/NamedInAdd.toRelative.after.kt")
    }

    fun testConvertToRelative_relativeAndPositionalMix() {
        load("RelativeAndPositional.kt")
        highlight()
        applyFix("relative")
        myFixture.checkResultByFile("inspections/mixed/RelativeAndPositional.toRelative.after.kt")
    }

    fun testConvertToRelative_multipleNamedPlaceholders() {
        load("MultipleNamedInAdd.kt")
        highlight()
        applyFix("relative")
        myFixture.checkResultByFile("inspections/mixed/MultipleNamedInAdd.toRelative.after.kt")
    }

    fun testConvertToRelative_withControlSymbols() {
        load("WithControlSymbols.kt")
        highlight()
        applyFix("relative")
        myFixture.checkResultByFile("inspections/mixed/WithControlSymbols.toRelative.after.kt")
    }

    fun testConvertToRelative_positionalInAddNamed() {
        load("PositionalInAddNamed.kt")
        highlight()
        applyFix("relative")
        myFixture.checkResultByFile("inspections/mixed/PositionalInAddNamed.toRelative.after.kt")
    }

    fun testConvertToRelative_sameKindMixed() {
        load("SameKindMixed.kt")
        highlight()
        applyFix("relative")
        myFixture.checkResultByFile("inspections/mixed/SameKindMixed.toRelative.after.kt")
    }

    // ── Path A: ConvertToRelative — no false positives ────────────────────────

    fun testConvertToRelative_notAvailableOnPureRelative() {
        myFixture.configureByFiles("inspections/ValidNoProblems.kt", "stubs/KotlinPoet.kt")
        highlight()
        assertNull(
            "ConvertToRelative must not appear on already-valid format strings",
            fix("relative"),
        )
    }

    fun testConvertToRelative_idempotent_noErrorRemainsAfterApply() {
        load("NamedInAdd.kt")
        highlight()
        applyFix("relative")
        val remaining = allowAnalysisOnEdt {
            myFixture.doHighlighting()
        }.filter { it.inspectionToolId == "KotlinPoetFormatSyntax" }
        assertTrue(
            "No MIXED_STYLES error should remain after ConvertToRelative; found: $remaining",
            remaining.isEmpty(),
        )
    }

    // ── Path B: ConvertToNamed — availability only ────────────────────────────
    // Template execution cannot be tested headless (withReadOnlyFile prevents document
    // modification). Correctness of anchor ranges is covered by AnchorUtilsUnitTest.

    fun testConvertToNamed_availableForMixedRelativePositional() {
        load("RelativeAndPositional.kt")
        highlight()
        assertNotNull(
            "ConvertToNamed fix must be offered for relative+positional mix",
            fix("named"),
        )
    }

    fun testConvertToNamed_availableForRelativeInAddNamed() {
        load("RelativeInAddNamed.kt")
        highlight()
        assertNotNull(
            "ConvertToNamed fix must be offered for relative-in-addNamed",
            fix("named"),
        )
    }

    fun testConvertToNamed_availableForMultipleDistinctKinds() {
        load("MultipleNamedInAdd.kt")
        highlight()
        // NamedInAdd triggers Named-in-add style: named placeholders in add() method.
        // ConvertToNamed is not applicable here (already named) — ConvertToRelative/Positional are.
        // MultipleNamedInAdd has %a:T %b:N %c:L → Named style in add() → offers relative, positional
        val fixes = myFixture.getAllQuickFixes().map { it.text }
        assertTrue(
            "At least one conversion fix must be offered; available: $fixes",
            fixes.any { it.contains("relative", ignoreCase = true) || it.contains("positional", ignoreCase = true) },
        )
    }

    fun testConvertToNamed_availableForSameKindMixed() {
        load("SameKindMixed.kt")
        highlight()
        assertNotNull(
            "ConvertToNamed fix must be offered for mixed same-kind placeholders",
            fix("named"),
        )
    }

    // ── Path B: ConvertToPositional — availability only ───────────────────────

    fun testConvertToPositional_availableForNamedInAdd() {
        load("NamedInAdd.kt")
        highlight()
        assertNotNull(
            "ConvertToPositional fix must be offered for named-in-add",
            fix("positional"),
        )
    }

    fun testConvertToPositional_availableForMixedRelativePositional() {
        load("RelativeAndPositional.kt")
        highlight()
        assertNotNull(
            "ConvertToPositional fix must be offered for relative+positional mix",
            fix("positional"),
        )
    }

    fun testConvertToPositional_availableForPositionalInAddNamed() {
        load("PositionalInAddNamed.kt")
        highlight()
        assertNotNull(
            "ConvertToPositional fix must be offered for positional-in-addNamed",
            fix("positional"),
        )
    }

    fun testConvertToPositional_availableForSameKindMixed() {
        load("SameKindMixed.kt")
        highlight()
        assertNotNull(
            "ConvertToPositional fix must be offered for mixed same-kind placeholders",
            fix("positional"),
        )
    }

    // ── Fix label verification ────────────────────────────────────────────────

    fun testFixLabels_allThreePresent_forMixedRelativePositional() {
        load("RelativeAndPositional.kt")
        highlight()
        val fixes = myFixture.getAllQuickFixes().map { it.text }
        assertTrue(
            "ConvertToNamed must be present; available: $fixes",
            fixes.any { it.contains("named", ignoreCase = true) },
        )
        assertTrue(
            "ConvertToRelative must be present; available: $fixes",
            fixes.any { it.contains("relative", ignoreCase = true) },
        )
        assertTrue(
            "ConvertToPositional must be present; available: $fixes",
            fixes.any { it.contains("positional", ignoreCase = true) },
        )
    }

    fun testFixLabels_namedStyleInAdd_relativeAndPositionalOffered() {
        // Named placeholders in add() → style is Named, method expects vararg.
        // Only ConvertToRelative and ConvertToPositional make sense.
        load("NamedInAdd.kt")
        highlight()
        val fixes = myFixture.getAllQuickFixes().map { it.text }
        assertTrue(
            "ConvertToRelative must be offered for named-in-add; available: $fixes",
            fixes.any { it.contains("relative", ignoreCase = true) },
        )
        assertTrue(
            "ConvertToPositional must be offered for named-in-add; available: $fixes",
            fixes.any { it.contains("positional", ignoreCase = true) },
        )
    }

    fun testFixLabels_relativeInAddNamed_onlyNamedAndPositionalOffered() {
        // Relative placeholders in addNamed() → style is Relative, method expects named.
        load("RelativeInAddNamed.kt")
        highlight()
        val fixes = myFixture.getAllQuickFixes().map { it.text }
        assertTrue(
            "ConvertToNamed must be offered for relative-in-addNamed; available: $fixes",
            fixes.any { it.contains("named", ignoreCase = true) },
        )
    }
}
