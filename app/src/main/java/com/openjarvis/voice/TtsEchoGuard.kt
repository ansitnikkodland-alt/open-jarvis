package com.openjarvis.voice

import java.text.Normalizer

/** Suppresses only text that matches Jarvis's own just-spoken TTS output. */
internal class TtsEchoGuard(
    private val postSpeechWindowMs: Long = VoiceSessionTiming.TTS_ECHO_WINDOW_MS
) {
    private var normalizedSpokenText: String = ""
    private var ignoreUntilMs: Long = 0L

    fun onSpeechStarted(text: String) {
        normalizedSpokenText = normalize(text)
        ignoreUntilMs = Long.MAX_VALUE
    }

    fun onSpeechFinished(nowMs: Long) {
        if (normalizedSpokenText.isNotBlank()) {
            ignoreUntilMs = nowMs + postSpeechWindowMs
        }
    }

    fun shouldIgnore(transcript: String, nowMs: Long): Boolean {
        if (normalizedSpokenText.isBlank() || nowMs > ignoreUntilMs) {
            reset()
            return false
        }
        val candidate = normalize(transcript)
        if (candidate.isBlank()) return false
        if (candidate == normalizedSpokenText) return true
        if (candidate.length < 10) return false
        val spokenTokens = normalizedSpokenText.split(" ")
        val candidateTokens = candidate.split(" ")
        return candidateTokens.size >= 2 &&
            spokenTokens.windowed(candidateTokens.size).any { it == candidateTokens }
    }

    fun reset() {
        normalizedSpokenText = ""
        ignoreUntilMs = 0L
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
