package com.openjarvis.agent

import android.content.Context
import android.util.Log

/**
 * Deterministic recovery planner. It classifies failures, records privacy-safe recurrence
 * counters, and returns a bounded next step. It never replays UI actions.
 */
class SelfHealingExecutor(context: Context) {
    private val patternStore = SelfHealingPatternStore(context.applicationContext)

    fun failureDisposition(action: Action, reason: String): String {
        val decision = SelfHealingRecoveryPolicy.classify(action, reason)
        val pattern = patternStore.record(decision.area.name)
        val recurrence = if (SelfHealingPatternCodec.recurring(pattern)) {
            " This is a recurring ${decision.area.name.lowercase().replace('_', ' ')} failure (${pattern.count} occurrences); escalate for review."
        } else ""
        Log.w("JarvisSelfHealing", "area=${decision.area} recovery=${decision.recovery} autoRetry=${decision.mayRetryAutomatically} recurring=${pattern.count}")
        return "${decision.explanation}${recurrence}"
    }
}
