package com.openjarvis.agent

/**
 * Jarvis-native, offline-safe subset of Headroom's context-compaction idea.
 *
 * This deliberately performs only lossless formatting cleanup: adjacent duplicate
 * context lines and repeated blank lines are collapsed. It never summarizes,
 * truncates, reorders, or rewrites command text, JSON, or policy instructions.
 */
object HeadroomLite {

    fun compressContext(input: String): String {
        if (input.isEmpty()) return input

        val lines = input.split('\n', ignoreCase = false, limit = Int.MAX_VALUE)
        val compacted = ArrayList<String>(lines.size)
        var previous: String? = null

        for (line in lines) {
            val isRepeatedBlank = line.isEmpty() && previous != null && previous.isEmpty()
            val isRepeatedContent = line.isNotEmpty() && line == previous
            if (isRepeatedBlank || isRepeatedContent) continue

            compacted += line
            previous = line
        }

        return compacted.joinToString("\n")
    }
}
