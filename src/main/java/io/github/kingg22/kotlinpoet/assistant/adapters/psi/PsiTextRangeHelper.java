package io.github.kingg22.kotlinpoet.assistant.adapters.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import kotlin.ranges.IntRange;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;

public final class PsiTextRangeHelper {
    /** Obtiene el offset inicial real del contenido de un string de Kotlin, saltándose las comillas (" o """). */
    public static int getContentStartOffset(@NotNull PsiElement element) {
        if (element instanceof KtStringTemplateExpression ktStringTemplate) {
            var contentRange = getContentRangeInElement(ktStringTemplate);
            return ktStringTemplate.getTextRange().getStartOffset() + contentRange.getStartOffset();
        }
        return element.getTextRange().getStartOffset();
    }

    /** Traduce un IntRange (del dominio) a un TextRange (de IntelliJ) absoluto. */
    @Contract(pure = true)
    public static @NotNull TextRange translateToAbsolute(@NotNull IntRange relativeRange, int baseOffset) {
        return TextRange.create(
                /* startOffset = */ baseOffset + relativeRange.getFirst(),
                /* endOffset = */ baseOffset + relativeRange.getLast() + 1);
    }

    /**
     * Retorna el rango del contenido excluyendo las comillas, relativo al elemento. Ejemplo: para "abc", retorna (1, 4)
     */
    @Contract(pure = true)
    private static @NotNull TextRange getContentRangeInElement(@NotNull KtStringTemplateExpression element) {
        var text = element.getText();
        if (text.startsWith("\"\"\"")) {
            return new TextRange(3, text.length() - 3);
        } else if (text.startsWith("\"")) {
            return new TextRange(1, text.length() - 1);
        } else {
            return new TextRange(0, text.length());
        }
    }
}
