package io.github.kingg22.kotlinpoet.assistant.adapters.psi;

import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText;
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatTextSegment;
import io.github.kingg22.kotlinpoet.assistant.domain.text.SegmentKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtParenthesizedExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;

import java.util.Objects;
import java.util.stream.Stream;

public final class PsiFormatTextExtractor {

    private PsiFormatTextExtractor() {
        throw new UnsupportedOperationException();
    }

    public static @Nullable FormatText extract(@Nullable KtExpression expression) {
        return switch (expression) {
            case KtParenthesizedExpression paren -> extract(paren.getExpression());

            case KtStringTemplateExpression template -> fromStringTemplate(template);

            case KtBinaryExpression bin
            when bin.getOperationToken() == KtTokens.PLUS -> {
                final var left = extract(bin.getLeft());
                final var right = extract(bin.getRight());
                yield (left != null && right != null) ? left.plus(right) : null;
            }

            case null, default -> null;
        };
    }

    public static @NotNull FormatText fromStringTemplate(@NotNull KtStringTemplateExpression template) {
        final var segments = Stream.of(template.getEntries())
                .map(entry -> {
                    final var text = entry.getText();
                    if (text == null || text.isEmpty()) return null;

                    return new FormatTextSegment(
                            text,
                            entry.getTextRange().getStartOffset(),
                            entry.getTextRange().getEndOffset(),
                            getKind(entry));
                })
                .filter(Objects::nonNull)
                .toList();

        return new FormatText(segments);
    }

    public static @NotNull SegmentKind getKind(@NotNull KtStringTemplateEntry entry) {
        if (entry instanceof KtStringTemplateEntryWithExpression) {
            return SegmentKind.DYNAMIC;
        }
        return SegmentKind.LITERAL;
    }
}
