package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Extracts explicit WhatsApp group and follow-up typing references.
 * This parser never sends a message; it only supplies context to the visible-UI planner.
 */
internal object WhatsAppGroupCommandParser {
    private val groupSuffix = Regex(
        "^(.+?)\\s+(?:whatsapp\\s+)?(?:group|ग्रुप|समूह)\\s+(?:kholo|khol|open|खोलो|खोल|उघडा|उघड)$",
        RegexOption.IGNORE_CASE
    )
    private val typeSuffix = Regex(
        "^(.+?)\\s+(?:likho|likh do|type karo|type|write|लिखो|लिख दो|लिहा|लिहून टाका)$",
        RegexOption.IGNORE_CASE
    )

    fun parseGroupName(command: String): String? {
        val cleaned = Normalizer.normalize(command, Normalizer.Form.NFKC).trim().replace(Regex("[\\\\p{Punct}।॥]+$"), "").trim()
        val match = groupSuffix.matchEntire(cleaned) ?: return null
        return match.groupValues[1].trim().takeIf { it.length in 2..80 }
    }

    fun parseFollowUpText(command: String, app: String?, groupName: String?): String? {
        if (app?.contains("whatsapp", ignoreCase = true) != true || groupName.isNullOrBlank()) return null
        val cleaned = Normalizer.normalize(command, Normalizer.Form.NFKC).trim()
        val match = typeSuffix.matchEntire(cleaned) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() && it.length <= 2000 }
    }
}
