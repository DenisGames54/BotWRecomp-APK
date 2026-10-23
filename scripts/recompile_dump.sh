#!/usr/bin/env bash
# Recompiles YOUR copy of the game's executable into C++ (work/generated). Needs only U-King.rpx.
# usage: scripts/recompile_dump.sh /path/to/U-King.rpx
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RPX="${1:?usage: $0 /path/to/U-King.rpx}"
EXPECTED=ba58da5b95ce929e005d058ceb08b9b2788d1ab2bbc8a6c189bbadca0bb34d30   # USA v208 (update 1.6.0)
GOT=$(sha256sum "$RPX" | cut -d' ' -f1)
[ "$GOT" = "$EXPECTED" ] || { echo "U-King.rpx is not the supported USA v208 build (sha256 $GOT)"; exit 1; }
command -v uv >/dev/null || { echo "uv not found: curl -LsSf https://astral.sh/uv/install.sh | sh"; exit 1; }
mkdir -p "$ROOT/work"
cd "$ROOT/engine"
uv run python -I tools/recompile.py "$RPX" "$ROOT/work/generated"
echo "guest code written to $ROOT/work/generated"
