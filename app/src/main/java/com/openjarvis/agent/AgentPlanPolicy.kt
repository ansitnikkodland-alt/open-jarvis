package com.openjarvis.agent

/**
 * OpenDroid-style bounded planning guard.
 *
 * Small local models can emit a partial action list followed by an error marker,
 * or a long chain containing speculative Back/Home actions. Re-observe and ask
 * for one compact plan before execution; never execute a mixed refusal.
 */
internal object AgentPlanPolicy {
    const val MAX_ACTIONS = 6

    fun needsCompactReplan(actions: List<Action>): Boolean =
        actions.size > MAX_ACTIONS ||
            (actions.any { it.action == Action.ERROR } && actions.any { it.action != Action.ERROR })

    fun isExecutablePlan(actions: List<Action>): Boolean =
        actions.isNotEmpty() &&
            actions.size <= MAX_ACTIONS &&
            actions.none {
                it.action == Action.ERROR ||
                    it.action == Action.READ_SCREEN ||
                    it.action == Action.EXTRACT_TEXT
            }
}
