package io.github.kingg22.kotlinpoet.assistant.infrastructure.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.adapters.psi.PsiTextRangeHelper
import io.github.kingg22.kotlinpoet.assistant.domain.binding.BindingEngineResolver
import io.github.kingg22.kotlinpoet.assistant.domain.extractor.FormatContextExtractorRegistry
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRange
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.ValueArgument

class KotlinPoetReferenceProvider(
    private val extractorRegistry: FormatContextExtractorRegistry = FormatContextExtractorRegistry,
    private val bindingResolver: BindingEngineResolver = BindingEngineResolver,
) : PsiSymbolReferenceProvider {
    private val logger = thisLogger()

    private fun isFirstArgument(element: PsiElement): Boolean {
        val valueArgument = PsiTreeUtil.getParentOfType(element, KtValueArgument::class.java)
            ?: return false
        val valueArgumentList = PsiTreeUtil.getParentOfType(valueArgument, KtValueArgumentList::class.java)
            ?: return false
        return valueArgumentList.arguments.indexOf(valueArgument) == 0
    }

    override fun getReferences(
        element: PsiExternalReferenceHost,
        hints: PsiSymbolReferenceHints,
    ): Collection<PsiSymbolReference> {
        if (element !is KtStringTemplateExpression) return emptyList()

        // El contexto completo: CodeBlock.of("...", arg1, arg2)
        if (!isFirstArgument(element)) return emptyList()
        val call = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java)
            ?: return emptyList()

        try {
            // 1 PSI a Dominio
            val context = extractorRegistry.extract(call) ?: return emptyList()

            // Si el formato ya es inválido, no generamos referencias
            if (context.format.errors.isNotEmpty()) {
                logger.info("Skipping references due to format errors".trimIndent())
                return emptyList()
            }

            // 2 Dominio → Binding
            val boundPlaceholders = bindingResolver
                .forStyle(context.format.style)
                .bind(context.format, context.arguments)

            val references = mutableListOf<PsiSymbolReference>()
            val args: List<KtValueArgument> = call.valueArguments

            for ((placeholder, argValue) in boundPlaceholders) {
                val targetExpression: KtExpression = when {
                    argValue != null ->
                        resolvePsiTarget(args, argValue)

                    placeholder.binding is PlaceholderSpec.PlaceholderBinding.Named && args.size >= 2 ->
                        args[1].getArgumentExpression()

                    else -> continue
                } ?: continue

                // IMPORTANTE:
                // placeholder.textRange es RELATIVO al contenido del string
                val relativeRange = placeholder.textRange.toTextRange(PsiTextRangeHelper.getTextStartOffset(element))

                // Validación estricta del rango
                if (relativeRange.startOffset < 0 || relativeRange.endOffset > element.textLength) {
                    logger.warn(
                        "Invalid reference range $relativeRange for $placeholder, " +
                            "element text range: ${element.textRange}, element text: '${element.text}', " +
                            "element text length: ${element.textLength}",
                    )
                    continue
                }

                val symbol = KotlinPoetArgumentSymbol(expression = targetExpression)

                references += KotlinPoetPlaceholderReference(element, relativeRange, symbol)

                logger.debug(
                    "Created KotlinPoet reference: placeholder=$placeholder, $references, to $symbol",
                )
            }

            return references
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            logger.error("Failed to build KotlinPoet references", e)
            return emptyList()
        }
    }

    /**
     * Heurística / Resolución: Convierte el objeto de dominio 'ArgumentValue'
     * en el 'KtExpression' real.
     */
    private fun resolvePsiTarget(psiArgs: List<ValueArgument>, value: ArgumentValue): KtExpression? =
        if (value.isRelative || value.isPositional) {
            val index = value.index!!
            if (index in psiArgs.indices) {
                psiArgs[index].getArgumentExpression()
            } else {
                null
            }
        } else {
            if (psiArgs.size >= 2) {
                psiArgs[1].getArgumentExpression()
            } else {
                null
            }
        }

    override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()
}
