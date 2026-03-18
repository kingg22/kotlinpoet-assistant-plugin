package io.github.kingg22.kotlinpoet.assistant.domain.parser

import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.FormatStyle
import io.github.kingg22.kotlinpoet.assistant.domain.model.FormatStringModel.ParserIssueKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.PlaceholderBinding
import io.github.kingg22.kotlinpoet.assistant.domain.text.FormatText
import io.github.kingg22.kotlinpoet.assistant.domain.validation.FormatProblem
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemSeverity
import io.github.kingg22.kotlinpoet.assistant.domain.validation.ProblemTarget
import org.jetbrains.annotations.Nls

private val NAMED_ARGUMENT_PATTERN = "%([\\w_]+):(\\w)".toPattern()

/**
 * Parses a KotlinPoet format string from a [FormatText] (PSI-backed, segmented).
 *
 * ## Error vs Warning separation
 *
 * | Issue                              | Destination        | Reasoning                                   |
 * |------------------------------------|--------------------|---------------------------------------------|
 * | Mixed argument styles              | `errors` (blocking)| No binding engine can handle the mix        |
 * | forceNamed + non-Named style       | `errors` (blocking)| `addNamed` contract violation               |
 * | Dangling `%`                       | `warnings`         | Other placeholders remain valid             |
 * | Unknown placeholder type (`%Z`)    | `warnings`         | Placeholder simply skipped, rest is fine    |
 * | Invalid positional index (`%0L`)   | `warnings`         | Placeholder skipped, rest is fine           |
 *
 * Warnings carry a [ParserIssueKind] in [FormatProblem.data] so the inspection layer can pick
 * the right quick fix without re-parsing the message.
 *
 * ## Style flag invariant
 * `hasRelative` / `hasPositional` / `hasNamed` are only set to `true` when a placeholder is
 * successfully added (i.e. [addPlaceholder] returns `true`). This prevents an unknown type like
 * `%Z` from polluting the style determination.
 *
 * ## Safety guarantees
 * - The cursor always advances by at least 1 per iteration → no infinite loop.
 * - All `span(start, endExclusive)` calls are bounded to `[0, text.length]`.
 * - No exception is thrown for malformed input; problems are accumulated in `errors`/`warnings`.
 */
class StringFormatParserImpl : StringFormatParser {
    /** Actual parser state */
    private class ParseState(val text: FormatText) {
        val rawString: String = text.asString()
        val placeholders = mutableListOf<PlaceholderSpec>()
        val controlSymbols = mutableListOf<ControlSymbol>()

        /** Blocking problems — stop binding/validation when non-empty. */
        val errors = mutableListOf<FormatProblem>()

        /** Non-blocking warnings — surfaced via inspection + quick fix. */
        val warnings = mutableListOf<FormatProblem>()

        // Style flags — set only when a valid placeholder is successfully registered.
        var hasNamed = false
        var hasPositional = false
        var hasRelative = false

        var cursor = 0

        fun isAtEnd(): Boolean = cursor >= rawString.length
        fun current(): Char = rawString[cursor]
        fun peek(offset: Int = 1): Char? = rawString.getOrNull(cursor + offset)
    }

    override fun parse(text: FormatText, isNamedStyle: Boolean, methodName: String): FormatStringModel {
        val state = ParseState(text)

        while (!state.isAtEnd()) {
            val cursorBefore = state.cursor // safety: detect stalls

            if (state.text.isIndexInDynamic(state.cursor)) {
                state.cursor++
                continue
            }

            when {
                tryParseControl(state, state.cursor) -> {}
                state.current() == '%' -> parsePercentToken(state)
                else -> state.cursor++
            }

            // Absolute safety net: cursor must always advance. This should never trigger if
            // every parse path sets cursor correctly, but guards against future regressions.
            check(state.cursor > cursorBefore) {
                "Parser stalled at cursor=${state.cursor} in '${state.rawString}'"
            }
        }

        return buildModel(text, state, isNamedStyle, methodName)
    }

    // ── Token dispatcher ───────────────────────────────────────────────────────

    /**
     * Called when `%` is at the current cursor.
     * Priority order matches KotlinPoet's runtime: Named → Positional → Relative → fallback.
     */
    private fun parsePercentToken(state: ParseState) {
        val start = state.cursor

        if (tryParseNamed(state, start)) return
        if (tryParsePositional(state, start)) return
        if (tryParseRelative(state, start)) return

        // Fallback: dangling or unrecognised `%`.
        reportError(
            state,
            start,
            start + 1,
            KPoetAssistantBundle.getMessage("argument.format.invalid.incomplete"),
            ParserIssueKind.DANGLING_PERCENT,
        )
        state.cursor++ // must advance to avoid stall
    }

    // ── Named: %argumentName:X ─────────────────────────────────────────────────

    private fun tryParseNamed(state: ParseState, start: Int): Boolean {
        val text = state.rawString
        val colonIndex = text.indexOf(':', start)
        if (colonIndex == -1) return false

        val potentialEnd = (colonIndex + 2).coerceAtMost(text.length)
        val snippet = text.substring(start, potentialEnd)

        val matcher = NAMED_ARGUMENT_PATTERN.matcher(snippet)
        if (!matcher.lookingAt()) return false

        val name = matcher.group(1)
        val typeChar = matcher.group(2)
        val end = start + matcher.end()

        if (addPlaceholder(state, typeChar, PlaceholderBinding.Named(name), start, end)) {
            state.hasNamed = true
        }
        state.cursor = end
        return true
    }

    // ── Control symbols ────────────────────────────────────────────────────────

    private fun tryParseControl(state: ParseState, start: Int): Boolean {
        val char = state.peek(0) ?: return false
        val nextChar = state.peek(1)

        val controlType = if (char == '%') {
            if (nextChar != '%') return false
            ControlSymbol.SymbolType.LITERAL_PERCENT
        } else {
            ControlSymbol.SymbolType.fromString(char.toString())
        } ?: return false

        val end = start + controlType.value.length
        state.controlSymbols.add(ControlSymbol(controlType, state.text.span(start, end)))
        state.cursor = end
        return true
    }

    // ── Positional: %1L ────────────────────────────────────────────────────────

    private fun tryParsePositional(state: ParseState, start: Int): Boolean {
        val firstDigit = state.peek() ?: return false
        if (!firstDigit.isDigit()) return false

        var p = start + 1
        while (p < state.rawString.length && state.rawString[p].isDigit()) p++

        val formatChar = state.rawString.getOrNull(p) ?: return false
        if (!formatChar.isLetter()) return false

        val indexStr = state.rawString.substring(start + 1, p)
        val index = indexStr.toIntOrNull() ?: -1
        val end = p + 1

        if (index < 1) {
            // Non-blocking: placeholder skipped, binding continues for valid ones.
            reportError(
                state,
                start,
                end,
                KPoetAssistantBundle.getMessage("positional.argument.index.invalid"),
                ParserIssueKind.INVALID_POSITIONAL_INDEX,
                data = formatChar, // carries the format char so the quick fix can reconstruct %1X
            )
            state.cursor = end
            return true
        }

        if (addPlaceholder(state, formatChar.toString(), PlaceholderBinding.Positional(index), start, end)) {
            state.hasPositional = true
        }
        state.cursor = end
        return true
    }

    // ── Relative: %L ──────────────────────────────────────────────────────────

    private fun tryParseRelative(state: ParseState, start: Int): Boolean {
        val nextChar = state.peek() ?: return false
        if (!nextChar.isLetter()) return false

        val end = start + 2
        if (addPlaceholder(state, nextChar.toString(), PlaceholderBinding.Relative, start, end)) {
            state.hasRelative = true
        }
        state.cursor = end
        return true
    }

    // ── Placeholder registration ───────────────────────────────────────────────

    /**
     * Validates the kind char and adds the placeholder.
     *
     * @return `true` if the placeholder was added; `false` if the kind is unknown (a warning is
     * emitted and the caller must NOT set the style flag for this binding).
     */
    private fun addPlaceholder(
        state: ParseState,
        kindStr: String,
        binding: PlaceholderBinding,
        start: Int,
        end: Int,
    ): Boolean {
        val char = kindStr.first()
        val kind = FormatKind.fromChar(char)
        return if (kind == null) {
            reportError(
                state,
                start,
                end,
                KPoetAssistantBundle.getMessage("argument.format.invalid.type", kindStr),
                ParserIssueKind.UNKNOWN_PLACEHOLDER_TYPE,
            )
            false
        } else {
            state.placeholders.add(PlaceholderSpec(kind, binding, state.text.span(start, end)))
            true
        }
    }

    // ── Problem helpers ────────────────────────────────────────────────────────

    /**
     * Records a **blocking** error. When any blocking error exists,
     * [FormatStringModel.errors] is non-empty and analysis stops at the parse stage.
     */
    private fun reportError(
        state: ParseState,
        start: Int,
        end: Int,
        @Nls msg: String,
        kind: ParserIssueKind,
        data: Any? = null,
    ) {
        state.errors.add(
            FormatProblem(
                ProblemSeverity.ERROR,
                msg,
                ProblemTarget.TextSpanTarget(state.text.span(start, end)),
                kind = kind,
                data = data,
            ),
        )
    }

    /**
     * Records a **non-blocking** warning with an optional [data] payload for the inspection layer.
     * The [kind] tag is always stored in [FormatProblem.data] unless [data] overrides it.
     */
    private fun reportWarning(
        state: ParseState,
        start: Int,
        end: Int,
        @Nls msg: String,
        kind: ParserIssueKind,
        data: Any? = null,
    ) {
        state.warnings.add(
            FormatProblem(
                ProblemSeverity.WARNING,
                msg,
                ProblemTarget.TextSpanTarget(state.text.span(start, end)),
                kind = kind,
                data = data,
            ),
        )
    }
    // ── Model construction ─────────────────────────────────────────────────────

    private fun buildModel(
        text: FormatText,
        state: ParseState,
        isNamedStyle: Boolean,
        methodName: String,
    ): FormatStringModel {
        val style = when {
            state.errors.isNotEmpty() && state.placeholders.isEmpty() -> FormatStyle.None
            state.hasNamed && (state.hasPositional || state.hasRelative) -> FormatStyle.Mixed
            state.hasPositional && state.hasRelative -> FormatStyle.Mixed
            state.hasNamed -> FormatStyle.Named
            state.hasPositional -> FormatStyle.Positional
            state.hasRelative -> FormatStyle.Relative
            else -> FormatStyle.None
        }

        // ── Style-method mismatch → MIXED_STYLES blocking error ────────────────
        val mixError = resolveMixError(style, isNamedStyle, methodName, text)
        if (mixError != null) {
            state.errors.add(mixError)
        }

        return FormatStringModel(
            text = text,
            style = style,
            placeholders = state.placeholders,
            controlSymbols = state.controlSymbols,
            errors = state.errors,
        )
    }

    /**
     * Returns a blocking [FormatProblem] when the parsed [style] is incompatible with the
     * calling method's expectations, or `null` when everything is consistent.
     *
     * Four distinct scenarios:
     * 1. Intrinsic mix (relative + positional, or named + non-named): method-agnostic.
     * 2. `isNamedStyle = true` but style is Relative or Positional: wrong placeholders for `addNamed`.
     * 3. `isNamedStyle = false` but style is Named: `%food:L` used in `add()`.
     * 4. No error — styles are consistent.
     */
    private fun resolveMixError(
        style: FormatStyle,
        isNamedStyle: Boolean,
        methodName: String,
        text: FormatText,
    ): FormatProblem? {
        if (text.isEmpty()) return null

        val msg = when {
            // Intrinsic mix
            style == FormatStyle.Mixed -> KPoetAssistantBundle.getMessage("argument.format.invalid.mix")

            // addNamed() used but no named placeholders found
            isNamedStyle && style != FormatStyle.Named && style != FormatStyle.None ->
                KPoetAssistantBundle.getMessage(
                    "argument.format.invalid.mix.should.be.named",
                    methodName.ifEmpty { "addNamed" },
                )

            // add() / addStatement() used but named placeholders found
            !isNamedStyle && style == FormatStyle.Named ->
                KPoetAssistantBundle.getMessage(
                    "argument.format.invalid.mix.should.be.vararg",
                    methodName.ifEmpty { "add" },
                )

            else -> return null
        }

        return FormatProblem(
            severity = ProblemSeverity.ERROR,
            message = msg,
            target = ProblemTarget.TextSpanTarget(text.fullSpan()),
            kind = ParserIssueKind.MIXED_STYLES,
        )
    }
}
