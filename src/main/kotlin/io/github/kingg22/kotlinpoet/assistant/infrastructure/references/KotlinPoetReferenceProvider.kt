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
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.domain.binding.BindingEngineResolver
import io.github.kingg22.kotlinpoet.assistant.domain.extractor.FormatContextExtractorRegistry
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.infrastructure.toTextRange
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.ValueArgument

class KotlinPoetReferenceProvider(
    private val extractorRegistry: FormatContextExtractorRegistry = FormatContextExtractorRegistry,
    private val bindingResolver: BindingEngineResolver = BindingEngineResolver,
) : PsiSymbolReferenceProvider {
    private val logger = thisLogger()

    override fun getReferences(
        element: PsiExternalReferenceHost,
        hints: PsiSymbolReferenceHints,
    ): Collection<PsiSymbolReference> {
        if (element !is KtStringTemplateExpression) return emptyList()
        // Recuperamos la llamada completa, que es el contexto compartido
        val call = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java) ?: return emptyList()

        try {
            // 1. Extraer Modelo (PSI -> Dominio)
            // El extractor calcula el baseOffset y nos da un modelo con rangos ABSOLUTOS
            val context = extractorRegistry.extract(call) ?: return emptyList()

            // Si hay errores de sintaxis graves, abortamos references
            if (context.format.errors.isNotEmpty()) {
                logger.trace("Skipping KotlinPoet link references due to earlier parser errors")
                return emptyList()
            }

            // 2. Realizar Binding (Lógica de Dominio)
            // Aquí el dominio nos dice: "El placeholder en rango X se une con el Argumento Y"
            val boundPlaceholders = bindingResolver.forStyle(context.format.style)
                .bind(context.format, context.arguments)

            // 3. Mapear de vuelta (Dominio -> PSI)
            val references = mutableListOf<PsiSymbolReference>()
            val args = call.valueArguments // La lista PSI real

            for ((placeholder, argValue) in boundPlaceholders) {
                val targetExpression: KtExpression? = if (argValue == null) {
                    if (placeholder.binding is PlaceholderSpec.PlaceholderBinding.Named && args.size >= 2) {
                        args[1].getArgumentExpression()
                    } else {
                        continue
                    }
                } else {
                    // A. Resolver el PSI Target usando el ID del ArgumentValue
                    resolvePsiTarget(args, argValue)
                }

                if (targetExpression != null) {
                    // B. Calcular el rango relativo para la referencia
                    // Formula: Rango Absoluto (Dominio) - Inicio del Elemento Host (PSI)

                    // Rango absoluto del placeholder
                    val absolute = placeholder.textRange.toTextRange()

                    // Inicio real del host (STRING_TEMPLATE)
                    val hostStart = element.textRange.startOffset
                    val hostEnd = element.textRange.endOffset

                    // Recortamos el rango al host antes de convertirlo
                    val clampedStart = maxOf(absolute.startOffset, hostStart)
                    val clampedEnd = minOf(absolute.endOffset, hostEnd)

                    // Si no hay intersección real, abortamos
                    if (clampedStart >= clampedEnd) {
                        logger.debug(
                            "Placeholder '$placeholder' range '$absolute' does not intersect host '$element' (${element.textRange})",
                        )
                        continue
                    }

                    // Convertimos a rango relativo
                    val relativeRange = TextRange.create(
                        clampedStart - hostStart,
                        clampedEnd - hostStart,
                    )

                    // asume the argument is the second one because the first es the string template
                    val symbol = KotlinPoetArgumentSymbol(targetExpression, argValue?.index ?: 1)

                    // Validación final (ya debería ser segura)
                    if (relativeRange.startOffset >= 0 && relativeRange.endOffset <= element.textLength) {
                        references += KotlinPoetPlaceholderReference(element, relativeRange, symbol)
                    }
                }
            }
            return references
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            logger.error("Error trying to link symbol references of KotlinPoet", e)
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
