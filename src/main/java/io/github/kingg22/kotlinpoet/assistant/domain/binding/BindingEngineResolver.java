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
            case FormatStyle.None ignored -> NoneBindingEngine.INSTANCE;
            case FormatStyle.Relative ignored -> RelativeBindingEngine.INSTANCE;
            case FormatStyle.Positional ignored -> PositionalBindingEngine.INSTANCE;
            case FormatStyle.Named ignored -> NamedBindingEngine.INSTANCE;
            case FormatStyle.Mixed ignored -> MixedBindingEngine.INSTANCE;
            default -> throw new IllegalArgumentException("Unexpected value: " + style);
        };
    }
}
