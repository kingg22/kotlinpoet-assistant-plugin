package io.github.kingg22.kotlinpoet.assistant.infrastructure.toolwindow;

import io.github.kingg22.kotlinpoet.assistant.domain.chain.ChainViolation;
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmissionState;
import io.github.kingg22.kotlinpoet.assistant.domain.chain.MethodEmissionContribution;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtCallExpression;

import java.util.List;

record ChainAnalysisResult(
        @NotNull List</*@NotNull*/ KtCallExpression> calls,
        @NotNull List</*@Nullable*/ MethodEmissionContribution> contributions,
        @NotNull List</*@NotNull*/ kotlin.Pair</*@NotNull */ Integer, /*@NotNull */ ChainViolation>> violations,
        @NotNull List</*@NotNull */ Integer> inspectionProblems,
        @NotNull EmissionState finalState) {

    boolean isEmpty() {
        return calls.isEmpty();
    }

    @Contract(pure = true)
    static @NotNull ChainAnalysisResult empty() {
        return new ChainAnalysisResult(List.of(), List.of(), List.of(), List.of(), EmissionState.getInitial());
    }
}
