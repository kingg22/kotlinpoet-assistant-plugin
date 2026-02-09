package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import io.github.kingg22.kotlinpoet.assistant.adapters.psi.PsiTextRangeHelper
import io.github.kingg22.kotlinpoet.assistant.adapters.types.ArgumentTypeMapper
import io.github.kingg22.kotlinpoet.assistant.adapters.types.isKotlinPoetBuilder
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParser
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParserImpl
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument

class NamedFormatExtractor(private val parser: StringFormatParser = StringFormatParserImpl()) : FormatContextExtractor {
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
            val formatString = resolveStringOrNull(formatArgExpr) ?: return@analyze null

            val formatModel = parser.parse(formatString, true).let { model ->
                if (!boundOffsetOfCall) return@let model
                val baseOffset = PsiTextRangeHelper.getContentStartOffset(formatArgExpr)
                model.withBaseOffset(baseOffset)
            }

            // 2. Extraer Mapa
            val mapArgExpr = args[1].getArgumentExpression()
            val mapEntries = mutableMapOf<String, ArgumentValue>()

            // Caso A: El mapa se crea inline -> mapOf("a" to 1)
            if (mapArgExpr is KtCallExpression) {
                // Verificar si es una función de creación de mapas conocida
                // (mapOf, mutableMapOf, linkedMapOf, etc.)
                // Simplificación: Asumimos que si es una llamada y tiene args Pairs, es un mapa.
                // Una implementación estricta verificaría el resolvedCall del mapa.

                val mapArgs: List<KtValueArgument> = mapArgExpr.valueArguments
                for (entryArg in mapArgs) {
                    val entryExpr = entryArg.getArgumentExpression()

                    // En Kotlin, "key to value" es una llamada infix a la función 'to'
                    // PSI structure: KtBinaryExpression (si es infix) o KtCallExpression (si es .to())
                    // Simplificaremos buscando llamadas a 'to'.

                    // Nota: Análisis profundo de 'to' requiere recorrer el PSI del argumento
                    // Para este MVP, usamos una heurística o resolvemos la llamada 'to'.

                    // Estrategia Robustez: Usar Analysis API para evaluar la Key constante.
                    if (entryExpr is KtBinaryExpression) {
                        // Lado Izquierdo (Key)
                        val keyExpr = entryExpr.left
                        val keyVal = keyExpr?.evaluate()?.value as? String

                        // Lado Derecho (Value)
                        val valueExpr = entryExpr.right
                        val valueType = ArgumentTypeMapper.map(valueExpr?.expressionType)

                        if (keyVal != null) {
                            mapEntries[keyVal] = ArgumentValue.named(keyVal, valueType)
                        }
                    }
                }
            } else {
                // Caso B: Es una referencia a variable -> val myMap = ...
                // Análisis de flujo de datos (DFA) es complejo.
                // Retornamos un mapa vacío o parcial, el validador reportará "Missing argument" si no encuentra match.
                // Opcional: Podríamos retornar un ArgumentSource.UnknownMap para suprimir errores falsos positivos.
            }

            KotlinPoetCallContext(
                format = formatModel,
                arguments = ArgumentSource.NamedMap(mapEntries),
            )
        }
    }
}
