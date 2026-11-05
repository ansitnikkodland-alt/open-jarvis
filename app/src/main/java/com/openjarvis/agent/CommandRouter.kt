package com.openjarvis.agent

import com.openjarvis.intelligence.FastCommandParser

/**
 * Shared deterministic route classifier used by AgentCore and parser/splitter tests.
 * Keep precedence aligned with AgentCore's direct-command pipeline.
 */
internal object CommandRouter {
    enum class Route {
        STEP_INTENT,
        SEARCH,
        MESSAGE,
        CONTACT,
        FAST,
        MESSAGE_CONTINUATION,
        NONE
    }

    fun classify(step: String): Route {
        if (StepIntentParser.parse(step) != null) return Route.STEP_INTENT

        if (SearchCommandParser.parseYouTubeQuery(step) != null ||
            SearchCommandParser.parseWebQuery(step) != null
        ) return Route.SEARCH

        if (MessageCommandParser.parse(step) != null) return Route.MESSAGE
        if (ContactCommandParser.parse(step) != null) return Route.CONTACT
        if (FastCommandParser.parse(step) != null) return Route.FAST
        if (messageContinuationContact(step) != null) return Route.MESSAGE_CONTINUATION

        return Route.NONE
    }

    fun messageContinuationContact(step: String): String? {
        val cleaned = step.trim().replace(Regex("[\\p{Punct}।॥]+$"), "").trim()
        val match = Regex(
            "^(.+?)\\s+(?:ko|को|ला)\\s+(?:bhi|also|too|भी|सुद्धा)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(cleaned) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() && it.length <= 80 }
    }
}
