package com.openjarvis.agent

/**
 * OpenDroid-style bounded planning guard.
 *
 * Small local models can emit a partial action list followed by an error marker,
 * or a long chain containing speculative Back/Home actions. Re-observe and ask
 * for one compact plan before execution; never execute a mixed refusal.
 */
internal object AgentPlanPolicy {
    const val MAX_ACTIONS = 10

    fun hasUnresolvedAppLaunch(actions: List<Action>): Boolean =
        actions.any { it.action == Action.OPEN_APP && AppTargetResolver.isPlaceholder(it.label) }

    fun needsCompactReplan(actions: List<Action>): Boolean =
        actions.size > MAX_ACTIONS ||
            (actions.any { it.action == Action.ERROR } && actions.any { it.action != Action.ERROR }) ||
            hasUnresolvedAppLaunch(actions)

    private val multiStepConnector = Regex("\\b(and|then|after|also|aur|phir|fir|uske baad|iske baad|baad me|baad mein)\\b", RegexOption.IGNORE_CASE)
    private val followUpIntent = Regex("\\b(search|find|look for|contact|chat|type|write|send|reply|message|scroll|click|tap|read|tell|play|display|brightness|about|section|options|batao|khojo|likho|bhejo|ढूंढ|खोज|लिख|भेज|सांग|पहा|शोध)\\b", RegexOption.IGNORE_CASE)

    fun isLikelyUnderPlanned(command: String, actions: List<Action>): Boolean {
        if (actions.size != 1 || actions.single().action != Action.OPEN_APP) return false
        val hasConnector = multiStepConnector.containsMatchIn(command) || listOf("और", "आणि", "त्यानंतर").any { command.contains(it) }
        val followUpCount = followUpIntent.findAll(command).count() + listOf("ढूंढ", "खोज", "लिख", "भेज", "सांग", "पहा", "शोध").count { command.contains(it) }
        return (hasConnector && followUpCount >= 1) || followUpCount >= 2
    }

    fun needsCompactReplan(actions: List<Action>, command: String): Boolean =
        needsCompactReplan(actions) || isLikelyUnderPlanned(command, actions)

    fun isExecutablePlan(actions: List<Action>): Boolean =
        actions.isNotEmpty() &&
            actions.size <= MAX_ACTIONS &&
            !hasUnresolvedAppLaunch(actions) &&
            actions.none {
                it.action == Action.ERROR ||
                    it.action == Action.READ_SCREEN ||
                    it.action == Action.EXTRACT_TEXT
            }

    fun isExecutablePlan(actions: List<Action>, command: String): Boolean =
        isExecutablePlan(actions) && !isLikelyUnderPlanned(command, actions)
}
