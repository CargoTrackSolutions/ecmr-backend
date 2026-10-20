#!/usr/bin/env bash
# Assembles the buildable source tree in build/src:
#   1. upstream ecmr-backend at the pinned revision (upstream.lock)
#   2. patches/*.patch applied in order (should normally be empty)
#   3. overlay/ copied on top; overriding an upstream file is an error
#
# Usage: scripts/assemble.sh [upstream-sha]   (defaults to UPSTREAM_SHA from upstream.lock)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
. "$ROOT/upstream.lock"

SHA="${1:-$UPSTREAM_SHA}"
CACHE="$ROOT/.upstream/ecmr-backend"
OUT="$ROOT/build/src"

if [ ! -d "$CACHE/.git" ]; then
  echo ">> Cloning upstream into $CACHE"
  git clone --quiet --filter=blob:none --no-checkout "$UPSTREAM_REPO" "$CACHE"
fi
if ! git -C "$CACHE" cat-file -e "$SHA^{commit}" 2>/dev/null; then
  echo ">> Fetching upstream"
  git -C "$CACHE" fetch --quiet origin
fi
git -C "$CACHE" cat-file -e "$SHA^{commit}" || { echo "Upstream commit $SHA not found" >&2; exit 1; }

echo ">> Exporting upstream $SHA"
rm -rf "$OUT"
mkdir -p "$OUT"
# autocrlf off: identical LF tree on Windows and CI, so patches apply the same everywhere
git -C "$CACHE" -c core.autocrlf=false archive "$SHA" | tar -x -C "$OUT"

shopt -s nullglob
patches=("$ROOT"/patches/*.patch)
for p in "${patches[@]}"; do
  echo ">> Applying patch $(basename "$p")"
  (cd "$OUT" && git apply --whitespace=nowarn "$p") || {
    echo "Patch $(basename "$p") no longer applies to upstream $SHA - rebase it or drop it if merged upstream." >&2
    exit 1
  }
done

echo ">> Copying overlay"
collisions=0
while IFS= read -r f; do
  if [ -e "$OUT/$f" ]; then
    echo "Overlay file overrides an upstream file: $f (use a patch instead, or rename)" >&2
    collisions=1
  fi
done < <(cd "$ROOT/overlay" && find . -type f)
[ "$collisions" -eq 0 ] || exit 1
cp -R "$ROOT/overlay/." "$OUT/"

OVERLAY_SHA="$(git -C "$ROOT" rev-parse --verify -q HEAD || echo unknown)"
cat > "$OUT/src/main/resources/cargotrack-build.properties" <<EOF
upstream.sha=$SHA
overlay.sha=$OVERLAY_SHA
EOF

echo ">> Done: $OUT (upstream $SHA, overlay $OVERLAY_SHA, ${#patches[@]} patch(es))"
