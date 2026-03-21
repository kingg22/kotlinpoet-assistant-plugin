package io.github.kingg22.kotlinpoet.assistant.infrastructure.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.kingg22.kotlinpoet.assistant.domain.chain.ChainViolation
import io.github.kingg22.kotlinpoet.assistant.domain.chain.ContributionAnalyzer
import io.github.kingg22.kotlinpoet.assistant.domain.chain.ContributionResolvability
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmissionState
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodEmissionContribution
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodSemantics
import io.github.kingg22.kotlinpoet.assistant.domain.chain.renderChain
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator
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
import javax.swing.Timer
import javax.swing.event.HyperlinkEvent

/**
 * Factory for the **KotlinPoet Chain** tool window.
 *
 * ## Threading model
 *
 * - Caret/document offsets are captured at **event time** — no PSI access in the Timer.
 * - Background analysis runs via [ReadAction.nonBlocking] — yields to write actions.
 * - [com.intellij.openapi.application.NonBlockingReadAction.finishOnUiThread] guarantees EDT-safe panel mutation.
 * - On tool window **first open**, analysis is triggered for the current editor position
 *   so the panel is not empty before the user moves the caret.
 *
 * ## Memory model
 *
 * Each editor attachment creates a child [Disposable] of [ChainUpdateScheduler.parentDisposable].
 * When the tool window is disposed, all child disposables cascade-dispose, removing listeners
 * without any global editor registry.
 */
class CodeBlockChainToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CodeBlockChainPanel()
        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(panel.component, "", false),
        )
        panel.showPlaceholder("Open a file with a KotlinPoet usage to see the chain of calls")

        val scheduler = ChainUpdateScheduler(project, panel, toolWindow.disposable)

        val bus = project.messageBus.connect(toolWindow.disposable)
        bus.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    source.selectedTextEditor?.let { scheduler.attachTo(it, triggerImmediate = false) }
                }

                override fun selectionChanged(event: FileEditorManagerEvent) {
                    panel.showPlaceholder("Move cursor into a KotlinPoet builder call")
                    FileEditorManager.getInstance(project)
                        .selectedTextEditor
                        ?.let { scheduler.attachTo(it, triggerImmediate = true) }
                }
            },
        )

        // Attach to the editor already open when the tool window is created and
        // immediately trigger analysis so the panel isn't empty on first open.
        FileEditorManager.getInstance(project)
            .selectedTextEditor
            ?.let { scheduler.attachTo(it, triggerImmediate = true) }
    }
}

// ── Update scheduler ───────────────────────────────────────────────────────────
private val attachKey: Key<Boolean> = Key.create("kotlinpoet.chain.listener.attached")

/**
 * Manages caret/document listeners and schedules debounced background analysis.
 *
 * ## Offset capture
 *
 * Both [CaretListener] and [DocumentListener] run in a context where the caret model
 * is accessible. The offset is capture **at event time** — the Swing Timer only reads a pre-captured [Int],
 * never the editor model.
 *
 * ## No editor registry
 *
 * Each [attachTo] creates a child [Disposable] of [parentDisposable]. When the tool
 * window closes, all children are cascade-disposed. A user-data key guards against
 * attaching the same editor twice.
 */
private class ChainUpdateScheduler(
    private val project: Project,
    private val panel: CodeBlockChainPanel,
    private val parentDisposable: Disposable,
) {
    private lateinit var debounceTimer: Timer

    /**
     * Attaches caret and document listeners to [editor], scoped to a child disposable.
     *
     * @param triggerImmediate If `true`, schedules one immediate analysis with the
     *        current caret position (used on tool window open and editor switch).
     */
    fun attachTo(editor: Editor, triggerImmediate: Boolean) {
        if (editor.getUserData(attachKey) == true) {
            // Already attached — still allow immediate trigger if requested
            if (triggerImmediate) scheduleUpdate(editor, editor.caretModel.currentCaret.offset)
            return
        }
        editor.putUserData(attachKey, true)

        // Child disposable so listeners are cleaned up when either the tool window
        // or the editor component is disposed — whichever comes first.
        val editorDisposable = Disposer.newDisposable(parentDisposable, "KPoetChainListener")

        val caretListener = object : CaretListener {
            // Runs on EDT with full caret model access — capture offset here
            override fun caretPositionChanged(event: CaretEvent) {
                val offset = event.caret.offset
                scheduleUpdate(editor, offset)
            }
        }

        val documentListener = object : DocumentListener {
            // Runs on EDT in write-action context — caret model accessible
            override fun documentChanged(event: DocumentEvent) {
                scheduleUpdate(editor, editor.caretModel.currentCaret.offset)
            }
        }

        editor.caretModel.addCaretListener(caretListener, editorDisposable)
        editor.document.addDocumentListener(documentListener, editorDisposable)

        if (triggerImmediate) scheduleUpdate(editor, editor.caretModel.currentCaret.offset)
    }

    // ── Debounce ──────────────────────────────────────────────────────────────
    private fun stopDebounceTimer() {
        if (::debounceTimer.isInitialized) {
            debounceTimer.stop()
        }
    }

    /**
     * Resets the debounce timer. Captures [editor] and [offset] into local vals that
     * the Timer lambda closes over — no PSI or editor model access inside the Timer.
     */
    private fun scheduleUpdate(editor: Editor, offset: Int) {
        stopDebounceTimer()
        debounceTimer = Timer(400) {
            // EDT — only reads the pre-captured local vals
            launchBackgroundAnalysis(editor, offset)
        }.also {
            it.isRepeats = false
            it.start()
        }
    }

    // ── Background analysis ────────────────────────────────────────────────────

    private fun launchBackgroundAnalysis(editor: Editor, offset: Int) {
        ReadAction
            .nonBlocking<ChainAnalysisResult> {
                analyzeAtOffset(editor, offset)
            }
            .expireWith(parentDisposable)
            .finishOnUiThread(ModalityState.defaultModalityState()) { result ->
                panel.showResult(result, editor)
            }
            .coalesceBy(this, editor)
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun analyzeAtOffset(editor: Editor, offset: Int): ChainAnalysisResult {
        val psiFile = PsiDocumentManager.getInstance(project)
            .getPsiFile(editor.document)
            ?: return ChainAnalysisResult.empty()

        val element = psiFile.findElementAt(offset)
            ?: return ChainAnalysisResult.empty()

        val call = CodeBlockPsiNavigator.findBuilderCallAt(element)
            ?: return ChainAnalysisResult.empty()

        val chain = CodeBlockPsiNavigator.findChain(call)
        if (chain.isEmpty()) return ChainAnalysisResult.empty()

        val contributions = mutableListOf<MethodEmissionContribution?>()
        val violations = mutableListOf<Pair<Int, ChainViolation>>()
        val inspectionProblems = mutableListOf<Int>()
        var state = EmissionState.Initial

        for ((index, chainCall) in chain.withIndex()) {
            val contribution = ContributionAnalyzer.analyze(chainCall)
            contributions += contribution

            val cachedAnalysis = getCachedAnalysis(chainCall, extractOnMissing = false)
            if (cachedAnalysis?.haveFormatProblems == true || cachedAnalysis?.haveProblems == true) {
                inspectionProblems += index
            }

            if (contribution != null) {
                when (val r = state.apply(contribution.stateDelta)) {
                    is EmissionState.StateApplyResult.Success -> state = r.newState
                    is EmissionState.StateApplyResult.Failure -> violations += index to r.violation
                }
            }
        }

        return ChainAnalysisResult(chain, contributions, violations, inspectionProblems, state)
    }
}

// ── Analysis result ────────────────────────────────────────────────────────────

private data class ChainAnalysisResult(
    val calls: List<KtCallExpression>,
    val contributions: List<MethodEmissionContribution?>,
    val violations: List<Pair<Int, ChainViolation>>,
    val inspectionProblems: List<Int>,
    val finalState: EmissionState,
) {
    fun isEmpty(): Boolean = calls.isEmpty()

    companion object {
        fun empty(): ChainAnalysisResult = ChainAnalysisResult(
            calls = emptyList(),
            contributions = emptyList(),
            violations = emptyList(),
            inspectionProblems = emptyList(),
            finalState = EmissionState.Initial,
        )
    }
}

// ── Panel ──────────────────────────────────────────────────────────────────────

/**
 * The Swing panel for the KotlinPoet Chain tool window.
 *
 * All mutations happen on the EDT (enforced by [com.intellij.openapi.application.NonBlockingReadAction.finishOnUiThread] in the scheduler).
 * The preview section uses [renderChain] to produce properly indented output, visually
 * distinct from the per-call metadata rows via a different background and border.
 */
private class CodeBlockChainPanel {

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

    fun showResult(result: ChainAnalysisResult, editor: Editor) {
        if (result.isEmpty()) {
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
