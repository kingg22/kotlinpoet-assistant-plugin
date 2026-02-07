package io.github.kingg22.kotlinpoet.assistant.infrastructure.references

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.model.Pointer
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinPoetFormatUsageSearcher : UsageSearcher {
    private lateinit var provider: KotlinPoetReferenceProvider

    override fun collectImmediateResults(parameters: UsageSearchParameters): Collection<Usage> {
        val target = parameters.target
        if (target is KotlinPoetArgumentSymbol) {
            val formatHost = target.getFormatString() ?: return emptyList()
            val argumentExpression = target.expression

            // Reutilizamos la lógica del provider para encontrar las referencias
            return getFormatUsages(target, formatHost, argumentExpression)
        }
        return emptyList()
    }

    private fun getFormatUsages(
        symbol: KotlinPoetArgumentSymbol,
        formatHost: KtStringTemplateExpression,
        arg: KtExpression,
    ): List<Usage> {
        if (!this::provider.isInitialized) provider = KotlinPoetReferenceProvider()
        // 1. Obtenemos todas las referencias que el provider genera para ese String
        val allRefs = provider.getReferences(formatHost, PsiSymbolReferenceHints.offsetHint(1))

        // Añadimos la definición del argumento mismo a la lista de usos
        return allRefs
            .filter { ref -> ref.resolvesTo(symbol) }
            .map { ref -> PsiUsage.textUsage(ref) }
            .plus(PsiElementUsage(arg))
    }

    /**
     * A class, which represents a PsiUsage, based on concrete PsiElement.
     * @see PsiUsage
     */
    class PsiElementUsage(private val myArg: PsiElement) : PsiUsage {
        @Suppress("UnstableApiUsage")
        override fun createPointer(): Pointer<out PsiUsage?> = Pointer.hardPointer(this)
        override val file: PsiFile get() = myArg.containingFile
        override val range: TextRange get() = myArg.textRange
        override val declaration: Boolean get() = true
    }
}
