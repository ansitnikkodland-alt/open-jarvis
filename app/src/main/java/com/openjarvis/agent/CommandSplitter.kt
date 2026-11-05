package com.openjarvis.agent

import java.util.Locale

/**
 * Splits one long utterance into ordered steps.
 * Strong sequencers always split. Weak connectors split only when both sides
 * parse as commands (or the right side is a message continuation).
 * After a message-text delimiter nothing is split: the rest is message text.
 * The pipeline is deliberately bounded to five steps.
 */
object CommandSplitter {
    private val PUNCT = Regex("^[\\p{Punct}।॥]+|[\\p{Punct}।॥]+$")
    private val STRONG: List<List<String>> = listOf(
        listOf("uske", "baad"), listOf("उसके", "बाद"), listOf("and", "then"),
        listOf("phir"), listOf("fir"), listOf("then"), listOf("फिर"), listOf("नंतर")
    )
    private val WEAK = setOf("aur", "and", "और", "आणि")
    private val TEXT_DELIMS = setOf("ki", "कि", "that", "saying", "say")
    private val MESSAGE_WORDS = setOf(
        "message", "msg", "sms", "whatsapp", "मैसेज", "मेसेज", "संदेश", "एसएमएस", "व्हाट्सएप"
    )
    private const val MAX_STEPS = 5

    private fun n(w: String): String = w.replace(PUNCT, "").lowercase(Locale.ROOT)

    fun split(text: String, canParse: (String) -> Boolean = { true }): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val steps = ArrayList<String>()
        val cur = ArrayList<String>()
        var inText = false

        fun flush() {
            val s = cur.joinToString(" ").trim().trim(',', ';')
            if (s.isNotEmpty() && steps.size < MAX_STEPS) steps.add(s)
            cur.clear()
            inText = false
        }

        var i = 0
        while (i < words.size) {
            val w = n(words[i])
            if (!inText && steps.size < MAX_STEPS - 1) {
                val strong = STRONG.firstOrNull { seq ->
                    seq.indices.all { k -> i + k < words.size && n(words[i + k]) == seq[k] }
                }
                if (strong != null) {
                    flush()
                    i += strong.size
                    continue
                }
                if (w in WEAK && cur.isNotEmpty()) {
                    val rest = words.subList(i + 1, words.size).joinToString(" ")
                    if (canParse(cur.joinToString(" ")) && canParse(rest)) {
                        flush()
                        i++
                        continue
                    }
                }
            }
            if (!inText && w in TEXT_DELIMS && cur.any { n(it) in MESSAGE_WORDS }) inText = true
            cur.add(words[i])
            i++
        }
        flush()
        return steps
    }
}
