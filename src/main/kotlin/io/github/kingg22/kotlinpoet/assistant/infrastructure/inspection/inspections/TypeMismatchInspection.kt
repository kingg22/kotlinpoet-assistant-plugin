package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.validation.validators.TypeMismatchValidator
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.AbstractKotlinPoetInspection
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Reports when an argument's runtime type is incompatible with the placeholder kind it is mapped
 * to (e.g. passing a `Boolean` where `%S` expects a `String`/`CharSequence`).
 *
 * Level: varies per placeholder kind (WARNING for `%T`, `%N`, `%M`; INFORMATION for `%S`/`%P`).
 *
 * No automatic fix is offered because the correct replacement type cannot be inferred.
 */
class TypeMismatchInspection : AbstractKotlinPoetInspection() {

    private val validator = TypeMismatchValidator()

    @Nls
    override fun getDisplayName(): String = KPoetAssistantBundle.getMessage("inspection.type.mismatch.display.name")

    override fun checkCall(expression: KtCallExpression, analysis: KotlinPoetAnalysis, holder: ProblemsHolder) {
        validator.validate(analysis.bounds, analysis.argumentSource).forEach { problem ->
            problem.register(expression, holder)
        }
    }
}
