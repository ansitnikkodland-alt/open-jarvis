package com.openjarvis.agent

import android.content.Context
import android.util.Log

/**
 * Deterministic recovery planner. It classifies failures, verifies local dependencies
 * with bounded read-only probes, records privacy-safe recurrence counters, and never
 * replays UI actions or silently changes the configured model/provider.
 */
class SelfHealingExecutor(context: Context) {
    private val patternStore = SelfHealingPatternStore(context.applicationContext)
    private val ollamaHealthCheck = LocalOllamaHealthCheck()

    /** Consult privacy-safe local history before permitting another read-only retry. */
    fun shouldRetry(action: Action, reason: String): Boolean {
        val decision = SelfHealingRecoveryPolicy.classify(action, reason)
        val pattern = patternStore.patterns().firstOrNull { it.area == decision.area.name }
        val consecutive = pattern?.consecutiveFailures ?: 0
        val allowed = SelfHealingSafetyPolicy.shouldRetryAfterLearning(
            action = action,
            failureReason = reason,
            consecutiveUnresolved = consecutive
        )
        if (!allowed && SelfHealingSafetyPolicy.shouldRetry(action, reason)) {
            Log.w("JarvisSelfHealing", "retry_paused area=${decision.area} consecutiveUnresolved=$consecutive")
        }
        return allowed
    }

    suspend fun recordVerifiedReadOnlyRecovery(action: Action, originalFailure: String) {
        val decision = SelfHealingRecoveryPolicy.classify(action, originalFailure)
        if (!decision.mayRetryAutomatically) return

        // A successful UI observation does not prove that Ollama recovered. Verify each
        // failure area with evidence from that same subsystem before updating learned state.
        val verified = when (decision.area) {
            SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY -> {
                val result = ollamaHealthCheck.verify()
                Log.i("JarvisSelfHealing", "post_retry_ollama_probe state=${result.state}")
                result.state == LocalOllamaHealthPolicy.State.READY
            }
            else -> SelfHealingVerificationPolicy.confirmsReadOnlyRecovery(action, decision.area)
        }
        val outcome = patternStore.recordOutcome(decision.area.name, verified)
        Log.i("JarvisSelfHealing", "recovery_outcome area=${decision.area} verified=$verified unresolved=${outcome?.unresolvedFailures ?: -1}")
    }

    suspend fun failureDisposition(action: Action, reason: String): String {
        val decision = SelfHealingRecoveryPolicy.classify(action, reason)
        var pattern = patternStore.record(decision.area.name)
        val verification = when (decision.area) {
            SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY,
            SelfHealingRecoveryPolicy.FailureArea.MODEL_UNAVAILABLE -> {
                val result = ollamaHealthCheck.verify()
                Log.i("JarvisSelfHealing", "local_ollama_probe state=${result.state}")
                val dependencyVerified = result.state == LocalOllamaHealthPolicy.State.READY
                val outcome = patternStore.recordOutcome(decision.area.name, dependencyVerified)
                Log.i("JarvisSelfHealing", "recovery_outcome area=${decision.area} verified=$dependencyVerified unresolved=${outcome?.unresolvedFailures ?: -1}")
                " Local dependency check: ${result.detail} The failed task was not replayed; retry it only after reviewing this result."
            }
            else -> " Verification required: ${SelfHealingVerificationPolicy.instruction(decision.area)}"
        }
        // Re-read after the probe: a verified dependency recovery must clear the
        // consecutive-unresolved streak before recurrence guidance is generated.
        pattern = patternStore.patterns().firstOrNull { it.area == decision.area.name } ?: pattern
        val recurrence = if (SelfHealingPatternCodec.recurring(pattern)) {
            " This failure category has ${pattern.consecutiveFailures} consecutive unresolved occurrences; pause automatic retries and escalate for user review."
        } else ""
        Log.w("JarvisSelfHealing", "area=${decision.area} recovery=${decision.recovery} autoRetry=${decision.mayRetryAutomatically} consecutiveUnresolved=${pattern.consecutiveFailures} unresolved=${pattern.unresolvedFailures}")
        return "${decision.explanation}${verification}${recurrence}"
    }
}
