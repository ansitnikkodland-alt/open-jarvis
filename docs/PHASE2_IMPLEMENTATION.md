# JARVIS Phase 2 — continuation

## Branch and baseline

- Phase 2 branch: `feature/jarvis-phase2-continuation`
- Parent baseline: Phase 1 P0 PR #20 head `6c44d36ce75a462da5a59048ed3a1a858cb44f09`
- PR #20 remains draft/open and is not merged.
- Keep the local-only Ollama `qwen3:1.7b` default unchanged. No cloud/API fallback.

## Existing foundations already present in the parent history

- Compact floating assistant bubble and command panel.
- Deterministic Hindi/Hinglish mute and wake behavior.
- Sherpa-ONNX key-free offline wake-word engine.
- Local WorkManager reminders, opt-in daily briefing, and battery/charging notifications.
- Dashboard proactive-assistant controls.
- Floating HUD waveform for Listening / Thinking / Speaking states.

These are foundations, not a claim that every feature has passed real-phone acceptance.

## Phase 2 continuation — first implementation slice

- Use Android `MIC` input for Sherpa KWS and enable device AGC / NoiseSuppressor when available.
- Validate the generated BPE keyword asset before starting the KWS engine.
- Add adjustable wake-word sensitivity and live audio diagnostics (RMS, peak, frames, AGC/NS state, last detection).
- Add deterministic wake-word session state, STT watchdog, bounded restart backoff, and optional Picovoice fallback only when a valid user-supplied key exists.
- Keep all audio processing on-device. No paid key is required for the primary Sherpa engine.
- Preserve typed commands, manual microphone use, overlay controls, bridge lifecycle, existing data, and Euronics assets.

## Next implementation slices

1. Validate the wake-word lifecycle and auto-rearm on real phone; tune only from measured diagnostics.
2. Verify reminder scheduling, notification permission handling, and battery/charging receiver behavior.
3. Verify the HUD reflects actual Listening / Thinking / Speaking states and remains lightweight.
4. Add memory/performance checks for idle CPU, battery drain, and bounded RAM on the 8 GB device.

## Verification and safety

- Required CI: `unit-tests`, `apk-build`, `smoke-test-minimal`, and preview APK artifact.
- CI/emulator PASS does not equal physical-phone PASS.
- Do not claim wake-word accuracy, notification delivery, or battery performance without device evidence.
- Do not merge any PR without explicit user approval.
- Do not modify Euronics HTML, Firebase configuration, or report workflows in this Phase 2 branch.
