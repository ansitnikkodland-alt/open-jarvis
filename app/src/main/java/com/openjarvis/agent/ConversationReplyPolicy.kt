package com.openjarvis.agent

/** Blocks internal model reasoning and prompt leakage from reaching UI/TTS. */
internal object ConversationReplyPolicy {
    const val SAFE_FALLBACK = "Maaf kijiye, main abhi iska jawab nahi de pa raha hoon."

    private val internalTerms = Regex(
        """(?i)(?<![\p{L}\p{N}_])(plan|planning|thinking|thought process|analysis|reasoning)(?![\p{L}\p{N}_])"""
    )
    private val thinkTags = Regex("""(?is)</?\s*think\b[^>]*>""")
    private val promptLeak = Regex(
        """(?is)(system prompt|developer message|you are (?:jarvis|an ai|a helpful)|user\s*:|assistant\s*:|return only|allowed skills|ignore (?:all )?(?:previous|prior) instructions|memory:|screen ocr:|installed apps:)"""
    )
    private val structuredInternals = Regex("""(?is)\{\s*["'](?:action|skill|args)["']\s*:""")

    fun safeReply(raw: String): String {
        val reply = raw.trim()
        if (reply.isBlank() || reply.length > 2400) return SAFE_FALLBACK
        if (internalTerms.containsMatchIn(reply) ||
            thinkTags.containsMatchIn(reply) ||
            promptLeak.containsMatchIn(reply) ||
            structuredInternals.containsMatchIn(reply) ||
            reply.contains(96.toChar().toString().repeat(3))
        ) return SAFE_FALLBACK
        return reply
    }
}
