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

# Agent Guide for grails-gradle

> **IMPORTANT**: `grails-gradle` is a **hybrid** subproject — unlike [`grails-forge`](../grails-forge/AGENTS.md),
> it is NOT independent of the root build's dependency management. It has its own `settings.gradle`
> and its own test harness (Gradle TestKit, not Spock/GORM mocking), but it deliberately shares
> `dependencies.gradle`/`gradle.properties` with the repo root via `SharedPropertyPlugin`. Read this
> file for what's actually different; root [`AGENTS.md`](../AGENTS.md) still governs PR/branch/review
> conventions, dependency *versions*, and repository-wide policy.

## Quick Reference

Run from inside `grails-gradle/`, not the repo root:

```bash
./gradlew build --continue --stacktrace
./gradlew build -PskipTests -PskipCodeStyle
./gradlew validateDependencyVersions --continue --stacktrace
./gradlew aggregateStyleViolations --continue
./gradlew jacocoAggregateReport --continue --stacktrace -PskipCodeStyle
```

## Critical Rules (corrected for this subproject)

Checked against actual source, not assumed:

1. **Use plain `@CompileStatic`, NOT `@GrailsCompileStatic`.** This inverts the root rule. Grails-gradle is build tooling — Gradle plugins, not Grails artefacts. Every `@GrailsCompileStatic` string in this module's own source is javadoc describing the feature the plugin implements *for a consumer app*, never a real annotation here. Confirmed: 47 files use plain `@CompileStatic`, zero use `@GrailsCompileStatic` as an actual annotation.
2. **`javax.*` is legitimate here, not stale migration debt.** `javax.inject.Inject` (JSR-330, unrelated to Jakarta EE) and `javax.xml.*` (permanent JDK APIs) both appear correctly. Don't "fix" these to `jakarta.*` — they were never part of that migration. The `jakarta.*` references that do exist here are about configuring a *downstream Grails application's* dependencies, not this module's own runtime.
3. **Dependency versions are NOT independent — this is the opposite of `grails-forge`.** `grails-gradle/gradle.properties` defines no version properties at all, only Gradle daemon/cache flags. Versions come from root's `dependencies.gradle` (via `allprojects { apply from: '../dependencies.gradle' }`) and root's `gradle.properties` (via `SharedPropertyPlugin`, which walks up the directory tree loading every `gradle.properties` it finds). Don't hardcode a version here; don't assume `validateDependencyVersions` doesn't apply — it does, just run separately (see Quick Reference).
4. **Module source compiles against Gradle's own embedded Groovy version, not root's Groovy 5.0.x.** This is a real, structural split (`groovy-gradle-plugin`), not a version-drift bug. Don't "upgrade" it to match root — it can't, by design, since it compiles inside Gradle's own plugin classpath. The embedded version tracks whatever Gradle version `gradle/wrapper/gradle-wrapper.properties` points at; don't hardcode a patch number here, it drifts on every wrapper bump.
5. **Testing is Gradle TestKit (`GradleRunner`), not Spock/GORM-mock conventions.** See the `grails-gradle-developer` skill for the actual pattern (`GradleSpecification` base class, fixture projects under `test-projects/`).
6. **Apache license header, 4 spaces, no tabs** — unchanged from root.

## Available Skills

Same directory-based discovery as root: list `../.agents/skills/*/SKILL.md`, read each front-matter `description`, load the ones that match. The skill most relevant to this subproject is `grails-gradle-developer`, which covers the plugin-ID-to-class mapping, TestKit testing patterns, and the shared-vs-divergent build split in depth.

```bash
for f in ../.agents/skills/*/SKILL.md; do awk -F': *' '/^description:/{print FILENAME": "$2; exit}' "$f"; done
```

## Project Structure

| Directory | Gradle project | Role |
|---|---|---|
| `bom/` | `:grails-gradle-bom` | `java-platform` only — constrains sibling subprojects |
| `common/` | `:grails-gradle-common` | Tiny — one shared-utility class |
| `model/` | `:grails-gradle-model` | Vendored subset of Grails runtime classes needed at build time before the framework is on the classpath |
| `plugins/` | `:grails-gradle-plugins` | The actual `Plugin<Project>` implementations — see `grails-gradle-developer` skill for the ID→class table |
| `tasks/` | `:grails-gradle-tasks` | Two small task classes |

See the `grails-gradle-developer` skill for code patterns and the full plugin catalog — this table is orientation only.

## CI

`.github/workflows/gradle.yml`, `codestyle.yml`, `coverage.yml`, `codeanalysis.yml`, and `release.yml` all have dedicated `working-directory: grails-gradle` jobs — this subproject is actively CI'd independently of root, unlike some other independent subprojects in this repo.

## What's Unchanged From Root AGENTS.md

PR guidelines, branch naming, review process, adversarial self-review, security reporting, and the single-large-PR-over-reviewability-stack default all apply here exactly as documented in [`../AGENTS.md`](../AGENTS.md) — this file doesn't repeat them so they can't drift out of sync with the root copy. Read that file for anything not covered above.
