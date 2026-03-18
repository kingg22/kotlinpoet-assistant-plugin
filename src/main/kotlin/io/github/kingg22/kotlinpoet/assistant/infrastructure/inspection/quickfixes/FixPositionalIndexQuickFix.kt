package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle

/**
 * Replaces an invalid positional index (< 1) with 1, e.g. `%0L` → `%1L`.
 *
 * KotlinPoet requires positional indices to be 1-based. An index of 0 (or any value < 1) causes
 * an [IllegalArgumentException] at runtime. This fix rewrites the entire token using index 1 and
 * preserves the original format character.
 *
 * @param formatChar The placeholder character extracted by the parser (e.g. `L`, `S`, `T`).
 */
class FixPositionalIndexQuickFix(private val formatChar: Char) : LocalQuickFix {
    override fun getName(): String = KPoetAssistantBundle.getMessage("quickfix.format.fix.positional.index", formatChar)

    override fun getFamilyName(): String = KPoetAssistantBundle.getMessage("inspection.group.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        replaceInDocument(project, descriptor, replacement = "%1$formatChar")
    }
}
