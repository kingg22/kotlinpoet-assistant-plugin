package io.github.kingg22.kotlinpoet.assistant.domain.text

data class FormatTextSegment(val text: String, val range: IntRange, val kind: SegmentKind = SegmentKind.LITERAL) {
    init {
        require(text.isNotEmpty()) { "Segment text must not be empty" }
    }

    constructor(text: String, start: Int, end: Int, kind: SegmentKind = SegmentKind.LITERAL) : this(
        text,
        start..<end,
        kind,
    )
}
