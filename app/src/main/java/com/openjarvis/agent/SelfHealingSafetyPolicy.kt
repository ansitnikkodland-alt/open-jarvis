package com.openjarvis.agent

/**
 * Fail-closed retry policy for self-healing.
 * Bounded waits and read-only observations may be repeated. UI side effects are never
 * replayed because the app may have accepted an action even when acknowledgement was lost.
 */
internal object SelfHealingSafetyPolicy {
    const val MAX_WAIT_ATTEMPTS = 2
    const val MAX_WAIT_MS = 5_000L

    fun maxAttempts(action: Action): Int = when (action.action) {
        Action.WAIT, Action.WAIT_FOR, Action.READ_SCREEN, Action.EXTRACT_TEXT -> MAX_WAIT_ATTEMPTS
        else -> 1
    }

    fun boundedWaitMs(action: Action): Long =
        action.timeoutMs.coerceIn(0L, MAX_WAIT_MS)

    /** LLM-generated alternatives are deliberately not executable. */
    fun acceptsModelAlternative(action: Action?): Boolean = false
}
