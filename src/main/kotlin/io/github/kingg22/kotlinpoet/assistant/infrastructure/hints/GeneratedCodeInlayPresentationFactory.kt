// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package io.github.kingg22.kotlinpoet.assistant.infrastructure.hints

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.getFontWithFallback
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import org.jetbrains.kotlin.idea.KotlinFileType
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.math.ceil

internal class GeneratedCodeInlayFactory(val factory: PresentationFactory, val editor: Editor)

internal fun InlayPresentation.withInsetOfButtons(): InlayPresentation =
    this.inset(left = INSET_SIZE, right = INSET_SIZE / 2, top = INSET_SIZE / 2, down = INSET_SIZE / 2)

context(_: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withButtons(vararg additionalOptions: AnAction): InlayPresentation {
    val settingsButton = createSettingsButton(*additionalOptions)
        /* the right inset is set in `asGeneratedCodeBlock`*/
        .inset(left = INSET_SIZE / 2, right = 0, top = 0, down = 0)
    return listOf(this, settingsButton).horizontal()
}

internal fun InlayPresentation.withSmallInset() =
    this.inset(left = SMALL_INSET_SIZE, right = SMALL_INSET_SIZE, top = 0, down = 0)

/**
 * Wraps the inlay into the background, adds padding, and ensures that the inlay stays in the text line like regular code.
 */
context(_: GeneratedCodeInlayFactory)
internal fun InlayPresentation.asSmallInlayAlignedToTextLine(): InlayPresentation = this
    .withSmallInset()
    .withDefaultInlayBackground()

internal fun InlayPresentation.inset(left: Int, right: Int, top: Int, down: Int): InlayPresentation =
    InsetPresentation(this, left = left, right = right, top = top, down = down)

context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withDefaultInlayBackground(): InlayPresentation {
    val bgColor = factory.editor.colorsScheme
        .getAttributes(DefaultLanguageHighlighterColors.INLAY_DEFAULT)?.backgroundColor
        ?: factory.editor.colorsScheme.defaultBackground
    return RoundWithBackgroundPresentation(
        this,
        arcWidth = BG_ARC_DIAMETER,
        arcHeight = BG_ARC_DIAMETER,
        color = bgColor,
    )
}

context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withTooltip(@NlsContexts.HintText tooltip: String): InlayPresentation =
    factory.factory.withTooltip(tooltip, this)

internal fun List<InlayPresentation>.vertical(): InlayPresentation = when (size) {
    0 -> SpacePresentation(0, 0)
    1 -> single()
    else -> VerticalListInlayPresentation(this)
}

internal fun List<InlayPresentation>.horizontal(): InlayPresentation = when (size) {
    0 -> SpacePresentation(0, 0)
    1 -> single()
    else -> SequencePresentation(this)
}

/**
 * @see indented
 */
context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.indentedAsElementInEditorAtOffset(
    offset: Int,
    extraIndent: Int = 0,
    shiftLeftToInset: Boolean,
): InlayPresentation {
    val document = factory.editor.document
    val startOffset = document.getLineStartOffset(document.getLineNumber(offset))

    val column = offset - startOffset + extraIndent
    return indented(column, shiftLeftToInset)
}

/**
 * Adjusts the given inlay presentation by adding an indent to its left side.
 * Added indent size is equal to [columns] number of spaces.
 *
 * @param columns The number of spaces to indent by.
 * @param shiftLeftToInset if set to true, indent will be shifted a little left to the offset of inlay inset,
 * aligning its text start with real declarations text
 */
context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.indented(columns: Int, shiftLeftToInset: Boolean): InlayPresentation {
    val spaceWidth = EditorUtil.getPlainSpaceWidth(factory.editor)
    var left = columns * spaceWidth
    if (shiftLeftToInset) left -= INSET_SIZE
    if (left < 0) left = 0
    return inset(left = left, right = 0, top = 0, down = 0)
}

context(factory: GeneratedCodeInlayFactory)
internal fun getKotlinIndentSize(): Int = CodeStyle.getSettings(factory.editor).getIndentSize(KotlinFileType.INSTANCE)

context(factory: GeneratedCodeInlayFactory)
private fun createSettingsButton(vararg additionalOptions: AnAction): InlayPresentation = createButton(
    icon = AllIcons.Actions.More,
    onClick = { event, _ ->
        val actions = InlayHintsUtils.getDefaultInlayHintsProviderPopupActions(
            KotlinPoetHintsSettings.KEY,
            KPoetAssistantBundle.getLazyMessage("inlay.hints.provider.name"),
        ) + additionalOptions
        if (actions.isNotEmpty()) {
            JBPopupMenu.showByEvent(event, "InlayMenu", DefaultActionGroup(actions))
        }
    },
)

context(factory: GeneratedCodeInlayFactory)
internal fun createIcon(icon: Icon): InlayPresentation = LineAlignedIconPresentation(
    icon,
    factory.editor.component,
    factory.editor.lineHeight,
    iconSize = (factory.editor.lineHeight * .65).toInt(),
)

context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withRoundBackground(): InlayPresentation {
    val noHoverBg = factory.editor.colorsScheme.getAttributes(
        DefaultLanguageHighlighterColors.INLAY_DEFAULT,
    ).backgroundColor ?: factory.editor.colorsScheme.defaultBackground
    val hoverBg = noHoverBg.brighter()
    return RoundWithBackgroundPresentation(this, arcWidth = 4, arcHeight = 4, color = hoverBg)
}

context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withHoverDoRoundBackground(): InlayPresentation = this.withHover {
    it.withRoundBackground()
}

context(factory: GeneratedCodeInlayFactory)
internal fun createButton(icon: Icon, onClick: (MouseEvent, Point) -> Unit): InlayPresentation = createIcon(icon)
    .inset(left = SMALL_INSET_SIZE, right = SMALL_INSET_SIZE, top = 0, down = 0)
    .withOnClickListener(MouseButton.Left, onClick)
    .withHoverDoRoundBackground()
    .withCursorOnHover(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withOnClickListener(
    button: MouseButton,
    onClick: (MouseEvent, Point) -> Unit,
): InlayPresentation = factory.factory.onClick(this, button, onClick)

private fun InlayPresentation.withHover(hover: (InlayPresentation) -> InlayPresentation): InlayPresentation =
    ChangeOnHoverPresentation(this, hover = { hover(this) })

context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withCursorOnHover(cursor: Cursor): InlayPresentation =
    factory.factory.withCursorOnHover(this, cursor)

private const val INSET_SIZE = 8
private const val SMALL_INSET_SIZE = 1
private const val BG_ARC_DIAMETER = 8

context(inlayFactory: GeneratedCodeInlayFactory)
internal fun code(text: String, attributes: TextAttributes): InlayPresentation =
    CodeInlay(text, attributes, inlayFactory.editor)

context(inlayFactory: GeneratedCodeInlayFactory)
internal fun code(text: String): InlayPresentation =
    code(text, inlayFactory.editor.colorsScheme.getAttributes(HighlighterColors.TEXT))

// <editor-fold desc="Line aligned icon presentation rendering">
private class LineAlignedIconPresentation(
    private val icon: Icon,
    private val component: Component,
    private val lineHeight: Int,
    private val iconSize: Int = lineHeight,
) : BasePresentation() {

    override val width: Int
        get() = lineHeight

    override val height: Int
        get() = lineHeight

    override fun paint(g: Graphics2D, attributes: TextAttributes) {
        val graphics = g.create()
        try {
            (graphics as? Graphics2D)?.composite = AlphaComposite.SrcAtop.derive(1.0f)

            // Calculate scale factor to fit icon within specified icon size while maintaining aspect ratio
            val scaleX = iconSize.toFloat() / icon.iconWidth
            val scaleY = iconSize.toFloat() / icon.iconHeight
            val scaleFactor = minOf(scaleX, scaleY)

            val scaledIcon = com.intellij.util.IconUtil.scale(icon, component, scaleFactor)

            // Center the scaled icon within the line height container
            val xOffset = (lineHeight - scaledIcon.iconWidth) / 2
            val yOffset = (lineHeight - scaledIcon.iconHeight) / 2

            scaledIcon.paintIcon(component, graphics, xOffset, yOffset)
        } finally {
            graphics.dispose()
        }
    }

    override fun toString(): String = "<settings-icon>"
}
// </editor-fold>

// <editor-fold desc="Code Inlay presentation with foreground rendering">
private class CodeInlay(
    private val text: String,
    private val ownAttributes: TextAttributes,
    private val editor: Editor,
) : BasePresentation() {
    /**
     * Fetches metrics from cache on each access to ensure IDE scale changes are reflected.
     */
    private val textMetrics: InlayTextMetrics
        get() = EditorFontMetricsCache.getMetrics(editor)

    override val width: Int
        get() = textMetrics.getStringWidth(text)

    override val height: Int
        get() = editor.lineHeight

    override fun paint(g: Graphics2D, attributes: TextAttributes) {
        val attributesToUse = TextAttributes.merge(ownAttributes, attributes)
        val savedHint = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
        try {
            val foreground = attributesToUse.foregroundColor ?: error("ForegroundColor cannot be null")
            val metric = textMetrics
            val font = metric.font
            g.font = font
            g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                AntialiasingType.getKeyForCurrentScope(true),
            )
            g.color = foreground
            // a sum of gaps between which the text is situated on the line
            val fontGap = editor.lineHeight - metric.fontBaseline
            val yCoordinate = editor.lineHeight - fontGap / 2
            g.drawString(text, 0, yCoordinate)
        } finally {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)
        }
    }

    override fun toString(): String = text
}
// </editor-fold>

// <editor-fold desc="Editor font metrics cache with editor font for inlays">

/**
 * A cache for [InlayTextMetrics] that always uses the editor font (for code-like inlays).
 * This differs from [InlayHintsUtils.getTextMetricStorage] which respects the user's
 * "Use editor font for inlays" setting.
 *
 * The cache uses [InlayTextMetricsStorage.getCurrentStamp] for invalidation, ensuring
 * metrics are recreated when IDE scale, font size, or font render context changes.
 */
private object EditorFontMetricsCache {
    private class CachedEntry(val metrics: InlayTextMetrics, val stamp: InlayTextMetricsStamp)

    /**
     * thread unsafe but [EditorFontMetricsCache.getMetrics] is called only from EDT, so it's OK
     */
    private val cache: MutableMap<Editor, CachedEntry> = WeakHashMap()

    @RequiresEdt
    fun getMetrics(editor: Editor): InlayTextMetrics {
        EDT.assertIsEdt()

        val storage = InlayHintsUtils.getTextMetricStorage(editor)
        val currentStamp = storage.getCurrentStamp()

        val cached = cache[editor]
        if (cached != null && cached.stamp === currentStamp) {
            return cached.metrics
        }

        val metrics = InlayTextMetrics.create(
            editor,
            editor.colorsScheme.editorFontSize2D,
            editor.colorsScheme.getAttributes(HighlighterColors.TEXT).fontType,
            getFontRenderContext(editor.component),
        )
        cache[editor] = CachedEntry(metrics, currentStamp)
        return metrics
    }

    private fun InlayTextMetrics.Companion.create(
        editor: Editor,
        size: Float,
        fontType: Int,
        context: FontRenderContext,
        isUseEditorFontInInlays: Boolean = true,
    ): InlayTextMetrics {
        val font = if (isUseEditorFontInInlays) {
            val editorFont = EditorUtil.getEditorFont()
            editorFont.deriveFont(fontType, size)
        } else {
            val familyName = StartupUiUtil.labelFont.family
            getFontWithFallback(familyName, fontType, size)
        }
        val metrics = FontInfo.getFontMetrics(font, context)
        // We assume this will be a better approximation to a real line height for a given font
        val fontHeight = ceil(font.createGlyphVector(context, "Albpq@").visualBounds.height).toInt()
        val fontBaseline = ceil(font.createGlyphVector(context, "Alb").visualBounds.height).toInt()
        return InlayTextMetrics(editor, fontHeight, fontBaseline, metrics, fontType, UISettings.getInstance().ideScale)
    }

    private fun getFontRenderContext(editorComponent: JComponent): FontRenderContext {
        val editorContext = FontInfo.getFontRenderContext(editorComponent)
        return FontRenderContext(
            editorContext.transform,
            AntialiasingType.getKeyForCurrentScope(false),
            UISettings.editorFractionalMetricsHint,
        )
    }
}
// </editor-fold>
