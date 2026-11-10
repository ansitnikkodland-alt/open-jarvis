package com.openjarvis.agent

/**
 * Android-native structural compaction for repeated screen/OCR lines.
 * Only adjacent, byte-identical, non-empty lines are candidates. A group is
 * collapsed only when its compact representation is shorter than the source.
 * Warning/error lines and all unique lines remain untouched.
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

        // Keep the trailing empty element so a final newline is preserved.
        val lines = ArrayList<String>()
        var start = 0
        for (position in input.indices) {
            if (input[position] == '\n') {
                lines.add(input.substring(start, position))
                start = position + 1
            }
        }
        lines.add(input.substring(start))

        val output = StringBuilder(input.length)
        var collapsed = 0
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            var end = index + 1
            val candidate = line.isNotBlank() && !importantLine.containsMatchIn(line)
            if (candidate) {
                while (end < lines.size && lines[end] == line) end++
            }

            val count = end - index
            val marker = " ×$count"
            val sourceLength = count * line.length + (count - 1)
            val compactLength = line.length + marker.length
            val shouldCollapse = candidate && count > 1 && compactLength < sourceLength

            if (shouldCollapse) {
                output.append(line).append(marker)
                collapsed += count - 1
                index = end
            } else {
                output.append(line)
                index++
            }
            if (index < lines.size) output.append('\n')
        }

        val compacted = output.toString()
        return Result(compacted, input.length, compacted.length, collapsed)
    }
}
