package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadroomContextBudgetTest {
    @Test
    fun keepsAllContextWhenItFitsWithinItsPriorityCaps() {
        val result = HeadroomContextBudget.allocate(
            memory = "saved fact",
            screenText = "Visible heading",
            screenElements = "Button: Search",
            installedApps = "WhatsApp"
        )

        assertEquals("saved fact", result.memory)
        assertEquals("Visible heading", result.screenText)
        assertEquals("Button: Search", result.screenElements)
        assertEquals("WhatsApp", result.installedApps)
        assertEquals(result.outputChars, result.inputChars)
    }

    @Test
    fun allocatesInteractiveElementsBeforeMemoryOcrAndAppLabels() {
        val result = HeadroomContextBudget.allocate(
            memory = "M".repeat(400),
            screenText = "O".repeat(200),
            screenElements = "E".repeat(700),
            installedApps = "A".repeat(200)
        )

        assertEquals(450, result.screenElements.length)
        assertEquals(350, result.memory.length)
        assertEquals(150, result.screenText.length)
        assertEquals(50, result.installedApps.length)
        assertTrue(result.outputChars <= HeadroomContextBudget.MAX_TOTAL_CHARS)
    }

    @Test
    fun truncatesAtLineBoundaryWhenThereIsEnoughRoom() {
        val firstLine = "a".repeat(260)
        val result = HeadroomContextBudget.allocate(
            memory = "",
            screenText = "",
            screenElements = "$firstLine\n" + "b".repeat(600),
            installedApps = ""
        )

        assertEquals("$firstLine…", result.screenElements)
    }

    @Test
    fun emptyContextStaysEmpty() {
        val result = HeadroomContextBudget.allocate("", "", "", "")
        assertEquals(0, result.outputChars)
        assertEquals(0, result.inputChars)
    }
}
