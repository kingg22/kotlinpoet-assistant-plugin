package io.github.kingg22.kotlinpoet.assistant.infrastructure.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ExceptionUtil
import com.intellij.util.ProcessingContext
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.looksLikeKotlinPoetCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinPoetCompletionContributor :
    CompletionContributor(),
    DumbAware {

    init {
        val pattern: PsiElementPattern.Capture<PsiElement> = psiElement().inside(KtStringTemplateExpression::class.java)
        extend(CompletionType.BASIC, pattern, PlaceholderCompletionProvider)
    }

    private object PlaceholderCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val logger = thisLogger()

        /**
         * All recognized placeholder kinds with their UI labels and bundle keys.
         * The `%%` control symbol is appended separately since it has no [PlaceholderSpec.FormatKind].
         */
        private val placeholderKind by lazy {
            listOf(
                buildPlaceholderItem(
                    PlaceholderSpec.FormatKind.LITERAL,
                    "Literal",
                    "doc.placeholder.L.desc",
                ),
                buildPlaceholderItem(
                    PlaceholderSpec.FormatKind.STRING,
                    "String",
                    "doc.placeholder.S.desc",
                ),
                buildPlaceholderItem(PlaceholderSpec.FormatKind.TYPE, "Type", "doc.placeholder.T.desc"),
                buildPlaceholderItem(PlaceholderSpec.FormatKind.NAME, "Name", "doc.placeholder.N.desc"),
                buildPlaceholderItem(
                    PlaceholderSpec.FormatKind.MEMBER,
                    "Member",
                    "doc.placeholder.M.desc",
                ),
                buildPlaceholderItem(
                    PlaceholderSpec.FormatKind.STRING_TEMPLATE,
                    "String template",
                    "doc.placeholder.P.desc",
                ),
                buildPercentItem(),
            )
        }

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            try {
                val hostString = parameters.position.parentOfType<KtStringTemplateExpression>() ?: run {
                    logger.trace("Skipping completion inside non-string expression: ${parameters.position.text}")
                    return
                }
                val call = hostString.parentOfType<KtCallExpression>() ?: run {
                    logger.trace("Skipping completion inside non-call expression: ${hostString.text}")
                    return
                }

                val kotlinPoetCallAnalysis = getCachedAnalysis(call)
                if (kotlinPoetCallAnalysis == null ||
                    (DumbService.isDumb(call.project) && !call.looksLikeKotlinPoetCall())
                ) {
                    logger.trace("Skipping completion inside non-KotlinPoet call: ${call.text}")
                    return
                }

                val offset = parameters.offset
                val document = parameters.editor.document
                val startOffset = hostString.textRange.startOffset
                val currentText = document.getText(TextRange(startOffset, offset))

                if (currentText.endsWith("%") && !currentText.endsWith("%%")) {
                    result.withPrefixMatcher("%").addAllElements(placeholderKind)
                }
            } catch (e: Exception) {
                if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
                logger.error("Error trying to provide completion KotlinPoet format string", e)
            }
        }
    }
}

// ── LookupElement builders ─────────────────────────────────────────────────────

/**
 * Builds a completion item for a format placeholder.
 *
 * ## Presentation
 * ```
 * %L  [bold]    Literal [grayed right]
 *   Emits a literal value… [grayed tail, plain text, truncated]
 * ```
 */
private fun buildPlaceholderItem(
    kind: PlaceholderSpec.FormatKind,
    typeLabel: String,
    descBundleKey: String,
): LookupElement {
    val lookupString = "%${kind.value}"
    val fullDesc = KPoetAssistantBundle.getMessage(descBundleKey)
    val tailText = fullDesc.stripHtml().truncated(TAIL_TEXT_MAX_LENGTH)

    return LookupElementBuilder
        .create(lookupString)
        .withPresentableText(lookupString)
        .withTypeText(typeLabel, true)
        .withTailText("  $tailText", true)
        .withBoldness(true)
}

/**
 * Builds the `%%` (literal percent sign) completion item.
 *
 * The tail text is sufficient to convey the meaning inline.
 */
private fun buildPercentItem(): LookupElement {
    val desc = KPoetAssistantBundle.getMessage("doc.control.percent.desc")
    return LookupElementBuilder
        .create("%%")
        .withPresentableText("%%")
        .withTypeText("Percent sign", true)
        .withTailText("  ${desc.stripHtml().truncated(TAIL_TEXT_MAX_LENGTH)}", true)
        .withBoldness(true)
}

private const val TAIL_TEXT_MAX_LENGTH = 80

private fun String.stripHtml(): String = replace(Regex("<[^>]+>"), "").trim()

private fun String.truncated(max: Int): String = if (length > max) take(max) + "…" else this
