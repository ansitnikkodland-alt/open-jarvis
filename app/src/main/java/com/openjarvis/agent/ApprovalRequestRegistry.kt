package com.openjarvis.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory one-shot approvals. They cannot survive process restarts.
 * No affirmative answer is stored for future actions.
 *
 * Thread-safe for requests originating from separate AgentCore instances.
 */
class ApprovalRequestRegistry {
    data class Request(val id: String, val details: String)

    private class Pending(
        val request: Request,
        val answer: CompletableDeferred<Boolean>
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    fun create(details: String): Request {
        val request = Request(UUID.randomUUID().toString(), details)
        pending[request.id] = Pending(request, CompletableDeferred())
        return request
    }

    fun get(id: String): Request? = pending[id]?.request

    /**
     * Only the first answer is accepted. An expired/removed request rejects
     * late answers, including an approval arriving after a timeout.
     */
    fun respond(id: String, approved: Boolean): Boolean =
        pending[id]?.answer?.complete(approved) == true

    suspend fun await(id: String, timeoutMs: Long): Boolean {
        val request = pending[id] ?: return false
        return try {
            withTimeoutOrNull(timeoutMs) { request.answer.await() } == true
        } finally {
            pending.remove(id, request)
        }
    }

    fun remove(id: String) {
        pending.remove(id)?.answer?.complete(false)
    }
}
