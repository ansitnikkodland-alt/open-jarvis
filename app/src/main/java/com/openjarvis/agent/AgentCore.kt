                } catch (error: Exception) {
                    error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                }
                if (failure == null) {
                    completed = true
                    break
                }
                Log.w(TAG, "Action ${index + 1} ${action.action} attempt $attempt/$maxAttempts failed: $failure")
                if (attempt < maxAttempts && SelfHealingSafetyPolicy.shouldRetry(action, failure.orEmpty())) {
                    kotlinx.coroutines.delay(400)
                } else {
                    break
                }
            }

            if (!completed) {
                val failureReason = failure ?: "verification failed"
                val recovery = selfHealingExecutor.failureDisposition(action, failureReason)
                val retryNote = if (maxAttempts > 1) "after bounded wait retry" else "without replay"
                val message = "Action ${index + 1}/${actions.size} (${action.action}) failed $retryNote: $failureReason. $recovery"
                results.add(message)
                Log.e(TAG, message)
                _state.value = AgentState.Error(message)
                return ActionExecutionResult(
                    succeeded = false,
                    completedActions = completedActions.toList(),
                    failedAction = action,
                    failure = message
                )
            }