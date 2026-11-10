package com.openjarvis.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small per-app store for generic successful-flow hints; no raw action history is persisted. */
class AppTaskHintStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("app_task_hints_v1", Context.MODE_PRIVATE)

    fun getHints(packageName: String): List<AppTaskHint> {
        if (packageName.isBlank()) return emptyList()
        val raw = preferences.getString(key(packageName), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AppTaskHintPolicy.create(packageName, item.optString("task"), item.optString("guidance"))
            }
        }.getOrDefault(emptyList())
    }

    /** Records only a successful generic flow. Returns false when privacy policy rejects it. */
    fun recordSuccessfulFlow(packageName: String, taskLabel: String, guidance: String): Boolean {
        val candidate = AppTaskHintPolicy.create(packageName, taskLabel, guidance) ?: return false
        val merged = AppTaskHintPolicy.merge(getHints(packageName), candidate)
        val array = JSONArray()
        merged.forEach { hint ->
            array.put(JSONObject().put("task", hint.taskLabel).put("guidance", hint.guidance))
        }
        preferences.edit().putString(key(packageName), array.toString()).apply()
        return true
    }

    fun clear(packageName: String) {
        if (packageName.isNotBlank()) preferences.edit().remove(key(packageName)).apply()
    }

    private fun key(packageName: String): String = "app_" + packageName.replace(Regex("[^A-Za-z0-9_]"), "_")
}