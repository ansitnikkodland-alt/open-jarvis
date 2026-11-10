package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadroomLiteContextTest {
    @Test
    fun collapsesOnlyAdjacentIdenticalLinesAndPreservesFinalNewline() {
        val input = "Settings\nSettings\nWiFi\nWiFi\nWiFi\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals(
            "Settings [repeated 2 consecutive times]\nWiFi [repeated 3 consecutive times]\n",
            result.text
        )
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

        assertEquals(
            "सेटिंग्स [repeated 2 consecutive times]\nआवाज [repeated 2 consecutive times]\n",
            result.text
        )
        assertEquals(2, result.collapsedLines)
    }

    @Test
    fun keepsEmptyInputAndBlankLineBoundariesUnchanged() {
        assertEquals("", HeadroomLiteContext.compressRepeatedLines("").text)
        assertEquals("\n\n", HeadroomLiteContext.compressRepeatedLines("\n\n").text)
        assertEquals("one", HeadroomLiteContext.compressRepeatedLines("one").text)
    }
}
