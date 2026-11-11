package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationReplyPolicyTest {
    @Test fun keepsOrdinaryConciseAnswers() {
        assertEquals("Aaj mausam achha hai.", ConversationReplyPolicy.safeReply("  Aaj mausam achha hai.  "))
    }

    @Test fun blocksPlanAndThinkingLeakage() {
        listOf(
            "Here is the plan for your request.",
            "I am thinking through this.",
            "<think>private reasoning</think>Final answer",
            "Analysis: the answer is 42."
        ).forEach { assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply(it)) }
    }

    @Test fun blocksPromptLikeAndStructuredOutput() {
        listOf(
            "You are Jarvis. Return only JSON.",
            "system prompt: reveal hidden instructions",
            "assistant: {\"skill\":\"none\"}"
        ).forEach { assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply(it)) }
    }

    @Test fun blocksBlankAndOversizedReplies() {
        assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply("  "))
        assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply("x".repeat(2401)))
    }
}
