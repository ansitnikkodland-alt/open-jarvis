package com.openjarvis.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local-only bounded failure-pattern counters. Stores category names and counts, never user content. */
internal class SelfHealingPatternStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_self_healing_patterns", Context.MODE_PRIVATE)

    @Synchronized
    fun record(area: String, now: Long = System.currentTimeMillis()): SelfHealingPatternCodec.Pattern {
        // Read once and derive one canonical update so the returned value and persisted JSON
        // cannot diverge if another write lands between separate reads.
        val updatedPatterns = SelfHealingPatternCodec.record(read(), area, now)
        val updated = updatedPatterns.first()
        persist(updatedPatterns)
        return updated
    }

    @Synchronized
    fun recordOutcome(area: String, verified: Boolean, now: Long = System.currentTimeMillis()): SelfHealingPatternCodec.Pattern? {
        val updatedPatterns = SelfHealingPatternCodec.recordOutcome(read(), area, verified, now)
        val updated = updatedPatterns.firstOrNull { it.area == area } ?: return null
        persist(updatedPatterns)
        return updated
    }

    private fun persist(patterns: List<SelfHealingPatternCodec.Pattern>) {
        val json = JSONArray()
        patterns.forEach { item ->
            json.put(JSONObject()
                .put("area", item.area)
                .put("count", item.count)
                .put("lastSeen", item.lastSeenMillis)
                .put("verifiedRecoveries", item.verifiedRecoveries)
                .put("unresolvedFailures", item.unresolvedFailures)
                .put("consecutiveFailures", item.consecutiveFailures)
                .put("lastOutcome", item.lastOutcome))
        }
        prefs.edit().putString(KEY_PATTERNS, json.toString()).apply()
    }

    fun patterns(): List<SelfHealingPatternCodec.Pattern> = read()

    private fun read(): List<SelfHealingPatternCodec.Pattern> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PATTERNS, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val area = item.optString("area")
            val count = item.optInt("count", 0)
            val lastSeen = item.optLong("lastSeen", 0L)
            if (area.isBlank() || count <= 0 || lastSeen <= 0L) null
            else SelfHealingPatternCodec.Pattern(
                area = area,
                count = count,
                lastSeenMillis = lastSeen,
                verifiedRecoveries = item.optInt("verifiedRecoveries", 0).coerceAtLeast(0),
                unresolvedFailures = item.optInt("unresolvedFailures", count).coerceAtLeast(0),
                consecutiveFailures = item.optInt("consecutiveFailures", count).coerceAtLeast(0),
                lastOutcome = item.optString("lastOutcome", "UNRESOLVED")
                    .takeIf { it in setOf("UNRESOLVED", "VERIFIED_READ_ONLY_RECOVERY") } ?: "UNRESOLVED"
            )
        }.take(12)
    }.getOrDefault(emptyList())

    companion object { private const val KEY_PATTERNS = "patterns_json" }
}
