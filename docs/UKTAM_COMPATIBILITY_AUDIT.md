# Uktam.ai compatibility audit (2026-11-10)

## License and model rights
- Application repository is GPL-3.0. Copying its application source into Jarvis would introduce GPL-3.0 distribution obligations; do not copy or vendor Uktam application code in this proprietary/independently maintained Jarvis branch.
- AI4Bharat's official IndicConformer project and its model card declare MIT for the source model family. Uktam's Hindi/Marathi ONNX assets are Git LFS objects of 140,451,596 bytes each (about 134 MiB), with a shared 67,605-byte tokens file. Preserve AI4Bharat attribution and verify the exact quantized export's notice before redistribution.
- Sherpa-ONNX is Apache-2.0; Uktam pins 1.13.3. It has native Android/C++ binaries and must retain its license/notices and ABI checks.

## Dependency and platform comparison
- Uktam app: Kotlin/C++, Jetpack Compose, Sherpa-ONNX 1.13.3, CMake/JNI, Play Asset Delivery install-time asset pack, WorkManager, Firebase Analytics/Crashlytics.
- Uktam declares minSdk 34, compileSdk 37 and arm64-v8a only. Jarvis currently declares minSdk 26, compileSdk 34, arm64-v8a and x86_64.
- Uktam also runs a llama.cpp translation model chosen by RAM tier; this is not required for Jarvis speech recognition and would be too heavy to add by default on the user's 6 GB phone.
- Jarvis already ships ONNX Runtime Android 1.20.0 and has an STTEngine interface. Adding Sherpa-ONNX now would duplicate native inference runtimes and increase APK/native memory footprint.

## Language and offline fit
- Uktam advertises offline ASR for Hindi, Marathi, Kannada, Malayalam, Tamil and Telugu, using IndicConformer through Sherpa-ONNX.
- The repo includes per-language model assets (each ONNX model is about 134 MiB); exact peak resident memory, decode latency and the quantized export's full attribution/notice chain were not proven by repository metadata alone. The official AI4Bharat source model family is MIT.
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