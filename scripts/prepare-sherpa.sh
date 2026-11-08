#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar"
AAR_SHA256="a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2"
MODEL_DIR="app/src/main/assets/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"

mkdir -p app/libs
curl --fail --location --retry 2 "$AAR_URL" --output app/libs/sherpa-onnx.aar
echo "$AAR_SHA256  app/libs/sherpa-onnx.aar" | sha256sum --check

# Voicute uses ONNX Runtime 1.20.0 for its multi-keyword model. sherpa-onnx
# v2.0.1 bundles an older libonnxruntime.so in its AAR; an APK can contain
# only one copy of this SONAME. Keep Sherpa's JNI bridge, but use the shared
# 1.20.0 runtime supplied by app/build.gradle for both engines.
python3 - <<'PY'
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

aar = Path("app/libs/sherpa-onnx.aar")
tmp = aar.with_suffix(".aar.tmp")
removed = []
with ZipFile(aar, "r") as source, ZipFile(tmp, "w", ZIP_DEFLATED) as target:
    for entry in source.infolist():
        if entry.filename.startswith("jni/") and entry.filename.endswith("/libonnxruntime.so"):
            removed.append(entry.filename)
            continue
        target.writestr(entry, source.read(entry.filename))

if not removed:
    tmp.unlink(missing_ok=True)
    raise SystemExit("Expected Sherpa AAR to contain jni/*/libonnxruntime.so; refusing an unverified packaging change")
with ZipFile(tmp, "r") as check:
    names = check.namelist()
    if any(name.startswith("jni/") and name.endswith("/libonnxruntime.so") for name in names):
        raise SystemExit("Sherpa AAR still contains a duplicate libonnxruntime.so")
    if not any(name.startswith("jni/") and name.endswith("/libsherpa-onnx-jni.so") for name in names):
        raise SystemExit("Sherpa JNI bridge missing after AAR rewrite")
tmp.replace(aar)
print("Removed Sherpa-bundled ONNX Runtime native libraries:", ", ".join(removed))
print("Sherpa JNI bridge retained; app ONNX Runtime 1.20.0 is the sole native runtime.")
PY

if [[ "$MODE" == "all" ]]; then
  mkdir -p app/src/main/assets
  curl --fail --location --retry 2 "$MODEL_URL" --output /tmp/jarvis-kws-model.tar.bz2
  tar -xjf /tmp/jarvis-kws-model.tar.bz2 -C app/src/main/assets
  if [[ ! -d "$MODEL_DIR" ]]; then
    echo "Expected sherpa KWS model directory not found: $MODEL_DIR" >&2
    exit 1
  fi
  rm -rf "$MODEL_DIR/test_wavs"

  # The English GigaSpeech model expects BPE-tokenized custom keywords.
  # Passing plain "JARVIS" to createStream() is not valid token syntax.
  python3 -m venv /tmp/jarvis-sherpa-tokenizer
  /tmp/jarvis-sherpa-tokenizer/bin/pip install --disable-pip-version-check sherpa-onnx click sentencepiece pypinyin
  # Keep this list in sync with WakeWordConfig.KEYWORD_PHRASES.
  # The runtime validator requires three model-tokenized lines.
  printf '%s\n' 'JARVIS' 'HEY MITRA' 'HEY DOST' > /tmp/jarvis-wake-keywords.raw
  /tmp/jarvis-sherpa-tokenizer/bin/sherpa-onnx-cli text2token --tokens "$MODEL_DIR/tokens.txt" --tokens-type bpe --bpe-model "$MODEL_DIR/bpe.model" /tmp/jarvis-wake-keywords.raw "$MODEL_DIR/keywords.txt"
  test -s "$MODEL_DIR/keywords.txt"
  keyword_line_count="$(grep -cve '^[[:space:]]*$' "$MODEL_DIR/keywords.txt")"
  if [[ "$keyword_line_count" -lt 3 ]]; then
    echo "Expected at least 3 BPE-tokenized wake keyword lines, found $keyword_line_count" >&2
    exit 1
  fi
  for phrase in 'JARVIS' 'HEY MITRA' 'HEY DOST'; do
    if grep -Fqx "$phrase" "$MODEL_DIR/keywords.txt"; then
      echo "Wake keyword file contains raw text instead of BPE tokens: $phrase" >&2
      exit 1
    fi
  done
fi
