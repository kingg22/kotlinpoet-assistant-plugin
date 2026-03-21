package io.github.kingg22.kotlinpoet.assistant.domain.chain

import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan

/**
 * Records **where** an [EmittedPart] came from within the source file.
 *
 * Uses [TextSpan] (absolute file offsets) rather than holding a PSI node directly.
 * The infrastructure layer can recover the `KtCallExpression` from the offset when needed
 * (e.g., for navigation, highlighting, or tool window display).
 *
 * ## Two categories
 *
 * - **[ExplicitCall]**: the user wrote this method call; the part comes from the format
 *   string or arguments of that call.
 * - **[ImplicitFromMethod]**: the part was injected by a wrapper method, not written
 *   directly by the user. For example, `addStatement` implicitly adds `«` and `\n»`.
 */
sealed interface EmissionOrigin {

    /**
     * The part was emitted by an explicit user-written builder call.
     *
     * @param methodName Name of the KotlinPoet method (e.g. `"addStatement"`, `"add"`).
     * @param callSpan Absolute file offsets of the `KtCallExpression` that produced this part.
     *        Used by the tool window to navigate to the source location.
     */
    data class ExplicitCall(val methodName: String, val callSpan: TextSpan) : EmissionOrigin

    /**
     * The part is an implicit structural emission injected by a wrapper method,
     * not visible in the user's format string.
     *
     * Examples:
     * - `addStatement("foo")` → the `«` and `\n»` are implicit from `StatementCall`
     * - `beginControlFlow("if (%L)", cond)` → the ` {\n` and `⇥` are implicit
     *
     * @param wrapperMethodName Name of the method that triggered this implicit emission.
     * @param wrapperCallSpan Absolute file offsets of the wrapping `KtCallExpression`.
     */
    data class ImplicitFromMethod(val wrapperMethodName: String, val wrapperCallSpan: TextSpan) : EmissionOrigin
}
