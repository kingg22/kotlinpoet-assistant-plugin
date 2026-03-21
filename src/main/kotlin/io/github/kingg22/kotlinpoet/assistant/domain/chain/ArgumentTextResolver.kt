package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.chain.ArgumentTextResolver.resolve
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue.*
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Resolves a `KtExpression` argument to a human-readable text approximation.
 *
 * ## Resolution strategy (in order)
 *
 * 1. **String literal** (`KtStringTemplateExpression` with no interpolation): raw text.
 * 2. **[KaConstantValue]** via K2 `evaluate()`: handles all primitive + String constants
 *    and constant expressions (e.g., `1 + 2` → `3`).
 * 3. **Class literal** (`SomeClass::class` or `SomeClass::class.java`).
 * 4. **Name reference** to a `val`/`var` with a literal initializer — one hop via K2.
 * 5. **Fallback**: `null` — caller produces [EmittedPart.UnresolvedPlaceholder].
 *
 * ## Threading
 *
 * [resolve] opens its own `analyze {}` session. [KaSession.resolveInSession] must be
 * called inside an active session — used when the caller already holds one.
 */
object ArgumentTextResolver {

    /**
     * Attempts to resolve [expr] to a displayable text string.
     *
     * @param expr The argument expression to resolve.
     * @param followReferences `true` to follow one level of property references to their
     *        initializer. Set to `false` to avoid infinite recursion when called recursively.
     * @return A displayable string, or `null` if resolution is not possible.
     */
    @JvmStatic
    fun resolve(expr: KtExpression, followReferences: Boolean = true): ResolvedText? =
        analyze(expr) { this.resolve(expr, followReferences) }

    // ── Session-internal resolution (callable without starting a new analyze{}) ─

    /**
     * Resolves [expr] within the current [KaSession].
     *
     * @param followReferences Follow one level of property reference. `false` prevents
     *        infinite recursion in recursive calls.
     */
    @JvmStatic
    fun KaSession.resolve(expr: KtExpression, followReferences: Boolean = true): ResolvedText? {
        if (expr is KtStringTemplateExpression) return resolveStringTemplate(expr)

        // KaConstantValue covers: IntValue, LongValue, FloatValue, DoubleValue, ByteValue,
        // ShortValue, UIntValue, ULongValue, UByteValue, UShortValue, BooleanValue,
        // CharValue, StringValue, NullValue, ErrorValue.
        val constant = expr.evaluate()
        if (constant != null) return resolveConstant(constant)

        val classRef = resolveClassLiteral(expr)
        if (classRef != null) return classRef

        if (followReferences && expr is KtNameReferenceExpression) {
            return resolveNameReference(expr)
        }

        return null
    }
}

private fun resolveStringTemplate(expr: KtStringTemplateExpression): ResolvedText? {
    if (expr.entries.any { it is KtStringTemplateEntryWithExpression }) return null
    val raw = expr.entries.joinToString("") { it.text }
    return ResolvedText.StringLiteral(
        raw
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\'", "'"),
    )
}

// Can't merge cases because doesn't share a common interface
private fun resolveConstant(constant: KaConstantValue): ResolvedText? = when (constant) {
    is StringValue -> ResolvedText.StringLiteral(constant.value)
    is IntValue -> ResolvedText.NumberLiteral(constant.value)
    is LongValue -> ResolvedText.NumberLiteral(constant.value)
    is FloatValue -> ResolvedText.NumberLiteral(constant.value)
    is DoubleValue -> ResolvedText.NumberLiteral(constant.value)
    is ByteValue -> ResolvedText.NumberLiteral(constant.value)
    is ShortValue -> ResolvedText.NumberLiteral(constant.value)
    is BooleanValue -> ResolvedText.Primitive(constant.value.toString())
    is CharValue -> ResolvedText.Primitive("'${constant.value}'")
    is UIntValue -> ResolvedText.NumberLiteral(constant.value.toLong())
    is ULongValue -> ResolvedText.Primitive("${constant.value}UL")
    is UByteValue -> ResolvedText.NumberLiteral(constant.value.toInt())
    is UShortValue -> ResolvedText.NumberLiteral(constant.value.toInt())
    is NullValue, is ErrorValue -> null
}

private fun KaSession.resolveClassLiteral(expr: KtExpression): ResolvedText? {
    if (expr is KtClassLiteralExpression) {
        val type = expr.receiverExpression?.expressionType as? KaClassType ?: return null
        return ResolvedText.TypeReference(type.classId.shortClassName.asString())
    }
    if (expr is KtDotQualifiedExpression) {
        val receiver = expr.receiverExpression as? KtClassLiteralExpression ?: return null
        val type = receiver.receiverExpression?.expressionType as? KaClassType ?: return null
        return ResolvedText.TypeReference(type.classId.shortClassName.asString())
    }
    return null
}

private fun KaSession.resolveNameReference(expr: KtNameReferenceExpression): ResolvedText? {
    val psi = expr.resolveToCall()
        ?.singleCallOrNull<KaCallableMemberCall<*, *>>()
        ?.partiallyAppliedSymbol?.symbol?.psi
        ?: return null
    if (psi !is KtProperty) return null
    val initializer = psi.initializer ?: return null
    return resolve(initializer, followReferences = false)
}
