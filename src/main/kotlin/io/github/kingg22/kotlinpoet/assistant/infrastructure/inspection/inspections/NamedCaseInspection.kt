package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.NamedArgumentCaseValidator
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.AbstractKotlinPoetInspection
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes.RenameToLowercaseQuickFix
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Reports named argument keys that do not match KotlinPoet's required lowercase pattern
 * (`[a-z]+[\w_]*`). Passing such a key to `addNamed` causes an [IllegalArgumentException] at
 * runtime.
 *
 * Level: WARNING — the quick fix [RenameToLowercaseQuickFix] renames both the map key and the
 * matching placeholder in the format string.
 *
 * Note: The logic for detecting invalid keys is intentionally duplicated from
 * [NamedArgumentCaseValidator] so each problem can be paired with its concrete [original name →
 * fix] at registration time, without leaking quick-fix concerns into the domain layer.
 */
class NamedCaseInspection : AbstractKotlinPoetInspection() {
    private val validator = NamedArgumentCaseValidator()

    @Nls
    override fun getDisplayName(): String = KPoetAssistantBundle.getMessage("inspection.named.case.display.name")

    override fun checkCall(expression: KtCallExpression, analysis: KotlinPoetAnalysis, holder: ProblemsHolder) {
        validator.validate(analysis.bounds, analysis.argumentSource).forEach { problem ->
            problem.register(expression, holder, arrayOf(RenameToLowercaseQuickFix(problem.data as String)))
        }
    }
}
