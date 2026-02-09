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
import org.jetbrains.kotlin.psi.KtCallExpression

class VarargFormatExtractor(private val parser: StringFormatParser = StringFormatParserImpl()) :
    FormatContextExtractor {

    override fun extract(call: KtCallExpression, boundOffsetOfCall: Boolean): KotlinPoetCallContext? = analyze(call) {
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
        val formatString = resolveStringOrNull(formatArgExpr) ?: return@analyze null

        // 4. Parsear el modelo y recalcular los offsets relativos
        val formatModel = parser.parse(formatString).let { model ->
            if (!boundOffsetOfCall) return@let model
            // Calculamos el offset base
            val baseOffset = PsiTextRangeHelper.getContentStartOffset(formatArgExpr)
            model.withBaseOffset(baseOffset)
        }

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
