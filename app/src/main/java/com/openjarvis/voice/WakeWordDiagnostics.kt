package com.openjarvis.voice

/**
 * Process-local diagnostics shared by the foreground listener and Settings.
 * Sherpa's Kotlin KeywordSpotterResult does not expose an acoustic confidence
 * score, so score remains null rather than presenting a fabricated value.
 */
data class WakeWordDiagnosticSnapshot(
    val engineName: String = "Sherpa-ONNX KWS",
    val state: String = "NOT_STARTED",
    val micPermissionGranted: Boolean = false,
    val rms: Float = 0f,
    val framesProcessed: Long = 0L,
    val lastDetectionAtMs: Long? = null,
    val lastDetectionKeyword: String? = null,
    val lastDetectionScore: Float? = null,
    val keywords: List<String> = emptyList(),
    val threshold: Float = 0.25f,
    val keywordBoost: Float = 1.5f,
    val lastError: String? = null
)

object WakeWordDiagnostics {
    @Volatile private var current = WakeWordDiagnosticSnapshot()

    fun snapshot(): WakeWordDiagnosticSnapshot = current

    @Synchronized
    fun started(permissionGranted: Boolean, keywords: List<String>, threshold: Float, boost: Float) {
        current = current.copy(
            engineName = "Sherpa-ONNX KWS",
            state = "LISTENING",
            micPermissionGranted = permissionGranted,
            rms = 0f,
            framesProcessed = 0L,
            keywords = keywords,
            threshold = threshold,
            keywordBoost = boost,
            lastError = null
        )
    }

    @Synchronized
    fun audioFrame(level: Float) {
        current = current.copy(
            state = "LISTENING",
            rms = level.coerceIn(0f, 1f),
            framesProcessed = current.framesProcessed + 1L
        )
    }

    @Synchronized
    fun detected(keyword: String, score: Float? = null) {
        current = current.copy(
            state = "DETECTED",
            lastDetectionAtMs = System.currentTimeMillis(),
            lastDetectionKeyword = keyword,
            lastDetectionScore = score
        )
    }

    @Synchronized
    fun failed(message: String) {
        current = current.copy(state = "ERROR", lastError = message)
    }

    @Synchronized
    fun stopped() {
        current = current.copy(state = "STOPPED", rms = 0f)
    }
}
