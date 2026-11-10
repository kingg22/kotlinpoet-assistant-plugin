package io.github.kingg22.kotlinpoet.assistant.k2

import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.evaluation.evaluate
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.allSupertypes
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression

/** Bridge for the Analysis API where `KaSession` members are context-parameter functions (IDE builds 263+). */
class ModernKotlinAnalysisBridge : KotlinAnalysisBridge {
    override fun resolveCall(element: KtElement): ResolvedCallInfo? = analyze(element) {
        val call: KaCallableMemberCall<*, *> = (element as? KtExpression)
            ?.resolveToCall()
            ?.singleCallOrNull()
            ?: return@analyze null
        val symbol = call.partiallyAppliedSymbol
        val receiver = symbol.dispatchReceiver?.type ?: symbol.extensionReceiver?.type
        ResolvedCallInfo(
            callableName = symbol.symbol.callableId?.callableName?.asString(),
            receiverType = receiver?.let {
                TypeInfo(
                    classId = (it as? KaClassType)?.classId,
                    isError = it is KaErrorType,
                    supertypeFqNames = emptySet(),
                    description = it.toString(),
                )
            },
            symbolPsi = symbol.symbol.psi,
        )
    }

    override fun typeOf(expression: KtExpression, withSupertypes: Boolean): TypeInfo? = analyze(expression) {
        val type = expression.expressionType ?: return@analyze null
        val supertypes = if (withSupertypes && type is KaClassType) {
            type.allSupertypes
                .mapNotNull { (it as? KaClassType)?.classId?.asSingleFqName()?.asString() }
                .toSet()
        } else {
            emptySet()
        }
        TypeInfo(
            classId = (type as? KaClassType)?.classId,
            isError = type is KaErrorType,
            supertypeFqNames = supertypes,
            description = type.toString(),
        )
    }

    override fun evaluateConstant(expression: KtExpression): ConstantInfo? = analyze(expression) {
        when (val constant = expression.evaluate()) {
            null -> null
            is KaConstantValue.NullValue, is KaConstantValue.ErrorValue -> ConstantInfo(null)
            else -> ConstantInfo(constant.value)
        }
    }
}
