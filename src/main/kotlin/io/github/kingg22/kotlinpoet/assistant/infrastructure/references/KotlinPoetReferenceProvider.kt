package io.github.kingg22.kotlinpoet.assistant.infrastructure.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.domain.extractor.extractMapEntry
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.putCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRange
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.ValueArgument

@Suppress("UnstableApiUsage")
class KotlinPoetReferenceProvider : PsiSymbolReferenceProvider {
    private val logger = thisLogger()

    override fun getReferences(
        element: PsiExternalReferenceHost,
        hints: PsiSymbolReferenceHints,
    ): Collection<PsiSymbolReference> {
        if (element !is KtStringTemplateExpression) return emptyList()
        if (!hintsCheck(hints)) return emptyList()

        // El contexto completo: CodeBlock.of("...", arg1, arg2)
        if (!isFirstArgument(element)) return emptyList()
        val call = element.parentOfType<KtCallExpression>() ?: return emptyList()

        try {
            val boundPlaceholders = getCachedAnalysis(call)
                ?.bind()
                ?.also { putCachedAnalysis(call, it) }
                ?.bounds
                ?: return emptyList()

            val references = mutableListOf<PsiSymbolReference>()
            val args: List<KtValueArgument> = call.valueArguments
            val offsetInElement = hints.offsetInElement
            val hintedTarget = hints.target as? KotlinPoetArgumentSymbol

            for ((placeholder, argValue) in boundPlaceholders) {
                val targetExpression: KtExpression = when {
                    argValue != null -> resolvePsiTarget(call, args, argValue)

                    placeholder.binding is PlaceholderSpec.PlaceholderBinding.Named && args.size >= 2 ->
                        resolveNamedTarget(args[1].getArgumentExpression(), placeholder.binding.name)
                            ?: args[1].getArgumentExpression()

                    else -> continue
                } ?: continue

                val elementRange = element.textRange
                val matchingRanges = placeholder.span.ranges.filter { r ->
                    r.first >= elementRange.startOffset && r.last < elementRange.endOffset
                }
                if (matchingRanges.size != 1) {
                    logger.debug("Skipping placeholder outside current string: $placeholder")
                    continue
                }

                val absoluteRange = matchingRanges.single()
                val relativeRange = absoluteRange.toTextRange().shiftLeft(elementRange.startOffset)

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
                if (hintedTarget != null && symbol != hintedTarget) continue
                if (offsetInElement >= 0 && !relativeRange.containsOffset(offsetInElement)) continue

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

    override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()
}

private fun isFirstArgument(element: PsiElement): Boolean {
    val valueArgument = element.parentOfType<KtValueArgument>() ?: return false
    val valueArgumentList = valueArgument.parentOfType<KtValueArgumentList>() ?: return false
    return valueArgumentList.arguments.indexOf(valueArgument) == 0
}

@Suppress("UnstableApiUsage")
private fun hintsCheck(hints: PsiSymbolReferenceHints): Boolean {
    if (!hints.referenceClass.isAssignableFrom(KotlinPoetPlaceholderReference::class.java)) {
        return false
    }

    val targetClass = hints.targetClass
    if (targetClass != null && !targetClass.isAssignableFrom(KotlinPoetArgumentSymbol::class.java)) {
        return false
    }

    val target = hints.target
    return target == null || target is KotlinPoetArgumentSymbol
}

/**
 * Heurística / Resolución: Convierte el objeto de dominio 'ArgumentValue'
 * en el 'KtExpression' real.
 */
private fun resolvePsiTarget(
    call: KtCallExpression,
    psiArgs: List<ValueArgument>,
    value: ArgumentValue,
): KtExpression? {
    if (value.isNamed) {
        val span = value.span?.singleRangeOrNull() ?: return null
        val element = call.containingFile.findElementAt(span.first) ?: return null
        return element.parentOfType<KtExpression>(withSelf = true)
            ?.takeIf { it.textRange.startOffset >= span.first && it.textRange.endOffset <= span.last + 1 }
    }
    val index = value.index
    // implies isPositional
    if (index != null && index in psiArgs.indices) {
        return psiArgs[index].getArgumentExpression()
    }
    return null
}

private fun resolveNamedTarget(mapExpression: KtExpression?, name: String): KtExpression? {
    val call = mapExpression as? KtCallExpression ?: return null
    if (DumbService.isDumb(mapExpression.project)) return null
    analyze(mapExpression) {
        val mapArgs = call.valueArguments
        for (entryArg in mapArgs) {
            val entryExpr = entryArg.getArgumentExpression()
            val (key, valueExpr) = extractMapEntry(entryExpr) ?: continue
            if (key == name) return valueExpr
        }
    }
    return null
}
