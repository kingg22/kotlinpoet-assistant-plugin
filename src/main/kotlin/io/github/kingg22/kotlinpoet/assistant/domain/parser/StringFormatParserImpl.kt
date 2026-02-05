package io.github.kingg22.kotlinpoet.assistant.domain.parser

import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import org.jetbrains.annotations.Nls

class StringFormatParserImpl : StringFormatParser {
    companion object {
        private val NAMED_ARGUMENT_PATTERN = "%([\\w_]+):(\\w)".toPattern()
    }

    /** Actual parser state */
    private class ParseState(val rawString: String) {
        val placeholders = mutableListOf<PlaceholderSpec>()
        val controlSymbols = mutableListOf<ControlSymbol>()
        val errors = mutableListOf<FormatProblem>()

        var hasNamed = false
        var hasPositional = false
        var hasRelative = false
        var cursor = 0

        fun isAtEnd() = cursor >= rawString.length

        fun current(): Char = rawString[cursor]

        fun peek(offset: Int = 1): Char? = if (cursor + offset < rawString.length) rawString[cursor + offset] else null
    }

    override fun parse(rawString: String): FormatStringModel {
        val state = ParseState(rawString)

        while (!state.isAtEnd()) {
            // 1. Analizar si es un inicio de token (%)
            if (state.current() == '%') {
                parsePercentToken(state)
            } else {
                // Avanzamos caracteres normales
                state.cursor++
            }
        }

        return buildModel(rawString, state)
    }

    /**
     * Orquestador principal cuando se encuentra un '%'.
     * Intenta hacer match en orden de especificidad: Named -> Control -> Positional -> Relative
     */
    private fun parsePercentToken(state: ParseState) {
        val start = state.cursor

        // Check 1: Named Arguments (%name:T)
        // CodeBlock chequea Named primero si hay un ':' adelante
        if (tryParseNamed(state, start)) return

        // Check 2: Control Symbols (%%, %>, %W, etc)
        if (tryParseControl(state, start)) return

        // Check 3: Positional Arguments (%1L)
        if (tryParsePositional(state, start)) return

        // Check 4: Relative Arguments (%L)
        if (tryParseRelative(state, start)) return

        // Fallback: Si llegamos aquí, es un % colgado o inválido
        reportError(
            state,
            start,
            state.cursor + 1,
            KPoetAssistantBundle.getMessage("argument.format.invalid.incomplete"),
        )
        state.cursor++ // Avanzamos para evitar loop infinito
    }

    /** Intenta parsear argumentos con nombre: %argumentName:X */
    private fun tryParseNamed(state: ParseState, start: Int): Boolean {
        // Optimización: Si no hay ':', no puede ser named
        val text = state.rawString
        val colonIndex = text.indexOf(':', start)
        if (colonIndex == -1) return false

        // Limitamos la búsqueda para el matcher
        val potentialEnd = (colonIndex + 2).coerceAtMost(text.length)
        val snippet = text.substring(start, potentialEnd)

        val matcher = NAMED_ARGUMENT_PATTERN.matcher(snippet)
        if (matcher.lookingAt()) {
            val name = matcher.group(1)
            val typeChar = matcher.group(2)
            val end = start + matcher.end()

            // Validar regla de CodeBlock: debe empezar con minúscula
            if (!name[0].isLowerCase()) {
                reportError(state, start, end, KPoetAssistantBundle.getMessage("named.argument.lowercase", name))
            }

            addPlaceholder(state, typeChar, PlaceholderBinding.Named(name), start, end)
            state.hasNamed = true
            state.cursor = end
            return true
        }
        return false
    }

    /** Intenta parsear símbolos de control */
    private fun tryParseControl(state: ParseState, start: Int): Boolean {
        val nextChar = state.peek() ?: return false

        // Construimos el token de 2 caracteres (ej: %>)
        val token = "%$nextChar"
        val symbolType = ControlSymbol.SymbolType.fromString(token)

        if (symbolType != null) {
            val end = start + 2
            state.controlSymbols.add(ControlSymbol(symbolType, start until end))
            state.cursor = end
            return true
        }
        return false
    }

    /**
     * Intenta parsear argumentos posicionales: %1L, %2T
     */
    private fun tryParsePositional(state: ParseState, start: Int): Boolean {
        val firstDigit = state.peek()
        if (firstDigit == null || !firstDigit.isDigit()) return false

        // Consumir dígitos
        var p = start + 1
        while (p < state.rawString.length && state.rawString[p].isDigit()) {
            p++
        }

        // Verificar si después de los dígitos hay un caracter de formato válido
        if (p < state.rawString.length) {
            val formatChar = state.rawString[p]
            // Validamos si es un caracter "single char" válido para placeholder
            // Usamos una lista laxa aquí, la validación estricta del tipo se hace al crear el placeholder
            if (formatChar.isLetter()) {
                val indexStr = state.rawString.substring(start + 1, p)
                val index = indexStr.toIntOrNull() ?: -1

                // KotlinPoet usa 1-based index en el string
                val end = p + 1

                if (index < 1) {
                    reportError(state, start, end, KPoetAssistantBundle.getMessage("positional.argument.index.invalid"))
                }

                addPlaceholder(state, formatChar.toString(), PlaceholderBinding.Positional(index), start, end)
                state.hasPositional = true
                state.cursor = end
                return true
            }
        }
        return false
    }

    /**
     * Intenta parsear argumentos relativos: %L, %S
     */
    private fun tryParseRelative(state: ParseState, start: Int): Boolean {
        val nextChar = state.peek() ?: return false

        // En Relative, es simplemente % + Carácter
        if (nextChar.isLetter()) {
            val end = start + 2
            addPlaceholder(state, nextChar.toString(), PlaceholderBinding.Relative, start, end)
            state.hasRelative = true
            state.cursor = end
            return true
        }
        return false
    }

    /**
     * Helper para agregar el placeholder y validar si el Kind es conocido.
     */
    private fun addPlaceholder(state: ParseState, kindStr: String, binding: PlaceholderBinding, start: Int, end: Int) {
        // Convertimos el char al enum. CodeBlock usa 'first()' del grupo capturado.
        // Si fromChar retorna null, significa que es sintaxis válida estructuralmente (ej. %Z)
        // pero semánticamente inválida en KotlinPoet estándar.
        val char = kindStr.first()
        val kind = FormatKind.fromChar(char)

        if (kind == null) {
            // Reportamos error pero creamos un placeholder "inválido" para que el IDE lo muestre
            reportError(state, start, end, KPoetAssistantBundle.getMessage("argument.format.invalid.type", kindStr))
            // Podríamos agregarlo con un Kind especial o ignorarlo en la lista de placeholders válidos.
            // Aquí optamos por solo reportar el error en `errors` y NO agregarlo a `placeholders`
            // para evitar crash en lógica posterior que asuma Kinds válidos.
        } else {
            state.placeholders.add(PlaceholderSpec(kind, binding, start until end))
        }
    }

    private fun reportError(state: ParseState, start: Int, end: Int, @Nls msg: String) {
        state.errors.add(FormatProblem(ProblemSeverity.ERROR, msg, ProblemTarget.TextRange(start until end)))
    }

    private fun buildModel(rawText: String, state: ParseState): FormatStringModel {
        // Determinar estilo final
        val style = when {
            state.errors.isNotEmpty() && state.placeholders.isEmpty() -> FormatStringModel.FormatStyle.None
            (state.hasNamed && (state.hasPositional || state.hasRelative)) -> FormatStringModel.FormatStyle.Mixed
            (state.hasPositional && state.hasRelative) -> FormatStringModel.FormatStyle.Mixed
            state.hasNamed -> FormatStringModel.FormatStyle.Named
            state.hasPositional -> FormatStringModel.FormatStyle.Positional
            state.hasRelative -> FormatStringModel.FormatStyle.Relative
            else -> FormatStringModel.FormatStyle.None
        }

        // Si detectamos mezcla inválida y no había errores explícitos, agregamos uno global
        if (style == FormatStringModel.FormatStyle.Mixed) {
            // Nota: El rango es 0 para indicar error global del string, o podríamos calcular el rango del primer conflicto
            reportError(state, 0, rawText.length, KPoetAssistantBundle.getMessage("argument.format.invalid.mix"))
        }

        return FormatStringModel(
            rawText = rawText,
            style = style,
            placeholders = state.placeholders,
            controlSymbols = state.controlSymbols,
            errors = state.errors,
        )
    }
}
