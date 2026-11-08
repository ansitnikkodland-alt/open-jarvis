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
  # The runtime validator requires at least five model-tokenized lines.
  printf '%s\n' 'JARVIS' 'HEY JARVIS' 'JARVISS' 'JARVEES' 'JARVIZ' > /tmp/jarvis-wake-keywords.raw
  /tmp/jarvis-sherpa-tokenizer/bin/sherpa-onnx-cli text2token --tokens "$MODEL_DIR/tokens.txt" --tokens-type bpe --bpe-model "$MODEL_DIR/bpe.model" /tmp/jarvis-wake-keywords.raw "$MODEL_DIR/keywords.txt"
  test -s "$MODEL_DIR/keywords.txt"
  keyword_line_count="$(grep -cve '^[[:space:]]*$' "$MODEL_DIR/keywords.txt")"
  if [[ "$keyword_line_count" -lt 5 ]]; then
    echo "Expected at least 5 BPE-tokenized wake keyword lines, found $keyword_line_count" >&2
    exit 1
  fi
  for phrase in 'JARVIS' 'HEY JARVIS' 'JARVISS' 'JARVEES' 'JARVIZ'; do
    if grep -Fqx "$phrase" "$MODEL_DIR/keywords.txt"; then
      echo "Wake keyword file contains raw text instead of BPE tokens: $phrase" >&2
      exit 1
    fi
  done
fi
