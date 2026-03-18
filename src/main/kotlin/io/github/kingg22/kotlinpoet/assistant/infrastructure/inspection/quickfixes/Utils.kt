package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Replaces the text in the editor that corresponds to [descriptor.textRangeInElement] (relative
 * to [descriptor.psiElement]) with [replacement].
 *
 * Uses the [com.intellij.openapi.editor.Document] API so the edit works regardless of the
 * internal structure of the KtStringTemplateExpression (plain strings, raw strings, templates).
 */
fun replaceInDocument(project: Project, descriptor: ProblemDescriptor, replacement: String) {
    val element = descriptor.psiElement
    val file = element.containingFile ?: return
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
    val relRange = descriptor.textRangeInElement
    val startAbs = element.textRange.startOffset + relRange.startOffset
    val endAbs = element.textRange.startOffset + relRange.endOffset
    document.replaceString(startAbs, endAbs, replacement)
    PsiDocumentManager.getInstance(project).commitDocument(document)
}

/**
 * Applies [transforms] to the text of [formatArg] in reverse document order (highest offset
 * first) so earlier offsets remain valid while later tokens are replaced.
 *
 * Each triple is `(absoluteStart, absoluteEnd, replacementText)` where start/end are absolute
 * document offsets inside the file.
 *
 * @return The new expression text including surrounding quotes.
 */
fun applyReversedTransforms(
    formatArg: KtStringTemplateExpression,
    transforms: List<Triple<Int, Int, String>>,
): String {
    val fileStart = formatArg.textRange.startOffset
    var text = formatArg.text // e.g. `"%L %S"` or `"""%L"""`

    transforms
        .sortedByDescending { it.first }
        .forEach { (absStart, absEnd, replacement) ->
            val relStart = absStart - fileStart
            val relEnd = absEnd - fileStart
            if (relStart < 0 || relEnd > text.length) return@forEach
            text = text.substring(0, relStart) + replacement + text.substring(relEnd)
        }
    return text
}
