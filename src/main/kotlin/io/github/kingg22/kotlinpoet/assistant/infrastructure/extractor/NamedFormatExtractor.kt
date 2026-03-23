package io.github.kingg22.kotlinpoet.assistant.infrastructure.extractor

import io.github.kingg22.kotlinpoet.assistant.adapters.psi.PsiFormatTextExtractor.extract
import io.github.kingg22.kotlinpoet.assistant.adapters.types.ArgumentTypeMapper
import io.github.kingg22.kotlinpoet.assistant.domain.extractor.KotlinPoetCallContext
import io.github.kingg22.kotlinpoet.assistant.domain.extractor.RenderHint
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParser
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.extractMapEntry
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument

class NamedFormatExtractor(private val parser: StringFormatParser) : FormatContextExtractor {
    override fun extract(call: KtCallExpression): KotlinPoetCallContext? {
        val target = KotlinPoetCallTargetResolver.resolve(call) ?: return null
        if (target.methodName != "addNamed") return null

        return analyze(call) {
            val args: List<KtValueArgument> = call.valueArguments
            // addNamed(format, map) -> requiere al menos 2 argumentos
            if (args.size < 2) return@analyze null

            // 1. Extraer Formato
            val formatArgExpr = args[0].getArgumentExpression() ?: return@analyze null
            val formatText = extract(formatArgExpr) ?: return@analyze null

            val formatModel = parser.parse(formatText, true, target.methodName)

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
                renderHint = RenderHint(
                    methodName = target.methodName,
                    receiverFqName = target.receiverFqName,
                    isDelegated = target.isDelegated,
                ),
            )
        }
    }
}
