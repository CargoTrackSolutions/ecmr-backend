# Patches

Changes to upstream files that cannot be done via `overlay/` (new files only) or configuration.
Goal: this directory stays empty.

Rules:
- One change per file, numbered: `0001-short-description.patch` (create with `git format-patch` / `git diff` in `build/src`).
- First line of each patch description: link to the upstream merge request that carries the same change.
- When the upstream MR is merged and the lock is bumped past it, delete the patch.
- `scripts/assemble.sh` fails if a patch no longer applies - the upstream bump PR will show it.
