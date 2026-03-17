#!/usr/bin/env bash
set -euo pipefail

if [ $# -eq 0 ]; then
  echo "Usage: $0 <source-branch-or-commit>"
  echo "Example: $0 feature/other-branch"
  exit 1
fi

SOURCE="$1"
MERGE_BASE=$(git merge-base HEAD "$SOURCE")

echo "Using merge base: $MERGE_BASE"

# Files changed in the source branch
changed_in_source=$(git diff --name-only --no-color "$MERGE_BASE" "$SOURCE" | sort)

# Files currently modified in working directory vs index
modified=$(git diff --name-only --no-color | sort)

# Files to stage = modified files that the source branch did NOT change
to_stage=$(comm -23 <(printf '%s\n' "$modified") <(printf '%s\n' "$changed_in_source"))

if [ -z "$to_stage" ]; then
  echo "No files to stage — all modifications came from the source branch."
  exit 0
fi

# Stage safely (handles spaces, quotes, special chars in filenames)
printf '%s\0' "$to_stage" | xargs -0 git add --

echo "✅ Staged these files (your local changes only):"
printf '%s\n' "$to_stage"

# Optional but recommended: clean up the merge-introduced changes
remaining=$(git diff --name-only --no-color)
if [ -n "$remaining" ]; then
  echo "🧹 Discarding source-branch changes in:"
  printf '%s\n' "$remaining"
  printf '%s\0' "$remaining" | xargs -0 git checkout --
fi

echo "Done! Now run 'git status' — only your local work is staged."