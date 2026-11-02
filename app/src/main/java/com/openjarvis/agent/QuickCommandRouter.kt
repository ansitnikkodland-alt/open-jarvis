package com.openjarvis.agent

/**
 * Detects unambiguous, single-intent app-launch commands that do not need LLM
 * reasoning, screen OCR, or retrieval context.
 */
internal object QuickCommandRouter {
    private val openAppPattern = Regex(
        "^(?:please\\s+)?(?:open|launch|start)\\s+([a-zA-Z0-9][a-zA-Z0-9 ._-]{0,49})$",
        RegexOption.IGNORE_CASE
    )

    fun parse(command: String): Action? {
        val match = openAppPattern.matchEntire(command.trim()) ?: return null
        val label = match.groupValues[1].trim().trimEnd('.', '!', '?')
        if (label.isBlank()) return null
        // A conjunction usually signals a multi-step request, not an app name.
        if (Regex("\\b(and|then|after|before)\\b", RegexOption.IGNORE_CASE).containsMatchIn(label)) return null
        return Action(action = Action.OPEN_APP, label = label)
    }
}
