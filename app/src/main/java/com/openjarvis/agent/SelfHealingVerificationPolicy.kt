package com.openjarvis.agent

/**
 * User-visible verification guidance after a failure has been classified.
 * This policy is descriptive only: it never changes Android settings or replays actions.
 */
internal object SelfHealingVerificationPolicy {
    fun instruction(area: SelfHealingRecoveryPolicy.FailureArea): String = when (area) {
        SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY ->
            "confirm the local Ollama server and configured qwen3:1.7b model are ready."
        SelfHealingRecoveryPolicy.FailureArea.MODEL_UNAVAILABLE ->
            "confirm qwen3:1.7b is installed locally; do not switch or download another model automatically."
        SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY ->
            "check Android Accessibility permission/service, then request a fresh read-only screen observation."
        SelfHealingRecoveryPolicy.FailureArea.APP_LAUNCH ->
            "confirm the requested app is installed and launchable before trying again."
        SelfHealingRecoveryPolicy.FailureArea.SPEECH ->
            "check microphone permission and the selected offline speech recognizer, then run a short listening test."
        SelfHealingRecoveryPolicy.FailureArea.OVERLAY ->
            "check the Android display-over-other-apps permission and restart the floating overlay manually."
        SelfHealingRecoveryPolicy.FailureArea.ACTION ->
            "inspect the current screen and app state before deciding whether the action needs a user-approved retry."
        SelfHealingRecoveryPolicy.FailureArea.UNKNOWN ->
            "stop automation and review the failure; no automatic retry is safe."
    }
}
