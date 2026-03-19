package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
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

/**
 * Builds [AbstractMixedStyleFix.TemplateAnchor]s for all [placeholders] inside [formatArg],
 * pairing each with its [defaultValues] and [variableNames] by index.
 *
 * ## Strategy
 *
 * Mirrors the map-entry template approach that works correctly: locate each token with
 * [String.indexOf] directly on `formatArg.text` and use that position as-is for
 * [com.intellij.codeInsight.template.TemplateBuilderImpl.replaceRange]. No PSI entry arithmetic or offset translation needed.
 *
 * [com.intellij.codeInsight.template.TemplateBuilderImpl.replaceRange] calls
 * `rangeWithinElement.shiftRight(myContainerElement.getStartOffset())` internally, so the
 * range must be relative to `formatArg.text` exactly as [String.indexOf] produces.
 *
 * ## Multi-occurrence handling
 *
 * When the same token appears more than once (e.g. `"%L %L"`), each successive search
 * starts from the end of the previous match so that anchors are assigned left-to-right
 * in the order [placeholders] are provided.
 *
 * Returns `null` for a given placeholder if its token cannot be found at or after the
 * current search position (e.g., multi-segment span or token split across entries).
 */
fun buildAnchorsForPlaceholders(
    placeholders: List<PlaceholderSpec>,
    formatArg: KtStringTemplateExpression,
    defaultValues: List<String>,
    variableNames: List<String>,
): List<AbstractMixedStyleFix.TemplateAnchor> {
    check(placeholders.size == defaultValues.size && placeholders.size == variableNames.size) {
        "Placeholder lists with variables and default values must be the same size, " +
            "got ${placeholders.size} placeholders with ${variableNames.size} variables and ${defaultValues.size} default values"
    }

    val elementText = formatArg.text
    var searchFrom = 0

    return placeholders.mapIndexedNotNull { idx, placeholder ->
        if (placeholder.span.singleRangeOrNull() == null) return@mapIndexedNotNull null

        val tokenText = placeholder.tokenText()
        val pos = elementText.indexOf(tokenText, searchFrom)
        if (pos < 0) return@mapIndexedNotNull null

        searchFrom = pos + tokenText.length

        AbstractMixedStyleFix.TemplateAnchor(
            TextRange(pos, pos + tokenText.length),
            variableNames[idx],
            defaultValues[idx],
        )
    }
}
