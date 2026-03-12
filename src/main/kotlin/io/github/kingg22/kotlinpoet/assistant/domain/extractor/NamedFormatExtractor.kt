package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import io.github.kingg22.kotlinpoet.assistant.adapters.types.ArgumentTypeMapper
import io.github.kingg22.kotlinpoet.assistant.adapters.types.isKotlinPoetBuilder
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParser
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtValueArgument

class NamedFormatExtractor(private val parser: StringFormatParser) : FormatContextExtractor {
    override fun extract(call: KtCallExpression, boundOffsetOfCall: Boolean): KotlinPoetCallContext? {
        // Filtro rápido por nombre antes de entrar al análisis pesado
        if (call.calleeExpression?.text != "addNamed") return null

        return analyze(call) {
            val resolvedCall: KaCallableMemberCall<*, *> =
                call.resolveToCall()?.singleCallOrNull() ?: return@analyze null
            val receiverType = resolvedCall.partiallyAppliedSymbol.dispatchReceiver?.type

            if (receiverType == null || !receiverType.isKotlinPoetBuilder()) return@analyze null

            val args: List<KtValueArgument> = call.valueArguments
            // addNamed(format, map) -> requiere al menos 2 argumentos
            if (args.size < 2) return@analyze null

            // 1. Extraer Formato
            val formatArgExpr = args[0].getArgumentExpression() ?: return@analyze null
            val formatText = resolveFormatTextOrNull(formatArgExpr) ?: return@analyze null

            val formatModel = parser.parse(formatText, true)

            // 2. Extraer Mapa
            val mapArgExpr = args[1].getArgumentExpression()
            val mapEntries = mutableMapOf<String, ArgumentValue>()
            var isComplete: Boolean

            // Caso A: El mapa se crea inline -> mapOf("a" to 1)
            if (mapArgExpr is KtCallExpression) {
                // Verificar si es una función de creación de mapas conocida
                // (mapOf, mutableMapOf, linkedMapOf, etc.)
                // Simplificación: Asumimos que si es una llamada y tiene args Pairs, es un mapa.
                // Una implementación estricta verificaría el resolvedCall del mapa.

                val mapArgs: List<KtValueArgument> = mapArgExpr.valueArguments
                isComplete = true

                mapArgs.forEach { entryArg ->
                    val entryExpr = entryArg.getArgumentExpression()

                    val (keyVal, valueExpr) = extractMapEntry(entryExpr) ?: run {
                        isComplete = false
                        return@forEach
                    }

                    val valueType = with(ArgumentTypeMapper) { map(valueExpr?.expressionType) }
                    val span = valueExpr?.textRange?.let { TextSpan.of(it.startOffset..<it.endOffset) }
                    mapEntries[keyVal] = ArgumentValue.named(keyVal, valueType, span)
                }
            } else {
                // Caso B: Es una referencia a variable -> val myMap = ...
                // Análisis de flujo de datos (DFA) es complejo.
                // Retornamos un mapa vacío o parcial, el validador reportará "Missing argument" si no encuentra match.
                // Opcional: Podríamos retornar un ArgumentSource.UnknownMap para suprimir errores falsos positivos.
                isComplete = false
            }

            KotlinPoetCallContext(
                format = formatModel,
                arguments = ArgumentSource.NamedMap(mapEntries, isComplete),
            )
        }
    }

    private fun KaSession.extractMapEntry(entryExpr: KtExpression?): Pair<String, KtExpression?>? {
        // En Kotlin, "key to value" es una llamada infix a la función 'to'
        // PSI structure: KtBinaryExpression (si es infix) o KtCallExpression (si es .to())
        return when (entryExpr) {
            is KtBinaryExpression -> {
                val keyExpr = entryExpr.left
                val keyVal = keyExpr?.evaluate()?.value as? String ?: return null
                keyVal to entryExpr.right
            }

            is KtCallExpression -> {
                if (entryExpr.calleeExpression?.text != "to") return null
                val keyExpr = entryExpr.valueArguments.getOrNull(0)?.getArgumentExpression()
                val keyVal = keyExpr?.evaluate()?.value as? String ?: return null
                val valueExpr = entryExpr.valueArguments.getOrNull(1)?.getArgumentExpression()
                keyVal to valueExpr
            }

            else -> null
        }
    }
}
