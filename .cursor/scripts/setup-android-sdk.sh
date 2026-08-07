#!/usr/bin/env bash
# Installs the Android command-line SDK tools so Cursor Cloud Agents can
# build, lint and test this Android project without Android Studio.
#
# Idempotent: safe to re-run (used as the `install` step in
# .cursor/environment.json, which may run more than once on cached state).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"
PROFILE_SNIPPET="/etc/profile.d/android-sdk.sh"

log() { echo "[setup-android-sdk] $*"; }

sudo_if_available() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    "$@"
  fi
}

ensure_packages() {
  local missing=()
  command -v unzip >/dev/null 2>&1 || missing+=(unzip)
  command -v curl >/dev/null 2>&1 || missing+=(curl)
  command -v java >/dev/null 2>&1 || missing+=(openjdk-21-jdk-headless)

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

  # Make the SDK available for the remainder of this install script too.
  export ANDROID_SDK_ROOT
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
  export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
}

install_sdk_packages() {
  log "Accepting SDK licenses"
  yes | sdkmanager --licenses >/dev/null 2>&1 || true

  log "Installing platform-tools"
  sdkmanager --install "platform-tools" >/dev/null

  # The exact platform/build-tools packages (e.g. platforms;android-37.0)
  # are intentionally NOT hardcoded here: their naming has changed across
  # releases, and the Android Gradle Plugin auto-downloads whatever
  # compileSdk/buildToolsVersion a module needs the first time it's built,
  # as long as licenses are already accepted (handled above).
}

warm_gradle_build() {
  if [ ! -f "$REPO_ROOT/gradlew" ]; then
    return
  fi
  chmod +x "$REPO_ROOT/gradlew"
  log "Priming Gradle + Android SDK caches (first build downloads the compileSdk platform/build-tools)"
  (cd "$REPO_ROOT" && ./gradlew testDebugUnitTest --stacktrace) || \
    log "WARNING: cache-warming build failed; future agents will retry on first build"
}

main() {
  ensure_packages
  install_cmdline_tools
  persist_env
  install_sdk_packages
  warm_gradle_build

  log "Android SDK ready at $ANDROID_SDK_ROOT"
}

main "$@"
