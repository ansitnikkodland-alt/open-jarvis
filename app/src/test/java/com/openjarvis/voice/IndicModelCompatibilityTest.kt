package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicModelCompatibilityTest {
    private fun profile(
        modelBytes: Long? = 100L, peakBytes: Long? = 500L, license: String? = "Apache-2.0",
        minimumApi: Int = 26, language: String = "mr", abi: String = "arm64-v8a"
    ) = IndicModelProfile(language, "test-indic-model", minimumApi, abi, modelBytes, peakBytes, license)

    @Test fun acceptsOnlyFullySpecifiedProfileWithinConservativeBudget() {
        val decision = IndicModelCompatibility.evaluate(profile(), 34, "arm64-v8a", 1_000L, 2_000L)
        assertTrue(decision.compatible)
    }
    @Test fun rejectsUnsupportedLanguageAndPlatformMismatch() {
        assertEquals(IndicModelRejection.UNSUPPORTED_LANGUAGE, IndicModelCompatibility.evaluate(profile(language = "en"), 34, "arm64-v8a", 1_000L, 2_000L).rejection)
        assertEquals(IndicModelRejection.API_TOO_LOW, IndicModelCompatibility.evaluate(profile(minimumApi = 34), 26, "arm64-v8a", 1_000L, 2_000L).rejection)
        assertEquals(IndicModelRejection.ABI_MISMATCH, IndicModelCompatibility.evaluate(profile(), 34, "x86_64", 1_000L, 2_000L).rejection)
    }
    @Test fun refusesUnknownModelSizeMemoryOrLicense() {
        assertEquals(IndicModelRejection.MODEL_SIZE_UNKNOWN, IndicModelCompatibility.evaluate(profile(modelBytes = null), 34, "arm64-v8a", 1_000L, 2_000L).rejection)
        assertEquals(IndicModelRejection.MEMORY_ESTIMATE_UNKNOWN, IndicModelCompatibility.evaluate(profile(peakBytes = null), 34, "arm64-v8a", 1_000L, 2_000L).rejection)
        assertEquals(IndicModelRejection.LICENSE_UNVERIFIED, IndicModelCompatibility.evaluate(profile(license = "unknown"), 34, "arm64-v8a", 1_000L, 2_000L).rejection)
    }
    @Test fun rejectsInsufficientStorageOrMemoryHeadroom() {
        assertEquals(IndicModelRejection.STORAGE_INSUFFICIENT, IndicModelCompatibility.evaluate(profile(modelBytes = 2_000L), 34, "arm64-v8a", 1_000L, 2_000L).rejection)
        assertEquals(IndicModelRejection.MEMORY_BUDGET_EXCEEDED, IndicModelCompatibility.evaluate(profile(peakBytes = 1_001L), 34, "arm64-v8a", 1_000L, 2_000L).rejection)
    }
}