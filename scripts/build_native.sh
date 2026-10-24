#!/usr/bin/env bash
# Builds libmain.so + libSDL3.so with the NDK and copies them into app/src/main/jniLibs/arm64-v8a.
# Env: JOBS (default 3; the generated guest code is memory hungry), BOTW_CPU (default cortex-a78: no SVE), BOTW_PGO=gen|use + BOTW_PGO_DATA
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/.botw-apk-env"
cmake -S "$ROOT/native" -B "$ROOT/build/native" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release \
  -DBOTW_CPU="${BOTW_CPU:-cortex-a78}" ${BOTW_PGO:+-DBOTW_PGO=$BOTW_PGO} ${BOTW_PGO_DATA:+-DBOTW_PGO_DATA=$BOTW_PGO_DATA}
ninja -C "$ROOT/build/native" -j"${JOBS:-3}" main
ls -lh "$ROOT/app/src/main/jniLibs/arm64-v8a/"
