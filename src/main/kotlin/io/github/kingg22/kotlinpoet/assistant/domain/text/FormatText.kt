package io.github.kingg22.kotlinpoet.assistant.domain.text

/**
 * A segmented view of a format string where each segment is tied to an absolute source range.
 * This keeps PSI as the source of truth while staying PSI-free in the domain.
 */
data class FormatText(val segments: List<FormatTextSegment>) {
    val length: Int = segments.sumOf { it.text.length }

    fun isEmpty(): Boolean = segments.isEmpty() || length == 0

    fun asString(): String = segments.joinToString(separator = "") { it.text }

    fun isIndexInDynamic(index: Int): Boolean {
        if (index !in 0..<length) return false
        var cursor = 0
        for (segment in segments) {
            val end = cursor + segment.text.length
            if (index < end) return segment.kind == SegmentKind.DYNAMIC
            cursor = end
        }
        return false
    }

    fun span(start: Int, endExclusive: Int): TextSpan {
        require(start >= 0) { "start must be >= 0" }
        require(endExclusive >= start) { "endExclusive must be >= start" }
        require(endExclusive <= length) { "endExclusive must be <= length" }

        if (start == endExclusive) return TextSpan.Empty

        val ranges = mutableListOf<IntRange>()
        var cursor = 0
        for (segment in segments) {
            val segmentLength = segment.text.length
            val segmentStartIndex = cursor
            val segmentEndIndex = cursor + segmentLength
            if (endExclusive <= segmentStartIndex) break
            if (start < segmentEndIndex) {
                val overlapStart = maxOf(start, segmentStartIndex)
                val overlapEnd = minOf(endExclusive, segmentEndIndex)
                val localStart = overlapStart - segmentStartIndex
                val localEndExclusive = overlapEnd - segmentStartIndex
                val absoluteStart = segment.range.first + localStart
                val absoluteEndInclusive = segment.range.first + localEndExclusive - 1
                ranges += absoluteStart..absoluteEndInclusive
            }
            cursor = segmentEndIndex
        }

        return TextSpan(ranges)
    }

    fun fullSpan(): TextSpan = if (isEmpty()) TextSpan.Empty else span(0, length)

    operator fun plus(other: FormatText): FormatText = FormatText(this.segments + other.segments)
}

data class FormatTextSegment(val text: String, val range: IntRange, val kind: SegmentKind = SegmentKind.LITERAL) {
    init {
        require(text.isNotEmpty()) { "Segment text must not be empty" }
    }
}

enum class SegmentKind { LITERAL, DYNAMIC }
