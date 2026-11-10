# LLMDroid compatibility audit (2026-11-10)

## License and reuse boundary
- GitHub repository metadata does not declare an SPDX license and the repository root has no LICENSE file.
- Decision: do not copy or vendor LLMDroid source. Its implementation license is unverified. This branch uses independently written Kotlin logic based only on the general observe/act/verify design pattern.
- LLMDroid README describes its own Vosk, OpenWakeWord/TFLite, Ktor and Compose stack. None is required for the first Jarvis adaptation.

## Dependency comparison
- LLMDroid: Kotlin/Compose, Ktor, kotlinx serialization/coroutines, DataStore, Vosk Android, LiteRT, JNA.
- Jarvis: native Kotlin/Android, ONNX Runtime, OkHttp, Room, Compose and existing accessibility service. Adding the entire LLMDroid dependency set would duplicate networking, speech and wake components.
- Its Whisper implementation calls a remote API; explicitly excluded because Jarvis remains local-only.
- Vosk/OpenWakeWord components are also excluded from this slice; Voicute remains unchanged.

## Jarvis compatibility
- Strong architectural overlap: Jarvis already has AccessibilityService, ScreenReader, ActionExecutor, AgentCore and safe re-evaluation policy.
- Gap identified: action success is often only the Android API Boolean; there is no reusable post-action evidence check.
- New isolated implementation: pure Kotlin screen evidence verifier. It does not execute actions, request permissions, read private UI, or change the wake pipeline.
- No third-party dependency added; compatible with current Android minSdk 26 and JVM unit-test setup.

## Integration gate
1. Feed real before/after snapshots from Jarvis existing ScreenReader into this verifier.
2. Verify navigation, text-entry and non-mutating actions separately; do not treat every tap as requiring a screen transition.
3. Keep verification advisory until device tests demonstrate no false failures.
4. Do not merge this branch without explicit approval.