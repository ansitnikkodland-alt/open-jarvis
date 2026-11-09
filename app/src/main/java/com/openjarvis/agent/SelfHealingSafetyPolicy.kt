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

    /** Retry a read-only observation only when the classifier explicitly permits refresh. */
    fun shouldRetry(action: Action, failureReason: String): Boolean = when (action.action) {
        Action.WAIT, Action.WAIT_FOR -> true
        Action.READ_SCREEN, Action.EXTRACT_TEXT ->
            SelfHealingRecoveryPolicy.classify(action, failureReason).mayRetryAutomatically
        else -> false
    }

    fun boundedWaitMs(action: Action): Long =
        action.timeoutMs.coerceIn(0L, MAX_WAIT_MS)

    fun acceptsModelAlternative(action: Action?): Boolean = false
}
