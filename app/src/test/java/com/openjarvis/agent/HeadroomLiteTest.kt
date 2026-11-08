package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadroomLiteTest {

    @Test
    fun collapsesOnlyImmediatelyRepeatedContextLines() {
        val input = "screen title\nbutton: Search\nbutton: Search\nbutton: Send"
        assertEquals(
            "screen title\nbutton: Search\nbutton: Send",
            HeadroomLite.compressContext(input)
        )
    }

    @Test
    fun collapsesRepeatedBlankLinesWithoutTrimmingContent() {
        val input = "\n\n  keep leading spaces  \n\n\nlast line\n"
        assertEquals("\n  keep leading spaces  \n\nlast line\n", HeadroomLite.compressContext(input))
    }

    @Test
    fun preservesNonAdjacentRepeatedLinesAndOrder() {
        val input = "A\nB\nA\nC"
        assertEquals(input, HeadroomLite.compressContext(input))
    }

    @Test
    fun preservesJsonAndSafetyInstructionsVerbatim() {
        val input = """Return ONLY JSON.
Never tap Send.
{"action":"click","target":"Search"}"""
        assertEquals(input, HeadroomLite.compressContext(input))
    }

    @Test
    fun leavesEmptyAndSingleLineContextsUnchanged() {
        assertEquals("", HeadroomLite.compressContext(""))
        assertEquals("one line", HeadroomLite.compressContext("one line"))
    }
}
