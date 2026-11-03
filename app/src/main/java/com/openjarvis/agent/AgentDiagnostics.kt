package com.openjarvis.agent

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only, user-copyable diagnostics. Never includes API keys or full screen OCR. */
object AgentDiagnostics {
    private const val PREFS = "jarvis_agent_diagnostics"
    private const val KEY_LATEST = "latest_report"
    private const val TAG = "JarvisDiagnostics"

    fun record(
        context: Context,
        stage: String,
        command: String,
        reason: String,
        rawResponse: String = "",
        error: Throwable? = null,
        provider: String = "Not identified",
        model: String = "Not applicable"
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("OPEN JARVIS DEBUG REPORT")
            appendLine("Time: $timestamp")
            appendLine("Stage: $stage")
            appendLine("Provider: $provider")
            appendLine("Model: $model")
            appendLine("Command: ${command.take(240)}")
            appendLine("Reason: ${reason.take(1200)}")
            if (rawResponse.isNotBlank()) {
                appendLine("--- MODEL RESPONSE (max 4000 chars) ---")
                appendLine(rawResponse.take(4000))
            }
            if (error != null) {
                appendLine("--- EXCEPTION ---")
                appendLine(Log.getStackTraceString(error).take(5000))
            }
        }.trim()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LATEST, report).apply()
        Log.e(TAG, report)
    }

    fun latest(context: Context): String = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LATEST, "No debug log saved yet. Run a command first.").orEmpty()
}
