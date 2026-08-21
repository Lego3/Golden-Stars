#!/usr/bin/env bash
# Build the debug APK, install it on the Cloud Agent emulator, and launch
# the Golden Stars hub. Safe to re-run after code changes.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=android-cloud-env.sh
source "$SCRIPT_DIR/android-cloud-env.sh"

log() { echo "[run-android-app] $*"; }

main() {
  load_android_env

  if ! adb_has_device; then
    log "No emulator device yet; starting it"
    bash "$SCRIPT_DIR/start-android-emulator.sh"
  else
    wait_for_emulator 420
  fi

  chmod +x "$REPO_ROOT/gradlew"
  log "Installing debug APK"
  (cd "$REPO_ROOT" && ./gradlew installDebug --stacktrace)

  log "Launching $LAUNCHER_ACTIVITY"
  "$(adb_bin)" shell am start -n "$LAUNCHER_ACTIVITY" >/dev/null
  sleep 6
  if "$(adb_bin)" shell dumpsys window 2>/dev/null | grep -q 'Application Not Responding'; then
    log "Dismissing System UI ANR (common on TCG) by choosing Wait"
    "$(adb_bin)" shell input keyevent KEYCODE_DPAD_DOWN >/dev/null
    "$(adb_bin)" shell input keyevent KEYCODE_ENTER >/dev/null
    sleep 8
  fi
  log "App launched"
}

main "$@"
