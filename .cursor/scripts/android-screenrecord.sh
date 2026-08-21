#!/usr/bin/env bash
# Record the emulator screen with adb and pull an MP4.
#
# Usage: android-screenrecord.sh [seconds] [output.mp4]
# Default duration: 10 seconds. Default output follows android-screenshot.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=android-cloud-env.sh
source "$SCRIPT_DIR/android-cloud-env.sh"

log() { echo "[android-screenrecord] $*"; }

default_output() {
  local ts
  ts="$(date +%Y%m%d_%H%M%S)"
  if [ -d /opt/cursor/artifacts ]; then
    echo "/opt/cursor/artifacts/golden_stars_${ts}.mp4"
  else
    echo "golden_stars_${ts}.mp4"
  fi
}

main() {
  load_android_env
  local seconds="${1:-10}"
  local out="${2:-$(default_output)}"
  local adb remote="/data/local/tmp/golden_stars_record.mp4"
  adb="$(adb_bin)"

  if ! adb_has_device; then
    log "ERROR: no emulator device. Run bash .cursor/scripts/start-android-emulator.sh first."
    exit 1
  fi

  if ! [[ "$seconds" =~ ^[0-9]+$ ]] || [ "$seconds" -lt 1 ] || [ "$seconds" -gt 180 ]; then
    log "ERROR: duration must be an integer 1–180 seconds"
    exit 1
  fi

  mkdir -p "$(dirname "$out")"
  log "Recording ${seconds}s to $out"
  "$adb" shell rm -f "$remote" >/dev/null 2>&1 || true
  "$adb" shell screenrecord --time-limit "$seconds" "$remote"
  "$adb" pull "$remote" "$out" >/dev/null
  "$adb" shell rm -f "$remote" >/dev/null 2>&1 || true

  if [ ! -s "$out" ]; then
    log "ERROR: recording produced an empty file at $out"
    exit 1
  fi

  log "Wrote $out ($(wc -c <"$out") bytes)"
}

main "$@"
