package io.github.kingg22.kotlinpoet.assistant.domain.text

import io.github.kingg22.kotlinpoet.assistant.domain.parser.StringFormatParserImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormatTextUnitTest {
    @Test
    fun spanMapsAcrossSegments() {
        val text = FormatText(
            listOf(
                FormatTextSegment("A%L", 10..12),
                FormatTextSegment("B", 20..20),
            ),
        )

        val span = text.span(1, 3) // "%L"
        assertEquals(listOf(11..12), span.ranges)
    }

    @Test
    fun parserSkipsDynamicSegments() {
        val text = FormatText(
            listOf(
                FormatTextSegment("Hello ", 0..5, SegmentKind.LITERAL),
                FormatTextSegment("\$name", 6..10, SegmentKind.DYNAMIC),
                FormatTextSegment(" %L", 11..13, SegmentKind.LITERAL),
            ),
        )

        val model = StringFormatParserImpl().parse(text)
        assertEquals(1, model.placeholders.size)
        assertTrue(model.placeholders.first().span.isSingle())
    }
}
