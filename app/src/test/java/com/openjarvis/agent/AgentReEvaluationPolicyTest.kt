package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentReEvaluationPolicyTest {
    @Test fun allowsReplanAfterSafeObservationFailure() {
        val completed = listOf(Action(Action.SCROLL, direction = "down"))
        val failed = Action(Action.WAIT, text = "Inbox")
        assertTrue(AgentReEvaluationPolicy.canReplan(completed, failed))
    }

    @Test fun blocksReplanAfterCompletedClickOrTextEntry() {
        val failed = Action(Action.WAIT, text = "Inbox")
        assertFalse(
            AgentReEvaluationPolicy.canReplan(
                listOf(Action(Action.CLICK, text = "Rahul")), failed
            )
        )
        assertFalse(
            AgentReEvaluationPolicy.canReplan(
                listOf(Action(Action.TYPE_TEXT, value = "hello")), failed
            )
        )
    }

    @Test fun neverReplansFailedClickOrTextEntry() {
        assertFalse(AgentReEvaluationPolicy.canReplan(emptyList(), Action(Action.CLICK, text = "Send")))
        assertFalse(AgentReEvaluationPolicy.canReplan(emptyList(), Action(Action.TYPE_TEXT, value = "message")))
    }

    @Test fun rejectsEmptyRefusalAndRepeatedFailedStep() {
        val failed = Action(Action.WAIT, text = "Inbox")
        assertFalse(AgentReEvaluationPolicy.isAcceptableReplan(emptyList(), failed))
        assertFalse(
            AgentReEvaluationPolicy.isAcceptableReplan(
                listOf(Action(Action.ERROR, message = "not safe")), failed
            )
        )
        assertFalse(
            AgentReEvaluationPolicy.isAcceptableReplan(
                listOf(Action(Action.WAIT, text = "Inbox")), failed
            )
        )
    }

    @Test fun acceptsDifferentRemainingAction() {
        val failed = Action(Action.WAIT, text = "Inbox")
        assertTrue(
            AgentReEvaluationPolicy.isAcceptableReplan(
                listOf(Action(Action.CLICK, text = "Chats")), failed
            )
        )
    }
}
