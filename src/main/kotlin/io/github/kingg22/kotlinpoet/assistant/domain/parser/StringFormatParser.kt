package io.github.kingg22.kotlinpoet.assistant.domain.parser

import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText

sealed interface StringFormatParser {
    /**
     * Analyses [text] and extracts placeholders, control symbols, and blocking errors.
     *
     * Never throws for malformed input — all issues are accumulated in [FormatStringModel.errors].
     *
     * @param text         PSI-backed segmented format string.
     * @param isNamedStyle `true` when the call site uses `addNamed` (forces named-only style).
     * @param methodName   The KotlinPoet method being called (e.g. `"addNamed"`, `"addStatement"`).
     *                     Used to produce context-sensitive mix-error messages. Defaults to `""`.
     */
    fun parse(text: FormatText, isNamedStyle: Boolean = false, methodName: String = ""): FormatStringModel
}
