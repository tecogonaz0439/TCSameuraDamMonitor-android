#!/bin/bash
set -euo pipefail

# 段階② build: Play Console アップロード用の署名付き Release AAB を作成する。
# 外部作用はない(ローカルの Gradle 実行とファイル出力のみ)。アップロードは
# release-play-upload.sh が担う。
# 旧 android_build_aab スキルのロジックを移植したものであり、挙動は同一である。

export ANDROID_HOME="${ANDROID_HOME:-/Users/tecogonaz/Library/Android/sdk}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

ANDROID_PROJECT="$(cd "$(dirname "$0")/../.." && pwd)"
APP_NAME="TCSameuraDamMonitor"
OUTPUT_DIR="$ANDROID_PROJECT/build/cicd/aab"
SKIP_VERIFY="false"

usage() {
    cat <<'EOF'
Usage: build-aab.sh [--output <dir>] [--skip-verify]

  --output DIR     Output directory (default: <repo>/build/cicd/aab)
  --skip-verify    Skip jarsigner signature verification of the built .aab
EOF
    exit 1
}

log() { echo "[build-aab] $*" >&2; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --output) OUTPUT_DIR="$2"; shift ;;
        --skip-verify) SKIP_VERIFY="true" ;;
        -h|--help) usage ;;
        *) usage ;;
    esac
    shift
done

ENV_FILE="$ANDROID_PROJECT/keystore/upload-env.sh"
KEYSTORE_FILE="$ANDROID_PROJECT/keystore/tcsameura-upload.jks"

if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    log "Loaded signing environment: $ENV_FILE"
else
    log "Note: $ENV_FILE not found. Using environment variables if already set."
fi

if [[ -z "${TCSAMEURA_UPLOAD_STORE_PASSWORD:-}" ]]; then
    log "Error: TCSAMEURA_UPLOAD_STORE_PASSWORD is not set."
    log "Load the release signing environment before running this script:"
    log "  source android/TCSameuraDamMonitor/keystore/upload-env.sh"
    log "or export TCSAMEURA_UPLOAD_STORE_PASSWORD (and optionally TCSAMEURA_UPLOAD_KEY_PASSWORD) directly."
    log "Refusing to produce an unsigned AAB. See docs/android/build-and-verify.md 'release signing' section."
    exit 1
fi

if [[ ! -f "$KEYSTORE_FILE" ]]; then
    log "Error: upload keystore not found: $KEYSTORE_FILE"
    log "Restore keystore/tcsameura-upload.jks from your backup."
    log "See docs/android/build-and-verify.md 'release signing' section."
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

AAB_SRC="$ANDROID_PROJECT/app/build/outputs/bundle/release/app-release.aab"
AAB_DST="$OUTPUT_DIR/$APP_NAME-release.aab"
GRADLE_LOG="$OUTPUT_DIR/bundleRelease.log"

log "Running bundleRelease (log: $GRADLE_LOG)..."
if ! (cd "$ANDROID_PROJECT" && ./gradlew bundleRelease) >"$GRADLE_LOG" 2>&1; then
    log "Error: bundleRelease failed. Last lines:"
    tail -30 "$GRADLE_LOG" >&2 || true
    exit 1
fi
log "bundleRelease succeeded."

if [[ ! -f "$AAB_SRC" ]]; then
    log "Error: expected AAB not found: $AAB_SRC"
    exit 1
fi
cp "$AAB_SRC" "$AAB_DST"
log "AAB copied: $AAB_DST ($(du -h "$AAB_DST" | cut -f1))"

if [[ "$SKIP_VERIFY" != "true" ]]; then
    VERIFY_LOG="$OUTPUT_DIR/jarsigner-verify.log"
    log "Verifying signature with jarsigner (log: $VERIFY_LOG)..."
    if ! jarsigner -verify "$AAB_DST" >"$VERIFY_LOG" 2>&1; then
        log "Error: jarsigner verification failed. See: $VERIFY_LOG"
        tail -20 "$VERIFY_LOG" >&2 || true
        log "Removing unverified AAB: $AAB_DST"
        rm -f "$AAB_DST"
        exit 1
    fi
    grep -E "jar verified|jarが検証されました" "$VERIFY_LOG" | tail -1 >&2 || true
    log "Signature verification succeeded."
else
    log "Skipping jarsigner verification (--skip-verify)."
fi

log "Done. Signed AAB: $AAB_DST"
echo "$AAB_DST"
exit 0
