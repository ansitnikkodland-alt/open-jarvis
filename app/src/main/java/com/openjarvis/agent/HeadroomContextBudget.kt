package com.openjarvis.agent

/**
 * Bounds optional action-planning context before it is added to the system prompt.
 *
 * Priority is deliberate: visible interactive targets are most useful for safe UI actions,
 * followed by query-relevant saved facts, OCR text, and installed-app labels. The user's
 * command is not accepted by this API, so it cannot be truncated or rewritten here.
 */
internal object HeadroomContextBudget {
    const val MAX_TOTAL_CHARS = 1_000

    private const val MAX_SCREEN_ELEMENTS = 500
    private const val MAX_MEMORY = 250
    private const val MAX_SCREEN_TEXT = 150
    private const val MAX_INSTALLED_APPS = 100

    data class Allocation(
        val memory: String,
        val screenText: String,
        val screenElements: String,
        val installedApps: String,
        val inputChars: Int
    ) {
        val outputChars: Int
            get() = memory.length + screenText.length + screenElements.length + installedApps.length
    }

    fun allocate(
        memory: String,
        screenText: String,
        screenElements: String,
        installedApps: String
    ): Allocation {
        var remaining = MAX_TOTAL_CHARS
        fun take(value: String, limit: Int): String {
            if (remaining <= 0 || value.isEmpty()) return ""
            val allowed = minOf(limit, remaining)
            val bounded = fitAtLineBoundary(value, allowed)
            remaining -= bounded.length
            return bounded
        }

        // Allocate in priority order, independent of argument order.
        val elements = take(screenElements, MAX_SCREEN_ELEMENTS)
        val facts = take(memory, MAX_MEMORY)
        val ocr = take(screenText, MAX_SCREEN_TEXT)
        val apps = take(installedApps, MAX_INSTALLED_APPS)

        return Allocation(
            memory = facts,
            screenText = ocr,
            screenElements = elements,
            installedApps = apps,
            inputChars = memory.length + screenText.length + screenElements.length + installedApps.length
        )
    }

    private fun fitAtLineBoundary(value: String, limit: Int): String {
        if (value.length <= limit) return value
        if (limit <= 1) return "…".take(limit)

        val candidate = value.take(limit - 1)
        val lastLineBreak = candidate.lastIndexOf('\n')
        val safePrefix = if (lastLineBreak >= limit / 2) {
            candidate.substring(0, lastLineBreak)
        } else {
            candidate
        }
        return safePrefix.trimEnd() + "…"
    }
}
