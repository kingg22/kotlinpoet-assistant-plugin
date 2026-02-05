package io.github.kingg22.kotlinpoet.assistant.domain.parser

import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel

interface StringFormatParser {
    /**
     * Analiza el [rawString] y extrae placeholders, símbolos de control y errores.
     * No debe lanzar excepciones ante sintaxis inválida.
     */
    fun parse(rawString: String, isNamedStyle: Boolean = false): FormatStringModel
}
