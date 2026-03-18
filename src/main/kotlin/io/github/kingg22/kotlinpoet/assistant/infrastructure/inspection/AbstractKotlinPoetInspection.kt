package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.putCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRanges
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Base class for all KotlinPoet local inspections.
 *
 * Handles the boilerplate of:
 * - Visiting [KtCallExpression] nodes
 * - Getting/updating the cached [KotlinPoetAnalysis]
 * - Skipping calls that have format-level parse errors (those are shown by the annotator)
 * - Guarding against text-range-out-of-bounds when registering problems
 * - Mapping [ProblemSeverity] to [ProblemHighlightType]
 *
 * Subclasses implement [checkCall] to run their specific [io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatValidator]
 * and register problems via [FormatProblem.register].
 */
abstract class AbstractKotlinPoetInspection :
    LocalInspectionTool(),
    DumbAware {

    protected val logger: Logger = thisLogger()

    final override fun getGroupDisplayName(): String = KPoetAssistantBundle.getMessage("inspection.group.name")

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                try {
                    var analysis = getCachedAnalysis(expression) ?: return

                    // Format-level parse errors are handled by KotlinPoetAnnotator; skip here.
                    if (analysis.haveFormatProblems) return

                    analysis = analysis.bind()
                    putCachedAnalysis(expression, analysis)

                    checkCall(expression, analysis, holder)
                } catch (e: Exception) {
                    if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
                    logger.warn("Error in ${this@AbstractKotlinPoetInspection::class.simpleName}", e)
                }
            }
        }

    /** Subclasses run their validator here and call [FormatProblem.register] for each problem. */
    protected abstract fun checkCall(
        expression: KtCallExpression,
        analysis: KotlinPoetAnalysis,
        holder: ProblemsHolder,
    )

    /**
     * Registers this [FormatProblem] on [expression] with optional [fixes].
     *
     * The text range guard mirrors the one in [KotlinPoetAnnotator][io.github.kingg22.kotlinpoet.assistant.infrastructure.annotator.KotlinPoetAnnotator]:
     * instead of comparing against the file length (annotator scope), here we validate against the
     * [KtCallExpression]'s own range, then convert to a range *relative* to the element as required
     * by [ProblemsHolder.registerProblem].
     */
    protected fun FormatProblem.register(
        expression: KtCallExpression,
        holder: ProblemsHolder,
        vararg fixes: LocalQuickFix,
    ) {
        val highlightType = severity.toHighlightType()

        when (target) {
            ProblemTarget.Call -> {
                // Highlight the entire call expression.
                holder.registerProblem(expression, message, highlightType, *fixes)
            }

            else -> {
                val absRanges = target.toTextRanges(expression)
                if (absRanges.isEmpty()) {
                    holder.registerProblem(expression, message, highlightType, *fixes)
                    return
                }
                for (absRange in absRanges) {
                    // Guard: range must be fully inside the KtCallExpression.
                    if (absRange.startOffset < expression.textRange.startOffset ||
                        absRange.endOffset > expression.textRange.endOffset
                    ) {
                        logger.warn(
                            "Text range out of bounds: $absRange for expression ${expression.textRange}, " +
                                "problem: $this",
                        )
                        continue
                    }
                    val relativeRange = TextRange(
                        absRange.startOffset - expression.textRange.startOffset,
                        absRange.endOffset - expression.textRange.startOffset,
                    )
                    val descriptor = holder.manager.createProblemDescriptor(
                        expression,
                        relativeRange,
                        message,
                        highlightType,
                        holder.isOnTheFly,
                        *fixes,
                    )
                    holder.registerProblem(descriptor)
                }
            }
        }
    }

    private fun ProblemSeverity.toHighlightType(): ProblemHighlightType = when (this) {
        ProblemSeverity.ERROR -> ProblemHighlightType.ERROR
        ProblemSeverity.WARNING -> ProblemHighlightType.WARNING
        ProblemSeverity.INFORMATION -> ProblemHighlightType.INFORMATION
    }
}
