package com.openjarvis.agent

import android.content.Context
import android.util.Log
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.accessibility.ScreenReader
import com.openjarvis.graphify.AnalysisEngine
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.intelligence.AIAppInteractor
import com.openjarvis.intelligence.AIApps
import com.openjarvis.intelligence.AppAnalyzer
import com.openjarvis.intelligence.TaskRouter
import com.openjarvis.intelligence.FastCommandParser
import com.openjarvis.intelligence.TaskWorkingMemory
import com.openjarvis.llm.UniversalAdapter
import com.openjarvis.vision.VisionModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AgentCore(private val context: Context) {

    private val graphifyRepo = GraphifyRepository(context)
    private val analysisEngine = AnalysisEngine(context)
    private val universalAdapter = UniversalAdapter(context)
    private val screenReader = ScreenReader(context)
    private val visionModule = VisionModule.getInstance(context)
    private val taskRouter = TaskRouter(context)
    private val appAnalyzer = AppAnalyzer(context)
    private val aiAppInteractor = AIAppInteractor(context)

    private var workingMemory = TaskWorkingMemory()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskMutex = Mutex()

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state

    private val systemPrompt = """
You are Jarvis, an Android command executor. Return ONLY a JSON array. No markdown, reasoning, or prose.
Use ONLY these actions:
{"action":"open_app","label":"App"}
{"action":"click","text":"visible UI text"}
{"action":"type_text","value":"text"}
{"action":"scroll","direction":"up|down"}
{"action":"back"}
{"action":"home"}
{"action":"wait","text":"visible text","timeout_ms":3000}
{"action":"read_screen"}
{"action":"error","message":"reason"}
Understand Hindi, Marathi, Hinglish, and English. Convert user intent into the shortest safe action list. Never invent visible text or app names. Use read_screen when the target is unclear. Never clear, submit, reset, or delete user data unless explicitly requested. If the task cannot be done safely, return one error action.
SCREEN: {SCREEN_OCR}
""".trimIndent()

    fun executeTask(cleanCommand: String) {
        workingMemory = TaskWorkingMemory()

        scope.launch {
            taskMutex.withLock {
                try {
                    val sanitized = PromptSanitizer.sanitize(cleanCommand)

                    when (sanitized) {
                        is PromptSanitizer.SanitizeResult.Rejected -> {
                            _state.value = AgentState.Error(sanitized.reason)
                            return@withLock
                        }

                        is PromptSanitizer.SanitizeResult.Suspicious -> {
                            _state.value = AgentState.Running("analyzing...")
                        }

                        is PromptSanitizer.SanitizeResult.Clean -> {
                        }
                    }

                    val safeCommand = when (sanitized) {
                        is PromptSanitizer.SanitizeResult.Suspicious ->
                            sanitized.sanitized

                        is PromptSanitizer.SanitizeResult.Clean ->
                            sanitized.text

                        else -> cleanCommand
                    }

                    _state.value = AgentState.Running("checking direct command...")

                    val fastCommand = FastCommandParser.parse(safeCommand)
                    if (fastCommand != null) {
                        try {
                            val result = executeFastCommand(fastCommand)
                            graphifyRepo.logTask(
                                command = safeCommand,
                                result = "success (verified fast path): $result",
                                provider = "Android fast path",
                                latencyMs = 0
                            )
                            _state.value = AgentState.Done(result)
                        } catch (error: Exception) {
                            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                            AgentDiagnostics.record(context, "fast-command-execution", safeCommand, detail, error = error, provider = "Android fast path", model = "Not used")
                            graphifyRepo.logTask(safeCommand, "failed (fast path): $detail", "Android fast path", 0)
                            _state.value = AgentState.Error(detail)
                        }
                        return@withLock
                    }

                    _state.value = AgentState.Running("reading screen...")
                    // Keep local-model command planning lean: app ranking and graph-memory
                    // retrieval were adding latency before every request.
                    val screenText = runCatching {
                        screenReader.extractAllText()
                    }.onFailure { Log.e(TAG, "Screen extraction failed; continuing without OCR", it) }
                        .getOrDefault("")

                    val fullSystem = systemPrompt
                        .replace("{SCREEN_OCR}", screenText.take(450))

                    _state.value = AgentState.Running("Soch raha hoon...")

                    val startTime = System.currentTimeMillis()

                    val result = universalAdapter.complete(
                        fullSystem,
                        safeCommand
                    )

                    result.fold(
                        onSuccess = { rawJson ->
                            val latency =
                                System.currentTimeMillis() - startTime

                            val validation =
                                LLMResponseValidator.validate(rawJson)

                            if (!validation.isValid) {
                                val reason = validation.errors.firstOrNull() ?: "Empty action plan"
                                AgentDiagnostics.record(context, "response-validation", safeCommand, reason, rawJson)
                                _state.value = AgentState.Error("AI response invalid: $reason")
                                graphifyRepo.logTask(safeCommand, "failed: validation error", universalAdapter.getProviderName(), latency)
                                return@fold
                            }

                            val actions = validation.actions

                            _state.value = AgentState.Running(
                                "executing ${actions.size} actions..."
                            )

                            val executionSucceeded = executeActions(actions)
                            if (!executionSucceeded || _state.value is AgentState.Error) {
                                val failure = (_state.value as? AgentState.Error)?.message ?: "Action execution returned false"
                                AgentDiagnostics.record(context, "action-execution", safeCommand, failure, rawJson)
                                graphifyRepo.logTask(safeCommand, "failed: action execution", universalAdapter.getProviderName(), latency)
                                return@fold
                            }

                            graphifyRepo.logTask(
                                command = safeCommand,
                                result = "success",
                                provider = universalAdapter.getProviderName(),
                                latencyMs = latency
                            )

                            runCatching { analysisEngine.analyzeLastTask() }
                                .onFailure { Log.e(TAG, "Post-task analysis failed", it) }

                            _state.value =
                                AgentState.Done("Verified ${actions.size}/${actions.size} actions • done in ${latency}ms")
                        },

                        onFailure = { error ->
                            val msg = when {
                                error.message?.contains("401") == true ->
                                    "Invalid API key"

                                error.message?.contains("429") == true ->
                                    "Rate limited — wait a moment"

                                error.message?.contains("timeout") == true ->
                                    "Request timed out"

                                error.message?.contains("Unable to resolve") == true ->
                                    "Network error — check connection"

                                else -> {
                                    val detail = error.message?.takeIf { it.isNotBlank() }
                                        ?: error.cause?.message?.takeIf { it.isNotBlank() }
                                        ?: error.javaClass.simpleName
                                    if (universalAdapter.getProviderName() == "Ollama (Local)") {
                                        "Ollama error: $detail"
                                    } else detail.ifBlank { "Unknown error" }
                                }
                            }

                            AgentDiagnostics.record(context, "ollama-request", safeCommand, msg, error = error)
                            _state.value = AgentState.Error(msg)

                            graphifyRepo.logTask(
                                safeCommand,
                                "failed: $msg",
                                "",
                                0
                            )
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Task execution crashed for command: $cleanCommand", e)
                    val detail = e.message?.takeIf { it.isNotBlank() }
                        ?: e.cause?.message?.takeIf { it.isNotBlank() }
                        ?: e.javaClass.simpleName
                    val errorMessage = if (universalAdapter.getProviderName() == "Ollama (Local)") {
                        "Ollama error: $detail"
                    } else detail.ifBlank { "Unknown error" }
                    AgentDiagnostics.record(context, "agent-crash", cleanCommand, errorMessage, error = e)
                    _state.value = AgentState.Error(errorMessage)

                    graphifyRepo.logTask(
                        cleanCommand,
                        "failed: $errorMessage",
                        "",
                        0
                    )
                }
            }
        }
    }

    suspend fun testConnection(): Result<Long> {
        return universalAdapter.testConnection()
    }

    fun getCurrentProviderName(): String {
        return universalAdapter.getProviderName()
    }

    fun getStateFlow(): StateFlow<AgentState> = state

    private fun getInstalledAIApps(): String {
        return AIApps.KNOWN_AI_APPS.keys.joinToString(", ")
    }

    suspend fun getAnalyzedAppCount(): Int =
        appAnalyzer.getAnalyzedCount()

    suspend fun getAIAppCount(): Int =
        appAnalyzer.getAICount()

    private suspend fun executeActions(actions: List<Action>): Boolean {
        val results = mutableListOf<String>()

        suspend fun executeOnce(action: Action): String? {
            val activeService = JarvisAccessibilityService.instance
            return when (action.action) {
                Action.OPEN_APP -> {
                    val label = action.label
                    val packageName = action.packageName ?: label?.let { findPackageByLabel(it) }
                    if (packageName.isNullOrBlank()) return "App not found: ${label ?: "package missing"}"
                    if (activeService == null || !activeService.openAppByPackage(packageName)) return "Could not start app: ${label ?: packageName}"
                    var foregroundConfirmed = false
                    for (check in 0 until 16) {
                        if (activeService.isPackageActive(packageName)) {
                            foregroundConfirmed = true
                            break
                        }
                        kotlinx.coroutines.delay(250)
                    }
                    if (!foregroundConfirmed) {
                        Log.w(TAG, "App launch accepted for $packageName; foreground confirmation unavailable. Continuing action plan.")
                    }
                    null
                }

                Action.CLICK, Action.TAP -> {
                    val target = action.text
                    if (target.isNullOrBlank()) return "Click target is missing"
                    if (activeService == null || !activeService.tapByText(target)) return "Could not click visible text: $target"
                    null // AccessibilityNodeInfo.ACTION_CLICK returned true.
                }

                Action.TYPE_TEXT, Action.TYPE -> {
                    val value = action.value
                    if (value.isNullOrEmpty()) return "Text to enter is missing"
                    if (activeService == null || !activeService.typeText(value)) return "Could not type: no editable field accepted the text"
                    kotlinx.coroutines.delay(250)
                    if (activeService.containsVisibleText(value)) null else "Text entry was sent but could not be verified on screen"
                }

                Action.SCROLL -> {
                    val direction = action.direction?.lowercase()?.takeIf { it in listOf("up", "down", "forward", "backward") } ?: "down"
                    if (activeService == null || !activeService.scrollScreen(direction)) "No scrollable area accepted scroll $direction" else null
                }

                Action.BACK, Action.PRESS_BACK -> if (activeService?.pressBack() == true) null else "Android Back action failed"
                Action.HOME, Action.PRESS_HOME -> if (activeService?.pressHome() == true) null else "Android Home action failed"
                Action.PRESS_RECENTS -> if (activeService?.pressRecents() == true) null else "Android Recent Apps action failed"

                Action.WAIT, Action.WAIT_FOR -> {
                    val expected = action.text
                    if (expected.isNullOrBlank()) return "Wait action is missing the text to verify"
                    val timeout = action.timeoutMs.coerceIn(500L, 15000L)
                    val started = System.currentTimeMillis()
                    while (System.currentTimeMillis() - started < timeout) {
                        if (activeService?.containsVisibleText(expected) == true ||
                            screenReader.extractAllText().contains(expected, ignoreCase = true)) return null
                        kotlinx.coroutines.delay(300)
                    }
                    "Timed out waiting for visible text: $expected"
                }

                Action.READ_SCREEN -> {
                    val screen = screenReader.extractAllText()
                    if (screen.isBlank()) "Could not read current screen (no accessible text)" else {
                        workingMemory.set(action.outputKey ?: "page_text", screen)
                        null
                    }
                }

                Action.AI_PROMPT -> {
                    val packageName = action.packageName
                    val prompt = workingMemory.interpolate(action.prompt ?: "")
                    val outputKey = action.outputKey
                    if (packageName == null) return "AI app package is missing"
                    val meta = AIApps.KNOWN_AI_APPS[packageName] ?: return "Unsupported AI app: $packageName"
                    aiAppInteractor.openAIApp(meta)
                    kotlinx.coroutines.delay(2000)
                    aiAppInteractor.typePrompt(prompt)
                    kotlinx.coroutines.delay(1000)
                    val response = aiAppInteractor.waitForResponse()
                    if (response.isBlank()) "AI app did not return a response" else {
                        if (outputKey != null) workingMemory.set(outputKey, response)
                        null
                    }
                }

                Action.EXTRACT_TEXT -> {
                    val outputKey = action.outputKey ?: "page_text"
                    val text = screenReader.extractAllText()
                    if (text.isBlank()) "Could not extract text from current screen" else {
                        workingMemory.set(outputKey, text)
                        null
                    }
                }

                Action.ERROR -> action.message ?: "Task failed"
                else -> "Unsupported action: ${action.action}"
            }
        }

        for ((index, action) in actions.withIndex()) {
            var failure: String? = null
            var completed = false
            for (attempt in 1..2) {
                _state.value = AgentState.Running(
                    "Action ${index + 1}/${actions.size}: ${action.action} (attempt $attempt/2)"
                )
                failure = try {
                    executeOnce(action)
                } catch (error: Exception) {
                    error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                }
                if (failure == null) {
                    completed = true
                    break
                }
                Log.w(TAG, "Action ${index + 1} ${action.action} attempt $attempt failed: $failure")
                if (attempt == 1) kotlinx.coroutines.delay(400)
            }

            if (!completed) {
                val message = "Action ${index + 1}/${actions.size} (${action.action}) failed after one retry: ${failure ?: "verification failed"}"
                results.add(message)
                Log.e(TAG, message)
                _state.value = AgentState.Error(message)
                return false
            }
            val actionResult = if (action.action == Action.OPEN_APP) {
                "Action ${index + 1}/${actions.size} ${action.action}: launch accepted"
            } else {
                "Action ${index + 1}/${actions.size} ${action.action}: completed"
            }
            results.add(actionResult)
            Log.i(TAG, results.last())
            kotlinx.coroutines.delay(250)
        }
        Log.i(TAG, "Command completed: ${results.joinToString("; ")}")
        return true
    }

    private suspend fun executeFastCommand(command: FastCommandParser.Command): String {
        // An app launch is not safe to retry after startActivity succeeds: a failed
        // foreground observation must never launch the same app a second time.
        if (command is FastCommandParser.Command.OpenApp) {
            return executeFastCommandOnce(command)
        }
        var lastFailure = "Command could not be verified"
        for (attempt in 1..2) {
            try {
                return executeFastCommandOnce(command)
            } catch (error: Exception) {
                lastFailure = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                Log.w(TAG, "Fast command attempt $attempt failed: $lastFailure")
                if (attempt == 1) kotlinx.coroutines.delay(400)
            }
        }
        throw IllegalStateException("Command failed after one retry: $lastFailure")
    }

    private suspend fun executeFastCommandOnce(command: FastCommandParser.Command): String {
        return when (command) {
            is FastCommandParser.Command.OpenApp -> {
                val packageName = resolveAppPackage(command.target)
                    ?: throw IllegalStateException("App not found: ${command.target}")
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: throw IllegalStateException("No launch activity available for ${command.target} ($packageName)")
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } catch (error: Exception) {
                    throw IllegalStateException("Android could not launch ${command.target}: ${error.message ?: error.javaClass.simpleName}", error)
                }

                var foregroundConfirmed = false
                for (check in 0 until 16) {
                    if (JarvisAccessibilityService.instance?.isPackageActive(packageName) == true) {
                        foregroundConfirmed = true
                        break
                    }
                    kotlinx.coroutines.delay(250)
                }
                if (foregroundConfirmed) {
                    "Opened ${command.target} (foreground confirmed)"
                } else {
                    Log.w(TAG, "Launch accepted for ${command.target} ($packageName), but foreground confirmation was unavailable")
                    "Launch sent to ${command.target}; Android foreground confirmation unavailable"
                }
            }
            FastCommandParser.Command.CloseCurrentApp -> {
                val service = JarvisAccessibilityService.instance
                if (service?.pressHome() != true) throw IllegalStateException("Could not return to Home to close the current app")
                "Closed current app (returned to Home)"
            }
            FastCommandParser.Command.Back -> {
                if (JarvisAccessibilityService.instance?.pressBack() != true) throw IllegalStateException("Android Back action failed")
                "Went back"
            }
            FastCommandParser.Command.Home -> {
                if (JarvisAccessibilityService.instance?.pressHome() != true) throw IllegalStateException("Android Home action failed")
                "Returned Home"
            }
            is FastCommandParser.Command.Scroll -> {
                if (JarvisAccessibilityService.instance?.scrollScreen(command.direction) != true) {
                    throw IllegalStateException("No scrollable area accepted scroll ${command.direction}")
                }
                "Scrolled ${command.direction}"
            }
            is FastCommandParser.Command.OpenSettings -> {
                val action = when (command.action) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                kotlinx.coroutines.delay(700)
                if (screenReader.extractAllText().isBlank()) throw IllegalStateException("Settings opened, but the screen could not be verified")
                "Opened ${command.action} settings"
            }
            is FastCommandParser.Command.Dial -> {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", command.number, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                kotlinx.coroutines.delay(700)
                if (screenReader.extractAllText().isBlank()) throw IllegalStateException("Dialer opened, but the screen could not be verified")
                "Opened dialer for ${command.number} (call not placed)"
            }
        }
    }

    private fun resolveAppPackage(label: String): String? {
        if (label.equals("settings", ignoreCase = true)) return "com.android.settings"
        return findPackageByLabel(label)
    }

    companion object {
        private const val TAG = "OpenJarvisAgent"
    }

    private fun findPackageByLabel(label: String): String? {
        val pm = context.packageManager

        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps: List<ResolveInfo> =
            pm.queryIntentActivities(intent, 0)

        val normalizedLabel = label.lowercase().trim()
        if (normalizedLabel.isBlank()) return null

        val candidates = apps.map { app ->
            app.loadLabel(pm).toString().lowercase().trim() to app.activityInfo.packageName
        }.distinctBy { it.second }

        // Prefer an exact launcher-label match. Never let a short substring such as
        // "app" accidentally select an unrelated installed application.
        candidates.firstOrNull { it.first == normalizedLabel }?.let { return it.second }

        val prefixMatches = candidates.filter { (appLabel, _) ->
            appLabel.startsWith(normalizedLabel) && normalizedLabel.length >= 3
        }
        if (prefixMatches.size == 1) return prefixMatches.first().second

        val containsMatches = candidates.filter { (appLabel, _) ->
            normalizedLabel.length >= 4 &&
                (appLabel.contains(normalizedLabel) || normalizedLabel.contains(appLabel) && appLabel.length >= 4)
        }
        return containsMatches.singleOrNull()?.second
    }
}
