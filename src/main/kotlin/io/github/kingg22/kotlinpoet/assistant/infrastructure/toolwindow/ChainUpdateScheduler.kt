package io.github.kingg22.kotlinpoet.assistant.infrastructure.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.kingg22.kotlinpoet.assistant.domain.chain.ChainViolation
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmissionState
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodEmissionContribution
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator
import io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.ContributionAnalyzer
import javax.swing.Timer

// ── Update scheduler ───────────────────────────────────────────────────────────
private val attachKey: Key<Boolean> = Key.create("kotlinpoet.chain.listener.attached")

/**
 * Manages caret/document listeners and schedules debounced background analysis.
 *
 * ## Offset capture
 *
 * Both [com.intellij.openapi.editor.event.CaretListener] and [com.intellij.openapi.editor.event.DocumentListener] run in a context where the caret model
 * is accessible. The offset is capture **at event time** — the Swing Timer only reads a pre-captured [Int],
 * never the editor model.
 *
 * ## No editor registry
 *
 * Each [attachTo] creates a child [com.intellij.openapi.Disposable] of [parentDisposable]. When the tool
 * window closes, all children are cascade-disposed. A user-data key guards against
 * attaching the same editor twice.
 */
class ChainUpdateScheduler(
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
