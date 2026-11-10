# Jarvis Phase 3 — Headroom Lite/Hybrid + ECC development workflow

## Branch and safety boundary

- Branch: `feature/jarvis-phase3-headroom-ecc`
- Parent: Phase 2 candidate commit `d820747eef7b0887e617a0bba4983c9bd111e22c`
- Phase 2 PR #21 remains open/draft and must not be merged without explicit user approval.
- This phase is isolated from Phase 2 so wake-word/overlay work can be reviewed independently.
- Preserve Android local-only inference: Ollama at `127.0.0.1:11434`, default `qwen3:1.7b`; no cloud/API fallback, no API keys, no paid service.
- Do not modify Euronics assets, Firebase configuration, workflows, or data.

## Confirmed repository baseline

- `UniversalAdapter` currently pins the active provider to `OllamaProvider.DEFAULT_BASE_URL` and `OllamaProvider.DEFAULT_MODEL`; legacy provider preferences are overwritten to the local-only settings.
- `OllamaResponseParser` currently sends a 2,048-token context setting and `think=false`.
- `PersonalMemoryStore` is encrypted local storage with bounded notes and token-overlap recall.
- `SharedLlmBudget` enforces a shared 45-second LLM deadline.
- Cloud provider implementation classes exist in the repository, but are not listed as active providers and are not selected by `UniversalAdapter`. Keep them unreachable from the active Jarvis path.

## Headroom integration decision

Headroom is a context-compression layer, not an LLM and not an Android wake-word engine. Its maintained interfaces include a local Python library, a local proxy, and a TypeScript SDK. The TypeScript SDK uses the proxy; the proxy exposes OpenAI/Anthropic-style routes. The current Jarvis Ollama client uses Ollama's native `/api/chat` request format, so pointing it directly at the Headroom proxy is not a verified drop-in integration.

Official references:
- Headroom repository: https://github.com/headroomlabs-ai/headroom
- Headroom quickstart: https://docs.headroomlabs.ai/docs/quickstart
- Headroom proxy: https://docs.headroomlabs.ai/docs/proxy
- Headroom architecture: https://docs.headroomlabs.ai/docs/architecture

### Phase 3A — Headroom Lite (first; Android-native)

Implement a small deterministic Kotlin context-preparation layer inside Jarvis before any external sidecar:

1. Inventory the actual prompt inputs and their sources (system instructions, current user request, tool outputs, screen/OCR extracts, retrieved memory, and task traces).
2. Classify content by type and risk. Never rewrite the user's latest request, commands, confirmation text, identifiers, URLs, code, or safety-critical values.
3. Compact only repeated/structured tool output and stale intermediate traces. Preserve errors, warnings, unusual values, first/last records, and provenance.
4. Bound every section independently and enforce the existing 2,048 context budget; do not silently truncate the current user request.
5. Keep a reversible local original for any lossy summary, with explicit source IDs and a bounded size/retention policy. If storage is unavailable, pass original content.
6. Fail open: any compressor error returns the original input. No network, telemetry, background model, or additional APK-sized model.
7. Add tests for exact preservation, error/anomaly retention, ordering, Unicode/Hindi/Marathi, limits, empty input, and fail-open behavior.

### Phase 3B — Headroom Hybrid (opt-in experiment; not default)

Evaluate the official Headroom local proxy only after Lite has baseline measurements. Headroom documents structural compression and optional ML/ONNX compression; the proxy's ML path has CPU/RAM cost and may run local model inference. Keep remote Kompress endpoints disabled.

Before any Android integration:
- Prove a compatible request/response adapter for Jarvis's native Ollama `/api/chat` protocol; do not assume the proxy is a drop-in Ollama endpoint.
- If a local adapter is required, run it only as an explicitly enabled Termux sidecar on loopback with a random local token and a clear health check.
- Test cold-start, idle RAM, peak RAM, CPU, battery, latency, token reduction, and answer/action equivalence on the target phone.
- Keep the default direct Ollama path available and automatically bypass the sidecar on timeout/unavailable state.
- Do not enable CCR/retrieval markers unless Jarvis also implements and tests the matching retrieval tool end-to-end. Prefer marker-free/lossless mode initially.
- Do not package Python, proxy services, or large compressor models into the Android APK without a separate size/resource review.

### Phase 3C — Acceptance gates

- No cloud requests or external data egress during normal Jarvis operation.
- Existing quick skills remain deterministic and do not wait for an LLM/compressor.
- No regression to Qwen3 default, local Ollama URL, response parsing, command execution safety, or shared 45-second deadline.
- Measure baseline vs Lite vs Hybrid on the same fixed prompt/tool-output corpus. Report tokens/characters, latency, memory, and task-result equivalence; never claim savings without measurements.
- Required CI: unit tests, APK build, minimal smoke test, and preview artifact all green.
- Phone acceptance remains separate: verify normal-volume wake word, auto-rearm, overlay persistence, idle resource use, and LLM round-trip.

## ECC — development workflow only

ECC is a developer workflow/toolkit, not an Android runtime dependency. Use the official project at https://github.com/affaan-m/ECC and its Kotlin/Android reviewer, Kotlin build resolver, planner, TDD, code review, security review, performance review, and verification workflows where available.

Apply the loop to every Phase 3 code slice:

1. Plan and inspect current code.
2. Write focused failing tests first.
3. Implement the smallest isolated change.
4. Run targeted tests, then full unit tests and APK build.
5. Run fresh-context Kotlin/security review.
6. Inspect the diff for accidental cloud paths, secrets, data collection, or Euronics changes.
7. Record evidence and measured outcomes; do not mark untested phone behavior as passed.

Do not install ECC into the Jarvis APK. Do not add paid integrations or cloud credentials. Do not claim ECC is active in the user's local development environment unless it is actually installed and verified there.

## Current execution order

1. Keep Phase 2 PR #21 draft and independent.
2. Finish Phase 2 code-level audit and preserve the latest green CI evidence.
3. Build a prompt/tool-output inventory and baseline test corpus.
4. Implement Headroom Lite as a standalone pure Kotlin component with tests.
5. Integrate only at a proven tool-output/context boundary.
6. Benchmark Lite on the 6 GB target device before considering Hybrid.
7. Evaluate the Headroom sidecar only if Lite leaves a measurable gap and resource tests pass.


## Phase 3A progress — first isolated implementation slice

- Added `HeadroomLiteContext`, a pure Kotlin run-length compactor for adjacent identical non-empty screen/OCR lines.
- Error, warning, fatal, exception, and failure lines are deliberately never collapsed.
- Unique lines, ordering, blank lines, Hindi/Marathi Unicode, and final-newline boundaries are preserved.
- Wired compaction only into OCR text context in `AgentCore`; interactive screen-element records remain unmodified because repeated labels can represent distinct actionable controls. Existing prompt character caps remain unchanged.
- Added unit tests for repeated lines, important-line preservation, ordering, Unicode, empty input, and newline behavior.
- No Python/Node sidecar, model, network call, telemetry, cloud provider, or new Android dependency was added.
- Android CI run #969 is the first validation gate for this slice. Do not call it passed until the workflow finishes successfully.
- Added a deterministic OCR benchmark fixture that verifies character savings, collapsed-line counts, exact unique-fact retention, and idempotence. These are character-level unit measurements, not model-token or latency claims. Interactive element records are passed through unchanged. Next: add prompt-level equivalence fixtures and measure real Ollama token/latency impact before considering Hybrid.
