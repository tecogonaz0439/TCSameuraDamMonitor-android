#!/bin/bash
set -euo pipefail

# Debug APK をエミュレータ / 実機へビルド・インストールする。
# - エミュレータへのインストールは許可不要。
# - 実機へのインストールは許可制(提案 1.3 の A8)。実機には Play Console 配布の
#   署名済みバイナリがインストールされている可能性があるため、実行前に上書きの
#   同意を確認する。リリース署名バイナリが検出された場合は、アンインストールの
#   同意を別途確認してからインストールする。
# 対話確認は --yes を付けた場合のみ省略する(エージェントは事前に同意を得てから付ける)。
# android 版と sudmaintainer で共通のスクリプトであり、--package / --apk で
# applicationId と APK パスを切り替えられる。

export ANDROID_HOME="${ANDROID_HOME:-/Users/tecogonaz/Library/Android/sdk}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

ANDROID_PROJECT="$(cd "$(dirname "$0")/../.." && pwd)"
APK_PATH="$ANDROID_PROJECT/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="net.tecogonaz.tcsameuradammonitor"
ASSUME_YES="false"

usage() {
    cat <<'EOF'
Usage: install-android.sh [--all] [--device <serial>] [--build-only] [--list]
                          [--package <applicationId>] [--apk <path>] [--yes]

  --all                Install to all booted emulators and connected devices
  --device SERIAL      Install to a specific device by serial (e.g. emulator-5554)
  --build-only         Build only, do not install
  --list               List available devices only
  --package PACKAGE    applicationId of the app to launch
                       (default: net.tecogonaz.tcsameuradammonitor;
                        sudmaintainer: net.tecogonaz.tcsameuradammonitormaintainer)
  --apk PATH           Debug APK path (default: <repo>/app/build/outputs/apk/debug/app-debug.apk)
  --yes                Skip the interactive consent for physical devices
                       (requires prior consent from the developer)
EOF
    exit 1
}

log() { echo "[install-android] $*" >&2; }

list_devices() {
    log "Available devices:"
    adb devices -l 2>/dev/null | tail -n +2 | grep -v "^$" || echo "(none)"
}

build() {
    log "Building debug APK..."
    cd "$ANDROID_PROJECT"
    ./gradlew assembleDebug 2>&1 | grep -E "(BUILD SUCCESSFUL|BUILD FAILED|error:)" | tail -5
    if [[ ! -f "$APK_PATH" ]]; then
        log "Error: APK not found at $APK_PATH"
        exit 1
    fi
    log "APK built: $APK_PATH"
}

install_to_device() {
    local serial="$1"
    log "Installing to device: $serial"
    adb -s "$serial" install -r "$APK_PATH"
}

launch_on_device() {
    local serial="$1"
    log "Launching on device: $serial"
    adb -s "$serial" shell am start -n "$PACKAGE_NAME/.MainActivity"
}

get_all_device_serials() {
    adb devices 2>/dev/null | tail -n +2 | grep -v "^$" | awk '{print $1}'
}

is_emulator() {
    local serial="$1"
    adb -s "$serial" shell getprop ro.build.characteristics 2>/dev/null | grep -q "emulator" && return 0
    # fallback: check if serial starts with emulator-
    [[ "$serial" == emulator-* ]] && return 0
    return 1
}

confirm_physical_install() {
    # A8: 実機へのインストールは無条件に同意を確認する。
    if [[ "$ASSUME_YES" == "true" ]]; then
        return 0
    fi
    log "A physical device was selected. A debug build will be installed over the existing app."
    log "The device may already have a Play Console signed binary installed."
    read -r -p "Install (overwriting the existing app if any)? [y/N] " REPLY
    if [[ ! "$REPLY" =~ ^[yY]$ ]]; then
        log "Aborted by user."
        exit 1
    fi
}

# --- Main ---
MODE=""
DEVICE_SERIAL=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --all) MODE="all" ;;
        --device) MODE="device"; DEVICE_SERIAL="$2"; shift ;;
        --build-only) MODE="build-only" ;;
        --list) MODE="list" ;;
        --package) PACKAGE_NAME="$2"; shift ;;
        --apk) APK_PATH="$2"; shift ;;
        --yes) ASSUME_YES="true" ;;
        *) usage ;;
    esac
    shift
done

if [[ -z "$MODE" ]]; then
    usage
fi

if [[ "$MODE" == "list" ]]; then
    list_devices
    exit 0
fi

if [[ "$MODE" == "build-only" ]]; then
    build
    exit 0
fi

build

if [[ "$MODE" == "device" ]]; then
    ALL_SERIALS=$(get_all_device_serials)
    if ! echo "$ALL_SERIALS" | grep -qFx "$DEVICE_SERIAL"; then
        log "Error: Device not found or not available: $DEVICE_SERIAL"
        list_devices
        exit 1
    fi
    if ! is_emulator "$DEVICE_SERIAL"; then
        confirm_physical_install
    fi
    install_to_device "$DEVICE_SERIAL"
    if is_emulator "$DEVICE_SERIAL"; then
        launch_on_device "$DEVICE_SERIAL"
    else
        log "Installed to physical device. Unlock and tap the app icon to launch."
    fi
    exit 0
fi

if [[ "$MODE" == "all" ]]; then
    ALL_SERIALS=$(get_all_device_serials)
    if [[ -z "$ALL_SERIALS" ]]; then
        log "No available devices found."
        list_devices
        exit 1
    fi
    PHYSICAL_COUNT=0
    for SERIAL in $ALL_SERIALS; do
        if ! is_emulator "$SERIAL"; then
            PHYSICAL_COUNT=$((PHYSICAL_COUNT + 1))
        fi
    done
    if [[ "$PHYSICAL_COUNT" -gt 0 ]]; then
        confirm_physical_install
    fi
    INSTALLED=0
    for SERIAL in $ALL_SERIALS; do
        install_to_device "$SERIAL"
        if is_emulator "$SERIAL"; then
            launch_on_device "$SERIAL"
        else
            log "Installed to $SERIAL (physical device). Unlock and tap to launch."
        fi
        INSTALLED=$((INSTALLED + 1))
    done
    log "Installed to $INSTALLED device(s)."
    exit 0
fi
