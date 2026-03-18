package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Removes a surplus argument from a KotlinPoet call by its 0-based index in
 * [org.jetbrains.kotlin.psi.KtCallExpression.valueArguments].
 *
 * The index is determined at inspection time by matching the domain-level [TextSpan][io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan]
 * of the extra argument back to its concrete [org.jetbrains.kotlin.psi.KtValueArgument] in the
 * PSI tree. See [KotlinPoetExtraArgumentInspection.buildFix][io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.inspections.ExtraArgumentInspection].
 *
 * [KtValueArgumentList.removeArgument] is used so that the surrounding comma is removed cleanly.
 */
class RemoveExtraArgumentQuickFix(private val argPsiIndex: Int) : LocalQuickFix {

    @Nls
    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.remove.extra.argument")

    @Nls
    override fun getFamilyName(): String = KPoetAssistantBundle.getMessage("inspection.group.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? KtCallExpression ?: return
        val argList = call.valueArgumentList ?: return
        val toRemove = argList.arguments.getOrNull(argPsiIndex) ?: return
        argList.removeArgument(toRemove)
    }
}
