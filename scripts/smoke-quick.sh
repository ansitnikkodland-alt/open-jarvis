#!/usr/bin/env bash
set -euo pipefail

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
ARTIFACT_DIR="artifacts"
LOGCAT_PATH="$ARTIFACT_DIR/quick-smoke-logcat.txt"
APP_ID="com.openjarvis.preview"
MAIN_ACTIVITY="$APP_ID/com.openjarvis.ui.MainActivity"

mkdir -p "$ARTIFACT_DIR"
: > "$LOGCAT_PATH"

capture_failure_diagnostics() {
  local exit_code=$?
  if [[ "$exit_code" -ne 0 ]]; then
    echo "Quick smoke failed with exit code $exit_code; collecting diagnostics." >&2
    if ! adb logcat -b all -d -t 2000 > "$LOGCAT_PATH" 2>/dev/null; then
      echo "Could not collect failure logcat." >&2
    fi
  fi
  return "$exit_code"
}
trap capture_failure_diagnostics EXIT

[[ -s "$APK_PATH" ]] || { echo "Debug APK missing or empty: $APK_PATH" >&2; exit 10; }

echo "QUICK SMOKE: waiting for emulator device."
adb wait-for-device

echo "QUICK SMOKE: bounded Android readiness check (maximum 24 seconds)."
READY=0
STABLE=0
for ((attempt=1; attempt<=12; attempt++)); do
  BOOTED="$(adb shell getprop sys.boot_completed | tr -d '\r')"
  PACKAGE_SERVICE="$(adb shell service check package 2>/dev/null | tr -d '\r')"
  ACTIVITY_SERVICE="$(adb shell service check activity 2>/dev/null | tr -d '\r')"
  if [[ "$BOOTED" == "1" && "$PACKAGE_SERVICE" == *"found"* && "$ACTIVITY_SERVICE" == *"found"* ]] &&
     adb shell cmd package list packages >/dev/null 2>&1; then
    STABLE=$((STABLE + 1))
    if [[ "$STABLE" -ge 2 ]]; then
      READY=1
      break
    fi
  else
    STABLE=0
  fi
  sleep 2
done
[[ "$READY" -eq 1 ]] || { echo "Android package/activity services not ready within 24 seconds." >&2; exit 15; }

echo "QUICK SMOKE: installing APK (single bounded attempt)."
adb install --no-streaming -r "$APK_PATH"

echo "QUICK SMOKE: granting first-run runtime permissions."
adb shell pm grant "$APP_ID" android.permission.RECORD_AUDIO
SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$SDK" -ge 33 ]]; then
  adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS
fi

adb logcat -c
echo "QUICK SMOKE: launching MainActivity."
adb shell am start -W -n "$MAIN_ACTIVITY"

echo "QUICK SMOKE: waiting up to 20 seconds for resumed Activity."
RESUMED=""
for ((second=1; second<=20; second++)); do
  ACTIVITY_STATE="$(adb shell dumpsys activity activities | tr -d '\r')"
  if RESUMED="$(printf '%s\n' "$ACTIVITY_STATE" | grep -E 'mResumedActivity|topResumedActivity' | grep -F "$MAIN_ACTIVITY")"; then
    echo "MainActivity resumed after ${second}s."
    break
  fi
  RESUMED=""
  sleep 1
done
[[ -n "$RESUMED" ]] || { echo "MainActivity did not resume within 20 seconds." >&2; exit 11; }

PID="$(adb shell pidof "$APP_ID" | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process is not running after resume." >&2; exit 12; }

echo "QUICK SMOKE: observing process for 5 seconds."
sleep 5
PID="$(adb shell pidof "$APP_ID" | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process exited during observation." >&2; exit 13; }

adb logcat -d > "$LOGCAT_PATH"
if grep -Fq "FATAL EXCEPTION" "$LOGCAT_PATH"; then
  echo "FATAL EXCEPTION detected in logcat." >&2
  grep -F -A 30 -B 5 "FATAL EXCEPTION" "$LOGCAT_PATH"
  exit 14
fi

echo "QUICK_ANDROID_SMOKE: PASS"
echo "Verified: APK installed, MainActivity resumed, process remained alive for 5 seconds, and no FATAL EXCEPTION appeared."
