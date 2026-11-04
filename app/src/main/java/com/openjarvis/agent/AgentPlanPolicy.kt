package com.openjarvis.agent

/**
 * Bounded plan policy. Replans are only requested for structural safety failures
 * or when an action-bearing compound request was reduced to a single app launch.
 */
internal object AgentPlanPolicy {
    const val MAX_ACTIONS = 10

    fun hasUnresolvedAppLaunch(actions: List<Action>): Boolean =
        actions.any { it.action == Action.OPEN_APP && AppTargetResolver.isPlaceholder(it.label) }

    fun needsCompactReplan(actions: List<Action>): Boolean =
        actions.size > MAX_ACTIONS ||
            (actions.any { it.action == Action.ERROR } && actions.any { it.action != Action.ERROR }) ||
            hasUnresolvedAppLaunch(actions)

    private val multiStepConnector = Regex(
        "(?<![\\p{L}\\p{N}\\p{M}])(and|then|after|also|aur|phir|fir|uske baad|iske baad|baad me|baad mein)(?![\\p{L}\\p{N}\\p{M}])",
        RegexOption.IGNORE_CASE
    )
    private val actionableIntent = Regex(
        "(?<![\\p{L}\\p{N}\\p{M}])(search|find|type|write|send|reply|message|click|tap|scroll|likho|bhejo|khojo|खोजो|लिखो|भेजो|टाइप|क्लिक|टैप|स्क्रोल|शोधा|लिहा|पाठवा)(?![\\p{L}\\p{N}\\p{M}])",
        RegexOption.IGNORE_CASE
    )

    fun isLikelyUnderPlanned(command: String, actions: List<Action>): Boolean {
        if (actions.size != 1 || actions.single().action != Action.OPEN_APP) return false
        val hasConnector = multiStepConnector.containsMatchIn(command) ||
            listOf("और", "आणि", "त्यानंतर").any { command.contains(it) }
        val hasActionableFollowUp = actionableIntent.containsMatchIn(command)
        return hasConnector && hasActionableFollowUp
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
