package io.github.kingg22.kotlinpoet.assistant.domain.model

import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem

/** Model a string with format specifiers. */
data class FormatStringModel(
    val text: FormatText,
    val style: FormatStyle,
    val placeholders: List<PlaceholderSpec>,
    val controlSymbols: List<ControlSymbol>,
    /**
     * **Blocking** problems detected during parsing (e.g. mixed argument styles).
     * When non-empty, [io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinPoetAnalysis.haveFormatProblems]
     * is `true` and the binding + validation phases are skipped entirely.
     * These are rendered by the annotator.
     */
    val errors: List<FormatProblem> = emptyList(),
    /**
     * **Non-blocking** parser-level warnings (e.g. dangling `%`, unknown placeholder type,
     * invalid positional index). These do NOT stop binding or validation.
     * They are surfaced via [io.github.kingg22.kotlinpoet.assistant.infrastructure.inspections.KotlinPoetFormatSyntaxInspection]
     * and can offer quick fixes.
     *
     * [FormatProblem.data] carries a [ParserIssueKind] tag so the inspection layer can select
     * the appropriate quick fix without re-parsing the message.
     */
    val warnings: List<FormatProblem> = emptyList(),
) {
    /** Marker class for different types of format styles */
    sealed interface FormatStyle {
        data object Relative : FormatStyle
        data object Positional : FormatStyle
        data object Named : FormatStyle

        /** Se usa cuando conviven varios estilos (ej.: %L y %name:S) */
        data object Mixed : FormatStyle

        /** Cuando no hay ningún placeholder detectado */
        data object None : FormatStyle
    }

    /**
     * Tag stored in [FormatProblem.kind] for parser-level warnings so inspections can
     * differentiate them without inspecting the message string.
     */
    enum class ParserIssueKind {
        /** A `%` with no valid continuation — dangling or followed by whitespace/symbol. */
        DANGLING_PERCENT,

        /** A syntactically valid placeholder whose type char is not a known KotlinPoet kind (e.g. `%Z`). */
        UNKNOWN_PLACEHOLDER_TYPE,

        /** A positional placeholder whose index is < 1 (e.g. `%0L`). */
        INVALID_POSITIONAL_INDEX,

        /** A mix of positional or relative and named placeholders (e.g. `%1L %name:S %L`). */
        MIXED_STYLES,
    }
}
