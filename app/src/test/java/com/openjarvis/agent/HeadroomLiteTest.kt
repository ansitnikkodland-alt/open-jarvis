package com.openjarvis.agent

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadroomLiteTest {
    @Test
    fun minifiesValidJsonWithoutChangingValuesOrStringWhitespace() {
        val input = """{
          "label": "Search   now",
          "value": "line 1\\n  line 2",
          "actions": [ { "action": "click", "target": "Search" } ]
        }"""
        val result = HeadroomLite.compress(input)
        assertEquals(HeadroomLite.ContentKind.JSON, result.kind)
        assertTrue(result.outputChars < result.inputChars)
        val parsed = JSONObject(result.content)
        assertEquals("Search   now", parsed.getString("label"))
        assertEquals("line 1\\n  line 2", parsed.getString("value"))
        assertEquals("click", parsed.getJSONArray("actions").getJSONObject(0).getString("action"))
        assertEquals(input, result.restore())
    }

    @Test
    fun minifiesJsonArraysAndPreservesArrayOrder() {
        val input = """[ { "id": 1 }, { "id": 2 }, { "id": 3 } ]"""
        val result = HeadroomLite.compress(input)
        val parsed = JSONArray(result.content)
        assertEquals(3, parsed.length())
        assertEquals(1, parsed.getJSONObject(0).getInt("id"))
        assertEquals(3, parsed.getJSONObject(2).getInt("id"))
    }

    @Test
    fun invalidJsonIsNeverRewritten() {
        val input = """{ "action": "click", broken }"""
        val result = HeadroomLite.compress(input)
        assertEquals(HeadroomLite.ContentKind.TEXT, result.kind)
        assertEquals(input, result.content)
    }

    @Test
    fun compressesRepeatedTextLinesAndCanRestoreOriginalExactly() {
        val input = "button: Search\nbutton: Search\nbutton: Search\nbutton: Send"
        val result = HeadroomLite.compress(input, HeadroomLite.ContentKind.TEXT)
        assertTrue(result.outputChars < result.inputChars)
        assertTrue(result.content.contains("⟪x3⟫"))
        assertEquals(input, result.restore())
    }

    @Test
    fun collapsesBlankLineRunsAndPreservesUniqueLineOrder() {
        val input = "header\n\n\nbutton: Search\nbutton: Send\nfooter"
        val result = HeadroomLite.compress(input, HeadroomLite.ContentKind.TEXT)
        assertEquals("header\n\nbutton: Search\nbutton: Send\nfooter", result.content)
        assertEquals(input, result.restore())
    }

    @Test
    fun codeDiffAndPreserveKindsAreNeverModified() {
        val input = "fun main() {\n\n\nprintln(\"safe\")\n}"
        for (kind in listOf(HeadroomLite.ContentKind.CODE, HeadroomLite.ContentKind.DIFF, HeadroomLite.ContentKind.PRESERVE)) {
            assertEquals(input, HeadroomLite.compress(input, kind).content)
        }
    }

    @Test
    fun metricsAreBoundedAndEmptyInputIsSafe() {
        val result = HeadroomLite.compress("")
        assertEquals(0, result.savedChars)
        assertEquals(0, result.estimatedInputTokens)
        assertEquals(1.0, result.compressionRatio, 0.0)
        assertEquals(0, HeadroomLite.estimateTokens(""))
    }

    @Test
    fun systemSafetyInstructionsRemainVerbatim() {
        val input = "Return ONLY JSON.\nNever tap Send.\nPreserve user intent."
        val result = HeadroomLite.compress(input, HeadroomLite.ContentKind.TEXT)
        assertEquals(input, result.content)
        assertEquals(input, result.restore())
    }
}
