package io.github.kingg22.kotlinpoet.assistant.domain.parser

import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText

interface StringFormatParser {
    /**
     * Analiza el [text] y extrae placeholders, símbolos de control y errores.
     * No debe lanzar excepciones ante sintaxis inválida.
     */
    fun parse(text: FormatText, isNamedStyle: Boolean = false): FormatStringModel
}
