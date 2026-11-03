package com.openjarvis.agent

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object LLMResponseValidator {
    private val knownActions = setOf(
        Action.OPEN_APP, Action.CLICK, Action.TAP, Action.TYPE_TEXT, Action.TYPE,
        Action.SCROLL, Action.BACK, Action.PRESS_BACK, Action.HOME, Action.PRESS_HOME,
        Action.PRESS_RECENTS, Action.WAIT, Action.WAIT_FOR, Action.READ_SCREEN,
        Action.AI_PROMPT, Action.EXTRACT_TEXT, Action.ERROR
    )

    fun validate(rawResponse: String): ValidationResult {
        val clean = extractJson(rawResponse)
            ?: return invalid("Response is not JSON: no action array/object found")

        val parsed = try {
            when {
                clean.startsWith("[") -> JSONArray(clean)
                clean.startsWith("{") -> {
                    val root = JSONObject(clean)
                    when {
                        root.optJSONArray("actions") != null -> root.getJSONArray("actions")
                        root.optJSONArray("steps") != null -> root.getJSONArray("steps")
                        root.has("action") || root.has("type") -> JSONArray().put(root)
                        else -> return invalid("JSON object must contain an actions array")
                    }
                }
                else -> return invalid("Response is not JSON")
            }
        } catch (error: JSONException) {
            return invalid("Invalid JSON: ${error.message ?: "malformed response"}")
        }

        if (parsed.length() == 0) return invalid("Empty action plan")
        if (parsed.length() > 50) return invalid("Action plan too long (${parsed.length()} steps); maximum is 50")

        val actions = mutableListOf<Action>()
        for (index in 0 until parsed.length()) {
            val item = parsed.opt(index)
            if (item !is JSONObject) return invalid("Item $index is not an object")
            val rawType = firstString(item, "action", "type", "command")
            if (rawType.isBlank()) return invalid("Item $index missing 'action' field")
            val type = normalizeAction(rawType)
            if (type !in knownActions) return invalid("Unsupported action type: $rawType")
            actions.add(parseAction(item, type))
        }
        return ValidationResult(true, actions, emptyList(), false)
    }

    private fun extractJson(raw: String): String? {
        val withoutFence = raw.trim()
            .replace(Regex("^\\s*```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()
        val arrayStart = withoutFence.indexOf('[')
        val objectStart = withoutFence.indexOf('{')
        val start = listOf(arrayStart, objectStart).filter { it >= 0 }.minOrNull() ?: return null
        val open = withoutFence[start]
        val close = if (open == '[') ']' else '}'
        val end = withoutFence.lastIndexOf(close)
        if (end < start) return null
        return withoutFence.substring(start, end + 1)
    }

    private fun normalizeAction(raw: String): String {
        val key = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
        return when (key) {
            "tap", "click", "click_text", "tap_text", "tap_on", "click_element", "press" -> Action.CLICK
            "type", "type_text", "enter_text", "input_text", "input", "enter" -> Action.TYPE_TEXT
            "press_back", "go_back", "back", "navigate_back" -> Action.BACK
            "press_home", "go_home", "home", "go_to_home" -> Action.HOME
            "wait_for", "wait", "pause" -> Action.WAIT
            "read_screen", "readscreen", "screen_read", "get_screen_text" -> Action.READ_SCREEN
            "launch_app", "open", "open_application", "start_app" -> Action.OPEN_APP
            "swipe", "scroll_screen", "scroll_page" -> Action.SCROLL
            else -> key
        }
    }

    private fun parseAction(obj: JSONObject, type: String): Action {
        val target = firstString(obj, "text", "target", "element", "label").takeIf { it.isNotBlank() }
        val value = firstString(obj, "value", "text", "input").takeIf { it.isNotBlank() }
        return Action(
            action = type,
            packageName = firstString(obj, "package", "packageName", "package_name").takeIf { it.isNotBlank() },
            label = firstString(obj, "label", "app", "app_name").takeIf { it.isNotBlank() },
            text = target,
            value = value,
            hint = firstString(obj, "hint").takeIf { it.isNotBlank() },
            direction = firstString(obj, "direction", "scroll_direction").takeIf { it.isNotBlank() },
            distance = firstString(obj, "distance").takeIf { it.isNotBlank() },
            timeoutMs = firstLong(obj, 3000L, "timeout_ms", "timeoutMs", "duration_ms"),
            message = firstString(obj, "message", "reason").takeIf { it.isNotBlank() },
            prompt = firstString(obj, "prompt").takeIf { it.isNotBlank() },
            outputKey = firstString(obj, "outputKey", "output_key").takeIf { it.isNotBlank() }
        )
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.opt(key)
            if (value is String && value.isNotBlank()) return value.trim()
        }
        return ""
    }

    private fun firstLong(obj: JSONObject, fallback: Long, vararg keys: String): Long {
        for (key in keys) {
            if (obj.has(key)) {
                val value = obj.optLong(key, fallback)
                if (value > 0L) return value
            }
        }
        return fallback
    }

    private fun invalid(message: String) =
        ValidationResult(false, emptyList(), listOf(message), false)

    data class ValidationResult(
        val isValid: Boolean,
        val actions: List<Action>,
        val errors: List<String>,
        val wasRepaired: Boolean
    )
}
