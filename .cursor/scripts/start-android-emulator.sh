#!/usr/bin/env bash
# Start the Golden Stars Cloud Agent AVD if it is not already running.
#
# Used as the `start` command in .cursor/environment.json (per-boot) and is
# safe to re-run from a shell. The emulator process is detached so it keeps
# running after this script returns.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=android-cloud-env.sh
source "$SCRIPT_DIR/android-cloud-env.sh"

log() { echo "[start-android-emulator] $*"; }

start_emulator_process() {
  local bin
  local -a args
  bin="$(emulator_bin)"
  if [ -z "$bin" ]; then
    log "ERROR: emulator binary not found. Re-run bash .cursor/scripts/setup-android-sdk.sh"
    return 1
  fi

  mkdir -p "$(dirname "$EMULATOR_LOG")"
  mapfile -t args < <(emulator_common_args)

  # Headless when no desktop is attached (environment builds). Windowed on
  # Cloud Agent VMs so computer-use / screen recording can see the app.
  if [ -z "${DISPLAY:-}" ]; then
    args+=(-no-window)
  else
    # Shrink the Qt window so a 1080x1920 device fits the 1920x1200 desktop.
    export QT_SCALE_FACTOR="${QT_SCALE_FACTOR:-0.5}"
  fi

  log "Starting emulator AVD=$AVD_NAME DISPLAY=${DISPLAY:-none} accel=tcg"
  nohup "$bin" "${args[@]}" >>"$EMULATOR_LOG" 2>&1 &
  echo $! >"$EMULATOR_PID_FILE"
}

ensure_display() {
  if [ -n "${DISPLAY:-}" ]; then
    return
  fi
  if [ -S /tmp/.X11-unix/X1 ]; then
    export DISPLAY=:1
  elif [ -S /tmp/.X11-unix/X0 ]; then
    export DISPLAY=:0
  fi
}

main() {
  load_android_env
  ensure_display

  local adb
  adb="$(adb_bin)"
  if [ -z "$adb" ] || [ ! -x "$adb" ]; then
    log "ERROR: adb not found under $ANDROID_SDK_ROOT"
    exit 1
  fi

  "$adb" start-server >/dev/null

  if adb_has_device; then
    log "Emulator already booted"
    disable_emulator_animations
    exit 0
  fi

  if emulator_pid_running; then
    log "Emulator process is up; waiting for boot"
  else
    start_emulator_process
  fi

  if ! wait_for_emulator 420; then
    log "ERROR: emulator did not boot. Last log lines:"
    tail -n 80 "$EMULATOR_LOG" 2>/dev/null || true
    exit 1
  fi

  disable_emulator_animations
  log "Emulator ready:"
  "$adb" devices
}

main "$@"
