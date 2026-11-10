package com.openjarvis.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenEvidenceVerifierTest {
    @Test fun verifiesExpectedPackageAfterNavigation() {
        val before = ScreenEvidence("com.android.settings", "Settings", 4)
        val after = ScreenEvidence("com.whatsapp", "Chats", 8)
        assertEquals(ScreenVerification.VERIFIED, ScreenEvidenceVerifier.verifyNavigation(before, after, expectedPackage = "com.whatsapp"))
    }
    @Test fun verifiesVisibleExpectedTextWithoutPackageChange() {
        val before = ScreenEvidence("com.example.app", "Home", 2)
        val after = ScreenEvidence("com.example.app", "Home Search results for Sagar", 3)
        assertEquals(ScreenVerification.VERIFIED, ScreenEvidenceVerifier.verifyNavigation(before, after, expectedVisibleText = "Search results"))
    }
    @Test fun detectsObservableScreenTextChange() {
        val before = ScreenEvidence("com.example.app", "Home", 2)
        val after = ScreenEvidence("com.example.app", "Inbox unread messages", 5)
        assertEquals(ScreenVerification.VERIFIED, ScreenEvidenceVerifier.verifyNavigation(before, after))
    }
    @Test fun doesNotClaimSuccessWhenNothingChanged() {
        val evidence = ScreenEvidence("com.example.app", "Home", 2)
        assertEquals(ScreenVerification.UNVERIFIED, ScreenEvidenceVerifier.verifyNavigation(evidence, evidence))
    }
    @Test fun verifiesTypedTextByVisibleEvidence() {
        val after = ScreenEvidence("com.example.app", "Message body: नमस्कार मित्रा", 4)
        assertEquals(ScreenVerification.VERIFIED, ScreenEvidenceVerifier.verifyTextVisible(after, "नमस्कार मित्रा"))
    }
    @Test fun rejectsMissingOrInvalidEvidence() {
        assertEquals(ScreenVerification.INVALID_EVIDENCE, ScreenEvidenceVerifier.verifyNavigation(null, ScreenEvidence("app", "Home", 1)))
        assertEquals(ScreenVerification.INVALID_EVIDENCE, ScreenEvidenceVerifier.verifyTextVisible(ScreenEvidence("app", "", 0), "  "))
    }
}