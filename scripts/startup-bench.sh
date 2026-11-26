#!/usr/bin/env bash
# Startup benchmark: installs an APK over the existing app (data is kept) and measures, per compile
# state, the companion app's cold start (am start -W TotalTime), the word list build that starts with
# the process (the "Built … in N ms" log line), and memory after start.
#
#   scripts/startup-bench.sh <serial> <apk> <label> [runs]
#
# "verify" is the state right after a sideloaded install; "speed-profile" is the state after
# Android's idle-time optimisation, which uses the APK's baseline profile. Compare APKs on the same
# device in one session, and run each more than once: emulator timings vary with host load.
set -euo pipefail
# Optional: ANDROID_ENV points to a script that puts adb and the SDK on PATH.
if [ -n "${ANDROID_ENV:-}" ]; then source "$ANDROID_ENV"; fi
serial=${1:?Pass the adb device serial}
apk=${2:?Pass the APK to measure}
label=${3:?Pass a label for the output}
runs=${4:-10}
pkg=com.opstudio.lowkey
# Classes stay in the source namespace, which is not the application id.
activity=$pkg/com.rheerkens.androidkeyboard.companion.MainActivity
dev() { adb -s "$serial" "$@"; }
median() { tr ' ' '\n' | grep -E '^[0-9]+$' | sort -n | awk '{a[NR]=$1} END {if (NR) print (NR%2 ? a[(NR+1)/2] : (a[NR/2]+a[NR/2+1])/2); else print "?"}'; }

dev install -r "$apk" >/dev/null
dev shell ime set $pkg/com.rheerkens.androidkeyboard.ime.SlimKeyboardService >/dev/null
for mode in verify speed-profile; do
  # One launch first, so ProfileInstaller can write the bundled profile (it waits a few seconds).
  dev shell am force-stop $pkg
  dev shell am start -W -n $activity >/dev/null
  sleep 8
  dev shell cmd package compile -f -m $mode $pkg >/dev/null
  starts=""; builds=""
  for _ in $(seq 1 "$runs"); do
    dev shell am force-stop $pkg
    dev logcat -c
    sleep 1
    starts="$starts $(dev shell am start -W -n $activity | sed -n 's/^TotalTime: //p' | tr -d '\r')"
    sleep 4
    builds="$builds $(dev logcat -d -s SlimKeyboard:I | sed -n 's/.*Built .* in \([0-9]*\) ms.*/\1/p' | tail -1)"
  done
  echo "$label | $mode | cold start median $(echo $starts | median) ms |$starts"
  echo "$label | $mode | word list median $(echo $builds | median) ms |$builds"
  echo "$label | $mode | memory after start:$(dev shell dumpsys meminfo $pkg | grep -m1 'TOTAL PSS:' | tr -s ' ')"
done
