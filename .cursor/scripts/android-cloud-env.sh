# Shared helpers for Cursor Cloud Agent Android emulator scripts.
# Sourced by setup-android-sdk.sh, start-android-emulator.sh, and the
# screenshot / launch helpers. Not meant to be executed directly.

# shellcheck shell=bash

AVD_NAME="${AVD_NAME:-golden_stars_api34}"
SYSTEM_IMAGE="${SYSTEM_IMAGE:-system-images;android-34;google_apis;x86_64}"
APP_ID="${APP_ID:-com.edvinlinge.hemma.mathstars2}"
LAUNCHER_ACTIVITY="${LAUNCHER_ACTIVITY:-com.edvinlinge.hemma.mathstars2/.MainActivity}"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
EMULATOR_LOG="${EMULATOR_LOG:-$HOME/.android/emulator.log}"
EMULATOR_PID_FILE="${EMULATOR_PID_FILE:-$HOME/.android/emulator.pid}"

# Device pixels. QT_SCALE_FACTOR in start-android-emulator.sh shrinks the
# window so it fits the 1920x1200 Cloud Agent desktop; adb screencap stays
# at this full resolution.
AVD_LCD_WIDTH="${AVD_LCD_WIDTH:-1080}"
AVD_LCD_HEIGHT="${AVD_LCD_HEIGHT:-1920}"
AVD_LCD_DENSITY="${AVD_LCD_DENSITY:-420}"

load_android_env() {
  export ANDROID_SDK_ROOT ANDROID_HOME ANDROID_AVD_HOME
  export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
  mkdir -p "$HOME/.android" "$ANDROID_AVD_HOME"
}

sudo_if_available() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    "$@"
  fi
}

ensure_kvm_access() {
  if [ ! -e /dev/kvm ]; then
    echo "KVM device /dev/kvm is missing; nested virtualization is required." >&2
    return 1
  fi
  if [ -r /dev/kvm ] && [ -w /dev/kvm ]; then
    return 0
  fi
  sudo_if_available chmod a+rw /dev/kvm
  if [ -r /dev/kvm ] && [ -w /dev/kvm ]; then
    return 0
  fi
  echo "Cannot read/write /dev/kvm (needed for emulator hardware acceleration)." >&2
  return 1
}

emulator_bin() {
  if [ -x "$ANDROID_SDK_ROOT/emulator/emulator" ]; then
    echo "$ANDROID_SDK_ROOT/emulator/emulator"
    return 0
  fi
  command -v emulator 2>/dev/null || true
}

adb_bin() {
  if [ -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
    echo "$ANDROID_SDK_ROOT/platform-tools/adb"
    return 0
  fi
  command -v adb 2>/dev/null || true
}

emulator_pid_running() {
  local pid=""
  if [ -f "$EMULATOR_PID_FILE" ]; then
    pid="$(cat "$EMULATOR_PID_FILE" 2>/dev/null || true)"
  fi
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    return 0
  fi
  return 1
}

adb_has_device() {
  local adb
  adb="$(adb_bin)"
  if [ -z "$adb" ] || [ ! -x "$adb" ]; then
    return 1
  fi
  "$adb" devices 2>/dev/null | awk 'NR>1 && $2=="device" {found=1} END {exit found?0:1}'
}

wait_for_emulator() {
  local timeout_s="${1:-180}"
  local adb
  local start now boot
  adb="$(adb_bin)"
  if [ -z "$adb" ] || [ ! -x "$adb" ]; then
    echo "adb not found under $ANDROID_SDK_ROOT" >&2
    return 1
  fi
  start="$(date +%s)"

  while true; do
    if adb_has_device; then
      boot="$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
      if [ "$boot" = "1" ] && "$adb" shell pm path android >/dev/null 2>&1; then
        return 0
      fi
    fi
    now="$(date +%s)"
    if [ $((now - start)) -ge "$timeout_s" ]; then
      echo "Timed out after ${timeout_s}s waiting for the Android emulator to finish booting." >&2
      return 1
    fi
    sleep 2
  done
}

disable_emulator_animations() {
  local adb
  adb="$(adb_bin)"
  "$adb" shell settings put global window_animation_scale 0 >/dev/null
  "$adb" shell settings put global transition_animation_scale 0 >/dev/null
  "$adb" shell settings put global animator_duration_scale 0 >/dev/null
  "$adb" shell settings put global stay_on_while_plugged_in 3 >/dev/null
  "$adb" shell settings put system screen_off_timeout 2147483647 >/dev/null
  "$adb" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "$adb" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

emulator_common_args() {
  # Hardware acceleration + SwiftShader GPU. No audio or cameras in Cloud VMs.
  printf '%s\n' \
    -avd "$AVD_NAME" \
    -gpu swiftshader_indirect \
    -no-audio \
    -no-boot-anim \
    -camera-back none \
    -camera-front none \
    -netdelay none \
    -netspeed full
}
