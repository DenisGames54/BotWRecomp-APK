#!/usr/bin/env bash
# Fase 1: compila so o launcher. (Fase 2 acrescenta: recompilar o SEU dump -> libmain.so -> APK pessoal.)
set -euo pipefail
cd "$(dirname "$0")/.."
source .botw-apk-env
[ -x ./gradlew ] || gradle wrapper --gradle-version 8.9
./gradlew --no-daemon assembleDebug
echo; echo "APK: app/build/outputs/apk/debug/app-debug.apk"
