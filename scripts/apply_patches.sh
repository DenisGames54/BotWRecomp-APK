#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
for p in "$ROOT"/patches/*.patch; do
  [ -f "$p.applied" ] && continue
  if git -C "$ROOT/engine" apply --check "$p" 2>/dev/null; then
    git -C "$ROOT/engine" apply "$p" && touch "$p.applied"
  else
    echo "patch $(basename "$p"): already applied or does not apply (skipping)"; touch "$p.applied"
  fi
done
