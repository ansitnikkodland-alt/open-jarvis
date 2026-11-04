package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPlanPolicyTest {
    private val openInstagram = listOf(Action(action = Action.OPEN_APP, label = "Instagram", packageName = "com.instagram.android"))

    @Test fun conversationalFollowUpWordsDoNotTriggerUnderPlannedGuard() {
        listOf(
            "Instagram kholo aur batao about options",
            "Instagram kholo and tell me about this section",
            "Instagram kholo aur display karo",
            "Instagram kholo and read this"
        ).forEach { command ->
            assertFalse(command, AgentPlanPolicy.isLikelyUnderPlanned(command, openInstagram))
        }
    }

    @Test fun actionableFollowUpWordsTriggerUnderPlannedGuard() {
        listOf(
            "Instagram kholo aur search karo",
            "Instagram kholo and message Rahul",
            "Instagram kholo aur likho hello",
            "Instagram kholo and click profile"
        ).forEach { command ->
            assertTrue(command, AgentPlanPolicy.isLikelyUnderPlanned(command, openInstagram))
        }
    }

    @Test fun singleOpenAppWithoutActionableFollowUpIsExecutable() {
        assertTrue(AgentPlanPolicy.isExecutablePlan(openInstagram, "Instagram kholo aur batao"))
    }

    @Test fun mixedErrorAndActionNeedsReplan() {
        val mixed = listOf(
            Action(action = Action.OPEN_APP, label = "Instagram", packageName = "com.instagram.android"),
            Action(action = Action.ERROR, message = "uncertain")
        )
        assertTrue(AgentPlanPolicy.needsCompactReplan(mixed))
    }

    @Test fun moreThanTenActionsNeedsReplan() {
        val actions = List(11) { Action(action = Action.BACK) }
        assertTrue(AgentPlanPolicy.needsCompactReplan(actions))
    }
}
