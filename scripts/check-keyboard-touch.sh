#!/usr/bin/env bash
# Requires the debug app and assembleDebugAndroidTest APK installed on the owned device.
set -euo pipefail
# Optional: ANDROID_ENV points to a script that puts adb and the SDK on PATH.
if [ -n "${ANDROID_ENV:-}" ]; then source "$ANDROID_ENV"; fi
serial=${1:?Pass the owned adb device serial}
probe=${2:-burst}
case "$probe" in registration|blur|burst|overlap|same-key|same-key-reverse|numbers|accents|correction|clipboard|autocomplete|autocomplete-refresh|autocomplete-boundaries) ;; *) printf 'Unknown probe: %s\n' "$probe" >&2; exit 2;; esac
count=${3:-30}
interval=${4:-100}
scripts/mem-guard.sh --emulator-max 12
# agent-device text entry can select its helper IME. These probes must drive Lowkey itself.
adb -s "$serial" shell ime enable com.rheerkens.androidkeyboard/.ime.SlimKeyboardService
adb -s "$serial" shell ime set com.rheerkens.androidkeyboard/.ime.SlimKeyboardService
result=$(adb -s "$serial" shell am instrument -w -r -e probe "$probe" -e count "$count" -e interval "$interval" com.rheerkens.androidkeyboard.test/com.rheerkens.androidkeyboard.test.KeyboardTouchProbe)
printf '%s\n' "$result"
if [[ "$result" != *'INSTRUMENTATION_CODE: 0'* || "$result" == *'FAIL:'* || "$result" != *'probe='* ]]; then exit 1; fi
