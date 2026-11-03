#!/usr/bin/env bash
set -euo pipefail

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
XML_PATH="/tmp/jarvis-window.xml"

[[ -f "$APK_PATH" ]] || { echo "Debug APK not found: $APK_PATH" >&2; exit 10; }

adb install -r "$APK_PATH"
adb shell pm grant com.openjarvis android.permission.RECORD_AUDIO
adb shell appops set com.openjarvis SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services com.openjarvis/com.openjarvis.accessibility.JarvisAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell am start -W -n com.openjarvis/.ui.MainActivity
sleep 8

PID="$(adb shell pidof com.openjarvis | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process is not running" >&2; adb logcat -d -t 2000; exit 11; }
adb shell dumpsys accessibility | grep -F 'com.openjarvis/com.openjarvis.accessibility.JarvisAccessibilityService'
adb shell dumpsys activity activities | grep -F 'com.openjarvis/.ui.MainActivity'

adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb shell cat /sdcard/window.xml > "$XML_PATH"
TAP_COORDS="$(python3 scripts/find_bounds.py "$XML_PATH")"
read -r TAP_X TAP_Y <<< "$TAP_COORDS"
[[ "$TAP_X" =~ ^[0-9]+$ && "$TAP_Y" =~ ^[0-9]+$ ]] || { echo "Invalid tap coordinates: $TAP_COORDS" >&2; exit 12; }
echo "Tapping EURONICS SERVICE REPORT at $TAP_X,$TAP_Y"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 15

PID="$(adb shell pidof com.openjarvis | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process stopped after Euronics tap" >&2; adb logcat -d -t 2000; exit 13; }
adb shell dumpsys activity activities | grep -F 'com.openjarvis/.ui.EuronicsActivity'
adb logcat -d | grep -F 'EURONICS_HTML_TITLE:'
echo "EURONICS_SMOKE_TEST: PASS"
