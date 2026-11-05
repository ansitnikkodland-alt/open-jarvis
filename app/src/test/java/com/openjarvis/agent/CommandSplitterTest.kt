package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSplitterTest {
    private val can = { s: String -> StepIntentParser.parse(s) != null }
    private fun s(t: String) = CommandSplitter.split(t, can)

    @Test
    fun strongSequencers() {
        assertEquals(
            listOf("volume badhao", "Pune navigate karo"),
            s("volume badhao phir Pune navigate karo")
        )
        assertEquals(
            listOf("torch on karo", "6 am alarm lagao"),
            s("torch on karo, uske baad 6 am alarm lagao")
        )
    }

    @Test
    fun weakConnectorSplitsOnlyWhenBothSidesAreCommands() {
        assertEquals(
            listOf("alarm 6 baje lagao", "torch on karo"),
            s("alarm 6 baje lagao aur torch on karo")
        )
        assertEquals(
            listOf("Rahul aur Amit ko message bhejo ki hi"),
            s("Rahul aur Amit ko message bhejo ki hi")
        )
    }

    @Test
    fun messageTextIsNeverSplit() {
        assertEquals(
            listOf("Rahul ko message bhejo ki main aur tum phir milenge"),
            s("Rahul ko message bhejo ki main aur tum phir milenge")
        )
    }

    @Test
    fun singleAndEmpty() {
        assertEquals(listOf("torch on karo"), s("torch on karo"))
        assertEquals(emptyList<String>(), s("   "))
    }

    @Test
    fun appLaunchStepsAreRecognizedWithoutSwallowingConversation() {
        assertEquals(
            listOf("open Instagram", "open WhatsApp"),
            s("open Instagram then open WhatsApp")
        )
        assertEquals(
            listOf("open Instagram", "WhatsApp"),
            s("open Instagram and WhatsApp")
        )
        assertEquals(
            listOf("open instagram and tell me what to do dear"),
            s("open instagram and tell me what to do dear")
        )
        assertEquals(StepIntent.AppLaunch("instagram"), StepIntentParser.parse("open insta again"))
        assertEquals(
            listOf("open Instagram", "open WhatsApp"),
            s("open Instagram and then open WhatsApp")
        )
    }

    @Test
    fun messageRecipientContinuationCanSplitForContextCarry() {
        val canWithContinuation: (String) -> Boolean = { value ->
            StepIntentParser.parse(value) != null ||
                Regex("^.+\\s+(?:ko|को|ला)\\s+(?:bhi|also|too|भी|सुद्धा)$", RegexOption.IGNORE_CASE)
                    .matches(value.trim())
        }
        assertEquals(
            listOf("Rahul ko WhatsApp pe hello bhejo", "Amit ko bhi"),
            CommandSplitter.split("Rahul ko WhatsApp pe hello bhejo aur Amit ko bhi", canWithContinuation)
        )
    }
}
