package io.github.kingg22.kotlinpoet.assistant.domain.chain

/**
 * Names of `CodeBlock.Builder` and `CodeBlock.Companion` methods that the chain
 * analysis understands.
 *
 * Lives in the domain layer so that both domain classes ([ContributionAnalyzer]) and
 * infrastructure classes ([io.github.kingg22.kotlinpoet.assistant.infrastructure.chain.CodeBlockPsiNavigator])
 * can import it without creating upward dependencies.
 */
val BUILDER_METHOD_NAMES: Set<String> = setOf(
    "add", "addCode", "addNamed", "addStatement",
    "beginControlFlow", "nextControlFlow", "endControlFlow",
    "indent", "unindent",
    "addKdoc",
    "build",
    "builder", "of",
)

/**
 * Names of DSL extension functions that create a `CodeBlock` from a lambda receiver
 * (the lambda body has an implicit `CodeBlock.Builder` receiver).
 */
val DSL_BUILDER_NAMES: Set<String> = setOf("buildCodeBlock")
