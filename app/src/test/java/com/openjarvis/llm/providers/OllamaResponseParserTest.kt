package com.openjarvis.llm.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OllamaResponseParserTest {
    @Test fun emptyBaseUrlReportsUrlMissing() {
        try {
            OllamaResponseParser.normalizeBaseUrl("   ")
            fail("Expected URL missing")
        } catch (error: IllegalArgumentException) {
            assertEquals("URL missing", error.message)
        }
    }

    @Test fun addsHttpSchemeWhenMissing() {
        assertEquals(
            "http://127.0.0.1:11434",
            OllamaResponseParser.normalizeBaseUrl("127.0.0.1:11434")
        )
    }

    @Test fun removesTrailingSlash() {
        assertEquals(
            "http://127.0.0.1:11434",
            OllamaResponseParser.normalizeBaseUrl("http://127.0.0.1:11434/")
        )
    }

    @Test fun preservesNormalHttpAndHttpsUrls() {
        assertEquals(
            "https://localhost:11434",
            OllamaResponseParser.normalizeBaseUrl("  https://localhost:11434  ")
        )
        assertEquals(
            "http://127.0.0.1:11434",
            OllamaResponseParser.normalizeBaseUrl("http://127.0.0.1:11434")
        )
    }

    @Test fun removesThinkBlockAndKeepsFinalAnswer() {
        val result = OllamaResponseParser.parseChatContent(
            """{"message":{"content":"<think>private reasoning</think>Namaste! Ready."}}"""
        )
        assertEquals("Namaste! Ready.", result)
    }

    @Test fun reportsEmptyAnswerAfterThinkBlockRemoval() {
        try {
            OllamaResponseParser.parseChatContent(
                """{"message":{"content":"<think>only reasoning</think>"}}"""
            )
            fail("Expected empty-answer error")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("no answer"))
        }
    }

    @Test fun detectsTheRequiredInstalledModel() {
        val tags = """{"models":[{"name":"qwen3:1.7b","size":123}]}"""
        assertTrue(OllamaResponseParser.hasModel(tags, "qwen3:1.7b"))
        assertFalse(OllamaResponseParser.hasModel(tags, "llama3"))
    }

    @Test fun reportsOllamaApiErrorsClearly() {
        assertEquals(
            "Ollama HTTP 404: model not found",
            OllamaResponseParser.httpError(404, """{"error":"model not found"}""")
        )
    }
}
