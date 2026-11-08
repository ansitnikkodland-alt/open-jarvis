package com.openjarvis.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDoctorTest {
    @Test fun diagnosesLocalOllamaWithoutSuggestingCloudFallback() {
        val report = ReportDoctor.diagnose("LLM provider", "Connection refused at 127.0.0.1:11434")
        assertEquals(ReportDoctor.Area.OLLAMA_CONNECTION, report.area)
        assertTrue(report.safeNextStep.contains("Termux"))
        assertFalse(report.safeNextStep.contains("cloud"))
    }

    @Test fun diagnosesVoiceCuteWakeWordFailure() {
        val report = ReportDoctor.diagnose("Wake word", "VoiceCute wake detection timeout")
        assertEquals(ReportDoctor.Area.WAKE_WORD, report.area)
        assertTrue(report.safeNextStep.contains("normal speaking volume"))
    }

    @Test fun diagnosesOverlayWithoutAutomaticallyChangingSettings() {
        val report = ReportDoctor.diagnose("Overlay", "floating window detached")
        assertEquals(ReportDoctor.Area.OVERLAY, report.area)
        assertTrue(report.safeNextStep.contains("Check Display over other apps"))
        assertFalse(report.safeNextStep.contains("restart"))
    }

    @Test fun unknownFailureProducesSafeManualNextStep() {
        val report = ReportDoctor.diagnose("unknown", "unclassified failure")
        assertEquals(ReportDoctor.Area.UNKNOWN, report.area)
        assertEquals(ReportDoctor.Severity.INFO, report.severity)
    }
}
