#!/bin/bash
set -euo pipefail

# 段階① verify: android 版の lint + 単体テスト + debug ビルドを一括実行する。
# 外部作用はない(ローカルの Gradle 実行のみ)。成功時に
# build/agent-verify-stamp へコミットハッシュと実行時刻を記録し、
# pre-push hook がこれを確認する。
#
# 環境変数 ANDROID_HOME / JAVA_HOME が未設定の場合は、本機の既定値を使う。

DIR="$(cd "$(dirname "$0")/../.." && pwd)"

export ANDROID_HOME="${ANDROID_HOME:-/Users/tecogonaz/Library/Android/sdk}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

cd "$DIR"
./gradlew lintDebug testDebugUnitTest assembleDebug "$@"

mkdir -p build
printf '%s %s\n' "$(git rev-parse HEAD)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > build/agent-verify-stamp
echo "verify passed: $(cat build/agent-verify-stamp)"
