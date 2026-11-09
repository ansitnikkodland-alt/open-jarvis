package com.openjarvis.agent

/**
 * Deterministic recovery classification. This layer never executes an action.
 * It separates repeatable observations from actions whose completion may be ambiguous.
 */
internal object SelfHealingRecoveryPolicy {
    enum class FailureArea { OLLAMA_CONNECTIVITY, MODEL_UNAVAILABLE, ACCESSIBILITY, APP_LAUNCH, SPEECH, OVERLAY, ACTION, UNKNOWN }
    enum class RecoveryKind { VERIFY_LOCAL_SERVICE, VERIFY_CONFIGURATION, REFRESH_OBSERVATION, USER_ACTION_REQUIRED, ESCALATE }

    data class Decision(
        val area: FailureArea,
        val recovery: RecoveryKind,
        val mayRetryAutomatically: Boolean,
        val requiresUserConfirmation: Boolean,
        val verifyAfterRecovery: Boolean,
        val explanation: String
    )

    fun classify(action: Action, reason: String): Decision {
        val value = reason.lowercase()
        val isObservation = action.action == Action.WAIT || action.action == Action.WAIT_FOR ||
            action.action == Action.READ_SCREEN || action.action == Action.EXTRACT_TEXT
        val area = when {
            contains(value, "ollama", "11434", "connection refused", "failed to connect", "socket timeout") -> FailureArea.OLLAMA_CONNECTIVITY
            contains(value, "model not found", "unknown model", "qwen3:1.7b") -> FailureArea.MODEL_UNAVAILABLE
            contains(value, "accessibility", "visible text", "node not found", "no accessible text") -> FailureArea.ACCESSIBILITY
            contains(value, "app not installed", "package not found", "launch activity") -> FailureArea.APP_LAUNCH
            contains(value, "speech", "stt", "microphone", "no match") -> FailureArea.SPEECH
            contains(value, "overlay", "floating window") -> FailureArea.OVERLAY
            contains(value, "tap", "type", "click", "send", "delete", "payment") -> FailureArea.ACTION
            else -> FailureArea.UNKNOWN
        }
        return when (area) {
            FailureArea.OLLAMA_CONNECTIVITY -> Decision(area, RecoveryKind.VERIFY_LOCAL_SERVICE, true, false, true,
                "Check the local Ollama endpoint only; never switch provider or model.")
            FailureArea.MODEL_UNAVAILABLE -> Decision(area, RecoveryKind.VERIFY_CONFIGURATION, false, true, true,
                "Verify the installed local qwen3:1.7b model; do not download or change the default automatically.")
            FailureArea.ACCESSIBILITY -> {
                // Missing screen content/node is a stale observation, not proof that Android
                // accessibility is disabled. Refreshing a read-only observation is safe;
                // permission/service failures still require the user to intervene.
                val missingObservation = isObservation &&
                    contains(value, "visible text not found", "node not found", "element not found", "no accessible text")
                if (missingObservation) {
                    Decision(area, RecoveryKind.REFRESH_OBSERVATION, true, false, true,
                        "Refresh the read-only screen observation; do not repeat any UI action.")
                } else {
                    Decision(area, RecoveryKind.USER_ACTION_REQUIRED, false, true, true,
                        "Ask the user to check Android accessibility permission/service state, then verify again.")
                }
            }
            FailureArea.OVERLAY, FailureArea.SPEECH ->
                Decision(area, RecoveryKind.USER_ACTION_REQUIRED, false, true, true,
                    "Ask the user to check Android permission/service state, then verify again after the user acts.")
            FailureArea.APP_LAUNCH, FailureArea.ACTION ->
                Decision(
                    area,
                    if (isObservation) RecoveryKind.REFRESH_OBSERVATION else RecoveryKind.USER_ACTION_REQUIRED,
                    isObservation,
                    !isObservation,
                    true,
                    "Observe current state before any retry; never replay a possibly completed side effect."
                )
            FailureArea.UNKNOWN -> Decision(area, RecoveryKind.ESCALATE, false, true, false,
                "Unknown failure: stop automation and request user review.")
        }
    }

    private fun contains(value: String, vararg terms: String) = terms.any(value::contains)
}

/** Privacy-safe recurring-failure signature; never includes command, screen, or message text. */
internal object SelfHealingPatternCodec {
    data class Pattern(val area: String, val count: Int, val lastSeenMillis: Long)

    fun record(existing: List<Pattern>, area: String, now: Long, limit: Int = 12): List<Pattern> {
        val safeArea = area.takeIf { it in SelfHealingRecoveryPolicy.FailureArea.values().map { item -> item.name } }
            ?: SelfHealingRecoveryPolicy.FailureArea.UNKNOWN.name
        val current = existing.firstOrNull { it.area == safeArea }
        val updated = Pattern(safeArea, (current?.count ?: 0) + 1, now)
        return (listOf(updated) + existing.filterNot { it.area == safeArea })
            .sortedByDescending { it.lastSeenMillis }
            .take(limit.coerceAtLeast(1))
    }

    fun recurring(pattern: Pattern, threshold: Int = 3): Boolean = pattern.count >= threshold.coerceAtLeast(2)
}
