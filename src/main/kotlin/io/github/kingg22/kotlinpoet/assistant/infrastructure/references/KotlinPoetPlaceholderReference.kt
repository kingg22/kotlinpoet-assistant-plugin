package io.github.kingg22.kotlinpoet.assistant.infrastructure.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/** Representa una referencia a un argumento de KotlinPoet como un destino navegable. */
class KotlinPoetPlaceholderReference(
    private val element: PsiElement,
    private val range: TextRange,
    private val targetSymbol: KotlinPoetArgumentSymbol,
) : PsiSymbolReference {
    override fun getElement(): PsiElement = element
    override fun getRangeInElement(): TextRange = range
    override fun resolveReference(): Collection<Symbol> = listOf(targetSymbol)
}
