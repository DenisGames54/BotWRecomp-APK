#!/usr/bin/env bash
# Builds your personal APK.
#   scripts/build_apk.sh /path/to/U-King.rpx    first time (recompiles your dump, builds native, packages)
#   scripts/build_apk.sh                         later runs (reuses work/generated)
#   scripts/build_apk.sh --launcher-only         launcher UI only (no native engine)
set -euo pipefail
cd "$(dirname "$0")/.."
source .botw-apk-env
if [ "${1:-}" != "--launcher-only" ]; then
  scripts/fetch_deps.sh
  if [ ! -d work/generated ]; then scripts/recompile_dump.sh "${1:?first run needs the path to your U-King.rpx}"; fi
  scripts/build_native.sh
fi
[ -x ./gradlew ] || gradle wrapper --gradle-version 8.9
./gradlew --no-daemon assembleDebug
echo; echo "APK: app/build/outputs/apk/debug/app-debug.apk"
