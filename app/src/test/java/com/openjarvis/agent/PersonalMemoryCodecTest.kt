package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalMemoryCodecTest {
    @Test fun storesBoundedDeduplicatedNotesNewestFirst() {
        val first = PersonalMemoryCodec.remember(emptyList(), "I prefer Marathi")
        val second = PersonalMemoryCodec.remember(first, "I prefer English")
        val duplicate = PersonalMemoryCodec.remember(second, "I prefer Marathi")
        assertEquals(listOf("I prefer Marathi"), first)
        assertEquals(listOf("I prefer English", "I prefer Marathi"), second)
        assertEquals(listOf("I prefer Marathi", "I prefer English"), duplicate)
    }

    @Test fun storesUpToFiftyFactsAndReplacesProfileCategory() {
        val many = (1..55).fold(emptyList<String>()) { acc, i ->
            PersonalMemoryCodec.remember(acc, "Fact number $i")
        }
        assertEquals(50, many.size)
        val profile = PersonalMemoryCodec.remember(listOf("My name is Sagar"), "My name is Sandip")
        assertEquals(listOf("My name is Sandip"), profile)
    }

    @Test fun updateAndDeleteAreSupported() {
        val original = listOf("I prefer Marathi", "I work as an engineer")
        val updated = PersonalMemoryCodec.update(original, "I prefer Marathi", "I prefer Hindi")
        assertEquals(listOf("I prefer Hindi", "I work as an engineer"), updated)
        assertEquals(listOf("I work as an engineer"), PersonalMemoryCodec.delete(updated, "I prefer Hindi"))
    }

    @Test fun rejectsEmptyAndOversizedNotes() {
        assertFalse(PersonalMemoryCodec.normalize("  ") != null)
        assertEquals(null, PersonalMemoryCodec.normalize("x".repeat(181)))
        assertTrue(PersonalMemoryCodec.normalize("use dark theme") != null)
    }

    @Test fun forgetsMatchingNoteOrAllNotesExplicitly() {
        val notes = listOf("I prefer Marathi", "I prefer dark theme")
        assertEquals(listOf("I prefer dark theme"), PersonalMemoryCodec.forget(notes, "Marathi"))
        assertTrue(PersonalMemoryCodec.forget(notes, "all").isEmpty())
        assertTrue(PersonalMemoryCodec.forget(notes, "sab").isEmpty())
    }
}
