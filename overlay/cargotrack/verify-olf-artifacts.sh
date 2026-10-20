#!/bin/sh
# Verifies the OLF registry-hosted dependencies (data model) against olf-artifacts.sha256.
# The OLF GitLab Maven registry allows re-publishing an existing version - eSeal 1.2.0 was overwritten
# with incompatible content on 2026-10-19 - so a version number alone does not pin the content.
# eSeal signer/verifier are not checked here: they are built from source pinned by ESEAL_SHA (upstream.lock).
#
# Run from the assembled source root (build/src) after dependencies are resolved.
# On an intended change (e.g. upstream bump), copy the printed "actual" lines into
# overlay/cargotrack/olf-artifacts.sha256.
set -eu
LOCK="$(dirname "$0")/olf-artifacts.sha256"
REPO="${M2_REPO:-$HOME/.m2/repository}"

mkdir -p target
mvn -B -q dependency:list -DincludeGroupIds=org.openlogisticsfoundation.ecmr.data-model \
  -DoutputAbsoluteArtifactFilename=true -DoutputFile=target/olf-deps.txt
actual="$(grep -oE "$REPO/[^ ]+\.jar" target/olf-deps.txt | sort -u | while read -r f; do
  printf '%s  %s\n' "$(sha256sum "$f" | cut -d' ' -f1)" "${f#"$REPO"/}"
done | sort -k2)"

if [ -z "$actual" ]; then
  echo "No OLF artifacts resolved - check $REPO" >&2
  exit 1
fi
if [ "$actual" != "$(grep -v '^#' "$LOCK" | sort -k2)" ]; then
  echo "OLF artifact checksums differ from $LOCK" >&2
  echo "--- expected" >&2; grep -v '^#' "$LOCK" | sort -k2 >&2
  echo "--- actual" >&2; echo "$actual" >&2
  exit 1
fi
echo "OLF artifacts verified:"; echo "$actual"
