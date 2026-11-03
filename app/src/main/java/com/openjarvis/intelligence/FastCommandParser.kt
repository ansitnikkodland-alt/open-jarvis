package com.openjarvis.intelligence

/**
 * Deterministic commands that should never depend on an LLM-generated action plan.
 * Keep this parser deliberately conservative: an unrecognized command falls through to Ollama.
 */
internal object FastCommandParser {
    sealed class Command {
        data class OpenApp(val target: String) : Command()
        object CloseCurrentApp : Command()
        object Back : Command()
        object Home : Command()
        data class Scroll(val direction: String) : Command()
        data class OpenSettings(val action: String) : Command()
        data class Dial(val number: String) : Command()
    }

    private val englishOpen = Regex("^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val hindiOpen = Regex("^(.+?)\\s+(?:kholo|khol\\s+do|open\\s+karo)$", RegexOption.IGNORE_CASE)
    private val close = Regex("^(?:please\\s+)?(?:close|exit|band\\s+karo|band\\s+kar\\s+do)(?:\\s+(?:the\\s+)?app)?$", RegexOption.IGNORE_CASE)
    private val dial = Regex("^(?:call|dial|phone|mujhe\\s+call\\s+karo|call\\s+karo)\\s+([+0-9][+0-9() .-]{4,})$", RegexOption.IGNORE_CASE)

    fun parse(command: String): Command? {
        val normalized = normalize(command)
        if (normalized.isBlank()) return null

        if (close.matches(normalized)) return Command.CloseCurrentApp
        if (normalized.matches(Regex("^(?:back|go back|pichhe|peeche|wapas|वापस)$", RegexOption.IGNORE_CASE))) return Command.Back
        if (normalized.matches(Regex("^(?:home|go home|ghar|होम)$", RegexOption.IGNORE_CASE))) return Command.Home

        val scroll = Regex("^(?:scroll|स्क्रॉल)\\s*(up|down|upar|neeche|ऊपर|नीचे)?$", RegexOption.IGNORE_CASE).matchEntire(normalized)
        if (scroll != null) {
            val direction = when (scroll.groupValues[1].lowercase()) {
                "up", "upar", "ऊपर" -> "up"
                else -> "down"
            }
            return Command.Scroll(direction)
        }

        val settings = settingsAction(normalized)
        if (settings != null) return Command.OpenSettings(settings)

        val dialMatch = dial.matchEntire(normalized)
        if (dialMatch != null) {
            val number = dialMatch.groupValues[1].replace(Regex("[^+0-9]"), "")
            if (number.count { it.isDigit() } in 5..15) return Command.Dial(number)
        }

        val target = openAppTarget(normalized)
        if (target != null) return Command.OpenApp(target)
        return null
    }

    fun openAppTarget(command: String): String? {
        val normalized = normalize(command)
        if (normalized.isBlank()) return null
        val match = englishOpen.matchEntire(normalized) ?: hindiOpen.matchEntire(normalized) ?: return null
        return match.groupValues[1].trim().takeIf {
            it.isNotBlank() && it.length <= 60
        }
    }

    private fun settingsAction(command: String): String? {
        val q = command.lowercase()
        return when {
            listOf("wifi", "wi-fi", "वाईफाई").any { q.contains(it) } && listOf("setting", "settings", "on karo", "kholo", "khol").any { q.contains(it) } -> "wifi"
            listOf("bluetooth", "ब्लूटूथ").any { q.contains(it) } && listOf("setting", "settings", "kholo", "khol").any { q.contains(it) } -> "bluetooth"
            listOf("display settings", "display setting", "screen settings", "brightness settings").any { q.contains(it) } -> "display"
            listOf("sound settings", "sound setting", "volume settings", "audio settings").any { q.contains(it) } -> "sound"
            listOf("location setting", "location settings", "gps setting", "gps settings").any { q.contains(it) } -> "location"
            listOf("accessibility setting", "accessibility settings").any { q.contains(it) } -> "accessibility"
            listOf("app settings", "applications setting", "manage apps").any { q.contains(it) } -> "apps"
            q in listOf("settings", "open settings", "settings kholo", "settings khol do", "सेटिंग खोलो") -> "general"
            else -> null
        }
    }

    private fun normalize(command: String): String =
        command.trim().replace(Regex("\\s+"), " ")
}
