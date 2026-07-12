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
name: migration-scoping
description: Before starting any refactor/rewrite/optimization-shaped task on a core subsystem (GORM registry, datastore internals, binder/mapping layer, etc.), classify it as mechanical (bounded, safe to just do) or architectural (a project, not a patch) and check whether another local or remote branch already attempted it. Use this before writing code for anything that sounds like "improve/refactor/optimize/rewrite X", not for ordinary bug fixes or additive features.
license: Apache-2.0
compatibility: opencode, claude, grok, gemini, copilot, cursor, windsurf
metadata:
  audience: maintainers
  frameworks: grails
---

## What I Do

- Classify an incoming task as mechanical (bounded, single-session, no design decisions) or architectural (changes core behavior/object model, has more than one reasonable design) before any code gets written.
- For architectural work specifically, check local and remote branches for prior art on the same subsystem before starting, so a new attempt doesn't silently duplicate or conflict with one already in flight or already abandoned.
- Report findings — this skill doesn't decide *which* prior attempt to build on, that's a design call for the user; it surfaces what exists so the call can be made with full information instead of by accident.

## Why This Exists

Doing mechanical fixes first and discovering an architectural blocker last is wasted effort — a general lesson from legacy-modernization work (a rewrite of a removed dependency, say, needs to be identified and scoped as its own project *before* any mechanical patching begins around it, not discovered halfway through). This failure mode showed up concretely in this repo: four branches independently attempted the same GORM registry/scaling work — `8.0.x-hibernate7`, `8.0.x-hibernate7.gorm-registry-refactor`, `8.0.x-hibernate7.gorm-scaling-clean`, and `fix/gorm-api-registration-scaling` — before anyone checked whether prior art existed. Ancestry analysis (`git merge-base --is-ancestor`) showed `gorm-registry-refactor` and the 456-file `gorm-scaling-clean` rewrite were both fully orphaned dead ends (never merged anywhere, not ancestors of each other), while `fix/gorm-api-registration-scaling` was the branch that had actually landed and was already baked into the live chain that became `feat/neo4j-gorm-registry-migration`. That determination took real archaeological work *after the fact* — this skill exists so it happens *before*, when it's cheap.

## When to Use Me

Activate before writing any code for a task shaped like:

- "Improve/refactor/optimize/rewrite [core subsystem]"
- "Make [GORM registry / datastore internals / binder / mapping layer] faster/cleaner/scale better"
- Any request that touches `grails-datastore-core`, `grails-datamapping-core`, or a datastore module's mapping-context/binder/registry internals in a way that isn't a narrow, obviously-bounded fix

**Not needed for:** ordinary bug fixes, additive features with a single clear implementation, style/violation cleanup, dependency bumps, documentation.

## Step 1: Classify

Ask: does this change alter core behavior or the object model in a way that has more than one reasonable design, or touch a subsystem multiple other pieces of code depend on?

- **Mechanical** — bounded, one clear way to do it, completable in the current session (a renamed API, a deployment-target bump, a straightforward bug fix). Proceed normally, no further triage needed.
- **Architectural** — a rewrite of how a subsystem works, a new abstraction layer, a scaling/performance redesign, anything with real design-space to explore. This is a project, not a patch — go to Step 2 before writing any code.

If unsure which one it is, treat it as architectural. The cost of over-triaging a mechanical task is a few minutes of `git log`; the cost of under-triaging an architectural one is the four-branch scenario above.

## Step 2: Check for Prior Art (architectural work only)

```bash
# What's the actual current work on this subsystem? Look for branches/PRs whose name or
# recent commits mention the subsystem you're about to touch.
git branch -a | grep -i '<subsystem-keyword>'
gh pr list --repo apache/grails-core --search '<subsystem-keyword> in:title' --state all

# For any candidate found, determine its real relationship to the current branch/mainline —
# do NOT trust branch names or dates alone (see Pitfalls below).
git merge-base <candidate-branch> <current-branch>
git merge-base --is-ancestor <candidate-branch> <current-branch> && echo "already merged/subsumed"
git merge-base --is-ancestor <candidate-branch> origin/<default-branch> && echo "landed on mainline"
git log --oneline <merge-base>..<candidate-branch> | wc -l   # how much unique work is actually there
git diff --stat <merge-base> <candidate-branch>               # how large/real is that work
```

Classify each candidate found:

- **Landed** (ancestor of mainline or the branch you're about to build on) — its work is already yours, don't redo it.
- **Live** (has an open PR, or is clearly the branch other recent work descends from) — coordinate instead of duplicating; surface this to the user before proceeding.
- **Orphaned** (not an ancestor of anything, no open PR, no recent activity) — a prior attempt that didn't land. Worth reading before you start (it may show a design that was tried and abandoned for a reason, or simply ran out of steam) but don't silently build on it as if it were current.

## Pitfalls (from the real four-branch case)

- **Branch names lie.** `8.0.x-hibernate7.gorm-registry-refactor` sounds like it should be the predecessor of `8.0.x-hibernate7.gorm-scaling-clean` — it wasn't; they were siblings, and neither was an ancestor of the other. Don't infer lineage from naming alone; check with `git merge-base --is-ancestor`.
- **Last-commit date is a weak, sometimes-inverted signal.** In the real case, the branch with the *older* last-commit timestamp (`fix/gorm-api-registration-scaling`) was the one that actually won and landed — the newer-looking branches were dead ends. Ancestry, not recency, is the real signal.
- **Large diffs don't imply "more complete" or "more current."** The 456-file `gorm-scaling-clean` rewrite was the most extensive of the four branches and also the most thoroughly abandoned one — size is not a proxy for correctness or currency.
- **A merged-somewhere-else branch can still block deletion of a worktree/branch that isn't actually dead.** Cross-check against the [`worktree-hygiene`](../worktree-hygiene/SKILL.md) skill's PR-state check before assuming "orphaned" — a branch with no local merge but a live remote PR is not dead, it's in review.

## Source of Truth

This skill is the repository guidance for pre-work architectural triage. If the actual worked example above (the four Hibernate7/GORM-registry branches) gets pruned from git history entirely, keep the pattern and the pitfalls — they're general, not specific to that one incident.
