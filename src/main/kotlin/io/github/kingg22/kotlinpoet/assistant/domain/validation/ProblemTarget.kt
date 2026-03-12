package io.github.kingg22.kotlinpoet.assistant.domain.validation

import io.github.kingg22.kotlinpoet.assistant.domain.text.TextSpan

sealed interface ProblemTarget {
    interface SpanTarget {
        val span: TextSpan
    }

    /** El problema afecta a toda la llamada (ej.: argumentos insuficientes globales) */
    data object Call : ProblemTarget

    /** El problema está ligado a un placeholder específico (que ya tiene su rango) */
    data class Placeholder(override val span: TextSpan) :
        SpanTarget,
        ProblemTarget

    /** El problema está ligado a un argumento */
    data class Argument(override val span: TextSpan) :
        SpanTarget,
        ProblemTarget

    /** El problema es un rango específico relativo al string de formato (ej.: un placeholder inválido) */
    data class TextSpanTarget(override val span: TextSpan) :
        SpanTarget,
        ProblemTarget
}
