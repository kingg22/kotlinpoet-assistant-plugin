package io.github.kingg22.kotlinpoet.assistant.infrastructure.hints

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import javax.swing.JCheckBox

@Suppress("UnstableApiUsage")
class KotlinPoetHintsSettingsConfigurable(private val settings: KotlinPoetHintsSettings) : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): DialogPanel = panel {
        row {
            checkBox(KPoetAssistantBundle.getMessage("inlay.hints.per.call.label"))
                .applyToComponent { isSelected = settings.showPerCallHints }
                .onChanged {
                    settings.showPerCallHints = it.isSelected
                    listener.settingsChanged()
                }
                .comment(KPoetAssistantBundle.getMessage("inlay.hints.per.call.comment"))
        }
        var showChainPreviewCheckBox: JCheckBox? = null
        row {
            showChainPreviewCheckBox = checkBox(KPoetAssistantBundle.getMessage("inlay.hints.chain.preview.label"))
                .applyToComponent { isSelected = settings.showChainPreview }
                .onChanged {
                    settings.showChainPreview = it.isSelected
                    listener.settingsChanged()
                }
                .comment(KPoetAssistantBundle.getMessage("inlay.hints.chain.preview.comment"))
                .component
        }
        indent {
            row {
                checkBox(KPoetAssistantBundle.getMessage("inlay.hints.chain.preview.expanded.label"))
                    .comment(KPoetAssistantBundle.getMessage("inlay.hints.chain.preview.expanded.comment"))
                    .applyToComponent { isSelected = settings.showChainPreviewExpanded }
                    .enabledIf(showChainPreviewCheckBox!!.selected)
                    .onChanged {
                        settings.showChainPreviewExpanded = it.isSelected
                        listener.settingsChanged()
                    }
            }
        }

        separator()

        // Spinners are managed manually, change listener UI DSL not support this.
        row(KPoetAssistantBundle.getMessage("inlay.hints.max.lines.label")) {
            cell(
                JBIntSpinner(settings.maxPreviewLines, 1, 100).apply {
                    isOpaque = false
                    addChangeListener { _ ->
                        settings.maxPreviewLines = this.number
                        listener.settingsChanged()
                    }
                },
            )
        }
        row(KPoetAssistantBundle.getMessage("inlay.hints.max.length.label")) {
            cell(
                JBIntSpinner(settings.maxLineLength, 20, 200).apply {
                    isOpaque = false
                    addChangeListener { _ ->
                        settings.maxLineLength = this.number
                        listener.settingsChanged()
                    }
                },
            )
        }
    }
}
