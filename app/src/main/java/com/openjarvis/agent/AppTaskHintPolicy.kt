package com.openjarvis.agent

/**
 * Privacy-first policy for reusable app-specific automation hints.
 * Only generic successful-flow guidance is accepted; raw UI trees and user-entered data are rejected.
 */
data class AppTaskHint(val packageName: String, val taskLabel: String, val guidance: String)

object AppTaskHintPolicy {
    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val emailPattern = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val phonePattern = Regex("(?<!\\d)(?:\\+?91[-\\s]?)?[6-9]\\d{9}(?!\\d)")
    private val longNumberPattern = Regex("\\d{6,}")
    private val forbidden = Regex("(?i)\\b(password|passcode|otp|pin|upi|bank|card|account|message|contact|phone|email|clipboard|type|paste|node[_ -]?id|resource[_ -]?id|view[_ -]?id)\\b")

    fun create(packageName: String, taskLabel: String, guidance: String): AppTaskHint? {
        val pkg = packageName.trim()
        if (!packagePattern.matches(pkg)) return null
        val task = normalize(taskLabel).take(64)
        val hint = normalize(guidance).take(180)
        if (task.isBlank() || hint.isBlank()) return null
        if (containsSensitiveData(task) || containsSensitiveData(hint)) return null
        if (forbidden.containsMatchIn(task) || forbidden.containsMatchIn(hint)) return null
        return AppTaskHint(pkg, task, hint)
    }

    fun merge(existing: List<AppTaskHint>, candidate: AppTaskHint, maxEntries: Int = 10, maxChars: Int = 1_200): List<AppTaskHint> {
        if (maxEntries <= 0 || maxChars <= 0) return emptyList()
        val safeCandidate = create(candidate.packageName, candidate.taskLabel, candidate.guidance) ?: return existing.takeLast(maxEntries)
        val samePackage = existing.mapNotNull { create(it.packageName, it.taskLabel, it.guidance) }
            .filter { it.packageName == safeCandidate.packageName }
            .filterNot { it.taskLabel == safeCandidate.taskLabel }
        val combined = samePackage + safeCandidate
        val result = ArrayDeque<AppTaskHint>()
        var chars = 0
        for (entry in combined.asReversed()) {
            val size = entry.taskLabel.length + entry.guidance.length
            if (result.size >= maxEntries || chars + size > maxChars) continue
            result.addFirst(entry)
            chars += size
        }
        return result.toList()
    }

    private fun containsSensitiveData(value: String): Boolean =
        emailPattern.containsMatchIn(value) || phonePattern.containsMatchIn(value) || longNumberPattern.containsMatchIn(value)

    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")
}