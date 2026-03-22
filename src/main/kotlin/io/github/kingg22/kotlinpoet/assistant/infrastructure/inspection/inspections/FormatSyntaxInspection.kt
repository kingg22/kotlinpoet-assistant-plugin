package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.FormatStyle
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.FormatStyle.Named
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.FormatStyle.Positional
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.FormatStyle.Relative
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.ParserIssueKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.ParserIssueKind.DANGLING_PERCENT
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.ParserIssueKind.INVALID_POSITIONAL_INDEX
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.ParserIssueKind.MIXED_STYLES
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.ParserIssueKind.UNKNOWN_PLACEHOLDER_TYPE
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.AbstractKotlinPoetInspection
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.ConvertToNamedPlaceholderQuickFix
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.ConvertToPositionalPlaceholderQuickFix
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.ConvertToRelativePlaceholderQuickFix
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.EscapePercentQuickFix
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.FixPositionalIndexQuickFix
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.RemoveFormatTokenQuickFix
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Surfaces all parser-level blocking errors from [FormatStringModel.errors][io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.errors].
 *
 * Every entry in `errors` carries a [ParserIssueKind] in [FormatProblem.data][io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem.data].
 * This inspection dispatches quick fixes based on that tag:
 *
 * | [ParserIssueKind]          | Quick fixes                                              |
 * |----------------------------|----------------------------------------------------------|
 * | [DANGLING_PERCENT]           | Escape as `%%` · Remove token                           |
 * | [UNKNOWN_PLACEHOLDER_TYPE]   | Remove token                                             |
 * | [INVALID_POSITIONAL_INDEX]   | Fix index to 1 (preserves format char)                   |
 * | [MIXED_STYLES]               | Convert to named (with live template) · Convert relative |
 *
 * **Note**: this inspection is an exception to the usual [AbstractKotlinPoetInspection] pattern.
 * It does NOT skip when `haveFormatProblems` — it reads *from* `format.errors` and is the primary
 * surface for those errors. It extends [AbstractKotlinPoetInspection] only to reuse
 * [AbstractKotlinPoetInspection.register].
 */
class FormatSyntaxInspection : AbstractKotlinPoetInspection() {

    @Nls
    override fun getDisplayName(): String = KPoetAssistantBundle.getMessage("inspection.format.syntax.display.name")

    override fun checkCall(expression: KtCallExpression, analysis: KotlinPoetAnalysis, holder: ProblemsHolder) {
        // Read from format.errors — these are the blocking parser issues.
        // (The base class already skipped if haveFormatProblems, so we override that guard below.)
        analysis.format.errors.forEach { problem ->
            val fixes = when (problem.kind) {
                DANGLING_PERCENT -> arrayOf(EscapePercentQuickFix(), RemoveFormatTokenQuickFix())

                UNKNOWN_PLACEHOLDER_TYPE -> arrayOf(RemoveFormatTokenQuickFix())

                INVALID_POSITIONAL_INDEX -> {
                    // INVALID_POSITIONAL_INDEX with format char payload
                    val formatChar = problem.data as Char
                    arrayOf(FixPositionalIndexQuickFix(formatChar))
                }

                MIXED_STYLES -> buildMixFixes(
                    analysis.formatStyle,
                    problem.data as FormatStyle,
                    analysis.format.placeholders,
                    analysis.argumentSource as? ArgumentSource.NamedMap?,
                )

                null -> emptyArray()
            }
            problem.register(expression, holder, fixes)
        }
    }

    /**
     * Selects conversion quick fixes based on [actualStyle], the style detected in the format string
     *
     * [placeholders] are passed to each fix constructor so they don't need to re-fetch analysis.
     */
    private fun buildMixFixes(
        actualStyle: FormatStyle,
        targetStyle: FormatStyle,
        placeholders: List<PlaceholderSpec>,
        namedArgument: ArgumentSource.NamedMap?,
    ): Array<out LocalQuickFix> {
        val toNamed = ConvertToNamedPlaceholderQuickFix(placeholders, namedArgument)
        val toRelative = ConvertToRelativePlaceholderQuickFix(placeholders)
        val toPositional = ConvertToPositionalPlaceholderQuickFix(placeholders)

        return when (actualStyle to targetStyle) {
            // Format has named placeholders but method expects vararg style.
            // Offer: convert away from named → relative or positional.
            Named to Any() -> arrayOf(toRelative, toPositional)

            // Format has only relative placeholders.
            // Offer: add named structure or switch to positional.
            Relative to Any() -> arrayOf(toNamed, toPositional)

            // Format needs to target Named.
            Relative to Named -> arrayOf(toNamed)

            Positional to Named -> arrayOf(toNamed)

            // Format has only positional placeholders.
            // Offer: add named structure or strip to relative.
            Positional to Any() -> arrayOf(toNamed, toRelative)

            // True mix (multiple styles coexisting) — all conversions are valid targets.
            else -> arrayOf(toNamed, toRelative, toPositional)
        }
    }
}
