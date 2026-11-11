package com.openjarvis.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEchoGuardTest {
    @Test fun ignoresExactTtsEchoDuringAndShortlyAfterSpeech() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("Jarvis is listening now.")
        assertTrue(guard.shouldIgnore("Jarvis is listening now", 100L))
        guard.onSpeechFinished(200L)
        assertTrue(guard.shouldIgnore("JARVIS is listening now!", 2_000L))
    }

    @Test fun ignoresRecognizedPhraseFromInsideLongerTtsReply() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("Aaj mausam achha hai. Main aapki madad kar sakta hoon.")
        guard.onSpeechFinished(500L)
        assertTrue(guard.shouldIgnore("Main aapki madad kar sakta hoon", 600L))
    }

    @Test fun doesNotIgnoreDifferentUserCommand() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("Abhi 9 baj rahe hain.")
        guard.onSpeechFinished(100L)
        assertFalse(guard.shouldIgnore("volume badhao", 200L))
    }

    @Test fun expiresEchoWindowAndAllowsLaterMatchingWords() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("Jarvis is listening.")
        guard.onSpeechFinished(100L)
        assertFalse(guard.shouldIgnore("Jarvis is listening.", 100L + VoiceSessionTiming.TTS_ECHO_WINDOW_MS + 1L))
    }

    @Test fun postTtsRearmDelayIsSevenHundredMilliseconds() {
        assertTrue(VoiceSessionTiming.POST_TTS_REARM_DELAY_MS == 700L)
    }
}
