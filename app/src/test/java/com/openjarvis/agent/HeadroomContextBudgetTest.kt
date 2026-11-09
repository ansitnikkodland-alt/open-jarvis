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
            memory = "M".repeat(300),
            screenText = "O".repeat(200),
            screenElements = "E".repeat(700),
            installedApps = "A".repeat(200)
        )

        assertEquals(500, result.screenElements.length)
        assertEquals(250, result.memory.length)
        assertEquals(150, result.screenText.length)
        assertEquals(100, result.installedApps.length)
        assertTrue(result.outputChars <= HeadroomContextBudget.MAX_TOTAL_CHARS)
    }

    @Test
    fun truncatesAtLineBoundaryWhenThereIsEnoughRoom() {
        val result = HeadroomContextBudget.allocate(
            memory = "",
            screenText = "",
            screenElements = "first target\nsecond target\nthird target that is long",
            installedApps = ""
        )

        assertEquals("first target…", result.screenElements)
    }

    @Test
    fun emptyContextStaysEmpty() {
        val result = HeadroomContextBudget.allocate("", "", "", "")
        assertEquals(0, result.outputChars)
        assertEquals(0, result.inputChars)
    }
}
