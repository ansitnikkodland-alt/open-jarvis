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

    fun recordVerifiedReadOnlyRecovery(action: Action, originalFailure: String) {
        val decision = SelfHealingRecoveryPolicy.classify(action, originalFailure)
        if (!decision.mayRetryAutomatically) return
        val outcome = patternStore.recordOutcome(decision.area.name, verified = true)
        Log.i("JarvisSelfHealing", "recovery_outcome area=${decision.area} verified=true unresolved=${outcome?.unresolvedFailures ?: -1}")
    }

    suspend fun failureDisposition(action: Action, reason: String): String {
        val decision = SelfHealingRecoveryPolicy.classify(action, reason)
        val pattern = patternStore.record(decision.area.name)
        val recurrence = if (SelfHealingPatternCodec.recurring(pattern)) {
            " This failure category has ${pattern.consecutiveFailures} consecutive unresolved occurrences; pause automatic retries and escalate for user review."
        } else ""
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
        Log.w("JarvisSelfHealing", "area=${decision.area} recovery=${decision.recovery} autoRetry=${decision.mayRetryAutomatically} recurring=${pattern.count}")
        return "${decision.explanation}${verification}${recurrence}"
    }
}
