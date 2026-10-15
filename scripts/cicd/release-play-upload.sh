#!/bin/bash
set -euo pipefail

# 段階③ release(許可制: 提案 1.3 の A3): GPP 経由で Release AAB を
# Play Console の指定トラックへアップロードする。実行前に制作者の許可を得ること。
# 対話確認を内蔵し、--yes を付けた場合のみ省略する。
# 旧 android_upload_play_internal スキルのロジックを移植したものである。

export ANDROID_HOME="${ANDROID_HOME:-/Users/tecogonaz/Library/Android/sdk}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

ANDROID_PROJECT="$(cd "$(dirname "$0")/../.." && pwd)"
OUTPUT_DIR="$ANDROID_PROJECT/build/cicd/play-upload"
TRACK="internal"
STATUS="completed"
ASSUME_YES="false"

usage() {
    cat <<'EOF'
Usage: release-play-upload.sh [--track internal|alpha|beta|production] [--status completed|draft]
                              [--output <dir>] [--yes]

  --track TRACK      Target Play Console track (default: internal)
  --status STATUS    Release status (default: completed)
  --output DIR       Log output directory (default: <repo>/build/cicd/play-upload)
  --yes              Skip the interactive confirmation (requires prior consent from the developer)
EOF
    exit 1
}

log() { echo "[release-play-upload] $*" >&2; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --track) TRACK="$2"; shift ;;
        --status) STATUS="$2"; shift ;;
        --output) OUTPUT_DIR="$2"; shift ;;
        --yes) ASSUME_YES="true" ;;
        -h|--help) usage ;;
        *) usage ;;
    esac
    shift

done

if [[ "$ASSUME_YES" != "true" ]]; then
    log "This will upload the Release AAB to the Play Console '$TRACK' track (status: $STATUS)."
    read -r -p "Proceed with the upload? [y/N] " REPLY
    if [[ ! "$REPLY" =~ ^[yY]$ ]]; then
        log "Aborted by user."
        exit 1
    fi
fi

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
    log "Refusing to publish an unsigned bundle."
    exit 1
fi

if [[ ! -f "$KEYSTORE_FILE" ]]; then
    log "Error: upload keystore not found: $KEYSTORE_FILE"
    log "Restore keystore/tcsameura-upload.jks from your backup."
    exit 1
fi

if [[ -z "${TCSAMEURA_PLAY_SERVICE_ACCOUNT_JSON:-}" ]]; then
    TCSAMEURA_PLAY_SERVICE_ACCOUNT_JSON="$ANDROID_PROJECT/keystore/play-service-account.json"
fi

if [[ ! -f "$TCSAMEURA_PLAY_SERVICE_ACCOUNT_JSON" ]]; then
    log "Error: service account JSON not found: $TCSAMEURA_PLAY_SERVICE_ACCOUNT_JSON"
    log "Create a service account linked to the Google Play Android Developer API,"
    log "place its JSON key at android/TCSameuraDamMonitor/keystore/play-service-account.json"
    log "(or export TCSAMEURA_PLAY_SERVICE_ACCOUNT_JSON=/path/to/key.json)."
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

GRADLE_LOG="$OUTPUT_DIR/publishReleaseBundle.log"

log "Publishing release bundle to '$TRACK' track (status: $STATUS, log: $GRADLE_LOG)..."
# --rerun-tasks: GPP の publishReleaseBundle は前回成功時に Gradle キャッシュで
# UP-TO-DATE スキップされ、commit だけが空編集で「成功」してしまうことがあるため強制再実行する
if ! (cd "$ANDROID_PROJECT" && ./gradlew publishReleaseBundle --rerun-tasks \
        -Ptrack="$TRACK" \
        -PplayReleaseStatus="$STATUS" \
        -PplayServiceAccountJson="$TCSAMEURA_PLAY_SERVICE_ACCOUNT_JSON") >"$GRADLE_LOG" 2>&1; then
    log "Error: publishReleaseBundle failed. Last lines:"
    tail -30 "$GRADLE_LOG" >&2 || true
    exit 1
fi
log "publishReleaseBundle succeeded."

AAB="$ANDROID_PROJECT/app/build/outputs/bundle/release/app-release.aab"
if [[ -f "$AAB" ]]; then
    log "Published bundle: $AAB"
fi

log "Done. Check Play Console -> Testing -> '$TRACK' track."
exit 0
