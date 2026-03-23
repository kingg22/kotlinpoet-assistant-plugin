package io.github.kingg22.kotlinpoet.assistant.domain.text

/**
 * A span that can cover one or more absolute source ranges.
 * Use multiple ranges when a span crosses concatenated segments.
 */
data class TextSpan(val ranges: List<IntRange>) {
    init {
        require(ranges.none { it.isEmpty() }) { "Ranges must be non-empty when present" }
    }

    val start: Int get() = ranges.firstOrNull()?.start ?: 0

    val endInclusive: Int get() = ranges.lastOrNull()?.endInclusive ?: 0

    val endExclusive: Int get() = endInclusive + 1

    val isRelative: Boolean get() = start == 0

    val isAbsolute: Boolean get() = start != 0

    val isEmpty: Boolean get() = ranges.isEmpty()

    fun isSingle(): Boolean = ranges.size == 1

    fun singleRangeOrNull(): IntRange? = ranges.singleOrNull()

    operator fun plus(other: TextSpan): TextSpan = TextSpan(ranges + other.ranges)

    operator fun contains(offset: Int): Boolean = ranges.any { offset in it }

    companion object {
        val Empty: TextSpan = TextSpan(emptyList())

        fun of(range: IntRange): TextSpan = TextSpan(listOf(range))
    }
}
