package io.github.kingg22.kotlinpoet.assistant.infrastructure.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

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
