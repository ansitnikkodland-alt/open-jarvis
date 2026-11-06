package com.openjarvis.voice

/** Deterministic locale selection shared by speech recognition and speech output. */
internal object SpeechLanguage {
    val recognitionLocales: List<String> = listOf("hi-IN", "mr-IN", "en-IN")

    val commandBiasingPhrases: List<String> = listOf(
        "Jarvis", "open", "launch", "close", "back", "home", "settings",
        "call", "phone", "message", "WhatsApp", "Instagram", "YouTube",
        "Google Maps", "Bluetooth", "Wi-Fi", "volume", "brightness",
        "screenshot", "torch", "flashlight", "alarm", "timer", "reminder",
        "search", "scroll", "tap", "swipe", "खोलो", "बंद करो", "वापस",
        "कॉल करो", "मैसेज भेजो", "उघड", "बंद कर", "मागे जा", "फोन लाव",
        "मेसेज पाठव"
    )

    fun selectBestTranscript(matches: List<String>?, confidenceScores: FloatArray?): String? {
        val candidates = matches.orEmpty().mapIndexedNotNull { index, raw ->
            val value = raw.trim()
            if (value.isEmpty()) null else Triple(index, value, confidenceScores?.getOrNull(index))
        }
        return candidates.maxWithOrNull(
            compareBy<Triple<Int, String, Float?>> { it.third ?: Float.NEGATIVE_INFINITY }
                .thenBy { -it.first }
        )?.second
    }

    fun preferredRecognitionLocale(deviceLanguage: String): String = when (deviceLanguage.lowercase()) {
        "mr" -> "mr-IN"
        "en" -> "en-IN"
        else -> "hi-IN"
    }

    fun languageForText(text: String): String {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (!hasDevanagari) return "en"
        val marathiMarkers = listOf(
            "आहे", "आणि", "मला", "काय", "झाले", "झालं", "नाही",
            "दाखवा", "उघडा", "करायचे", "माझे", "तुम्ही", "सांगा", "नको"
        )
        return if (marathiMarkers.any { text.contains(it) }) "mr" else "hi"
    }
}
