package com.openjarvis.bridge

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class SocketServerTest {

    @Test
    fun `parseRequest with valid JSON returns command and requestId`() {
        val result = SocketServer.parseRequest("""{"cmd":"open chrome","requestId":"abc123"}""")
        assertNotNull(result)
        assertEquals("abc123", result?.first)
        assertEquals("open chrome", result?.second)
    }

    @Test
    fun `parseRequest with missing cmd returns null`() {
        assertNull(SocketServer.parseRequest("""{"requestId":"abc123"}"""))
    }

    @Test
    fun `parseRequest with invalid JSON returns null`() {
        assertNull(SocketServer.parseRequest("not json"))
    }

    @Test
    fun `parseRequest rejects command longer than configured limit`() {
        assertNull(SocketServer.parseRequest("""{"cmd":"${"x".repeat(2001)}"}"""))
    }

    @Test
    fun `parseRequest with empty requestId uses empty string`() {
        val result = SocketServer.parseRequest("""{"cmd":"status"}""")
        assertNotNull(result)
        assertEquals("", result?.first)
        assertEquals("status", result?.second)
    }

    @Test
    fun `escapeJson escapes special characters`() {
        val escaped = SocketServer.escapeJson("hello\nworld\"test\tvalue")
        assertTrue(escaped.contains("\\n"))
        assertTrue(escaped.contains("\\\""))
        assertTrue(escaped.contains("\\t"))
    }

    @Test
    fun `createErrorResponse escapes untrusted values`() {
        val response = SocketServer.createErrorResponse("id\"\n", "bad\"\nvalue")
        assertTrue(response.contains("id\\\"\\n"))
        assertTrue(response.contains("bad\\\"\\nvalue"))
    }

    @Test
    fun `peer authorization accepts only app Termux and adb shell UIDs`() {
        assertTrue(SocketServer.isAllowedPeerUid(10123, appUid = 10123, termuxUid = 10234))
        assertTrue(SocketServer.isAllowedPeerUid(10234, appUid = 10123, termuxUid = 10234))
        assertTrue(SocketServer.isAllowedPeerUid(2000, appUid = 10123, termuxUid = 10234))
        assertFalse(SocketServer.isAllowedPeerUid(10345, appUid = 10123, termuxUid = 10234))
        assertFalse(SocketServer.isAllowedPeerUid(10345, appUid = 10123, termuxUid = null))
    }

    @Test
    fun `bounded line reader reads UTF8 request and strips carriage return`() {
        val bytes = """{"cmd":"नमस्ते"}\r\n""".toByteArray(Charsets.UTF_8)
        assertEquals("""{"cmd":"नमस्ते"}""", SocketServer.readBoundedLine(ByteArrayInputStream(bytes), 100))
    }

    @Test
    fun `bounded line reader rejects oversized input before newline`() {
        val input = ByteArrayInputStream("123456789".toByteArray())
        try {
            SocketServer.readBoundedLine(input, 8)
            fail("Expected RequestTooLargeException")
        } catch (_: SocketServer.RequestTooLargeException) {
            // expected
        }
    }

    @Test
    fun `bounded line reader returns null for empty stream`() {
        assertNull(SocketServer.readBoundedLine(ByteArrayInputStream(byteArrayOf()), 8))
    }

    @Test
    fun `rate limiter allows ten requests and rejects eleventh in same window`() {
        val limiter = SlidingWindowRateLimiter(maxRequests = 10, windowMs = 1_000)
        repeat(10) { assertTrue(limiter.allow(uid = 10001, nowMs = it.toLong())) }
        assertFalse(limiter.allow(uid = 10001, nowMs = 10))
    }

    @Test
    fun `rate limiter isolates UIDs and resets after window`() {
        val limiter = SlidingWindowRateLimiter(maxRequests = 1, windowMs = 1_000)
        assertTrue(limiter.allow(uid = 10001, nowMs = 0))
        assertFalse(limiter.allow(uid = 10001, nowMs = 10))
        assertTrue(limiter.allow(uid = 10002, nowMs = 10))
        assertTrue(limiter.allow(uid = 10001, nowMs = 1_000))
    }

    @Test
    fun `special commands are parsed correctly`() {
        val commands = listOf("status", "history", "memory", "providers")
        commands.forEachIndexed { index, command ->
            val result = SocketServer.parseRequest(
                """{"cmd":"$command","requestId":"request-$index"}"""
            )
            assertNotNull(result)
            assertEquals("request-$index", result?.first)
            assertEquals(command, result?.second)
        }
    }

    @Test
    fun `malformed JSON handled gracefully`() {
        listOf("", "{", """{"cmd":""", "null", "[][]").forEach { input ->
            assertNull("Should return null for: $input", SocketServer.parseRequest(input))
        }
    }
}
