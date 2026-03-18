package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle

/**
 * Fixes a dangling `%` by escaping it as `%%`.
 *
 * The `%%` control symbol is KotlinPoet's way to emit a literal percent sign without treating
 * it as the start of a placeholder.
 */
class EscapePercentQuickFix : LocalQuickFix {
    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.format.escape.percent")

    override fun getFamilyName(): String = KPoetAssistantBundle.getMessage("inspection.group.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        replaceInDocument(project, descriptor, replacement = "%%")
    }
}
