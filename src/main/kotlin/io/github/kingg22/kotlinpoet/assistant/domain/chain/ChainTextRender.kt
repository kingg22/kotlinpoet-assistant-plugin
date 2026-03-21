package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.ControlSymbolPart
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.FormatLiteral
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.NestedCodeBlockPart
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.ResolvedPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.UnresolvedPlaceholder
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType

/**
 * Renders a list of [MethodEmissionContribution]s into human-readable, indented output.
 *
 * Unlike [MethodEmissionContribution.approximateText] (which outputs raw characters including
 * `«»⇥⇤`), this renderer:
 *
 * - Applies [EmissionState] transitions to track the current indent level
 * - Translates `⇥`/`⇤` into actual indentation (2 spaces per level)
 * - Strips `«`/`»` statement markers (they are invisible in the final output)
 * - Expands `%%` → `%`, `·` → space, `♢` → space
 * - Handles `\n` in literal parts as real line breaks
 * - Expands [EmittedPart.NestedCodeBlockPart] recursively
 *
 * ## Pure function
 * No PSI, no IntelliJ dependencies, no side effects. Safe to call from any context.
 *
 * ## Rendering contract
 * The result is an **approximation** of the runtime output. Parts with
 * [EmittedPart.UnresolvedPlaceholder] are rendered as their original token (e.g., `%S`).
 *
 * @see ChainRenderer
 */
fun renderChain(contributions: List<MethodEmissionContribution>): String = ChainRenderer()
    .apply { renderContribution(contributions) }
    .result()

// ── Internal renderer state ────────────────────────────────────────────────────

private class ChainRenderer {
    private val sb = StringBuilder()
    private var indentLevel = 0
    private var atLineStart = true

    fun renderContribution(contributions: List<MethodEmissionContribution>) {
        contributions.flatMap { it.parts }.forEach { renderPart(it) }
    }

    fun renderContribution(contribution: MethodEmissionContribution) {
        contribution.parts.forEach { renderPart(it) }
    }

    fun result(): String = sb.toString().trimEnd('\n')

    // ── Part dispatchers ───────────────────────────────────────────────────────

    private fun renderPart(part: EmittedPart) {
        when (part) {
            is FormatLiteral -> appendText(part.text)
            is ResolvedPlaceholder -> appendText(part.resolvedText)
            is ControlSymbolPart -> renderControlSymbol(part.symbolType)
            is NestedCodeBlockPart -> renderNestedBlock(part)
            is UnresolvedPlaceholder -> appendText('[' + part.placeholder.tokenText() + ']')
        }
    }

    private fun renderControlSymbol(symbolType: SymbolType) {
        // statement begin, end — invisible
        when (symbolType) {
            SymbolType.STATEMENT_BEGIN, SymbolType.STATEMENT_END -> {}
            SymbolType.INDENT -> indentLevel = (indentLevel + 1).coerceAtLeast(0)
            SymbolType.OUTDENT -> indentLevel = (indentLevel - 1).coerceAtLeast(0)
            SymbolType.LITERAL_PERCENT -> appendText("%")
            SymbolType.SPACE -> appendText(" ")
            SymbolType.SPACE_OR_NEW_LINE -> appendText(" ")
            else -> error("Unreachable path: $symbolType")
        }
    }

    private fun renderNestedBlock(part: NestedCodeBlockPart) {
        if (part.nestedContributions.isEmpty()) {
            appendText('[' + part.placeholder.tokenText() + ']')
            return
        }
        // Delegate to a fresh renderer for the nested block, preserving the outer
        // indent level by prepending it to each line of the nested output.
        val nestedText = renderChain(part.nestedContributions)
        if (nestedText.isNotEmpty()) {
            appendText(nestedText)
        } else {
            appendText('[' + part.placeholder.tokenText() + ']')
        }
    }

    // ── Text output with indent tracking ──────────────────────────────────────

    /**
     * Appends [text] to the output, inserting [indentLevel] × 2-space indent at
     * the beginning of each line (after `\n`).
     */
    private fun appendText(text: String) {
        for (char in text) {
            when (char) {
                '\n' -> {
                    sb.append('\n')
                    atLineStart = true
                }

                else -> {
                    if (atLineStart) {
                        repeat(indentLevel) { sb.append("  ") }
                        atLineStart = false
                    }
                    sb.append(char)
                }
            }
        }
    }
}
