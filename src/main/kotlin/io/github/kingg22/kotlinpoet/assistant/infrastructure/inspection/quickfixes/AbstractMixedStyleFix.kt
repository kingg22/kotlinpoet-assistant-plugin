package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateEditingListener
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
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

/**
 * Base for all mixed-style conversion quick fixes.
 *
 * ## Two execution paths
 *
 * ### Path A — direct PSI replace ([buildRewrite] returns non-null)
 * 1. [buildRewrite] returns the new expression text.
 * 2. [applyFix] replaces the PSI node and commits the document inside a [WriteCommandAction].
 * 3. [afterRewrite] is called with `committed = true` for any post-replacement work (e.g., caret).
 *
 * ### Path B — live template ([buildRewrite] returns null)
 * 1. [buildRewrite] returns `null`; [buildAnchors] returns the template variables.
 * 2. [applyFix] builds a [TemplateBuilderImpl] anchored on the **original** `formatArg` node
 *    (no PSI replace, no document commit) and starts the interactive session inside a
 *    [WriteCommandAction]: `startTemplate(editor, builder.buildInlineTemplate(), listener)`.
 *    The template engine itself deletes the anchor ranges and inserts the default values.
 * 3. [afterRewrite] is called from the [TemplateEditingListener] after the user finishes
 *    (`committed = true`) or cancels (`committed = false`).
 *
 * **Key design decision**: [placeholders] are captured at fix-construction time so [applyFix]
 * never calls `getCachedAnalysis` — Kotlin Analysis API is prohibited on the EDT.
 */
@Suppress("DialogTitleCapitalization")
abstract class AbstractMixedStyleFix(protected val placeholders: List<PlaceholderSpec>) : LocalQuickFix {

    final override fun getFamilyName(): String = KPoetAssistantBundle.getMessage("inspection.group.name")

    final override fun startInWriteAction(): Boolean = false

    final override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement.parentOfType<KtCallExpression>(withSelf = true) ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return

        val formatArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
            as? KtStringTemplateExpression ?: return

        val newText = buildRewrite(project, call, formatArg)

        if (newText != null) {
            // Path A: deterministic text rewrite, no template needed.
            WriteCommandAction.runWriteCommandAction(project, name, familyName, {
                val factory = KtPsiFactory(project)
                formatArg.replace(factory.createExpression(newText))
                PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            })
            val freshFormatArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
                as? KtStringTemplateExpression ?: return
            afterRewrite(project, editor, call, freshFormatArg, committed = true)
        } else {
            // Path B: live template — anchors point to ranges in the ORIGINAL formatArg.
            // No PSI replace, no document commit. The template engine handles all edits.
            val anchors = buildAnchors(project, call, formatArg).ifEmpty { return }

            val listener = buildTemplateListener(project, editor, call)

            val builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(formatArg) as TemplateBuilderImpl

            anchors.forEach { a ->
                builder.replaceRange(a.rangeInElement, a.variableName, ConstantNode(a.defaultValue), true)
            }

            WriteCommandAction.runWriteCommandAction(project, name, familyName, {
                // Capture the container start BEFORE buildInlineTemplate() mutates the document.
                // buildInlineTemplate() deletes the anchor ranges inside the container, but the
                // container's own start offset never moves (deletions are strictly inside it).
                val containerStart = formatArg.textRange.startOffset

                // Must move caret to container start before startTemplate — this is what
                // TemplateBuilderImpl.run() does internally and without it the template is
                // inserted at the wrong (current) caret position.
                editor.caretModel.moveToOffset(containerStart)
                TemplateManager
                    .getInstance(project)
                    .startTemplate(
                        editor,
                        builder.buildInlineTemplate(),
                        listener,
                    )
            })
        }
    }

    /**
     * Returns the new expression text for a **direct PSI replace** (Path A), or `null` to
     * signal that a **live template** should be used instead (Path B).
     *
     * When this returns non-null, [buildAnchors] is never called.
     */
    protected open fun buildRewrite(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): String? = null

    /**
     * Returns the template variables for the **live template** path (Path B).
     *
     * Only called when [buildRewrite] returns `null`.
     *
     * Each [TemplateAnchor] range must be **relative to [formatArg]** (the original, unmodified
     * node) and must cover the exact token to be replaced by the template engine (e.g. the full
     * `%L` or `%name:X` token). The template engine deletes these ranges and inserts
     * [TemplateAnchor.defaultValue] as the editable variable.
     *
     * Default: returns empty list (subclasses using Path B must override).
     */
    protected open fun buildAnchors(
        project: Project,
        call: KtCallExpression,
        formatArg: KtStringTemplateExpression,
    ): List<TemplateAnchor> = emptyList()

    /**
     * Called after the rewrite is complete.
     *
     * - **Path A**: called synchronously after PSI replace + commit, always `committed = true`.
     * - **Path B**: called from [TemplateEditingListener] when the user commits (`committed =
     *   true`) or cancels (`committed = false`).
     *
     * Default: no-op.
     */
    protected open fun afterRewrite(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
        freshFormatArg: KtStringTemplateExpression,
        committed: Boolean,
    ) {}

    private fun buildTemplateListener(
        project: Project,
        editor: Editor,
        call: KtCallExpression,
    ): TemplateEditingListener = object : TemplateEditingAdapter() {
        override fun templateFinished(template: Template, brokenOff: Boolean) {
            val freshFormatArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
                as? KtStringTemplateExpression ?: return
            afterRewrite(project, editor, call, freshFormatArg, committed = !brokenOff)
        }
    }

    /**
     * Describes one editable region that becomes a live-template variable.
     *
     * @param rangeInElement Range **relative** to the [KtStringTemplateExpression] text.
     *                       Must cover the exact token to replace (e.g. `%L`, `%name:X`).
     * @param variableName   Unique variable name inside the template.
     * @param defaultValue   Text pre-filled in the editable field.
     */
    data class TemplateAnchor(val rangeInElement: TextRange, val variableName: String, val defaultValue: String)
}
