package io.github.kingg22.kotlinpoet.assistant.infrastructure.references

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.psi.SmartPointerManager
import org.jetbrains.kotlin.psi.KtExpression

/** Representa un argumento de KotlinPoet como un destino navegable. */
@Suppress("UnstableApiUsage")
data class KotlinPoetArgumentSymbol(val expression: KtExpression) :
    Symbol,
    NavigatableSymbol {

    override fun createPointer(): Pointer<out KotlinPoetArgumentSymbol> {
        val pointer = SmartPointerManager.createPointer(expression)
        return Pointer.delegatingPointer(pointer) { KotlinPoetArgumentSymbol(it) }
    }

    override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = listOf(
        SymbolNavigationService.getInstance().psiElementNavigationTarget(expression),
    )
}
