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

# Agent Guide for build-logic

> **IMPORTANT**: `build-logic` is the Gradle convention-plugin layer every other build in this
> repo (root, [`grails-forge`](../grails-forge/AGENTS.md), [`grails-gradle`](../grails-gradle/AGENTS.md))
> consumes transitively via `includeBuild`. It has its own `settings.gradle`, but — like
> `grails-gradle`, unlike `grails-forge` — it is NOT independently versioned: it reaches across
> the filesystem into root's `dependencies.gradle`/`gradle.properties` by relative path. Read this
> file for what's actually different; root [`AGENTS.md`](../AGENTS.md) still governs PR/branch/review
> conventions and repository-wide policy.

## Quick Reference

Run from inside `build-logic/`, not the repo root:

```bash
./gradlew build                     # builds both :build-logic and :grails-docs-core
./gradlew :build-logic:test         # plugin Spock/TestKit specs
./gradlew :grails-docs-core:test    # docs-core Spock specs
```
Root `AGENTS.md` documents `cd build-logic && ../gradlew build` — the root's own wrapper, not this directory's `./gradlew`. Both pin Gradle 9.6.0; either works, but that's the documented form.

## Critical Rules (corrected for this subproject)

Checked against actual source:

1. **Use plain `@CompileStatic`, NOT `@GrailsCompileStatic`.** Zero real usages of the latter anywhere in this module — it's build-tooling code, not a Grails artefact. All 19 plugin/extension classes use plain `@CompileStatic`.
2. **No `jakarta.*`, and the `javax.*` present is legitimate.** `javax.inject.Inject` appears 7 times — it's Gradle's own DSL constructor-injection mechanism (required by Gradle's plugin API, which still uses `javax.inject`), unrelated to the Jakarta EE migration. Don't "fix" it.
3. **Dependency versions are NOT independently pinned.** `build-logic/gradle.properties` has no version properties at all — `plugins/build.gradle` and `docs-core/build.gradle` both explicitly `apply from: '../../dependencies.gradle'` (root's file) and read root's `gradle.properties` directly. Don't hardcode a version here.
4. **No dedicated CI job is expected, not a staleness signal.** Every other build in this repo `includeBuild('../build-logic')` and exercises these plugins transitively through their own CI runs. Don't assume "no CI job" means dormant the way it did for `grails-data-neo4j` — check git log recency and README accuracy directly instead.
5. **Apache license header, 4 spaces, no tabs** — unchanged from root.

## Available Skills

Same directory-based discovery as root: list `../.agents/skills/*/SKILL.md`, read each front-matter `description`, load the ones that match. The skill most relevant to this subproject is `build-logic-developer`, which catalogs all 13 registered convention plugins (what each actually configures, not just its name) and both testing patterns used here.

```bash
for f in ../.agents/skills/*/SKILL.md; do awk -F': *' '/^description:/{print FILENAME": "$2; exit}' "$f"; done
```

## Project Structure

| Directory | Gradle project | Role |
|---|---|---|
| `plugins/` | `:build-logic` | The 13 convention-plugin implementations |
| `docs-core/` | `:grails-docs-core` | Grails user-guide generation engine + BOM-extraction tooling for docs (README is a one-line stub — don't trust it to reflect what's actually here) |

See the `build-logic-developer` skill for the full plugin catalog and testing patterns — this table is orientation only.

## Why This Matters More Than Its Size Suggests

Several other skills in this repo already cite classes implemented here without documenting them: `hibernate-developer`'s note on Hibernate5/7 JaCoCo class-name collisions, root `AGENTS.md`'s `Coverage` section (`aggregateJacocoCoverage`, `jacocoAggregateReport`), and `violation-fixer`'s `*_VIOLATIONS.md`/`JACOCO_COVERAGE.md` output. If you change a convention plugin's behavior here, check whether those other documents need updating too — this module is the one they're all quietly depending on.

## What's Unchanged From Root AGENTS.md

PR guidelines, branch naming, review process, adversarial self-review, security reporting, and the single-large-PR-over-reviewability-stack default all apply here exactly as documented in [`../AGENTS.md`](../AGENTS.md) — this file doesn't repeat them so they can't drift out of sync with the root copy. Read that file for anything not covered above.
