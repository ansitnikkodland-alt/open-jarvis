# Uktam.ai compatibility audit (2026-11-10)

## License and model rights
- Application repository is GPL-3.0. Copying its application source into Jarvis would introduce GPL-3.0 distribution obligations; do not copy or vendor Uktam application code in this proprietary/independently maintained Jarvis branch.
- The IndicConformer ONNX weights are a separate work. Their exact model-card license and redistribution terms were not verified in this audit; do not bundle or redistribute those weights until verified.
- Sherpa-ONNX is a separate dependency with its own license and native-binary obligations; re-check the exact artifact license and notices before adding it.

## Dependency and platform comparison
- Uktam app: Kotlin/C++, Jetpack Compose, Sherpa-ONNX 1.13.3, CMake/JNI, Play Asset Delivery install-time asset pack, WorkManager, Firebase Analytics/Crashlytics.
- Uktam declares minSdk 34, compileSdk 37 and arm64-v8a only. Jarvis currently declares minSdk 26, compileSdk 34, arm64-v8a and x86_64.
- Uktam also runs a llama.cpp translation model chosen by RAM tier; this is not required for Jarvis speech recognition and would be too heavy to add by default on the user's 6 GB phone.
- Jarvis already ships ONNX Runtime Android 1.20.0 and has an STTEngine interface. Adding Sherpa-ONNX now would duplicate native inference runtimes and increase APK/native memory footprint.

## Language and offline fit
- Uktam advertises offline ASR for Hindi, Marathi, Kannada, Malayalam, Tamil and Telugu, using IndicConformer through Sherpa-ONNX.
- The repo includes per-language model assets, but exact peak resident memory, decode latency and model license for the model files were not proven by repository metadata alone.
- Therefore no model binaries or Sherpa dependency are added in this slice. A conservative compatibility gate is added first; it refuses to enable a profile when model size, peak-memory estimate or license metadata is unknown.

## Isolated implementation
- Adds a dependency-free Kotlin policy for language, API, ABI, storage, license and available-memory checks.
- The policy reserves half of currently available memory for Android/Jarvis and requires measured/provided model peak-memory metadata. This is a conservative admission rule, not a claim that a specific model fits.
- Existing Android STT and Voicute wake detection are untouched. Future Indic ASR must be a separately selectable STTEngine implementation, never a wake-word replacement.

## Next validation gate
1. Verify IndicConformer model-card license and checksum for each language.
2. Measure model bytes, peak PSS/RSS, cold-start time, real-time factor and battery on the 6 GB Realme P3 Pro.
3. Only then decide whether to add Sherpa-ONNX or use Jarvis' existing ONNX Runtime backend.
4. Keep Hindi/Marathi offline STT optional until benchmarks and regression tests pass.