#!/usr/bin/env bash
# Installs the Android command-line SDK, emulator, and a Pixel-sized API 34
# AVD so Cursor Cloud Agents can build, run, screenshot, and record this app
# without Android Studio.
#
# Idempotent: safe to re-run (used as the `install` step in
# .cursor/environment.json, which may run more than once on cached state).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=android-cloud-env.sh
source "$SCRIPT_DIR/android-cloud-env.sh"

CMDLINE_TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"
PROFILE_SNIPPET="/etc/profile.d/android-sdk.sh"

log() { echo "[setup-android-sdk] $*"; }

ensure_packages() {
  local missing=()
  command -v unzip >/dev/null 2>&1 || missing+=(unzip)
  command -v curl >/dev/null 2>&1 || missing+=(curl)
  command -v java >/dev/null 2>&1 || missing+=(openjdk-21-jdk-headless)

  local deb
  for deb in \
    cpu-checker \
    libpulse0 \
    libnss3 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxi6 \
    libxtst6 \
    libglu1-mesa \
    libxcb-cursor0 \
    libxcb-xinerama0 \
    libxrandr2; do
    dpkg -s "$deb" >/dev/null 2>&1 || missing+=("$deb")
  done

  if dpkg -s libasound2t64 >/dev/null 2>&1 || dpkg -s libasound2 >/dev/null 2>&1; then
    :
  else
    if apt-cache show libasound2t64 >/dev/null 2>&1; then
      missing+=(libasound2t64)
    else
      missing+=(libasound2)
    fi
  fi

  if [ "${#missing[@]}" -gt 0 ]; then
    log "Installing missing packages: ${missing[*]}"
    export DEBIAN_FRONTEND=noninteractive
    sudo_if_available apt-get update -qq
    sudo_if_available apt-get install -y -qq "${missing[@]}"
  fi
}

install_cmdline_tools() {
  if [ -x "$CMDLINE_TOOLS_DIR/bin/sdkmanager" ]; then
    log "Android command-line tools already installed at $CMDLINE_TOOLS_DIR"
    return
  fi

  log "Resolving latest Android command-line tools download URL"
  local fallback_url="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
  local tools_url
  tools_url="$(curl -fsSL https://developer.android.com/studio 2>/dev/null \
    | grep -Eo 'https://dl\.google\.com/android/repository/commandlinetools-linux-[0-9]+_latest\.zip' \
    | head -n1 || true)"
  tools_url="${tools_url:-$fallback_url}"

  log "Downloading command-line tools from $tools_url"
  local tmp_zip
  tmp_zip="$(mktemp --suffix=.zip)"
  curl -fsSL "$tools_url" -o "$tmp_zip"

  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  local extract_dir
  extract_dir="$(mktemp -d)"
  unzip -q -o "$tmp_zip" -d "$extract_dir"
  rm -rf "$CMDLINE_TOOLS_DIR"
  mv "$extract_dir/cmdline-tools" "$CMDLINE_TOOLS_DIR"
  rm -rf "$tmp_zip" "$extract_dir"
  log "Installed command-line tools to $CMDLINE_TOOLS_DIR"
}

persist_env() {
  local snippet
  snippet="$(cat <<EOF
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_AVD_HOME="\$HOME/.android/avd"
export PATH="\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools:\$ANDROID_SDK_ROOT/emulator:\$PATH"
EOF
)"

  if sudo_if_available test -w /etc/profile.d 2>/dev/null || [ -w /etc/profile.d ]; then
    echo "$snippet" | sudo_if_available tee "$PROFILE_SNIPPET" >/dev/null
    log "Wrote $PROFILE_SNIPPET so future shells have the SDK on PATH"
  fi

  for rc in "$HOME/.bashrc" "$HOME/.profile"; do
    if [ -f "$rc" ] && ! grep -q "ANDROID_SDK_ROOT" "$rc"; then
      {
        echo ""
        echo "# Added by .cursor/scripts/setup-android-sdk.sh"
        echo "$snippet"
      } >>"$rc"
    fi
  done

  load_android_env
}

install_sdk_packages() {
  log "Accepting SDK licenses"
  yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true

  log "Installing platform-tools, emulator, and $SYSTEM_IMAGE"
  # `yes` exits 141 (SIGPIPE) when sdkmanager closes stdin; ignore that and
  # keep sdkmanager's own status under pipefail.
  local sdk_status
  set +o pipefail
  yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --install \
    "platform-tools" \
    "emulator" \
    "$SYSTEM_IMAGE"
  sdk_status=${PIPESTATUS[1]}
  set -o pipefail
  if [ "$sdk_status" -ne 0 ]; then
    log "ERROR: sdkmanager install failed with status $sdk_status"
    exit 1
  fi

  log "Re-accepting licenses after package install"
  yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true

  if [ ! -x "$ANDROID_SDK_ROOT/emulator/emulator" ]; then
    log "ERROR: emulator binary missing after sdkmanager install"
    exit 1
  fi
}

set_avd_ini() {
  local file="$1"
  local key="$2"
  local value="$3"
  if grep -q "^${key}=" "$file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$file"
  else
    echo "${key}=${value}" >>"$file"
  fi
}

create_avd() {
  local avd_dir="$ANDROID_AVD_HOME/${AVD_NAME}.avd"
  local ini="$avd_dir/config.ini"

  if [ ! -d "$avd_dir" ]; then
    log "Creating AVD $AVD_NAME ($SYSTEM_IMAGE)"
    if avdmanager list device 2>/dev/null | grep -q 'pixel_5'; then
      printf 'no\n' | avdmanager create avd --force \
        --name "$AVD_NAME" \
        --package "$SYSTEM_IMAGE" \
        --device pixel_5
    else
      printf 'no\n' | avdmanager create avd --force \
        --name "$AVD_NAME" \
        --package "$SYSTEM_IMAGE"
    fi
  else
    log "AVD $AVD_NAME already exists"
  fi

  set_avd_ini "$ini" hw.keyboard yes
  set_avd_ini "$ini" hw.lcd.width "$AVD_LCD_WIDTH"
  set_avd_ini "$ini" hw.lcd.height "$AVD_LCD_HEIGHT"
  set_avd_ini "$ini" hw.lcd.density "$AVD_LCD_DENSITY"
  set_avd_ini "$ini" hw.gpu.enabled yes
  set_avd_ini "$ini" hw.gpu.mode swiftshader_indirect
  set_avd_ini "$ini" hw.ramSize 2048
  set_avd_ini "$ini" showDeviceFrame no
  set_avd_ini "$ini" firstboot.bootFromDownloadableSnapshot no
  set_avd_ini "$ini" fastboot.forceFastBoot no
}

warm_gradle_build() {
  if [ ! -f "$REPO_ROOT/gradlew" ]; then
    return
  fi
  chmod +x "$REPO_ROOT/gradlew"
  log "Priming Gradle + Android SDK caches and assembling a debug APK"
  (cd "$REPO_ROOT" && ./gradlew testDebugUnitTest assembleDebug --stacktrace) || \
    log "WARNING: cache-warming build failed; future agents will retry on first build"
}

first_boot_emulator() {
  local apk
  local -a args
  local bin

  if emulator_pid_running || adb_has_device; then
    log "Emulator already running; skipping first-boot snapshot"
    return
  fi

  bin="$(emulator_bin)"
  if [ -z "$bin" ]; then
    log "WARNING: emulator binary missing; skipping first-boot snapshot"
    return
  fi

  mkdir -p "$(dirname "$EMULATOR_LOG")"
  mapfile -t args < <(emulator_common_args)
  args+=(-no-window -no-snapshot-load)

  log "Cold-booting $AVD_NAME with TCG so later agents can load a snapshot"
  nohup "$bin" "${args[@]}" >>"$EMULATOR_LOG" 2>&1 &
  echo $! >"$EMULATOR_PID_FILE"

  if ! wait_for_emulator 600; then
    log "WARNING: first-boot did not finish; later agents will cold-boot. Last log lines:"
    tail -n 40 "$EMULATOR_LOG" 2>/dev/null || true
    stop_emulator
    return
  fi

  disable_emulator_animations

  shopt -s nullglob
  apk=""
  for apk in "$REPO_ROOT"/app/build/outputs/apk/debug/*.apk; do
    break
  done
  shopt -u nullglob
  if [ -n "$apk" ] && [ -f "$apk" ]; then
    log "Installing $apk on the AVD snapshot"
    "$(adb_bin)" install -r "$apk" >/dev/null || \
      log "WARNING: could not preinstall debug APK; agents can install later"
  fi

  log "Stopping emulator to save the AVD snapshot"
  stop_emulator
  log "First-boot snapshot saved"
}

main() {
  ensure_packages
  install_cmdline_tools
  persist_env
  install_sdk_packages
  create_avd
  warm_gradle_build
  first_boot_emulator

  log "Android SDK ready at $ANDROID_SDK_ROOT (AVD $AVD_NAME)"
}

main "$@"
