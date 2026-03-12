package io.github.kingg22.kotlinpoet.assistant.infrastructure.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.ProcessingContext
import io.github.kingg22.kotlinpoet.assistant.application.usecase.isKotlinPoetCall
import io.github.kingg22.kotlinpoet.assistant.infrastructure.looksLikeKotlinPoetCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinPoetCompletionContributor :
    CompletionContributor(),
    DumbAware {

    init {
        // Patrón: Estamos dentro de un String que es argumento de una función de KotlinPoet
        val pattern: PsiElementPattern.Capture<PsiElement> = psiElement().inside(KtStringTemplateExpression::class.java)

        extend(CompletionType.BASIC, pattern, PlaceholderCompletionProvider)
    }

    private object PlaceholderCompletionProvider : CompletionProvider<CompletionParameters>() {
        /**
         * @see io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec.FormatKind
         * @see io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType.LITERAL_PERCENT
         */
        private val placeholderKind = listOf(
            // Literal
            createPlaceholder('L', "Literal"),
            // String
            createPlaceholder('S', "String"),
            // Type
            createPlaceholder('T', "Type"),
            // Member
            createPlaceholder('M', "Member"),
            // Name
            createPlaceholder('N', "Name"),
            // String template
            createPlaceholder('P', "String template"),

            // Special from Control Symbol
            createPlaceholder('%', "Percent sign"),
        )

        private val logger = thisLogger()

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            try {
                // 1. Verificar si realmente estamos en un contexto de KotlinPoet (usando tu Extractor)
                val hostString = PsiTreeUtil.getParentOfType(
                    parameters.position,
                    KtStringTemplateExpression::class.java,
                ) ?: run {
                    logger.trace("Skipping completion inside non-string expression: ${parameters.position.text}")
                    return
                }
                val call = PsiTreeUtil.getParentOfType(hostString, KtCallExpression::class.java) ?: run {
                    logger.trace("Skipping completion inside non-call expression: ${hostString.text}")
                    return
                }

                val isKotlinPoetCall = if (DumbService.isDumb(parameters.position.project)) {
                    call.looksLikeKotlinPoetCall()
                } else {
                    call.isKotlinPoetCall()
                }

                // Si no es KotlinPoet, salimos
                if (!isKotlinPoetCall) {
                    logger.trace("Skipping completion inside non-KotlinPoet call: ${call.text}")
                    return
                }

                // 2. Si el usuario escribió '%', sugerimos los tipos básicos
                val offset = parameters.offset

                // Obtenemos el texto justo antes del cursor
                val document = parameters.editor.document
                val startOffset = hostString.textRange.startOffset
                val currentText = document.getText(TextRange(startOffset, offset))

                if (currentText.endsWith("%")) {
                    result.withPrefixMatcher("%").addAllElements(placeholderKind)
                }
            } catch (e: Exception) {
                if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
                // Fail-safe para no romper el editor si el binding falla inesperadamente
                logger.error("Error trying to provide completion KotlinPoet format string", e)
            }
        }

        private fun createPlaceholder(char: Char, type: String): LookupElementBuilder =
            LookupElementBuilder.create("%$char")
                .withPresentableText("%$char")
                .withTypeText(type)
                .withBoldness(true)
    }
}
