package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parentOfType
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

// ─── Root cause of the original crash ────────────────────────────────────────
//
// TemplateBuilderImpl anchors itself to the document via a RangeMarker created at construction
// time.  Calling element.replace() inside a write-action invalidates the original PSI node,
// making its RangeMarker stale.  Invoking builder.run() on that stale builder triggers:
//
//   "file: KtFile … container: RangeMarker(795,815) 526 markers: [[]19, 19]"
//
// Fix: split into TWO separate EDT phases:
//   Phase 1 (write-action)  – rewrite the format string text, commit document.
//   Phase 2 (invokeLater)   – find the FRESH element by the known document offset,
//                              build a NEW TemplateBuilderImpl on that element, then run.
//
// The fresh element is found by calling call.valueArguments[0].getArgumentExpression() again
// AFTER the write-action, because the KtCallExpression node is stable (only its first child
// was replaced). This guarantees the builder is always anchored to a live element.
// ─── Bug fixes ────────────────────────────────────────────────────────────────
//
// BUG 1 — "Must not change document outside command or undo-transparent action"
//   TemplateBuilderImpl.run() calls document.deleteString() internally, which requires
//   a Command context, not just a bare runWriteAction.
//   Fix: use WriteCommandAction.runWriteCommandAction() for both the PSI replace phase
//   AND the builder.run() call.
//
// BUG 2 — "Analysis is not allowed: Called in the EDT thread"
//   getCachedAnalysis(call) in applyFix can re-trigger the CachedValueProvider if the
//   cached value expired.  That provider invokes the extractor which uses Kotlin Analysis
//   API — prohibited on the EDT without @KaAllowAnalysisOnEdt / allowAnalysisOnEdt.
//   Fix: placeholders are injected into every quick-fix constructor at registration time
//   (inside the inspection, where the analysis is already valid and available).
//   applyFix never calls getCachedAnalysis.

/**
 * Base for all mixed-style conversion quick fixes.
 *
 * **Key design decision**: [placeholders] are captured at fix-construction time by the
 * inspection and never re-fetched from the analysis cache inside [applyFix].
 * This eliminates the EDT Kotlin-Analysis-API violation.
 *
 * Two-phase lifecycle (still required to avoid stale TemplateBuilderImpl RangeMarkers):
 *  - Phase 1 ([WriteCommandAction]): [buildRewrite] → replace PSI node → commit document.
 *  - Phase 2 (`invokeLater`): re-fetch fresh element from the stable call node, call [afterRewrite].
 */
abstract class AbstractMixedStyleFix(protected val placeholders: List<PlaceholderSpec>) : LocalQuickFix {

    final override fun getFamilyName(): String = KPoetAssistantBundle.getMessage("inspection.group.name")

    final override fun startInWriteAction(): Boolean = false

    final override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? KtCallExpression
            ?: descriptor.psiElement.parentOfType<KtCallExpression>()
            ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return

        val formatArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
            as? KtStringTemplateExpression ?: return

        val (newText, anchors) = buildRewrite(project, call, formatArg) ?: return

        // Phase 1: rewrite inside a named command so Undo works correctly.
        WriteCommandAction.runWriteCommandAction(project, name, familyName, {
            val factory = KtPsiFactory(project)
            formatArg.replace(factory.createExpression(newText))
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        })

        // Phase 2: invokeLater ensures the PSI tree has been fully updated before we
        // build a new TemplateBuilderImpl on the replacement node.
        ApplicationManager.getApplication().invokeLater {
            val freshFormatArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
                as? KtStringTemplateExpression ?: return@invokeLater
            afterRewrite(project, editor, call, freshFormatArg, anchors)
        }
    }

    /**
     * Pure function: compute the new expression text and the [TemplateAnchor]s.
     * No PSI mutation. Return `null` to abort.
     */
    protected abstract fun buildRewrite(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): Pair<String, List<TemplateAnchor>>?

    /**
     * Called in Phase 2 with the fresh (post-replace) PSI element.
     * Default: open [TemplateBuilderImpl] if [anchors] is non-empty.
     */
    protected open fun afterRewrite(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        freshFormatArg: KtStringTemplateExpression,
        anchors: List<TemplateAnchor>,
    ) {
        if (anchors.isEmpty()) return
        val builder = TemplateBuilderImpl(freshFormatArg)
        anchors.forEach { a ->
            builder.replaceRange(a.rangeInElement, a.variableName, ConstantNode(a.defaultValue), true)
        }
        // builder.run() modifies the document — must be inside a Command.
        WriteCommandAction.runWriteCommandAction(project, name, familyName, {
            builder.run(editor, true)
        })
    }

    /**
     * Describes one editable region in the rewritten format string that becomes a live-template
     * variable.
     *
     * @param rangeInElement Range relative to [KtStringTemplateExpression.getText] (NOT the document).
     * @param variableName   Unique variable name inside the template.
     * @param defaultValue   Text pre-filled in the field; user overwrites it.
     */
    data class TemplateAnchor(val rangeInElement: TextRange, val variableName: String, val defaultValue: String)
}
