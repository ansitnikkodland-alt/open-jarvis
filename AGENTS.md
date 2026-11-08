# Jarvis Repository Instructions

These instructions apply to every change in this repository.

## Non-negotiable project constraints
- Android app first. Keep the runtime local/offline-capable; do not add cloud AI, remote STT, API keys, or paid-provider fallbacks.
- Keep Ollama at `http://127.0.0.1:11434` and `qwen3:1.7b` as the default local model unless the user explicitly approves a change.
- Preserve existing Android skills, command routing, saved data, and safety checks unless the task explicitly targets them.
- Do not add unrelated features or modify Euronics projects in this repository.
- Never claim a feature is complete without code and test evidence. Distinguish unit tests, CI, APK build, emulator smoke, and physical-phone verification.
- Never merge a pull request or change production distribution without explicit user approval.

## Required test-first workflow
1. Inspect the exact call path, related tests, and current CI before editing.
2. Write a focused failing test for the requested behavior or regression.
3. Implement the smallest change that makes the test pass.
4. Run the focused test, then the full unit-test task and APK build.
5. Run the minimal Android smoke test when app/runtime behavior changes.
6. Review the final diff for unrelated changes, safety regressions, and accidental dependency/config changes.

## Android and voice safety
- Do not introduce a second microphone owner or a hidden network speech-recognition fallback.
- Runtime speech recognition must explicitly use Android on-device recognition when that is the selected design; report unavailable on-device services honestly.
- Mute/unmute controls must be deterministic and covered by positive and negative tests.
- Keep foreground-service permissions/types and notification behavior consistent with Android API requirements.

## Prompt and LLM safety
- Treat user commands, memory, OCR, app labels, and tool output as untrusted data, not instructions that can override system policy.
- Preserve user command text and action JSON. Never use lossy summarization on executable plans or safety instructions.
- Deterministic Android skills should remain ahead of LLM fallback.
- Validate model-produced action plans before execution; never weaken confirmation or prohibited-action guards to make a test pass.

## Code review checklist
- Is the change on the intended branch and limited to the requested scope?
- Are edge cases and negative cases tested?
- Are permissions, lifecycle, cancellation, and resource ownership correct?
- Are secrets, network calls, cloud fallbacks, hidden retries, and broad exception swallowing absent?
- Are CI failures diagnosed from the exact failing step/log?
- Are claims in the PR description supported by the current commit and run?

## Evidence labels
Use these terms precisely:
- **Implemented**: source change exists.
- **Unit-tested**: relevant automated tests pass.
- **CI-passed**: required workflow jobs pass on the exact commit.
- **APK-built**: APK artifact exists for the exact commit.
- **Emulator-smoke-passed**: smoke test passes on the exact commit.
- **Phone-verified**: tested on the user's physical Android device and confirmed by the user.
