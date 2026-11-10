package io.github.kingg22.kotlinpoet.assistant.infrastructure.chain

import io.github.kingg22.kotlinpoet.assistant.domain.chain.ResolvedText
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.KotlinAnalysisBridges
import io.github.kingg22.kotlinpoet.assistant.infrastructure.unescaped
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
 * 2. **Constant** via K2 `evaluate()`: handles all primitive + String constants
 *    and constant expressions (e.g., `1 + 2` → `3`).
 * 3. **Class literal** (`SomeClass::class` or `SomeClass::class.java`).
 * 4. **Name reference** to a `val`/`var` with a literal initializer — one hop via K2.
 * 5. **Fallback**: `null` — caller produces [io.github.kingg22.kotlinpoet.assistant.domain.chain.EmittedPart.UnresolvedPlaceholder].
 *
 * ## Threading
 *
 * Must be called inside a read action. Each K2 query opens its own short `analyze {}` session
 * through [KotlinAnalysisBridges], which hides the Analysis API differences between IDE versions.
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
    fun resolve(expr: KtExpression, followReferences: Boolean = true): ResolvedText? {
        if (expr is KtStringTemplateExpression) return resolveStringTemplate(expr)

        // Covers primitive + String constants and constant expressions. A null or erroneous
        // constant short-circuits the resolution (no further fallbacks).
        val constant = KotlinAnalysisBridges.instance.evaluateConstant(expr)
        if (constant != null) return resolveConstant(constant.value)

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
    return ResolvedText.StringLiteral(raw.unescaped())
}

private fun resolveConstant(value: Any?): ResolvedText? = when (value) {
    is String -> ResolvedText.StringLiteral(value.unescaped())
    is Int -> ResolvedText.NumberLiteral(value)
    is Long -> ResolvedText.NumberLiteral(value)
    is Float -> ResolvedText.NumberLiteral(value)
    is Double -> ResolvedText.NumberLiteral(value)
    is Byte -> ResolvedText.NumberLiteral(value)
    is Short -> ResolvedText.NumberLiteral(value)
    is Boolean -> ResolvedText.Primitive(value.toString())
    is Char -> ResolvedText.Primitive("'$value'")
    is UInt -> ResolvedText.NumberLiteral(value.toLong())
    is ULong -> ResolvedText.Primitive("${value}UL")
    is UByte -> ResolvedText.NumberLiteral(value.toInt())
    is UShort -> ResolvedText.NumberLiteral(value.toInt())
    else -> null
}

private fun resolveClassLiteral(expr: KtExpression): ResolvedText? {
    val classLiteral = when (expr) {
        is KtClassLiteralExpression -> expr
        is KtDotQualifiedExpression -> expr.receiverExpression as? KtClassLiteralExpression
        else -> null
    } ?: return null
    val receiver = classLiteral.receiverExpression ?: return null
    val classId = KotlinAnalysisBridges.instance.typeOf(receiver)?.classId ?: return null
    return ResolvedText.TypeReference(classId.shortClassName.asString())
}

private fun resolveNameReference(expr: KtNameReferenceExpression): ResolvedText? {
    val psi = KotlinAnalysisBridges.instance.resolveCall(expr)?.symbolPsi
    if (psi !is KtProperty) return null
    val initializer = psi.initializer ?: return null
    return ArgumentTextResolver.resolve(initializer, followReferences = false)
}
