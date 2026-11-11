package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionCandidateSelectorTest {
    @Test fun choosesHighestConfidenceNonBlankCandidate() {
        assertEquals(
            "Jarvis volume badhao",
            RecognitionCandidateSelector.select(
                listOf("Jarvis volume badal", "   ", "Jarvis volume badhao"),
                floatArrayOf(0.42f, 0.99f, 0.91f)
            )
        )
    }

    @Test fun skipsBlankCandidatesAndUsesFirstNonBlankWhenScoresMissing() {
        assertEquals(
            "Time kya hua",
            RecognitionCandidateSelector.select(listOf(" ", "", "Time kya hua", "What time is it"))
        )
    }

    @Test fun keepsRecognizerOrderWhenConfidenceScoresTieOrAreInvalid() {
        assertEquals(
            "पहला विकल्प",
            RecognitionCandidateSelector.select(
                listOf("पहला विकल्प", "दूसरा विकल्प"),
                floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY)
            )
        )
        assertEquals(
            "पहला",
            RecognitionCandidateSelector.select(
                listOf("पहला", "दूसरा"),
                floatArrayOf(0.8f, 0.8f)
            )
        )
    }

    @Test fun returnsNullWhenEveryUsableHypothesisIsBelowConfidenceThreshold() {
        assertNull(
            RecognitionCandidateSelector.select(
                listOf("open settings", "open settings please"),
                floatArrayOf(0.12f, RecognitionCandidateSelector.MIN_CONFIDENCE - 0.01f)
            )
        )
    }

    @Test fun treatsNegativeOneConfidenceAsUnavailableNotLowConfidence() {
        assertEquals(
            "पहला विकल्प",
            RecognitionCandidateSelector.select(
                listOf("पहला विकल्प", "दूसरा विकल्प"),
                floatArrayOf(-1f, -1f)
            )
        )
    }

    @Test fun returnsNullForMissingOrEntirelyBlankResults() {
        assertNull(RecognitionCandidateSelector.select(null))
        assertNull(RecognitionCandidateSelector.select(emptyList()))
        assertNull(RecognitionCandidateSelector.select(listOf(" ", "\t", "\n")))
    }

    @Test fun aBlankPartialCandidateCannotBecomeAnExecutableTranscript() {
        val partial = RecognitionCandidateSelector.select(listOf("", "   "))
        val final = RecognitionCandidateSelector.select(emptyList())
        assertNull(partial)
        assertNull(final)
    }
}
