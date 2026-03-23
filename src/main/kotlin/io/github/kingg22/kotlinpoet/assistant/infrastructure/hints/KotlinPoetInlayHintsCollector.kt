package io.github.kingg22.kotlinpoet.assistant.infrastructure.hints

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.SpacePresentation
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.chain.ContributionAnalyzer
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodEmissionContribution
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemantics
import io.github.kingg22.kotlinpoet.assistant.domain.chain.renderChain
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator
import org.jetbrains.kotlin.psi.KtCallExpression
import java.awt.Cursor

@Suppress("UnstableApiUsage")
class KotlinPoetInlayHintsCollector(editor: Editor, private val settings: KotlinPoetHintsSettings) :
    FactoryInlayHintsCollector(editor) {

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element !is KtCallExpression) return true

        with(GeneratedCodeInlayFactory(factory, editor)) {
            if (settings.showPerCallHints) {
                val contribution = ContributionAnalyzer.analyze(element) ?: return true
                addPerCallHint(element, contribution, sink)
            }

            if (settings.showChainPreview) {
                addChainPreviewHint(element, sink)
            }
        }

        return true
    }

    // ── Per-call EOL hint ──────────────────────────────────────────────────

    context(_: GeneratedCodeInlayFactory)
    private fun addPerCallHint(
        call: KtCallExpression,
        contribution: MethodEmissionContribution,
        sink: InlayHintsSink,
    ) {
        when (contribution.semantics) {
            MethodSemantics.StartBuilder, MethodSemantics.TerminalCall,
            MethodSemantics.IndentCall, MethodSemantics.UnindentCall,
            is MethodSemantics.UnknownCall,
            -> return

            else -> {}
        }

        val approx = renderChain(contribution)
            .cappedAt(settings.maxLineLength)
            .replace("\n", "↵")
            .replace("⇥", "→")
            .replace("⇤", "←")
        if (approx.isBlank()) return

        val offset = call.textRange.endOffset

        val inlay = code(approx)
            .asSmallInlayAlignedToTextLine()
            .withButtons()
            .withTooltip(KPoetAssistantBundle.getMessage("inlay.hints.per.call.tooltip"))

        sink.addInlineElement(
            offset = offset,
            relatesToPrecedingText = true,
            presentation = inlay,
            placeAtTheEndOfLine = true,
        )
    }

    // ── Chain preview collapsible block hint ───────────────────────────────

    context(_: GeneratedCodeInlayFactory)
    private fun addChainPreviewHint(call: KtCallExpression, sink: InlayHintsSink) {
        val chain = CodeBlockPsiNavigator.findChain(call)
        if (chain.isEmpty()) return
        val contributions = chain.mapNotNull { callExp ->
            val contribution = ContributionAnalyzer.analyze(callExp) ?: return@mapNotNull null
            contribution to callExp
        }
        if (contributions.isEmpty()) return

        val lastInChain = contributions.lastOrNull { (contribution) ->
            contribution.semantics is MethodSemantics.TerminalCall
        }?.second ?: return

        val preview = renderChain(contributions.map { it.first }).ifBlank { return }
        val allLines = preview.lines()
        val displayLines = allLines.take(settings.maxPreviewLines)
        val offset = lastInChain.textRange.startOffset

        // Build expanded presentation: one smallText per line, stacked vertically.
        // smallText already applies INLAY_DEFAULT styling (font + color).
        val suffixHint = createIcon(AllIcons.Actions.Preview)
            .withHoverDoRoundBackground()
            .withCursorOnHover(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
            .withSmallInset()

        val expanded = displayLines.map { code(it.cappedAt(settings.maxLineLength)) }
            .vertical()
            .asSmallInlayAlignedToTextLine()

        // Collapsed state: show line count so the user knows what to expand.
        val collapsed = code("${displayLines.size} line(s). Click to see KotlinPoet preview")
            .asSmallInlayAlignedToTextLine()

        val collapsableHint = factory
            .collapsible(
                prefix = SpacePresentation(0, 0),
                collapsed = collapsed,
                expanded = { expanded },
                suffix = suffixHint,
                startWithPlaceholder = !settings.showChainPreviewExpanded,
            )
            .withButtons()
            .withInsetOfButtons()
            .indentedAsElementInEditorAtOffset(
                offset = offset,
                shiftLeftToInset = true,
            ).withTooltip(KPoetAssistantBundle.getMessage("inlay.hints.chain.tooltip"))

        sink.addBlockElement(
            offset = offset,
            relatesToPrecedingText = true,
            showAbove = false,
            priority = 0,
            collapsableHint,
        )
    }
}

// FIXME improve UI of this
private fun String.cappedAt(max: Int): String = if (length > max) "${take(max)}…" else this
