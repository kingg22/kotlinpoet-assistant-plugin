package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.MissingArgumentValidator
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.AbstractKotlinPoetInspection
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Reports when a KotlinPoet format string has a placeholder (e.g. `%L`) with no corresponding
 * argument supplied to the call, which would cause an [IllegalArgumentException] at runtime.
 *
 * Level: ERROR — no automatic fix is possible because the correct argument value is unknown.
 */
class MissingArgumentInspection : AbstractKotlinPoetInspection() {

    private val validator = MissingArgumentValidator()

    @Nls
    override fun getDisplayName(): String = KPoetAssistantBundle.getMessage("inspection.missing.argument.display.name")

    override fun checkCall(expression: KtCallExpression, analysis: KotlinPoetAnalysis, holder: ProblemsHolder) {
        validator.validate(analysis.bounds, analysis.argumentSource).forEach { problem ->
            problem.register(expression, holder)
        }
    }
}
