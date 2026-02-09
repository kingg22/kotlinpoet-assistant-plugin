package io.github.kingg22.kotlinpoet.assistant.adapters.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;

public final class PsiTextRangeHelper {
    /** Obtiene el offset inicial real del contenido de un string de Kotlin, saltándose las comillas (" o """). */
    public static int getContentStartOffset(@NotNull PsiElement element) {
        var contentRange = getTextStartOffset(element);
        return element.getTextRange().getStartOffset() + contentRange;
    }

    /**
     * Retorna el rango del contenido excluyendo las comillas, relativo al elemento. Ejemplo: para "abc", retorna (1, 4)
     */
    @Contract(pure = true)
    public static @NotNull TextRange getContentRangeInElement(@NotNull KtStringTemplateExpression element) {
        var text = element.getText();
        var delta = getTextStartOffset(element);
        return new TextRange(delta, text.length() - delta);
    }

    /** Obtiene el start offset basado en el string. Ejemplo: para """abc""", retorna 3. */
    public static int getTextStartOffset(@NotNull PsiElement element) {
        var text = element.getText();
        if (text.startsWith("\"\"\"")) {
            return 3;
        } else if (text.startsWith("\"")) {
            return 1;
        } else {
            return 0;
        }
    }
}
