package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadroomLiteContextTest {
    @Test
    fun collapsesOnlyAdjacentIdenticalLinesAndPreservesFinalNewline() {
        val input = "Settings\nSettings\nWiFi\nWiFi\nWiFi\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals("Settings ×2\nWiFi ×3\n", result.text)
        assertEquals(3, result.collapsedLines)
        assertTrue(result.charsSaved > 0)
    }

    @Test
    fun preservesWarningsErrorsAndFailuresEvenWhenRepeated() {
        val input = "WARNING: mic busy\nWARNING: mic busy\nERROR: no permission\nERROR: no permission"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals(input, result.text)
        assertEquals(0, result.collapsedLines)
    }

    @Test
    fun preservesUniqueAndNonAdjacentLinesInOriginalOrder() {
        val input = "Home\nSettings\nHome\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals(input, result.text)
        assertEquals(0, result.collapsedLines)
    }

    @Test
    fun supportsHindiAndMarathiUnicodeWithoutNormalization() {
        val input = "सेटिंग्स\nसेटिंग्स\nआवाज\nआवाज\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals("सेटिंग्स ×2\nआवाज ×2\n", result.text)
        assertEquals(2, result.collapsedLines)
        assertTrue(result.charsSaved > 0)
    }

    @Test
    fun keepsEmptyInputAndBlankLineBoundariesUnchanged() {
        assertEquals("", HeadroomLiteContext.compressRepeatedLines("").text)
        assertEquals("\n\n", HeadroomLiteContext.compressRepeatedLines("\n\n").text)
        assertEquals("one", HeadroomLiteContext.compressRepeatedLines("one").text)
    }

    @Test
    fun doesNotExpandShortRepeatedLines() {
        val input = "a\na\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals(input, result.text)
        assertEquals(0, result.collapsedLines)
        assertEquals(0, result.charsSaved)
    }

    @Test
    fun preservesRepeatedUrlsIdentifiersAndCodeExactly() {
        val protectedLines = listOf(
            "https://example.com/device?id=123456",
            "device-serial-12345",
            "550e8400-e29b-41d4-a716-446655440000",
            "package com.openjarvis.agent",
            "val deviceId = 12345",
            "status: connected"
        )

        protectedLines.forEach { line ->
            val input = "$line\n$line"
            val result = HeadroomLiteContext.compressRepeatedLines(input)
            assertEquals("must preserve structured line: $line", input, result.text)
            assertEquals(0, result.collapsedLines)
        }
    }
}
