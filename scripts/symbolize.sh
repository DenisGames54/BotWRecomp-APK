#!/usr/bin/env bash
# Resolves the last "host frames (base 0x...): 0x... 0x..." line of a log (runtime.log or diagnostics.txt)
# to function names / source lines, using build/native/libmain.unstripped.so of the build that crashed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/.botw-apk-env"
LOG="${1:?usage: $0 <runtime.log|diagnostics.txt>}"
LIB="$ROOT/build/native/libmain.unstripped.so"
A2L="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-addr2line"
[ -f "$LIB" ] || { echo "missing $LIB: rebuild with scripts/build_native.sh first"; exit 1; }
line=$(grep 'host frames (base' "$LOG" | tail -1)
[ -n "$line" ] || { echo "no 'host frames' line in $LOG"; exit 1; }
mapfile -t offs < <(python3 - "$line" <<'PY'
import re, sys
l = sys.argv[1]
base = int(re.search(r'base (0x[0-9a-f]+)', l).group(1), 16)
for a in re.findall(r'0x[0-9a-f]+', l.split('):', 1)[1]):
    o = int(a, 16) - base
    if 0 <= o < 0x40000000:   # inside libmain (other libraries are skipped)
        print(hex(o))
PY
)
echo "offsets in libmain: ${offs[*]}"
"$A2L" -f -C -i -e "$LIB" "${offs[@]}"
