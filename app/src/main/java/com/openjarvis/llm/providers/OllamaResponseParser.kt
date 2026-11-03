package com.openjarvis.llm.providers

import org.json.JSONObject

internal object OllamaResponseParser {
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "URL missing" }
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)) trimmed else "http://$trimmed"
        return withScheme.trimEnd('/')
    }

    private val thinkBlock = Regex("(?is)<think>.*?</think>")

    fun parseChatContent(body: String): String {
        val json = JSONObject(body)
        if (json.has("error")) {
            throw IllegalStateException(json.optString("error").ifBlank { "Ollama returned an unspecified error." })
        }

        val message = json.optJSONObject("message")
            ?: throw IllegalStateException("Ollama response is missing the message object.")
        val content = message.optString("content", "")
        val cleaned = thinkBlock.replace(content, "").trim()
        if (cleaned.isBlank()) {
            throw IllegalStateException("Ollama returned no answer after removing its thinking block.")
        }
        return cleaned
    }

    fun hasModel(tagsBody: String, expectedModel: String): Boolean {
        val models = JSONObject(tagsBody).optJSONArray("models") ?: return false
        for (index in 0 until models.length()) {
            val name = models.optJSONObject(index)?.optString("name").orEmpty()
            if (name == expectedModel || name.startsWith("$expectedModel:")) return true
        }
        return false
    }

    fun httpError(code: Int, body: String): String {
        val detail = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
        return if (detail.isNotBlank()) "Ollama HTTP $code: $detail" else "Ollama HTTP $code"
    }
}
