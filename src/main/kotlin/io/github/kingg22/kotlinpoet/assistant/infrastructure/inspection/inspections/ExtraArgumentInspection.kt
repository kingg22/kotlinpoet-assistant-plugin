package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.ExtraArgumentValidator
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.AbstractKotlinPoetInspection
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.RemoveExtraArgumentQuickFix
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRanges
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Reports arguments that are passed to a KotlinPoet call but have no matching placeholder in the
 * format string, which would cause an [IllegalArgumentException] at runtime.
 *
 * Level: ERROR for positional/relative surplus arguments.
 * Level: INFORMATION for named surplus keys (the map may be shared/reused elsewhere).
 *
 * Quick fix: [RemoveExtraArgumentQuickFix] (only when the extra argument can be pinpointed in the
 * PSI argument list).
 */
class ExtraArgumentInspection : AbstractKotlinPoetInspection() {

    private val validator = ExtraArgumentValidator()

    @Nls
    override fun getDisplayName(): String = KPoetAssistantBundle.getMessage("inspection.extra.argument.display.name")

    override fun checkCall(expression: KtCallExpression, analysis: KotlinPoetAnalysis, holder: ProblemsHolder) {
        validator.validate(analysis.bounds, analysis.argumentSource).forEach { problem ->
            val fix = buildFix(expression, problem.target)
            problem.register(expression, holder, *fix)
        }
    }

    /**
     * Tries to match the problem's target [TextSpan][io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan]
     * back to a concrete [org.jetbrains.kotlin.psi.KtValueArgument] in the PSI argument list, so
     * [RemoveExtraArgumentQuickFix] knows which argument to delete.
     */
    private fun buildFix(expression: KtCallExpression, target: ProblemTarget): Array<RemoveExtraArgumentQuickFix> {
        val span = (target as? ProblemTarget.Argument)?.span ?: return emptyArray()
        val ranges = span.toTextRanges()
        val argIndex = expression.valueArguments.indexOfFirst { arg ->
            ranges.any { range -> arg.textRange.intersects(range) }
        }
        return if (argIndex >= 0) arrayOf(RemoveExtraArgumentQuickFix(argIndex)) else emptyArray()
    }
}
