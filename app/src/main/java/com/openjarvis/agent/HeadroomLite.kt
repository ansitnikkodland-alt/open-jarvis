package com.openjarvis.agent

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Offline, Android-native adaptation of Headroom's content-aware compression principles.
 * JSON is minified only after parsing succeeds; strings and values are preserved.
 * Text/log output uses conservative run-length encoding for adjacent identical lines.
 * Code, diffs and safety-critical payloads can explicitly opt out.
 *
 * CompressionResult retains the exact original so callers/tests can restore byte-for-byte.
 * No network, daemon, API key, model, or background service is used.
 */
object HeadroomLite {
    enum class ContentKind { AUTO, TEXT, LOG, JSON, CODE, DIFF, PRESERVE }

    data class CompressionResult(
        val original: String,
        val content: String,
        val kind: ContentKind,
        val inputChars: Int,
        val outputChars: Int,
        val estimatedInputTokens: Int,
        val estimatedOutputTokens: Int
    ) {
        val savedChars: Int get() = inputChars - outputChars
        val savedTokens: Int get() = estimatedInputTokens - estimatedOutputTokens
        val compressionRatio: Double
            get() = if (inputChars == 0) 1.0 else outputChars.toDouble() / inputChars.toDouble()

        /** CCR-style exact restoration: no information is discarded by this result object. */
        fun restore(): String = original
    }

    fun compress(input: String, requestedKind: ContentKind = ContentKind.AUTO): CompressionResult {
        val kind = if (requestedKind == ContentKind.AUTO) {
            if (isJsonDocument(input)) ContentKind.JSON else ContentKind.TEXT
        } else requestedKind

        val compacted = when (kind) {
            ContentKind.JSON -> compactJson(input) ?: input
            ContentKind.TEXT -> compactLines(input, encodeRepeats = true)
            ContentKind.LOG -> compactLines(input, encodeRepeats = true)
            ContentKind.AUTO -> input
            ContentKind.CODE, ContentKind.DIFF, ContentKind.PRESERVE -> input
        }.let { candidate -> if (candidate.length < input.length) candidate else input }

        return CompressionResult(
            original = input,
            content = compacted,
            kind = kind,
            inputChars = input.length,
            outputChars = compacted.length,
            estimatedInputTokens = estimateTokens(input),
            estimatedOutputTokens = estimateTokens(compacted)
        )
    }

    /** Backwards-compatible helper used by AgentCore context assembly. */
    fun compressContext(input: String): String = compress(input).content

    fun estimateTokens(text: String): Int = (text.length + 3) / 4

    private fun isJsonDocument(input: String): Boolean {
        val value = input.trim()
        if (value.length < 2 || !((value.first() == '{' && value.last() == '}') ||
                    (value.first() == '[' && value.last() == ']'))) return false
        return try {
            val tokener = JSONTokener(value)
            val parsed = tokener.nextValue()
            (parsed is JSONObject || parsed is JSONArray) && tokener.nextClean() == '\u0000'
        } catch (_: Exception) {
            false
        }
    }

    /** Remove only JSON insignificant whitespace; quoted strings and escapes stay byte-identical. */
    private fun compactJson(input: String): String? {
        if (!isJsonDocument(input)) return null
        val value = input.trim()
        val output = StringBuilder(value.length)
        var inString = false
        var escaped = false

        for (character in value) {
            if (inString) {
                output.append(character)
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when {
                    character == '"' -> {
                        inString = true
                        output.append(character)
                    }
                    character.isWhitespace() -> Unit
                    else -> output.append(character)
                }
            }
        }
        return output.toString().takeIf { isJsonDocument(it) }
    }

    /**
     * Collapse blank-line runs and encode adjacent identical lines as line + repeat count.
     * The CompressionResult keeps the original exact text for restoration.
     */
    private fun compactLines(input: String, encodeRepeats: Boolean): String {
        if (input.isEmpty()) return input
        val lines = input.split('\n')
        val output = ArrayList<String>(lines.size)
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            var end = index + 1
            while (end < lines.size && lines[end] == line) end++
            val count = end - index

            if (line.isEmpty() && count > 1) {
                output += ""
            } else if (encodeRepeats && count > 1) {
                val marker = "$line ⟪x$count⟫"
                val originalLength = count * line.length + (count - 1)
                if (marker.length < originalLength) output += marker
                else repeat(count) { output += line }
            } else {
                output += line
            }
            index = end
        }
        return output.joinToString("\n")
    }
}
