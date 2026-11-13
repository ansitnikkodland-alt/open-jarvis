package com.openjarvis.agent

/**
 * A conservative approval gate. Only observation and explicit error actions run
 * without human approval. Unknown actions also require approval (fail closed).
 */
object ActionApprovalPolicy {
    fun requiresApproval(action: Action): Boolean = when (action.action) {
        Action.WAIT_FOR, Action.READ_SCREEN, Action.EXTRACT_TEXT, Action.ERROR -> false
        else -> true
    }

    fun describe(action: Action): String = buildString {
        append("Action: ").append(action.action)
        action.packageName?.let { append("\nPackage: ").append(it) }
        action.label?.let { append("\nApplication: ").append(it) }
        action.text?.let { append("\nTarget: ").append(it) }
        action.value?.let { append("\nText to enter: ").append(it) }
        action.x?.let { append("\nX: ").append(it) }
        action.y?.let { append("\nY: ").append(it) }
        action.direction?.let { append("\nDirection: ").append(it) }
        append("\n\nApprove only if you recognize this exact operation.")
        append("\nA tap may trigger a purchase, send a message or delete data.")
    }
}
