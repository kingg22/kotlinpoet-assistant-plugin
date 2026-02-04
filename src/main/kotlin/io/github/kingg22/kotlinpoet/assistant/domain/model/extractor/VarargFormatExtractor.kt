package io.github.kingg22.kotlinpoet.assistant.domain.model.extractor

import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.model.extractor.mapper.ArgumentTypeMapper
import io.github.kingg22.kotlinpoet.assistant.domain.model.extractor.mapper.isKotlinPoetBuilder
import io.github.kingg22.kotlinpoet.assistant.domain.model.parser.StringFormatParser
import io.github.kingg22.kotlinpoet.assistant.domain.model.parser.StringFormatParserImpl
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class VarargFormatExtractor(private val parser: StringFormatParser = StringFormatParserImpl()) :
    FormatContextExtractor {

    override fun extract(call: KtCallExpression): KotlinPoetCallContext? {
        return analyze(call) {
            val resolvedCall: KaCallableMemberCall<*, *> =
                call.resolveToCall()?.singleCallOrNull() ?: return@analyze null
            val receiverType = resolvedCall.partiallyAppliedSymbol.dispatchReceiver?.type

            // 1. Validar contexto: Es un builder de KotlinPoet?
            if (receiverType == null || !receiverType.isKotlinPoetBuilder()) return@analyze null

            // 2. Validar firma: Debe tener (String, vararg Any?)
            // Buscamos firmas comunes: add, addStatement, beginControlFlow, etc.
            // val valueParameters = resolvedCall.typeArgumentsMapping
            // if (valueParameters.isEmpty()) return@analyze null

            // Asumimos que el primer parámetro es el formato.
            // TODO: Podríamos ser más estrictos verificando nombres o tipos de params.

            // 3. Obtener el String de formato (Argumento 0)
            val args = call.valueArguments
            val formatArgExpr = args.firstOrNull()?.getArgumentExpression() ?: return@analyze null

            // Intentamos resolver el valor constante del string
            val formatString = (formatArgExpr as? KtStringTemplateExpression)?.entries
                ?.joinToString("") { it.text } // Simple extraction for literals
                // Fallback a evaluación constante si es una variable const (más costoso)
                ?: formatArgExpr.evaluate()?.value as? String

            if (formatString == null) return@analyze null // No podemos analizar strings dinámicos/runtime

            // 4. Parsear el modelo
            val formatModel = parser.parse(formatString)

            // 5. Extraer argumentos VarArg
            // Saltamos el primero (que es el format string)
            val argumentValues = args.drop(1).mapIndexed { index, valueArg ->
                val expr = valueArg.getArgumentExpression()
                val kaType = expr?.expressionType
                val argType = ArgumentTypeMapper.map(kaType)

                // Index 1-based para coincidir con la lógica de PositionalFormat
                ArgumentValue.positionalOrRelative(index + 1, argType)
            }

            KotlinPoetCallContext(
                format = formatModel,
                arguments = ArgumentSource.VarArgs(argumentValues),
            )
        }
    }
}
