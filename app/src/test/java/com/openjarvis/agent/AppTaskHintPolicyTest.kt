package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTaskHintPolicyTest {
    @Test fun acceptsGenericSuccessfulFlowGuidance() {
        val hint = AppTaskHintPolicy.create("com.example.shop", "open search", "Open the search screen, then choose a visible result")
        assertEquals("com.example.shop", hint?.packageName)
    }
    @Test fun rejectsInvalidPackageAndSensitiveContent() {
        assertNull(AppTaskHintPolicy.create("not a package", "open search", "Tap the search control"))
        assertNull(AppTaskHintPolicy.create("com.example.app", "send message", "Open chat and type the message"))
        assertNull(AppTaskHintPolicy.create("com.example.app", "find user", "Search for 9876543210"))
        assertNull(AppTaskHintPolicy.create("com.example.app", "login", "Enter user@example.com"))
        assertNull(AppTaskHintPolicy.create("com.example.app", "open settings", "Tap node_id=button_4"))
    }
    @Test fun mergeIsBoundedAndReplacesDuplicateTask() {
        val old = AppTaskHintPolicy.create("com.example.app", "open search", "Use the top search control")!!
        val newer = AppTaskHintPolicy.create("com.example.app", "open search", "Use the search icon in the toolbar")!!
        val result = AppTaskHintPolicy.merge(listOf(old), newer, maxEntries = 3, maxChars = 200)
        assertEquals(1, result.size)
        assertEquals("Use the search icon in the toolbar", result.single().guidance)
    }
    @Test fun mergeKeepsOnlyMostRecentEntriesWithinLimits() {
        val first = AppTaskHintPolicy.create("com.example.app", "open home", "Open the home tab")!!
        val second = AppTaskHintPolicy.create("com.example.app", "open search", "Open the search tab")!!
        val third = AppTaskHintPolicy.create("com.example.app", "open profile", "Open the profile tab")!!
        val result = AppTaskHintPolicy.merge(listOf(first, second), third, maxEntries = 2, maxChars = 500)
        assertEquals(2, result.size)
        assertTrue(result.none { it.taskLabel == "open home" })
        assertEquals("open profile", result.last().taskLabel)
    }
    @Test fun rejectsEmptyAndOverlySensitiveGuidance() {
        assertNull(AppTaskHintPolicy.create("com.example.app", " ", "Open settings"))
        assertNull(AppTaskHintPolicy.create("com.example.app", "open settings", "Use the OTP field"))
    }
}