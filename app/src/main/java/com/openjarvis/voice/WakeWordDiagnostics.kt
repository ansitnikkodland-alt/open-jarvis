package com.openjarvis.voice

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WakeWordDiagnosticSnapshot(
    val engineName: String = "Voicute ONNX KWS",
    val state: String = "NOT_STARTED",
    val micPermissionGranted: Boolean = false,
    val rms: Float = 0f,
    val peakLevel: Float = 0f,
    val framesProcessed: Long = 0L,
    val silentFrames: Long = 0L,
    val clippedSamples: Long = 0L,
    val agcAvailable: Boolean = false,
    val agcEnabled: Boolean = false,
    val noiseSuppressorAvailable: Boolean = false,
    val noiseSuppressorEnabled: Boolean = false,
    val audioSource: String = "unknown",
    val sampleRate: Int = 0,
    val lastDetectionAtMs: Long? = null,
    val lastDetectionKeyword: String? = null,
    val lastDetectionScore: Float? = null,
    val keywords: List<String> = emptyList(),
    val threshold: Float = 0.25f,
    val keywordBoost: Float = 1.5f,
    val lastError: String? = null,
    val lastCandidateScores: String? = null,
    val candidateFrames: Long = 0L
)

data class JarvisDiagnosticEvent(val atMs: Long, val level: String, val message: String)

object WakeWordDiagnostics {
    private const val SILENCE_RMS_THRESHOLD = 0.003f
    private const val MAX_EVENTS = 300
    @Volatile private var current = WakeWordDiagnosticSnapshot()
    private val events = ArrayList<JarvisDiagnosticEvent>(MAX_EVENTS)

    @Synchronized fun snapshot(): WakeWordDiagnosticSnapshot = current
    @Synchronized fun recentEvents(): List<JarvisDiagnosticEvent> = events.toList()

    @Synchronized private fun addEvent(level: String, message: String) {
        events.add(JarvisDiagnosticEvent(System.currentTimeMillis(), level, message.take(500)))
        if (events.size > MAX_EVENTS) events.removeAt(0)
    }

    @Synchronized
    fun started(permissionGranted: Boolean, keywords: List<String>, threshold: Float, boost: Float, engineName: String = "Voicute ONNX KWS") {
        current = current.copy(engineName = engineName, state = "LISTENING", micPermissionGranted = permissionGranted,
            rms = 0f, peakLevel = 0f, framesProcessed = 0L, silentFrames = 0L, clippedSamples = 0L,
            agcAvailable = false, agcEnabled = false, noiseSuppressorAvailable = false,
            noiseSuppressorEnabled = false, audioSource = "unknown", sampleRate = 0,
            lastDetectionAtMs = null, lastDetectionKeyword = null, lastDetectionScore = null,
            keywords = keywords, threshold = threshold, keywordBoost = boost, lastError = null,
            lastCandidateScores = null, candidateFrames = 0L)
        addEvent("INFO", "Wake listener started; permission=" + permissionGranted + "; phrases=" + keywords.joinToString() + "; threshold=" + threshold)
    }

    @Synchronized
    fun audioConfiguration(agcAvailable: Boolean, agcEnabled: Boolean, noiseSuppressorAvailable: Boolean,
                           noiseSuppressorEnabled: Boolean, audioSource: String, sampleRate: Int) {
        current = current.copy(agcAvailable = agcAvailable, agcEnabled = agcEnabled,
            noiseSuppressorAvailable = noiseSuppressorAvailable, noiseSuppressorEnabled = noiseSuppressorEnabled,
            audioSource = audioSource, sampleRate = sampleRate)
        addEvent("INFO", "Audio config: source=" + audioSource + " rate=" + sampleRate + " AGC=" + agcEnabled + "/" + agcAvailable + " NS=" + noiseSuppressorEnabled + "/" + noiseSuppressorAvailable)
    }

    @Synchronized
    fun audioFrame(level: Float, peak: Float, clipped: Int) {
        current = current.copy(state = if (current.state == "ERROR") "ERROR" else "LISTENING",
            rms = level.coerceIn(0f, 1f), peakLevel = peak.coerceIn(0f, 1f),
            framesProcessed = current.framesProcessed + 1L,
            silentFrames = current.silentFrames + if (level < SILENCE_RMS_THRESHOLD) 1L else 0L,
            clippedSamples = current.clippedSamples + clipped.coerceAtLeast(0))
    }

    @Synchronized
    fun candidate(scores: String, threshold: Float, rms: Float, accepted: Boolean) {
        current = current.copy(lastCandidateScores = scores, candidateFrames = current.candidateFrames + 1L)
        addEvent(if (accepted) "MATCH" else "SCORE", "scores={" + scores + "}; threshold=" + threshold + "; rms=" + String.format(Locale.US, "%.4f", rms) + "; accepted=" + accepted)
    }

    @Synchronized
    fun detected(keyword: String, score: Float? = null) {
        current = current.copy(state = "DETECTED", lastDetectionAtMs = System.currentTimeMillis(),
            lastDetectionKeyword = keyword, lastDetectionScore = score)
        addEvent("MATCH", "Wake word accepted: " + keyword + " score=" + (score?.let { String.format(Locale.US, "%.4f", it) } ?: "n/a"))
    }

    @Synchronized
    fun failed(message: String) {
        current = current.copy(state = "ERROR", lastError = message)
        addEvent("ERROR", message)
    }

    @Synchronized
    fun stopped() {
        current = current.copy(state = "STOPPED", rms = 0f, peakLevel = 0f)
        addEvent("INFO", "Wake listener stopped")
    }

    @Synchronized
    fun buildReport(context: Context): String {
        val s = current
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName + " (" + info.versionCode + ")"
        }.getOrDefault("unknown")
        val out = StringBuilder()
        out.appendLine("JARVIS DIAGNOSTIC REPORT")
        out.appendLine("Generated: " + stamp)
        out.appendLine("App: " + version)
        out.appendLine("Device: " + Build.MANUFACTURER + " " + Build.MODEL)
        out.appendLine("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
        out.appendLine("Engine: " + s.engineName)
        out.appendLine("State: " + s.state)
        out.appendLine("Mic permission: " + s.micPermissionGranted)
        out.appendLine("Audio: source=" + s.audioSource + ", sampleRate=" + s.sampleRate)
        out.appendLine("Signal: rms=" + String.format(Locale.US, "%.5f", s.rms) + ", peak=" + String.format(Locale.US, "%.5f", s.peakLevel) + ", frames=" + s.framesProcessed + ", silentFrames=" + s.silentFrames + ", clippedSamples=" + s.clippedSamples)
        out.appendLine("Effects: AGC available=" + s.agcAvailable + ", enabled=" + s.agcEnabled + "; NoiseSuppressor available=" + s.noiseSuppressorAvailable + ", enabled=" + s.noiseSuppressorEnabled)
        out.appendLine("Phrases: " + s.keywords.joinToString())
        out.appendLine("Threshold: " + s.threshold + "; keywordBoost=" + s.keywordBoost)
        out.appendLine("Last candidate scores: " + (s.lastCandidateScores ?: "unavailable"))
        out.appendLine("Candidate frames recorded: " + s.candidateFrames)
        out.appendLine("Last accepted detection: " + (s.lastDetectionKeyword ?: "never") + "; score=" + (s.lastDetectionScore?.toString() ?: "unavailable") + "; at=" + (s.lastDetectionAtMs?.toString() ?: "n/a"))
        out.appendLine("Last error: " + (s.lastError ?: "none"))
        out.appendLine()
        out.appendLine("RECENT EVENTS (newest at bottom; max " + MAX_EVENTS + ")")
        recentEvents().forEach { event ->
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(event.atMs))
            out.appendLine("[" + time + "] [" + event.level + "] " + event.message)
        }
        out.appendLine()
        out.appendLine("Privacy: no microphone audio or speech recordings are included.")
        return out.toString()
    }
}