#!/usr/bin/env bash
# Downloads third-party sources into deps/ (idempotent) and copies SDL3's Java glue into the app.
# SDL3 / fmt / zstd versions and SHA-256 are the ones pinned by the engine's own ios/scripts/build_deps.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPS="$ROOT/deps"; mkdir -p "$DEPS/downloads"

fetch() {  # name version url sha256(optional)
  local name=$1
  local ver=$2
  local url=$3
  local sha=${4:-}
  local src="$DEPS/$name-$ver"
  local file="$DEPS/downloads/$(basename "$url")"
  [ -d "$src" ] && return 0
  echo ">> $name $ver"
  [ -f "$file" ] || curl -fL --retry 3 -o "$file" "$url"
  local got; got=$(sha256sum "$file" | cut -d' ' -f1)
  if [ -n "$sha" ]; then
    [ "$got" = "$sha" ] || { echo "SHA-256 mismatch for $file ($got)"; rm -f "$file"; exit 1; }
  else
    echo "   (no pinned SHA-256 for $name; downloaded file is $got - pin it in this script)"
  fi
  local tmp; tmp=$(mktemp -d)
  case "$file" in *.zip) unzip -q "$file" -d "$tmp" ;; *) tar -xzf "$file" -C "$tmp" ;; esac
  mv "$tmp"/* "$src"; rmdir "$tmp"
}

fetch SDL3 3.4.18 https://github.com/libsdl-org/SDL/releases/download/release-3.4.18/SDL3-3.4.18.tar.gz 9c75cf16330322c217dedd2e0609f1124f1b54b8633e763467b4684d0f4334a3
fetch fmt 12.2.0 https://github.com/fmtlib/fmt/releases/download/12.2.0/fmt-12.2.0.zip a2f4a8d51178f954e4c339007f77edd76ba0cb2e36f87a48e5a5403d9be5878f
fetch zstd 1.5.7 https://github.com/facebook/zstd/archive/refs/tags/v1.5.7.tar.gz 37d7284556b20954e56e1ca85b80226768902e2edabd3b649e9e72c0c9012ee3
if [ ! -d "$DEPS/boost_1_83_0" ]; then   # header-only use
  fetch boost 1.83.0 https://archives.boost.io/release/1.83.0/source/boost_1_83_0.tar.gz
  mv "$DEPS/boost-1.83.0" "$DEPS/boost_1_83_0"
fi

clone() {  # dir url tag
  [ -d "$DEPS/$1" ] && return 0
  echo ">> $1 ($3)"
  git clone --depth 1 --branch "$3" "$2" "$DEPS/$1"
}
clone glslang https://github.com/KhronosGroup/glslang.git 15.0.0
clone glm https://github.com/g-truc/glm.git 1.0.1

# libadrenotools (BSD-2-Clause, Billy Laws): custom GPU driver loading. Pinned commit + its linkernsbypass submodule.
if [ ! -d "$DEPS/libadrenotools" ]; then
  echo ">> libadrenotools"
  git clone https://github.com/bylaws/libadrenotools "$DEPS/libadrenotools"
  git -C "$DEPS/libadrenotools" checkout 8fae8ce254dfc1344527e05301e43f37dea2df80
  git -C "$DEPS/libadrenotools" submodule update --init --recursive
fi

# SDL3 Android glue (SDLActivity & co.), not committed to this repo
J="$ROOT/app/src/main/java/org/libsdl/app"
if [ ! -f "$J/SDLActivity.java" ]; then
  mkdir -p "$J"; cp "$DEPS/SDL3-3.4.18/android-project/app/src/main/java/org/libsdl/app/"*.java "$J/"
fi
echo "deps ready in $DEPS"
