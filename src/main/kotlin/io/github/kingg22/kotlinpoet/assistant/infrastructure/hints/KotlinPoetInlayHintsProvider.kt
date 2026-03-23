package io.github.kingg22.kotlinpoet.assistant.infrastructure.hints

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.VerticalListInlayPresentation
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile

/**
 * Non-declarative inlay hints provider for KotlinPoet builder chains.
 *
 * Uses [FactoryInlayHintsCollector] (old API) so [PresentationFactory.collapsible] and
 * [PresentationFactory.withTooltip] are available. Settings are persisted by the platform
 * via XML serialization of [KotlinPoetHintsSettings] — no application service needed.
 *
 * ## Per-call EOL hint ([KotlinPoetHintsSettings.showPerCallHints], off by default)
 * `→ <approximation>` placed at end of line via [InlayHintsSink.addInlineElement] with
 * `placeAtTheEndOfLine = true`.
 *
 * ## Chain preview above last call ([KotlinPoetHintsSettings.showChainPreview], on by default)
 * A [PresentationFactory.collapsible] block element placed above the terminal call.
 * - **Collapsed**: `N lines` — compact, click to expand.
 * - **Expanded**: [VerticalListInlayPresentation] of [PresentationFactory.smallText] lines,
 *   styled by [com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INLAY_DEFAULT] as
 *   applied by `smallText`. Truncation notice appended when preview exceeds
 *   [KotlinPoetHintsSettings.maxPreviewLines].
 * - Hover tooltip on the whole collapsible reads from the bundle.
 */
@Suppress("UnstableApiUsage")
class KotlinPoetInlayHintsProvider : InlayHintsProvider<KotlinPoetHintsSettings> {

    override val group: InlayGroup get() = InlayGroup.OTHER_GROUP

    override val key: SettingsKey<KotlinPoetHintsSettings> get() = KotlinPoetHintsSettings.KEY

    override val name: String get() = KPoetAssistantBundle.getMessage("inlay.hints.provider.name")

    override val previewText: String
        @org.intellij.lang.annotations.Language("kotlin")
        get() = """
        import com.squareup.kotlinpoet.CodeBlock

        val example = CodeBlock
         .builder()
         .addStatement("val x = %L", 42)
         .addStatement("return %S", "hello")
         .build()
        }
        """.trimIndent()

    override fun createSettings(): KotlinPoetHintsSettings = KotlinPoetHintsSettings()

    override fun getProperty(key: String): String = KPoetAssistantBundle.getMessage(key)

    override fun createConfigurable(settings: KotlinPoetHintsSettings): ImmediateConfigurable =
        KotlinPoetHintsSettingsConfigurable(settings)

    override val isVisibleInSettings: Boolean get() = true

    override fun isLanguageSupported(language: Language): Boolean = language.`is`(KotlinLanguage.INSTANCE)

    override fun getSettingsLanguage(language: Language): Language = KotlinLanguage.INSTANCE

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: KotlinPoetHintsSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        if (file !is KtFile) return null
        return KotlinPoetInlayHintsCollector(editor, settings)
    }
}
