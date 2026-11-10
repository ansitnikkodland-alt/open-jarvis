# Sanna compatibility audit (2026-11-10)

## License and reuse boundary
- Sanna README states MIT, but a root LICENSE file was not found through the repository contents endpoint. Treat MIT as the repository's stated license, but verify a canonical LICENSE file before copying any source.
- This branch does not copy Sanna TypeScript. It independently implements bounded, privacy-filtered Kotlin app hints inspired by the general idea of per-app task memory.

## Dependency comparison
- Sanna is React Native 0.84 / TypeScript and requires Node >=22.11 for its development/build toolchain.
- Android dependencies include Picovoice Porcupine 4.0.0 (paid/licensed wake-word product), Google Play Services Location, Media3, React Native native modules and OAuth integrations.
- Sanna also includes AsyncStorage, Keychain, clipboard, file-system and permissions packages. Porting the full app would add substantial runtime and dependency surface.
- Jarvis is native Kotlin and already has Room, SharedPreferences, PersonalMemoryStore, NativeLearnedStore and TaskWorkingMemory. Do not add React Native or Picovoice to Jarvis.

## Jarvis compatibility
- Sanna's useful concepts are bounded task memory, action success/failure history and per-app reusable hints.
- Jarvis already has task working memory and persistent learned native intents. Re-implementing those wholesale would duplicate existing systems.
- Missing capability targeted here: safe reusable app-specific guidance that excludes personal message text, contact details, credentials, OTPs, UI node IDs and stale selectors.
- New isolated Kotlin policy and SharedPreferences store use only Android platform APIs; no new Gradle/npm dependencies.

## Safety and integration gates
1. Only successful, generic, high-level guidance may be persisted. Failed flows and raw UI trees are not saved.
2. Hints are bounded by count and character budget; no node IDs, resource IDs, entered text, credentials or personal identifiers.
3. Add retrieval to AgentCore only after unit tests and review prove that hints cannot override system safety rules or user intent.
4. Do not integrate Sanna's Picovoice wake engine; Voicute remains unchanged.
5. Do not merge this branch without explicit approval.