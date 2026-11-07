package com.openjarvis.bridge

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Process
import android.os.SystemClock
import com.openjarvis.agent.AgentCore
import com.openjarvis.agent.AgentState
import com.openjarvis.graphify.GraphifyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

internal class SlidingWindowRateLimiter(
    private val maxRequests: Int = 10,
    private val windowMs: Long = 1_000L
) {
    private val lock = Any()
    private val requestsByUid = mutableMapOf<Int, ArrayDeque<Long>>()

    fun allow(uid: Int, nowMs: Long): Boolean = synchronized(lock) {
        val requests = requestsByUid.getOrPut(uid) { ArrayDeque() }
        while (requests.isNotEmpty() && nowMs - requests.first() >= windowMs) {
            requests.removeFirst()
        }
        if (requests.size >= maxRequests) return@synchronized false
        requests.addLast(nowMs)
        true
    }
}

class SocketServer(
    private val context: Context,
    private val agentCore: AgentCore,
    private val graphifyRepo: GraphifyRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: LocalServerSocket? = null
    @Volatile private var isRunning = false

    private val allowedUids: Set<Int> = buildSet {
        add(Process.myUid())
        add(SHELL_UID)
        runCatching {
            context.packageManager.getApplicationInfo(TERMUX_PACKAGE, 0).uid
        }.getOrNull()?.let(::add)
    }
    private val rateLimiter = SlidingWindowRateLimiter(
        MAX_REQUESTS_PER_SECOND,
        RATE_WINDOW_MS
    )

    companion object {
        const val SOCKET_NAME = "openjarvis_cli_bridge"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val SHELL_UID = 2000
        private const val MAX_COMMAND_LENGTH = 2_000
        private const val MAX_REQUEST_BYTES = 4_096
        private const val MAX_REQUESTS_PER_SECOND = 10
        private const val RATE_WINDOW_MS = 1_000L
        private const val CLIENT_TIMEOUT_MS = 3_000

        internal fun isAllowedPeerUid(
            peerUid: Int,
            appUid: Int,
            termuxUid: Int?,
            shellUid: Int = SHELL_UID
        ): Boolean = peerUid == appUid || peerUid == shellUid ||
            (termuxUid != null && peerUid == termuxUid)

        internal fun parseRequest(line: String): Pair<String, String>? {
            return try {
                val json = org.json.JSONObject(line)
                val cmd = json.optString("cmd", "")
                val requestId = json.optString("requestId", "")
                if (cmd.isBlank() || cmd.length > MAX_COMMAND_LENGTH) null
                else Pair(requestId, cmd)
            } catch (e: Exception) {
                null
            }
        }

        internal fun readBoundedLine(input: InputStream, maxBytes: Int): String? {
            val output = ByteArrayOutputStream()
            while (true) {
                val next = input.read()
                if (next == -1) {
                    if (output.size() == 0) return null
                    break
                }
                if (next == 10) break
                if (next != 13) {
                    output.write(next)
                    if (output.size() > maxBytes) {
                        throw RequestTooLargeException()
                    }
                }
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }

        internal class RequestTooLargeException : Exception("request too large")

        internal fun createErrorResponse(requestId: String, error: String): String =
            """{"requestId":"${escapeJson(requestId)}","status":"error","result":"${escapeJson(error)}"}"""

        internal fun escapeJson(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch { acceptConnections() }
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope.coroutineContext[Job]?.cancelChildren()
    }

    private suspend fun acceptConnections() = withContext(Dispatchers.IO) {
        try {
            // LocalServerSocket binds in Android's abstract UNIX-domain namespace;
            // it is not exposed as a TCP listener on Wi-Fi or other interfaces.
            serverSocket = LocalServerSocket(SOCKET_NAME)
            while (isRunning) {
                try {
                    val client = serverSocket?.accept() ?: break
                    scope.launch { handleClient(client) }
                } catch (e: Exception) {
                    if (isRunning) delay(100)
                }
            }
        } catch (e: Exception) {
            if (isRunning) e.printStackTrace()
        } finally {
            runCatching { serverSocket?.close() }
            serverSocket = null
        }
    }

    private fun isAuthorized(clientSocket: LocalSocket): Boolean {
        return runCatching {
            val peerUid = clientSocket.peerCredentials.uid
            peerUid in allowedUids
        }.getOrDefault(false)
    }

    private suspend fun handleClient(clientSocket: LocalSocket) = withContext(Dispatchers.IO) {
        try {
            clientSocket.soTimeout = CLIENT_TIMEOUT_MS
            val writer = PrintWriter(clientSocket.outputStream, true)

            if (!isAuthorized(clientSocket)) {
                writer.println(createErrorResponse("", "unauthorized"))
                return@withContext
            }

            val peerUid = clientSocket.peerCredentials.uid
            if (!rateLimiter.allow(peerUid, SystemClock.elapsedRealtime())) {
                writer.println(createErrorResponse("", "rate limit exceeded"))
                return@withContext
            }

            val line = try {
                readBoundedLine(clientSocket.inputStream, MAX_REQUEST_BYTES)
            } catch (_: RequestTooLargeException) {
                writer.println(createErrorResponse("", "request too large"))
                return@withContext
            } ?: run {
                writer.println(createErrorResponse("", "empty request"))
                return@withContext
            }

            val request = parseRequest(line)
            if (request == null) {
                writer.println(createErrorResponse("", "invalid request or command too long"))
                return@withContext
            }

            val (requestId, cmd) = request
            when (cmd.lowercase()) {
                "status" -> handleStatus(writer, requestId)
                "history" -> handleHistory(writer, requestId)
                "providers" -> handleProviders(writer, requestId)
                "memory" -> handleMemory(writer, requestId)
                else -> handleCommand(writer, requestId, cmd)
            }
            writer.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { clientSocket.close() }
        }
    }

    private fun handleStatus(writer: PrintWriter, requestId: String) {
        val accessibilityEnabled = com.openjarvis.accessibility.JarvisAccessibilityService.instance != null
        val provider = try {
            agentCore.getCurrentProviderName()
        } catch (e: Exception) { "unknown" }

        val statusJson = buildString {
            append("{")
            append("\"requestId\":\"\${escapeJson(requestId)}\",")
            append("\"status\":\"done\",")
            append("\"result\":{")
            append("\"service\":\"running\",")
            append("\"accessibility\":$accessibilityEnabled,")
            append("\"provider\":\"\${escapeJson(provider)}\",")
            append("\"socket\":\"localabstract:$SOCKET_NAME\"")
            append("}}")
        }
        writer.println(statusJson)
    }

    private suspend fun handleHistory(writer: PrintWriter, requestId: String) {
        val tasks = graphifyRepo.getRecentTasks(10)
        val tasksJson = tasks.joinToString(",", "[", "]") { task ->
            """{"id":${task.id},"command":"${escapeJson(task.command)}","result":"${escapeJson(task.result)}","timestamp":${task.timestamp}}"""
        }
        writer.println("""{"requestId":"${escapeJson(requestId)}","status":"done","result":$tasksJson}""")
    }

    private suspend fun handleProviders(writer: PrintWriter, requestId: String) {
        val providers = graphifyRepo.getProviderStats()
        val providersJson = providers.joinToString(",", "[", "]") { p ->
            """{"name":"${escapeJson(p.name)}","useCount":${p.useCount},"successRate":${p.successRate}}"""
        }
        writer.println("""{"requestId":"${escapeJson(requestId)}","status":"done","result":$providersJson}""")
    }

    private suspend fun handleMemory(writer: PrintWriter, requestId: String) {
        val memory = graphifyRepo.buildMemoryContext("")
        writer.println("""{"requestId":"${escapeJson(requestId)}","status":"done","result":"${escapeJson(memory)}"}""")
    }

    private suspend fun handleCommand(writer: PrintWriter, requestId: String, cmd: String) {
        val job = scope.launch {
            agentCore.getStateFlow().collectLatest { state ->
                when (state) {
                    is AgentState.Running -> {
                        writer.println("""{"requestId":"${escapeJson(requestId)}","status":"progress","result":"${escapeJson(state.step)}"}""")
                        writer.flush()
                    }
                    is AgentState.Done -> {
                        writer.println("""{"requestId":"${escapeJson(requestId)}","status":"done","result":"${escapeJson(state.result)}"}""")
                        writer.flush()
                        cancel()
                    }
                    is AgentState.Error -> {
                        writer.println("""{"requestId":"${escapeJson(requestId)}","status":"error","result":"${escapeJson(state.message)}"}""")
                        writer.flush()
                        cancel()
                    }
                    is AgentState.Idle -> Unit
                }
            }
        }
        agentCore.executeTask(cmd)
        job.join()
    }
}
