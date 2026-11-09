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

    suspend fun failureDisposition(action: Action, reason: String): String {
        val decision = SelfHealingRecoveryPolicy.classify(action, reason)
        val pattern = patternStore.record(decision.area.name)
        val recurrence = if (SelfHealingPatternCodec.recurring(pattern)) {
            " This is a recurring ${decision.area.name.lowercase().replace('_', ' ')} failure (${pattern.count} occurrences); escalate for review."
        } else ""
        val verification = if (decision.area == SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY) {
            val result = ollamaHealthCheck.verify()
            Log.i("JarvisSelfHealing", "local_ollama_probe state=${result.state}")
            " Local dependency check: ${result.detail} The failed task was not replayed; retry it only after reviewing this result."
        } else ""
        Log.w("JarvisSelfHealing", "area=${decision.area} recovery=${decision.recovery} autoRetry=${decision.mayRetryAutomatically} recurring=${pattern.count}")
        return "${decision.explanation}${verification}${recurrence}"
    }
}
