package io.github.kingg22.kotlinpoet.assistant.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.kingg22.kotlinpoet.assistant.Constants
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

private val placeholderRegex = "%[LSTMNP]".toRegex()
private val logger = logger<KotlinPoetAnnotator>()

/**
 * Handle the template of [KotlinPoet CodeBlock format strings](https://square.github.io/kotlinpoet/code-block-format-strings/)
 */
class KotlinPoetAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // 1. Solo strings Kotlin
        if (element !is KtStringTemplateExpression) {
            logger.trace("Not a string template of Kotlin: ${element::class.simpleName}")
            return
        }

        // 2. Debe estar dentro de una llamada
        val call = element.parent?.parent?.parent as? KtCallExpression ?: run {
            logger.trace("Not a call expression: ${element.parent?.parent ?: "null"}")
            return
        }

        // 3. Debe ser argumento 0
        val args: List<KtValueArgument> = call.valueArguments
        if (args.firstOrNull()?.getArgumentExpression() != element) {
            logger.trace("Not argument 0: ${args.firstOrNull()?.getArgumentExpression()}")
            return
        }

        // 4. Debe ser add(...)
        if (call.calleeExpression?.text != "add") {
            logger.trace("Not add(): ${call.calleeExpression?.text}")
            return
        }

        // 5. Confirmar KotlinPoet (K2)
        if (!isKotlinPoetCall(call)) {
            logger.trace("Not a KotlinPoet call: ${call.text}")
            return
        }

        // 6. Extraer el texto
        val templateText = element.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString("") { it.text }

        // 7. Extraer los placeholders
        val placeholders = placeholderRegex.findAll(templateText).toList()

        // 8. Extraer los argumentos sin el template
        val valueArgs = call.valueArguments.drop(1)

        placeholders.forEachIndexed { index, match ->
            val arg = valueArgs.getOrNull(index)

            // validar argumento
            if (arg == null) {
                logger.debug("Missing argument for ${match.value}")
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "Missing argument for ${match.value}",
                ).range(element.textRange).create()
                return
            }

            val isValid = analyze(arg) {
                val type = arg.getArgumentExpression()?.expressionType
                type?.isPrimitiveOrString() == true
            }

            if (!isValid) {
                logger.debug("Invalid argument for ${match.value}")
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "Invalid argument for ${match.value}",
                ).range(arg.textRange).create()
            }

            // UX
            val start = element.textRange.startOffset + match.range.first + 1
            val range = TextRange(start, start + 2)

            logger.debug("Found placeholder ${match.value} at ${range.startOffset}")
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR) // template format color
                .create()
        }
    }

    private fun KaType.isPrimitiveOrString(): Boolean {
        val fqName = this.symbol?.classId?.asSingleFqName()?.asString() ?: return false
        return fqName in setOf(
            "kotlin.String",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Boolean",
            "kotlin.Double",
            "kotlin.Float",
        )
    }

    private fun isKotlinPoetCall(call: KtCallExpression): Boolean {
        return analyze(call) {
            val resolvedCall = call.resolveToCall() ?: run {
                logger.debug("Not a resolved call: ${call.text}")
                return false
            }
            val callMember: KaCallableMemberCall<*, *> = resolvedCall.singleCallOrNull() ?: run {
                logger.debug("Not a single call of callable member: ${call.text}")
                return false
            }
            val receiverType = callMember.partiallyAppliedSymbol.dispatchReceiver?.type ?: run {
                logger.debug("No dispatch receiver type: ${call.text}")
                return false
            }
            receiverType.isClassType(Constants.ClassIds.CODE_BLOCK) ||
                receiverType.isClassType(Constants.ClassIds.CODE_BLOCK_BUILDER)
        }
    }
}
