package com.openjarvis.agent

internal data class InstalledAppTarget(val label: String, val packageName: String)

/** Resolves only explicit app names from the user's command; ambiguity is rejected. */
internal object AppTargetResolver {
    private data class Hit(val start: Int, val end: Int, val alias: String, val app: InstalledAppTarget)
    private val placeholders = setOf("", "app", "application", "target", "target app", "app name", "application name", "exact installed app label", "app_name")

    fun isPlaceholder(value: String?): Boolean = normalize(value.orEmpty()) in placeholders

    fun findMentionedApps(command: String, installedApps: List<InstalledAppTarget>): List<InstalledAppTarget>? {
        val query = normalize(command)
        if (query.isBlank()) return emptyList()
        val hits = installedApps.flatMap { app ->
            aliasesFor(app).mapNotNull { alias ->
                val phrase = normalize(alias)
                if (phrase.isBlank()) return@mapNotNull null
                val pattern = Regex("(?<![\\p{L}\\p{N}\\p{M}])" + Regex.escape(phrase) + "(?![\\p{L}\\p{N}\\p{M}])")
                val match = pattern.find(query) ?: return@mapNotNull null
                Hit(match.range.first, match.range.last + 1, phrase, app)
            }
        }.sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.alias.length })
        if (hits.isEmpty()) return emptyList()

        val output = mutableListOf<InstalledAppTarget>()
        val seen = mutableSetOf<String>()
        var cursor = 0
        var index = 0
        while (index < hits.size) {
            val first = hits[index]
            if (first.start < cursor) { index++; continue }
            val sameStart = hits.drop(index).takeWhile { it.start == first.start }
            val longest = sameStart.maxOf { it.alias.length }
            val best = sameStart.filter { it.alias.length == longest }.distinctBy { it.app.packageName }
            if (best.size > 1) return null
            val chosen = best.first()
            if (seen.add(chosen.app.packageName)) output.add(chosen.app)
            cursor = chosen.end
            index = hits.indexOfFirst { it.start >= cursor }.let { if (it < 0) hits.size else it }
        }
        return output
    }

    private fun aliasesFor(app: InstalledAppTarget): Set<String> {
        val label = normalize(app.label)
        val aliases = linkedSetOf(label)
        when (label) {
            "chrome", "google chrome" -> aliases.addAll(listOf("chrome", "google chrome"))
            "whatsapp", "whats app" -> aliases.addAll(listOf("whatsapp", "whats app", "व्हाट्सएप", "व्हॉट्सअॅप"))
            "youtube", "you tube" -> aliases.addAll(listOf("youtube", "you tube", "यूट्यूब"))
            "settings", "setting" -> aliases.addAll(listOf("settings", "setting", "सेटिंग", "सेटिंग्स", "सेटिंग्ज"))
            "phone", "dialer" -> aliases.addAll(listOf("phone", "dialer", "फोन"))
            "messages", "messaging" -> aliases.addAll(listOf("messages", "messaging", "sms", "संदेश"))
            "contacts" -> aliases.addAll(listOf("contacts", "contact", "संपर्क"))
            "photos", "google photos" -> aliases.addAll(listOf("photos", "google photos"))
            "gallery" -> aliases.add("gallery")
            "files", "file manager", "my files" -> aliases.addAll(listOf("files", "file manager", "my files"))
            "play store", "google play store" -> aliases.addAll(listOf("play store", "google play store"))
            "gmail" -> aliases.add("gmail")
            "maps", "google maps" -> aliases.addAll(listOf("maps", "google maps"))
            "camera" -> aliases.addAll(listOf("camera", "कैमरा"))
            "calculator" -> aliases.addAll(listOf("calculator", "कैलकुलेटर"))
            "clock" -> aliases.addAll(listOf("clock", "घड़ी"))
            "telegram" -> aliases.add("telegram")
            "instagram" -> aliases.add("instagram")
            "facebook" -> aliases.add("facebook")
        }
        return aliases.filter { it.length >= 2 }.toSet()
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ").trim().replace(Regex("\\s+"), " ")
}
