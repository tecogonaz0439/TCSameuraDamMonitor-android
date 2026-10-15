#!/bin/bash
set -euo pipefail

# 段階② build: Debug / Release APK を作成する。外部作用はない。
# Release APK の事後署名(zipalign + apksigner)も --sign-keystore で可能である。
# 旧 android_build_apk スキルのロジックを移植したものであり、挙動は同一である。

export ANDROID_HOME="${ANDROID_HOME:-/Users/tecogonaz/Library/Android/sdk}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

ANDROID_PROJECT="$(cd "$(dirname "$0")/../.." && pwd)"

APP_NAME="TCSameuraDamMonitor"
ONLY="both"
OUTPUT_DIR="$ANDROID_PROJECT/build/cicd/apk"
SIGN_KEYSTORE=""
KS_ALIAS=""
KS_PASS=""
KS_KEY_PASS=""

usage() {
    cat <<'EOF'
Usage: build-apk.sh [--only debug|release] [--output <dir>]
                    [--sign-keystore <path>] [--ks-key-alias <name>]
                    [--ks-pass <pass>] [--ks-key-pass <pass>]

  --only debug|release   Build only one of the variants (default: both)
  --output DIR           Output directory (default: <repo>/build/cicd/apk)
  --sign-keystore PATH   Sign the release APK after build via zipalign + apksigner
  --ks-key-alias NAME    Key alias inside the keystore (required with --sign-keystore)
  --ks-pass PASS         Keystore password (optional; apksigner prompts if omitted)
  --ks-key-pass PASS     Key password (optional; falls back to --ks-pass / prompt)
EOF
    exit 1
}

log() { echo "[build-apk] $*" >&2; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --only) ONLY="$2"; shift ;;
        --output) OUTPUT_DIR="$2"; shift ;;
        --sign-keystore) SIGN_KEYSTORE="$2"; shift ;;
        --ks-key-alias) KS_ALIAS="$2"; shift ;;
        --ks-pass) KS_PASS="$2"; shift ;;
        --ks-key-pass) KS_KEY_PASS="$2"; shift ;;
        -h|--help) usage ;;
        *) usage ;;
    esac
    shift
done

case "$ONLY" in
    both|debug|release) ;;
    *) log "Error: invalid --only value: $ONLY"; usage ;;
esac

if [[ -n "$SIGN_KEYSTORE" && -z "$KS_ALIAS" ]]; then
    log "Error: --sign-keystore requires --ks-key-alias"
    exit 1
fi

if [[ -n "$SIGN_KEYSTORE" && ! -f "$SIGN_KEYSTORE" ]]; then
    log "Error: keystore not found: $SIGN_KEYSTORE"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

find_build_tool() {
    local tool="$1"
    local dir
    dir=$(ls -1d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)
    if [[ -z "$dir" || ! -x "$dir/$tool" ]]; then
        log "Error: $tool not found under $ANDROID_HOME/build-tools"
        return 1
    fi
    echo "$dir/$tool"
}

run_gradle() {
    local task="$1" logfile="$2"
    log "Running ./$task (log: $logfile)"
    cd "$ANDROID_PROJECT"
    if ! ./gradlew "$task" >"$OUTPUT_DIR/$logfile" 2>&1; then
        log "Error: $task failed. Last lines:"
        tail -30 "$OUTPUT_DIR/$logfile" >&2 || true
        exit 1
    fi
    log "$task succeeded."
}

copy_apk() {
    local src="$1" dst="$2"
    cp "$src" "$dst"
    log "APK copied: $dst ($(du -h "$dst" | cut -f1))"
}

build_debug() {
    run_gradle assembleDebug assembleDebug.log
    copy_apk "$ANDROID_PROJECT/app/build/outputs/apk/debug/app-debug.apk" \
        "$OUTPUT_DIR/$APP_NAME-debug.apk"
}

build_release() {
    run_gradle assembleRelease assembleRelease.log
    RELEASE_SRC=$(ls "$ANDROID_PROJECT"/app/build/outputs/apk/release/*.apk | head -1)
    if [[ -n "$SIGN_KEYSTORE" ]]; then
        sign_release "$RELEASE_SRC"
    else
        copy_apk "$RELEASE_SRC" "$OUTPUT_DIR/$APP_NAME-release-unsigned.apk"
        log "Note: release APK is unsigned and cannot be installed directly. Use --sign-keystore to sign it."
    fi
}

sign_release() {
    local unsigned="$1"
    local zipalign apksigner aligned signed
    zipalign=$(find_build_tool zipalign)
    apksigner=$(find_build_tool apksigner)
    aligned="$OUTPUT_DIR/.aligned-release.apk"
    signed="$OUTPUT_DIR/$APP_NAME-release-signed.apk"
    log "Aligning release APK..."
    "$zipalign" -f -p 4 "$unsigned" "$aligned"
    log "Signing release APK with $SIGN_KEYSTORE (alias: $KS_ALIAS)..."
    local pass_args=()
    if [[ -n "$KS_PASS" ]]; then
        pass_args+=(--ks-pass "pass:$KS_PASS")
    fi
    if [[ -n "$KS_KEY_PASS" ]]; then
        pass_args+=(--ks-key-pass "pass:$KS_KEY_PASS")
    fi
    "$apksigner" sign --ks "$SIGN_KEYSTORE" --ks-key-alias "$KS_ALIAS" \
        "${pass_args[@]+"${pass_args[@]}"}" --out "$signed" "$aligned"
    rm -f "$aligned"
    "$apksigner" verify "$signed"
    log "Release APK signed: $signed"
}

write_build_info() {
    local aapt apk
    {
        echo "build date : $(date '+%Y-%m-%d %H:%M:%S %z')"
        if [[ "$ONLY" != release ]]; then
            apk="$OUTPUT_DIR/$APP_NAME-debug.apk"
            echo "--- $apk ---"
            if aapt=$(find_build_tool aapt); then
                "$aapt" dump badging "$apk" | grep "^package:" | head -1 || true
            fi
            shasum -a 256 "$apk"
        fi
        if [[ "$ONLY" != debug ]]; then
            apk="$OUTPUT_DIR/$APP_NAME-release-signed.apk"
            [[ -f "$apk" ]] || apk="$OUTPUT_DIR/$APP_NAME-release-unsigned.apk"
            echo "--- $apk ---"
            if aapt=$(find_build_tool aapt); then
                "$aapt" dump badging "$apk" | grep "^package:" | head -1 || true
            fi
            shasum -a 256 "$apk"
        fi
    } >"$OUTPUT_DIR/build-info.txt"
    log "Build info written: $OUTPUT_DIR/build-info.txt"
}

if [[ "$ONLY" != release ]]; then
    build_debug
fi
if [[ "$ONLY" != debug ]]; then
    build_release
fi
write_build_info

log "Done. Artifacts in $OUTPUT_DIR:"
ls -1 "$OUTPUT_DIR" | grep -E '\.(apk|txt|log)$' | sed 's/^/  /' >&2
