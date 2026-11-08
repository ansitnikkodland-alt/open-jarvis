package com.openjarvis.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local-only bounded failure-pattern counters. Stores category names and counts, never user content. */
internal class SelfHealingPatternStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_self_healing_patterns", Context.MODE_PRIVATE)

    fun record(area: String, now: Long = System.currentTimeMillis()): SelfHealingPatternCodec.Pattern {
        val updated = SelfHealingPatternCodec.record(read(), area, now).first()
        val json = JSONArray()
        SelfHealingPatternCodec.record(read(), area, now).forEach { item ->
            json.put(JSONObject().put("area", item.area).put("count", item.count).put("lastSeen", item.lastSeenMillis))
        }
        prefs.edit().putString(KEY_PATTERNS, json.toString()).apply()
        return updated
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
            else SelfHealingPatternCodec.Pattern(area, count, lastSeen)
        }.take(12)
    }.getOrDefault(emptyList())

    companion object { private const val KEY_PATTERNS = "patterns_json" }
}
