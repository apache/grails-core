<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
---
name: worktree-hygiene
description: Reports stale or orphaned git worktrees under .claude/worktrees/ (the agent-managed worktree directory) by checking each worktree's branch against its GitHub PR state and merge-into-default-branch status, not commit age. Use at the start of a session in this repo when .claude/worktrees/ has accumulated entries, or when asked to clean up worktrees or check branch hygiene. Report-only — never deletes without explicit confirmation, and never touches worktrees outside .claude/worktrees/.
license: Apache-2.0
compatibility: opencode, claude, grok, gemini, copilot, cursor, windsurf
paths: .claude/worktrees/**
metadata:
  audience: maintainers
  frameworks: grails
---

## What I Do

- Enumerate worktrees under `.claude/worktrees/` — the agent-managed worktree directory. Never touch worktrees elsewhere (e.g. a contributor's own manually created `../grails-core-8.0.x`); those aren't mine to judge.
- Classify each one's branch as merged, closed-unmerged, still active, or orphaned, using GitHub PR state as the authoritative signal — not commit age or idle time.
- Report findings as a table. Never run `git worktree remove` or `git branch -D` without explicit confirmation, especially where the worktree has uncommitted changes.

## When to Use Me

- At the start of a session in this repo, if `.claude/worktrees/` has more than a couple of entries.
- When asked to "clean up worktrees" or "check branch hygiene."
- Before creating a new worktree, to check whether an existing one for related work should be reused instead of piling on another.

---

## Why Age/Idle-Time Is the Wrong Signal

A worktree can sit untouched for weeks and still be exactly where it should be. This repo's PR chains (e.g. `feat/gorm-datastore-infra` → `feat/gorm-registry-core-impl` → `test/gorm-registry-core-tests` → ... → `feat/neo4j-gorm-registry-migration`) go quiet between review rounds without being abandoned — a worktree is valid for as long as its branch has a live remote or an open PR, no matter how stale it looks. The only signal that reliably distinguishes "waiting on review" from "actually dead" is PR/remote state, not the timestamp of the last commit.

## Classification Procedure

List the worktrees in scope:

```bash
git worktree list | grep '\.claude/worktrees/'
```

For each `<branch>` found:

### 1. Remote tracking status

```bash
git branch -vv | grep -F "$branch "
```

Look for `: gone]` — the remote branch was deleted, usually after a merge or a PR close.

### 2. PR state (authoritative — catches squash/rebase merges, where commit-ancestry checks alone give a false negative)

```bash
gh pr list --repo apache/grails-core --head "$branch" --state all \
  --json state,number,title,mergedAt,url
```

- `state: MERGED` → safe-to-remove candidate.
- `state: OPEN` → active. **Leave alone.** A live PR means the worktree is doing its job regardless of idle time.
- `state: CLOSED` (not merged) → abandoned or superseded. Flag for confirmation before removing — closure doesn't always mean the work was worthless.
- No PR found → likely local-only or workflow-generated (e.g. a `worktree-wf_*` branch left over from an isolated workflow run). Fall through to step 3.

### 3. Merge-into-default-branch check (only for branches with no PR record)

```bash
default_branch=$(git symbolic-ref refs/remotes/origin/HEAD --short | sed 's@^origin/@@')
git merge-base --is-ancestor "$branch" "origin/$default_branch" && echo merged || echo not-merged
```

- Merged into the default branch → safe-to-remove candidate.
- Not merged and never pushed to any remote → orphaned workflow artifact. Flag for confirmation; check for uncommitted work first (step 4).

### 4. Uncommitted work check (always run before recommending removal)

```bash
git -C .claude/worktrees/<name> status -sb
```

Any modified or untracked files mean the worktree cannot be silently removed. Surface exactly what's uncommitted and let the user decide whether to commit, stash, or discard it before `git worktree remove` runs.

## Output

Report a table: worktree path, branch, classification (`MERGED` / `CLOSED-UNMERGED` / `OPEN-ACTIVE` / `ORPHANED`), uncommitted-changes flag, and a recommended action. Recommend the removal — do not execute it — and wait for confirmation.

## Worked Example

`.claude/worktrees/wf_57549255-d5f-5`, `-7`, and `-8` (found 2026-07-11): all three based on commit `d921eda90a`, which is a merged ancestor of the default branch (step 3 → `merged`), none had a corresponding open PR (step 2 → no PR found), and each had small uncommitted leftovers — an untracked `grails-data-hibernate7/` directory in two of them, a modified `GrailsDataTckManager.groovy` in the third (step 4). Correct classification: `ORPHANED`, flagged for confirmation rather than auto-removed, because of the uncommitted content.
