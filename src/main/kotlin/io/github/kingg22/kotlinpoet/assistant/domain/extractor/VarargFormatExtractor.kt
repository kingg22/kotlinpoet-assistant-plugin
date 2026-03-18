package io.github.kingg22.kotlinpoet.assistant.domain.extractor

import io.github.kingg22.kotlinpoet.assistant.adapters.types.ArgumentTypeMapper
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentSource
import io.github.kingg22.kotlinpoet.assistant.domain.model.ArgumentValue
import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParser
import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression

class VarargFormatExtractor(private val parser: StringFormatParser) : FormatContextExtractor {
    override fun extract(call: KtCallExpression): KotlinPoetCallContext? {
        val target = KotlinPoetCallTargetResolver.resolve(call)
        val methodName = target?.methodName ?: call.calleeExpression?.text.orEmpty()
        if (target == null && methodName !in FALLBACK_METHODS) return null
        return analyze(call) {
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
            val formatText = resolveFormatTextOrNull(formatArgExpr) ?: return@analyze null

            // 4. Parsear el modelo (spans absolutos)
            val formatModel = parser.parse(formatText, false, methodName)

            // 5. Extraer argumentos VarArg
            // Saltamos el primero (que es el format string)
            val argumentValues = args.drop(1).mapIndexed { index, valueArg ->
                val expr = valueArg.getArgumentExpression()
                val kaType = expr?.expressionType
                val argType = with(ArgumentTypeMapper) { map(kaType) }
                val span = expr?.textRange?.let { TextSpan.of(it.startOffset..<it.endOffset) }

                // Index 1-based para coincidir con la lógica de PositionalFormat
                ArgumentValue.positionalOrRelative(index + 1, argType, span)
            }

            KotlinPoetCallContext(
                format = formatModel,
                arguments = ArgumentSource.VarArgs(argumentValues),
                renderHint = RenderHint(
                    methodName = methodName,
                    receiverFqName = target?.receiverFqName,
                    isDelegated = target?.isDelegated ?: false,
                ),
            )
        }
    }
}

private val FALLBACK_METHODS = setOf("addKdoc")
