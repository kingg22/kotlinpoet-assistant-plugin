package io.github.kingg22.kotlinpoet.assistant.domain.chain

/**
 * Why a specific placeholder argument could not be resolved to a concrete value.
 *
 * The plugin is transparent about analysis limits. When a placeholder argument cannot
 * be resolved, it is marked with one of these reasons so the UI can display an
 * appropriate indicator (e.g., `[%T?]` for an unresolved type placeholder).
 */
enum class UnresolvedReason {
    /**
     * The argument is a reference to an external variable (`val x = …`).
     * The plugin attempted `resolve()` via K2 Analysis API but the variable's
     * initializer is not a statically evaluable expression or CodeBlock builder chain.
     */
    EXTERNAL_VARIABLE,

    /**
     * The format string contains a Kotlin string template (`$variable` or `${expr}`)
     * in the segment that holds the placeholder. The parser marks these segments as
     * [io.github.kingg22.kotlinpoet.assistant.domain.text.SegmentKind.DYNAMIC].
     */
    DYNAMIC_STRING,

    /**
     * The argument expression is a non-constant call or complex expression
     * (e.g., `someFunction()`, `list[0]`) that cannot be evaluated statically.
     */
    NON_CONST_EXPRESSION,

    /**
     * The argument is inside a conditional branch (`if`/`when`) that cannot be
     * resolved without runtime information.
     */
    CONDITIONAL_FLOW,

    /**
     * The call uses `vararg` with a spread operator (`*someArray`) where the
     * array content is not a literal [arrayOf] expression.
     */
    SPREAD_OPERATOR,

    /**
     * The plugin reached its maximum recursion depth when following nested
     * `CodeBlock` arguments.
     */
    RECURSIVE_LIMIT_REACHED,
}
