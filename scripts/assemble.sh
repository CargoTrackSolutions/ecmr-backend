#!/usr/bin/env bash
# Assembles the build tree:
#   build/src    upstream ecmr-backend at UPSTREAM_SHA + patches/*.patch + overlay/
#   build/eseal  upstream eseal at ESEAL_SHA (signer/verifier are built from source)
# Overriding an upstream file from overlay/ is an error.
#
# Usage: scripts/assemble.sh [upstream-sha]   (defaults to UPSTREAM_SHA from upstream.lock)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
. "$ROOT/upstream.lock"

SHA="${1:-$UPSTREAM_SHA}"
OUT="$ROOT/build/src"

# export <repo-url> <sha> <cache-name> <target-dir>
export_upstream() {
  local repo="$1" sha="$2" cache="$ROOT/.upstream/$3" target="$4"
  if [ ! -d "$cache/.git" ]; then
    echo ">> Cloning $repo"
    git clone --quiet --filter=blob:none --no-checkout "$repo" "$cache"
  fi
  if ! git -C "$cache" cat-file -e "$sha^{commit}" 2>/dev/null; then
    git -C "$cache" fetch --quiet origin
  fi
  git -C "$cache" cat-file -e "$sha^{commit}" || { echo "Commit $sha not found in $repo" >&2; exit 1; }
  echo ">> Exporting $3 $sha"
  rm -rf "$target"
  mkdir -p "$target"
  # autocrlf off: identical LF tree on Windows and CI, so patches apply the same everywhere
  git -C "$cache" -c core.autocrlf=false archive "$sha" | tar -x -C "$target"
}

export_upstream "$UPSTREAM_REPO" "$SHA" ecmr-backend "$OUT"
export_upstream "$ESEAL_REPO" "$ESEAL_SHA" eseal "$ROOT/build/eseal"

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
eseal.sha=$ESEAL_SHA
overlay.sha=$OVERLAY_SHA
EOF

echo ">> Done: build/ (upstream $SHA, eseal $ESEAL_SHA, overlay $OVERLAY_SHA, ${#patches[@]} patch(es))"
