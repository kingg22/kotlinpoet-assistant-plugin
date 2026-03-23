package io.github.kingg22.kotlinpoet.assistant.infrastructure.annotator;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;

public final class KotlinPoetHighlightKeys {
    private KotlinPoetHighlightKeys() {
        throw new UnsupportedOperationException();
    }

    public static final @NotNull TextAttributesKey CONTROL_SYMBOL = TextAttributesKey.createTextAttributesKey(
            "KPOET_CONTROL", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL);

    public static final @NotNull TextAttributesKey PLACEHOLDER = TextAttributesKey.createTextAttributesKey(
            "KPOET_PLACEHOLDER", DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE);
}
