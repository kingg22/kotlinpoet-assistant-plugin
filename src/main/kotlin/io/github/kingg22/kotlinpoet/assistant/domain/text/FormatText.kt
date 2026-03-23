package io.github.kingg22.kotlinpoet.assistant.domain.text

/**
 * A segmented view of a format string where each segment is tied to an absolute source range.
 * This keeps PSI as the source of truth while staying PSI-free in the domain.
 */
data class FormatText(val segments: List<FormatTextSegment>) {
    val length: Int = segments.sumOf { it.text.length }

    /** Returns the absolute start offset (useful as baseOffset replacement). */
    val absoluteStartOffset: Int get() = segments.firstOrNull()?.range?.first ?: 0

    fun isEmpty(): Boolean = segments.isEmpty() || length == 0

    fun asString(): String = segments.joinToString(separator = "") { it.text }

    /**
     * Returns true if the given RELATIVE index is inside a DYNAMIC segment.
     */
    fun isIndexInDynamic(index: Int): Boolean {
        if (index !in 0 until length) return false
        var cursor = 0
        for (segment in segments) {
            val end = cursor + segment.text.length
            if (index < end) return segment.kind == SegmentKind.DYNAMIC
            cursor = end
        }
        return false
    }

    /**
     * Converts a RELATIVE range into an ABSOLUTE TextSpan.
     */
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

    /** FULL span in absolute coordinates. */
    fun fullSpan(): TextSpan = if (isEmpty()) TextSpan.Empty else span(0, length)

    operator fun plus(other: FormatText): FormatText = FormatText(this.segments + other.segments)

    /**
     * Converts an ABSOLUTE offset (PSI) into a RELATIVE offset inside this FormatText.
     *
     * Returns null if the offset is outside this FormatText.
     */
    fun toRelativeOffset(absolute: Int): Int? {
        var cursor = 0

        for (segment in segments) {
            val range = segment.range
            if (absolute in range) {
                return cursor + (absolute - range.first)
            }
            cursor += segment.text.length
        }

        return null
    }

    /**
     * Converts an ABSOLUTE TextSpan into a RELATIVE IntRange.
     *
     * Only supports single-range spans for now.
     * Returns null if it cannot be mapped cleanly.
     */
    fun toRelativeRange(span: TextSpan): IntRange? {
        val range = span.singleRangeOrNull() ?: return null

        val start = toRelativeOffset(range.first) ?: return null
        val end = toRelativeOffset(range.last) ?: return null

        return start..end
    }

    /** Converts an ABSOLUTE [TextSpan] into a RELATIVE [start, endExclusive) pair. */
    fun toRelativeRangeExclusive(span: TextSpan): IntRange? {
        val range = span.singleRangeOrNull() ?: return null

        val start = toRelativeOffset(range.first) ?: return null
        val endExclusive = toRelativeOffset(range.last)?.plus(1) ?: return null

        return start until endExclusive
    }

    /** Safe substring using RELATIVE offsets. */
    fun substring(start: Int, endExclusive: Int): String {
        if (start >= endExclusive) return ""
        if (start !in 0..length) return ""
        if (endExclusive !in 0..length) return ""

        val builder = StringBuilder()

        var cursor = 0

        for (segment in segments) {
            val segmentStart = cursor
            val segmentEnd = cursor + segment.text.length

            if (endExclusive <= segmentStart) break

            if (start < segmentEnd) {
                val overlapStart = maxOf(start, segmentStart)
                val overlapEnd = minOf(endExclusive, segmentEnd)

                val localStart = overlapStart - segmentStart
                val localEnd = overlapEnd - segmentStart

                builder.append(segment.text.substring(localStart, localEnd))
            }

            cursor = segmentEnd
        }

        return builder.toString()
    }

    /** Safe substring using ABSOLUTE span. */
    fun substring(span: TextSpan): String {
        val rel = toRelativeRangeExclusive(span) ?: return ""
        return substring(rel.first, rel.last)
    }

    /** Returns true if an ABSOLUTE offset belongs to this FormatText. */
    fun containsAbsoluteOffset(offset: Int): Boolean = segments.any { offset in it.range }
}
