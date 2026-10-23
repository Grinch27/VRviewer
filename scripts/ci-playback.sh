#!/usr/bin/env bash
# Scope: disposable GitHub-hosted emulator; never target a user's physical device.
# Research: Android emulator-commandline, emulator-acceleration and avdmanager docs.
# Verification: real connected tests, bounded boot, preserved logs and exact exit status.
set -euo pipefail

sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Android SDK is required}}"
adb_bin="${sdk_dir}/platform-tools/adb"
emulator_bin="${sdk_dir}/emulator/emulator"
serial="emulator-5554"
report_dir="${PWD}/build/emulator"
mkdir -p "$report_dir"

"$emulator_bin" -version
"$emulator_bin" -accel-check
printf 'no\n' | avdmanager create avd --force --name vrviewer-ci \
  --package 'system-images;android-35;google_apis;x86_64' --device pixel_2
"$adb_bin" start-server

"$emulator_bin" -avd vrviewer-ci -port 5554 -accel on -cores 2 -memory 2048 \
  -no-window -gpu swiftshader -no-snapshot -noaudio -no-boot-anim \
  -camera-back none -camera-front none > "${report_dir}/emulator.log" 2>&1 &
emulator_pid=$!

cleanup() {
  local result=$?
  trap - EXIT
  timeout 15 "$adb_bin" -s "$serial" logcat -d > "${report_dir}/logcat.txt" 2>&1 || true
  if (( result != 0 )); then
    tail -n 100 "${report_dir}/emulator.log"
  fi
  timeout 15 "$adb_bin" -s "$serial" emu kill || true
  kill "$emulator_pid" 2>/dev/null || true
  exit "$result"
}
trap cleanup EXIT

deadline=$((SECONDS + 600))
until [[ "$(timeout 5 "$adb_bin" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    echo 'Android emulator exited before boot completed.' >&2
    exit 1
  fi
  if (( SECONDS >= deadline )); then
    echo 'Android emulator did not boot within 600 seconds.' >&2
    exit 1
  fi
  sleep 2
done

for animation in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$adb_bin" -s "$serial" shell settings put global "$animation" 0
done
"$adb_bin" -s "$serial" shell settings put secure spell_checker_enabled 0
"$adb_bin" -s "$serial" shell input keyevent 82

./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest

# Preserve an actual rendered home screen beside test reports for visual review.
"$adb_bin" -s "$serial" shell am force-stop org.grinch27.vrviewer
"$adb_bin" -s "$serial" shell am start -W -n org.grinch27.vrviewer/.MainActivity
sleep 2
"$adb_bin" -s "$serial" exec-out screencap -p > "${report_dir}/home.png"
