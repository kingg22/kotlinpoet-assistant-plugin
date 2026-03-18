package io.github.kingg22.kotlinpoet.assistant.domain.extractor;

import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParser;
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParserImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtCallExpression;

import java.util.List;
import java.util.Objects;

public final class FormatContextExtractorRegistry {
    private FormatContextExtractorRegistry() {
        throw new UnsupportedOperationException();
    }

    private static final StringFormatParser PARSER = new StringFormatParserImpl();

    private static final List<FormatContextExtractor> EXTRACTORS =
            List.of(new NamedFormatExtractor(PARSER), new VarargFormatExtractor(PARSER));

    public static @Nullable KotlinPoetCallContext extract(@NotNull KtCallExpression call) {
        return EXTRACTORS.stream()
                .map(extractor -> extractor.extract(call))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
