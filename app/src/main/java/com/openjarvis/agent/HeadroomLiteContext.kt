package com.openjarvis.agent

/**
 * Android-native, lossless structural compaction for repeated screen/OCR lines.
 *
 * This is the first lightweight Headroom-style stage: no model, network, or
 * external process is used. Only adjacent, byte-identical, non-empty lines
 * are collapsed. Warning/error lines and all unique lines remain untouched.
 */
internal object HeadroomLiteContext {
    data class Result(
        val text: String,
        val originalChars: Int,
        val compactedChars: Int,
        val collapsedLines: Int
    ) {
        val charsSaved: Int get() = (originalChars - compactedChars).coerceAtLeast(0)
    }

    private val importantLine = Regex(
        """(?i)^\s*(error|warning|fatal|exception|failed|failure)\b"""
    )

    fun compressRepeatedLines(input: String): Result {
        if (input.isEmpty()) return Result(input, 0, 0, 0)

        // Keep exact line boundaries, including a final newline.
        val lines = ArrayList<String>()
        var start = 0
        for (index in input.indices) {
            if (input[index] == '\n') {
                lines.add(input.substring(start, index))
                start = index + 1
            }
        }
        lines.add(input.substring(start))

        val output = StringBuilder(input.length)
        var collapsed = 0
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            var end = index + 1
            val canCollapse = line.isNotBlank() && !importantLine.containsMatchIn(line)
            if (canCollapse) {
                while (end < lines.size && lines[end] == line) end++
            }
            val count = end - index
            output.append(line)
            if (count > 1) {
                output.append(" [repeated ").append(count).append(" consecutive times]")
                collapsed += count - 1
            }
            if (end < lines.size) output.append('\n')
            index = end
        }

        val compacted = output.toString()
        return Result(compacted, input.length, compacted.length, collapsed)
    }
}
