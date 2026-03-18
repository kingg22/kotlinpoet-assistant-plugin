package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle

/**
 * Removes the problematic format token entirely (dangling `%` or unknown type like `%Z`).
 *
 * Use this when the token has no valid meaning and the user does not want to escape it.
 */
class RemoveFormatTokenQuickFix : LocalQuickFix {
    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.format.remove.token")

    override fun getFamilyName(): String = KPoetAssistantBundle.getMessage("inspection.group.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        replaceInDocument(project, descriptor, replacement = "")
    }
}
