package com.openjarvis.accessibility

/**
 * Pure policy for checking whether an accessibility action produced observable evidence.
 * Callers must supply snapshots captured by Jarvis' existing ScreenReader.
 * This class deliberately does not capture screens or execute actions.
 */
data class ScreenEvidence(val packageName: String?, val visibleText: String, val interactiveElementCount: Int)

enum class ScreenVerification { VERIFIED, UNVERIFIED, INVALID_EVIDENCE }

object ScreenEvidenceVerifier {
    fun verifyNavigation(before: ScreenEvidence?, after: ScreenEvidence?, expectedPackage: String? = null, expectedVisibleText: String? = null): ScreenVerification {
        if (before == null || after == null || before.interactiveElementCount < 0 || after.interactiveElementCount < 0) return ScreenVerification.INVALID_EVIDENCE
        val packageName = expectedPackage?.trim()?.takeIf { it.isNotEmpty() }
        if (packageName != null && after.packageName.equals(packageName, ignoreCase = true)) return ScreenVerification.VERIFIED
        val expected = normalize(expectedVisibleText.orEmpty())
        if (expected.isNotEmpty() && normalize(after.visibleText).contains(expected)) return ScreenVerification.VERIFIED
        val beforePackage = before.packageName?.trim().orEmpty()
        val afterPackage = after.packageName?.trim().orEmpty()
        if (afterPackage.isNotEmpty() && !beforePackage.equals(afterPackage, ignoreCase = true)) return ScreenVerification.VERIFIED
        val beforeText = normalize(before.visibleText)
        val afterText = normalize(after.visibleText)
        return if (afterText.isNotEmpty() && beforeText != afterText) ScreenVerification.VERIFIED else ScreenVerification.UNVERIFIED
    }

    fun verifyTextVisible(after: ScreenEvidence?, expectedText: String): ScreenVerification {
        if (after == null || after.interactiveElementCount < 0) return ScreenVerification.INVALID_EVIDENCE
        val expected = normalize(expectedText)
        if (expected.isEmpty()) return ScreenVerification.INVALID_EVIDENCE
        return if (normalize(after.visibleText).contains(expected)) ScreenVerification.VERIFIED else ScreenVerification.UNVERIFIED
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
}