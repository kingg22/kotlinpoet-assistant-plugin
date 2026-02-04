package io.github.kingg22.kotlinpoet.assistant.domain.model.validation

sealed interface ProblemTarget {
    interface IntRangeTarget {
        val range: IntRange
    }

    /** El problema afecta a toda la llamada (ej.: argumentos insuficientes globales) */
    data object Call : ProblemTarget

    /** El problema está ligado a un placeholder específico (que ya tiene su rango) */
    data class Placeholder(override val range: IntRange) :
        IntRangeTarget,
        ProblemTarget

    // data class Argument(override val range: IntRange) : IntRangeTarget

    /** El problema es un rango específico relativo al string de formato (ej.: un placeholder inválido) */
    data class TextRange(override val range: IntRange) :
        IntRangeTarget,
        ProblemTarget
}
