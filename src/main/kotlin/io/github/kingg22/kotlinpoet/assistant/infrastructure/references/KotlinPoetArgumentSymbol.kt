package io.github.kingg22.kotlinpoet.assistant.infrastructure.references

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Representa un argumento de KotlinPoet como un destino navegable.
 * @property expression argumento de la función, no es el string template
 * @property argumentIndex índice para poder identificar el argumento dentro de la llamada
 */
@Suppress("UnstableApiUsage")
data class KotlinPoetArgumentSymbol(val expression: KtExpression) :
    Symbol,
    SearchTarget,
    NavigatableSymbol {
    override val usageHandler: UsageHandler get() = UsageHandler.createEmptyUsageHandler(expression.text)

    override fun createPointer(): Pointer<out KotlinPoetArgumentSymbol> = Pointer.delegatingPointer(
        SmartPointerManager.createPointer(expression),
    ) { ktExpression: KtExpression ->
        KotlinPoetArgumentSymbol(ktExpression)
    }

    override fun presentation(): TargetPresentation = TargetPresentation.builder(expression.text).presentation()

    override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = listOf(
        SymbolNavigationService.getInstance().psiElementNavigationTarget(expression),
    )

    fun getFormatString(): KtStringTemplateExpression? {
        val callExpression = PsiTreeUtil.getParentOfType(expression, KtCallExpression::class.java) ?: return null
        // Normalmente en KotlinPoet (addCode, addStatement), el primer argumento es el formato
        val firstArg = callExpression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
        // safe cast to prevent exceptions
        return firstArg as? KtStringTemplateExpression
    }
}
