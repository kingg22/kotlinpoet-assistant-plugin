package io.github.kingg22.kotlinpoet.assistant.infrastructure.hints

import com.intellij.codeInsight.hints.SettingsKey

data class KotlinPoetHintsSettings(
    @JvmField var showPerCallHints: Boolean = true,
    @JvmField var showChainPreview: Boolean = true,
    @JvmField var showChainPreviewExpanded: Boolean = false,
    @JvmField var maxPreviewLines: Int = 12,
    @JvmField var maxLineLength: Int = 80,
) {
    companion object {
        @JvmField
        val KEY: SettingsKey<KotlinPoetHintsSettings> = SettingsKey("kotlinpoet.chain.preview")
    }
}
