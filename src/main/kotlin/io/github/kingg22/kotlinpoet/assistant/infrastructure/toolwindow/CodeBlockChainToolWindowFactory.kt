package io.github.kingg22.kotlinpoet.assistant.infrastructure.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.concurrency.AppExecutorUtil
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
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.KNOWN_BUILDER_CALLS
import org.jetbrains.kotlin.psi.KtCallExpression
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Factory for the **KotlinPoet Chain** tool window.
 *
 * ## Registration (plugin.xml)
 * ```xml
 * <toolWindow
 *     id="KotlinPoet Chain"
 *     anchor="bottom"
 *     secondary="true"
 *     canCloseContents="false"
 *     factoryClass="...CodeBlockChainToolWindowFactory"/>
 * ```
 *
 * ## Threading model
 *
 * PSI offsets are captured **at event time** inside the caret/document listeners, which
 * already execute in a context where the caret model is accessible. The Swing Timer only
 * holds the pre-captured offset — it never accesses PSI or editor models.
 * Background work uses [ReadAction.nonBlocking] so it yields to write actions.
 *
 * ## Memory model
 *
 * Editor listeners are registered with a per-editor [Disposable] that is a child of the
 * tool window's own disposable. When the tool window is disposed (plugin unload, project
 * close), all child disposables are cascade-disposed and the listeners are removed.
 * No global mutable collections of editors are kept.
 */
class CodeBlockChainToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CodeBlockChainPanel()
        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(panel.component, "", false),
        )

        val scheduler = ChainUpdateScheduler(project, panel, toolWindow.disposable)

        val bus = project.messageBus.connect(toolWindow.disposable)
        bus.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    source.selectedTextEditor?.let { scheduler.attachTo(it) }
                }

                override fun selectionChanged(event: FileEditorManagerEvent) {
                    panel.showPlaceholder("Move cursor into a KotlinPoet builder call")
                    FileEditorManager.getInstance(project)
                        .selectedTextEditor
                        ?.let { scheduler.attachTo(it) }
                }
            },
        )

        // Attach to editor already open when the tool window is created
        FileEditorManager.getInstance(project)
            .selectedTextEditor
            ?.let { scheduler.attachTo(it) }
    }
}

// ── Update scheduler ───────────────────────────────────────────────────────────
private val key: Key<Boolean> = Key.create("kotlinpoet.chain.listener.attached")

/**
 * Manages caret/document listeners and schedules debounced background analysis.
 *
 * ## Key invariants
 *
 * - **Offset capture at event time**: Both [CaretListener] and [DocumentListener]
 *   run in a context where `event.caret.offset` / `editor.caretModel.currentCaret.offset`
 *   is accessible. The offset is stored before the debounce Timer fires.
 *
 * - **Timer runs on EDT, does NOT access PSI**: The Timer lambda only reads the
 *   pre-captured [Int] offset — no PSI, no editor model access.
 *
 * - **No editor registry**: Listeners are registered with a child disposable of
 *   [parentDisposable]. No `Set<Editor>` or similar structure is kept.
 */
private class ChainUpdateScheduler(
    private val project: Project,
    private val panel: CodeBlockChainPanel,
    private val parentDisposable: Disposable,
) {
    private var debounceTimer: Timer? = null

    // Last known offset — captured at event time, read on EDT inside Timer.
    // Volatile because it is written from various EDT calls (caret/document events)
    // and read in the same thread (Timer fires on EDT), but marking volatile makes
    // the intent explicit and is harmless.
    @Volatile
    private var lastOffset: Int = 0

    // Last editor — captured at event time, safe to store (weak reference semantics
    // via disposable: if the editor is closed, its disposable is disposed and listeners
    // are removed before the editor becomes invalid).
    @Volatile
    private var lastEditor: Editor? = null

    /**
     * Attaches caret and document listeners to [editor], scoped to a new child
     * disposable of [parentDisposable]. Idempotent per editor via a user-data key.
     */
    fun attachTo(editor: Editor) {
        // Guard: don't attach twice to the same editor
        if (editor.getUserData(key) == true) return
        editor.putUserData(key, true)

        // Child disposable so listeners are cleaned up when either the tool window
        // or the editor component is disposed — whichever comes first.
        val editorDisposable = Disposer.newDisposable(parentDisposable, "KPoetChainListener")

        val caretListener = object : CaretListener {
            // caretPositionChanged runs on EDT with full access to the caret model
            override fun caretPositionChanged(event: CaretEvent) {
                // Capture offset HERE — at event time, in the correct context
                val offset = event.caret.offset
                lastEditor = editor
                lastOffset = offset
                scheduleUpdate()
            }
        }

        val documentListener = object : DocumentListener {
            // documentChanged may run in write action context; we only need the
            // caret offset, which is accessible via the caretModel in this context.
            override fun documentChanged(event: DocumentEvent) {
                // The document change itself doesn't carry a caret offset.
                // We use the current caret position at the time of the change.
                // This is safe: documentChanged is called on EDT and caret access
                // is allowed (we are in the document's write context).
                lastEditor = editor
                lastOffset = editor.caretModel.currentCaret.offset
                scheduleUpdate()
            }
        }

        editor.caretModel.addCaretListener(caretListener, editorDisposable)
        editor.document.addDocumentListener(documentListener, editorDisposable)
    }

    // ── Debounce ──────────────────────────────────────────────────────────────

    /**
     * Resets the debounce timer. Called on EDT. Does NOT access PSI or editor models —
     * only reads [lastOffset] and [lastEditor] which were captured at event time.
     */
    private fun scheduleUpdate() {
        debounceTimer?.stop()
        // Snapshot both fields on EDT — the Timer lambda will close over these local vals.
        val offset = lastOffset
        val editor = lastEditor ?: return

        debounceTimer = Timer(400) {
            // Still on EDT here — immediately hand off to background.
            // No PSI access in this lambda.
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
                panel.showResult(result)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun analyzeAtOffset(editor: Editor, offset: Int): ChainAnalysisResult {
        val psiFile = PsiDocumentManager.getInstance(project)
            .getPsiFile(editor.document)
            ?: return ChainAnalysisResult.empty()

        val element = psiFile.findElementAt(offset)
            ?: return ChainAnalysisResult.empty()

        val call = findBuilderCallAt(element)
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

            val cachedAnalysis = getCachedAnalysis(chainCall)
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

    private fun findBuilderCallAt(element: PsiElement): KtCallExpression? {
        var e: PsiElement? = element
        while (e != null) {
            if (e is KtCallExpression && (e.calleeExpression?.text ?: "") in KNOWN_BUILDER_CALLS) {
                return e
            }
            e = e.parent
        }
        return null
    }
}

// ── Analysis result (plain data, no PSI references) ───────────────────────────

private data class ChainAnalysisResult(
    val calls: List<KtCallExpression>,
    val contributions: List<MethodEmissionContribution?>,
    val violations: List<Pair<Int, ChainViolation>>,
    val inspectionProblems: List<Int>,
    val finalState: EmissionState,
) {
    fun isEmpty(): Boolean = calls.isEmpty()

    companion object {
        fun empty(): ChainAnalysisResult =
            ChainAnalysisResult(emptyList(), emptyList(), emptyList(), emptyList(), EmissionState.Initial)
    }
}

// ── Panel ──────────────────────────────────────────────────────────────────────

/**
 * The Swing panel for the KotlinPoet Chain tool window.
 *
 * All mutations happen on EDT (enforced by [finishOnUiThread] above and [SwingUtilities.invokeLater]).
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

    fun showResult(result: ChainAnalysisResult) {
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
            contentPanel.add(buildCallRow(call, contribution, violation, hasInspection))
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
                JBLabel(".$methodName($argumentList)  ").apply {
                    font = font.deriveFont(Font.BOLD)
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
                BorderLayout.CENTER,
            )
        }
        outerPanel.add(innerPanel)
        return outerPanel
    }
}

// ── Display helpers (top-level, not singleton state) ──────────────────────────

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
