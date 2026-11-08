# ECC-inspired development workflow for Jarvis

This is a repository-local adaptation of the research-first, test-first, security-review, and regression-discipline practices documented by [Everything Claude Code (ECC)](https://github.com/affaan-m/ECC).

It intentionally adds **no ECC runtime, Node/Python dependency, agent daemon, cloud service, or API key** to the Android application. These are development instructions for contributors and AI coding agents, not an app feature.

## Change procedure

### 1. Research before editing
- Read the target implementation and its callers.
- Read nearby unit tests and relevant workflow steps.
- Check Android API level, lifecycle, permission, and service constraints for platform changes.
- Identify what must remain unchanged (local-only AI, Qwen3 default, Android skills, existing data and safety policy).

### 2. Test-first implementation
- Add or update a test that expresses the requested behavior, including at least one negative or boundary case.
- Confirm the test is meaningful; do not disable, skip, weaken, or broadly ignore it.
- Implement the smallest deterministic change.
- Keep pure policy/formatting logic independent of Android where practical.

### 3. Security and safety review
- Treat OCR, app labels, notifications, memory, model output, and tool output as untrusted.
- Preserve system policy and safety instructions across prompt processing.
- Do not execute unvalidated model JSON.
- Do not add remote speech, cloud LLM fallback, telemetry uploads, or secrets.
- Avoid broad catch-and-continue behavior, hidden retries, and silent failure.

### 4. Regression verification
Run in this order, as applicable:
1. Focused unit test for the changed behavior.
2. Full `./gradlew testDebugUnitTest`.
3. `./gradlew assembleDebug`.
4. `bash scripts/smoke-minimal.sh` in the configured Android emulator.
5. Review CI logs and the exact commit SHA.

For voice, foreground-service, permission, or overlay changes, emulator success is not physical-device proof. Record phone verification only after an actual device test.

### 5. Review before PR update
- Inspect the complete diff and changed-file list.
- Check for unrelated edits, accidental dependency changes, duplicated microphone owners, permission regressions, and altered default model/provider.
- Update the PR checklist with only evidence-backed statuses.
- Do not merge without explicit user approval.

## Scope
ECC is used here as a **development workflow reference**, not installed as a runtime dependency. The upstream project is MIT-licensed; no upstream source code is copied into Jarvis by this workflow document.
