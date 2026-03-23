package io.github.kingg22.kotlinpoet.assistant.infrastructure.toolwindow

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.TextRange
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.kingg22.kotlinpoet.assistant.domain.chain.ChainViolation
import io.github.kingg22.kotlinpoet.assistant.domain.chain.ContributionResolvability
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodEmissionContribution
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemantics
import io.github.kingg22.kotlinpoet.assistant.domain.chain.renderChain
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRange
import org.jetbrains.kotlin.psi.KtCallExpression
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent

/**
 * The Swing panel for the KotlinPoet Chain tool window.
 *
 * All mutations happen on the EDT (enforced by [com.intellij.openapi.application.NonBlockingReadAction.finishOnUiThread] in the scheduler).
 * The preview section uses [io.github.kingg22.kotlinpoet.assistant.domain.chain.renderChain] to produce properly indented output, visually
 * distinct from the per-call metadata rows via a different background and border.
 */
class CodeBlockChainPanel {

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getPanelBackground()
    }

    private val scrollPane = JBScrollPane(contentPanel).apply {
        border = BorderFactory.createEmptyBorder()
    }

    val component: JComponent = JPanel(BorderLayout()).also { root ->
        root.border = JBUI.Borders.empty(4)
        root.add(scrollPane, BorderLayout.CENTER)
    }

    fun showPlaceholder(message: String) {
        contentPanel.removeAll()
        contentPanel.add(
            JBLabel(message, SwingConstants.CENTER).apply {
                foreground = UIUtil.getLabelDisabledForeground()
                alignmentX = JComponent.CENTER_ALIGNMENT
                border = JBUI.Borders.empty(12)
            },
        )
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    internal fun showResult(result: ChainAnalysisResult, editor: Editor) {
        if (result.isEmpty) {
            showPlaceholder("Move cursor into a KotlinPoet builder call")
            return
        }

        contentPanel.removeAll()

        val validContributions = result.contributions.filterNotNull()

        for ((index, call) in result.calls.withIndex()) {
            val contribution = result.contributions.getOrNull(index)
            val violation = result.violations.firstOrNull { it.first == index }?.second
            val hasInspection = index in result.inspectionProblems
            contentPanel.add(buildCallRow(call, contribution, violation, hasInspection, editor))
        }

        if (result.finalState.isInStatement) {
            contentPanel.add(buildWarningRow("⚠ Unclosed statement at end of chain (missing »)"))
        }
        if (result.finalState.indentLevel > 0) {
            contentPanel.add(
                buildWarningRow(
                    "⚠ Unbalanced indent: ${result.finalState.indentLevel} level(s) still open",
                ),
            )
        }

        if (validContributions.isNotEmpty()) {
            contentPanel.add(buildSectionHeader("Preview (approximate):"))
            contentPanel.add(buildPreviewSection(validContributions))
        }

        if (result.inspectionProblems.isNotEmpty()) {
            contentPanel.add(
                buildWarningRow(
                    "⚠ ${result.inspectionProblems.size} call(s) have inspection errors — preview may be incomplete",
                ),
            )
        }

        contentPanel.revalidate()
        contentPanel.repaint()
    }

    // ── Row builders ───────────────────────────────────────────────────────────

    private fun buildCallRow(
        call: KtCallExpression,
        contribution: MethodEmissionContribution?,
        violation: ChainViolation?,
        hasInspection: Boolean,
        editor: Editor,
    ): JPanel {
        val bg = when {
            violation != null -> JBColor(0xFFEEEE, 0x4D2020)
            hasInspection -> JBColor(0xFFF8E1, 0x3D3010)
            else -> UIUtil.getPanelBackground()
        }
        val row = JPanel(BorderLayout()).apply {
            background = bg
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                    0,
                    0,
                    1,
                    0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
                ),
                JBUI.Borders.empty(3, 6),
            )
        }

        val methodName = contribution?.methodName ?: call.calleeExpression?.text ?: "?"
        val argumentList = if (call.valueArguments.isNotEmpty()) "..." else ""
        val tag = contribution?.semantics?.tag() ?: ""

        val leftPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(
                HyperlinkLabel(".$methodName($argumentList)  ").apply {
                    font = font.deriveFont(Font.BOLD)
                    addHyperlinkListener {
                        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                            navigateToSpan(
                                editor,
                                contribution?.callSpan?.singleRangeOrNull()?.toTextRange() ?: call.textRange,
                            )
                        }
                    }
                },
            )
            if (tag.isNotEmpty()) {
                add(
                    JBLabel("$tag  ").apply {
                        foreground = UIUtil.getLabelDisabledForeground()
                        font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
                    },
                )
            }
            if (hasInspection) {
                add(JBLabel("⚠  ").apply { foreground = JBColor.ORANGE })
            }
        }

        val rightText = when {
            violation != null -> "🔴 ${violation.describe()}"

            contribution != null -> contribution.approximateText()
                .take(100)
                .replace("\n", "↵")
                .replace("⇥", "→")
                .replace("⇤", "←")
                .let { if (it.length == 100) "$it…" else it }

            else -> "(not analyzed)"
        }
        val rightFg: Color = when {
            violation != null -> JBColor.RED
            contribution?.resolvability is ContributionResolvability.PartiallyResolved -> JBColor.ORANGE
            contribution?.resolvability is ContributionResolvability.Unresolvable -> JBColor.ORANGE
            else -> UIUtil.getLabelForeground()
        }

        row.add(leftPanel, BorderLayout.WEST)
        row.add(
            JBLabel(rightText).apply {
                foreground = rightFg
                font = JBUI.Fonts.create(Font.MONOSPACED, 11)
            },
            BorderLayout.CENTER,
        )

        return row
    }

    private fun buildWarningRow(message: String): JPanel = JPanel(BorderLayout()).apply {
        background = JBColor(0xFFF3CD, 0x3D2E00)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
            JBUI.Borders.empty(3, 6),
        )
        add(
            JBLabel(message).apply {
                foreground = JBColor(0x856404, 0xFFD54F)
            },
        )
    }

    private fun buildSectionHeader(title: String): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
            JBUI.Borders.empty(4, 6, 2, 6),
        )
        add(
            JBLabel(title).apply {
                font = font.deriveFont(Font.BOLD)
                foreground = UIUtil.getLabelDisabledForeground()
            },
        )
    }

    /**
     * Preview section: visually distinct from the per-call rows (different background,
     * monospaced font, border) so it is clear this is "generated output" not plugin UI.
     */
    private fun buildPreviewSection(contributions: List<MethodEmissionContribution>): JPanel {
        val renderedText = renderChain(contributions)
        val outerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 6, 6, 6)
        }
        val innerPanel = JPanel(BorderLayout()).apply {
            background = JBColor(0xF8F8F8, 0x1E1F22)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                JBUI.Borders.empty(6, 8),
            )
            add(
                JTextArea(renderedText.ifEmpty { "(empty)" }).apply {
                    isEditable = false
                    lineWrap = false
                    background = JBColor(0xF8F8F8, 0x1E1F22)
                    foreground = UIUtil.getLabelForeground()
                    font = JBUI.Fonts.create(Font.MONOSPACED, 12)
                    border = null
                },
            )
        }
        outerPanel.add(innerPanel)
        return outerPanel
    }
}

// ── Panel ──────────────────────────────────────────────────────────────────────

private fun navigateToSpan(editor: Editor, range: TextRange) {
    // Mueve el caret y hace scroll — no abre un nuevo editor
    editor.caretModel.moveToOffset(range.startOffset)
    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    editor.markupModel.addRangeHighlighter(
        CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES,
        range.startOffset,
        range.endOffset,
        HighlighterLayer.SELECTION,
        HighlighterTargetArea.EXACT_RANGE,
    ).also { highlighter ->
        // Auto-remove después de 1.5s
        EdtExecutorService
            .getScheduledExecutorInstance()
            .schedule(
                { editor.markupModel.removeHighlighter(highlighter) },
                1200,
                TimeUnit.MILLISECONDS,
            )
    }
}

// ── Display helpers ────────────────────────────────────────────────────────────

private fun MethodSemantics.tag(): String = when (this) {
    MethodSemantics.StatementCall -> "[stmt]"
    MethodSemantics.ControlFlowBegin -> "[begin]"
    MethodSemantics.ControlFlowNext -> "[next]"
    MethodSemantics.ControlFlowEnd -> "[end]"
    MethodSemantics.IndentCall -> "[indent]"
    MethodSemantics.UnindentCall -> "[unindent]"
    MethodSemantics.KdocCall -> "[kdoc]"
    MethodSemantics.TerminalCall -> "[build]"
    is MethodSemantics.UnknownCall -> "[?]"
    MethodSemantics.StartBuilder -> "[builder]"
    MethodSemantics.FormatCall -> ""
}

private fun ChainViolation.describe(): String = when (this) {
    is ChainViolation.DoubleOpenStatement -> "Statement already open (cannot nest «)"
    is ChainViolation.CloseWithoutOpenStatement -> "No open statement to close (unexpected »)"
    is ChainViolation.NegativeIndent -> "Cannot unindent: already at level $currentLevel"
}
