#!/usr/bin/env bash
# Capture a PNG of the running emulator via adb (full device resolution).
#
# Usage: android-screenshot.sh [output.png]
# Default: /opt/cursor/artifacts/golden_stars_<timestamp>.png when that
# directory exists, otherwise a file in the current directory.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=android-cloud-env.sh
source "$SCRIPT_DIR/android-cloud-env.sh"

log() { echo "[android-screenshot] $*"; }

default_output() {
  local ts
  ts="$(date +%Y%m%d_%H%M%S)"
  if [ -d /opt/cursor/artifacts ]; then
    echo "/opt/cursor/artifacts/golden_stars_${ts}.png"
  else
    echo "golden_stars_${ts}.png"
  fi
}

main() {
  load_android_env
  local out="${1:-$(default_output)}"
  local adb
  adb="$(adb_bin)"

  if ! adb_has_device; then
    log "ERROR: no emulator device. Run bash .cursor/scripts/start-android-emulator.sh first."
    exit 1
  fi

  mkdir -p "$(dirname "$out")"
  "$adb" exec-out screencap -p >"$out"

  if [ ! -s "$out" ]; then
    log "ERROR: screencap produced an empty file at $out"
    exit 1
  fi

  log "Wrote $out ($(wc -c <"$out") bytes)"
}

main "$@"
