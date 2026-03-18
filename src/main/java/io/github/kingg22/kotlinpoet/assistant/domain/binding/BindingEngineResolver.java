package io.github.kingg22.kotlinpoet.assistant.domain.binding;

import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.FormatStyle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class BindingEngineResolver {
    private BindingEngineResolver() {
        throw new UnsupportedOperationException();
    }

    @Contract(pure = true)
    public static @NotNull BindingEngine forStyle(@NotNull FormatStyle style) {
        return switch (style) {
            case FormatStyle.None ignored -> new NoneBindingEngine();
            case FormatStyle.Relative ignored -> new RelativeBindingEngine();
            case FormatStyle.Positional ignored -> new PositionalBindingEngine();
            case FormatStyle.Named ignored -> new NamedBindingEngine();
            case FormatStyle.Mixed ignored -> new MixedBindingEngine();
            default -> throw new IllegalArgumentException("Unexpected value: " + style);
        };
    }
}
