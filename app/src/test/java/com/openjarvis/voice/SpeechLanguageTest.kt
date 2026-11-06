package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguageTest {
    @Test fun recognizesPreferredDeviceLocales() {
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("hi"))
        assertEquals("mr-IN", SpeechLanguage.preferredRecognitionLocale("mr"))
        assertEquals("en-IN", SpeechLanguage.preferredRecognitionLocale("en"))
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("unknown"))
    }

    @Test fun selectsHighestConfidenceNonEmptyTranscript() {
        assertEquals("open WhatsApp", SpeechLanguage.selectBestTranscript(
            listOf("open what is app", "open WhatsApp", "  "),
            floatArrayOf(0.31f, 0.92f, 0.99f)
        ))
    }

    @Test fun fallsBackToFirstNonEmptyTranscriptWithoutConfidence() {
        assertEquals("पहला विकल्प", SpeechLanguage.selectBestTranscript(
            listOf("पहला विकल्प", "दूसरा विकल्प"), null
        ))
    }

    @Test fun returnsNullWhenNoTranscriptExists() {
        assertEquals(null, SpeechLanguage.selectBestTranscript(listOf(" ", ""), null))
    }

    @Test fun detectsHindiMarathiAndEnglishOutput() {
        assertEquals("hi", SpeechLanguage.languageForText("नमस्ते, आप कैसे हैं"))
        assertEquals("mr", SpeechLanguage.languageForText("मला मदत करा, हे चांगले आहे"))
        assertEquals("en", SpeechLanguage.languageForText("Hello, Jarvis"))
    }
}
