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

# Agent Guide for grails-forge

> **IMPORTANT**: `grails-forge` is a **Micronaut application that generates Grails applications**
> (the engine behind start.grails.org) — it is not itself a Grails application. It has its own
> `settings.gradle` and builds independently of the rest of this repository. Most of the root
> [`AGENTS.md`](../AGENTS.md) — GORM, artefact handlers, Hibernate, `@GrailsCompileStatic` — does
> not apply here. This file covers what's different; the root file still governs PR/branch/review
> conventions and repository-wide policy, which are unchanged for this subproject.

## Quick Reference

Run from inside `grails-forge/`, not the repo root:

```bash
# Build (no tests)
./gradlew build -PskipTests

# Build a single module
./gradlew :grails-forge-core:build

# Run tests
./gradlew :grails-forge-api:test
./gradlew :grails-forge-cli:test

# Code style (Checkstyle + Spotless — this is what CI's "Forge Projects" job runs)
./gradlew codeStyle
```

## Critical Rules (corrected for this subproject)

Root `AGENTS.md`'s rules assume a Grails application or the Grails framework itself. Here, checked against actual source:

1. **Use `jakarta.*` NOT `javax.*`** — same as root, and it holds here too (Micronaut is jakarta-based).
2. **Use plain `@CompileStatic`, NOT `@GrailsCompileStatic`.** This inverts the root rule. There are no Grails artefacts in this codebase, so `@GrailsCompileStatic` doesn't apply and shouldn't appear — plain `@CompileStatic` is correct.
3. **No `GrailsWebRequest`, no GORM, no artefact handlers.** These concepts don't exist here. If you find yourself reaching for one, you're importing a mental model from the wrong subproject.
4. **Code style tooling is Checkstyle + Spotless**, not the root project's CodeNarc/PMD/SpotBugs/`aggregateViolations` stack. Run `./gradlew codeStyle` from `grails-forge/`, not root's `aggregateViolations`.
5. **Dependency versions are independent of `grails-bom`.** This subproject has its own `gradle.properties` (`micronautVersion`, `picocliVersion`, etc.) and isn't governed by the root `validateDependencyVersions` check.
6. **Apache license header is still required** on every new source file — this rule is unchanged from root.
7. **4 spaces, no tabs** — also unchanged from root.

## Available Skills

Same directory-based discovery as root: list `../.agents/skills/*/SKILL.md`, read each front-matter `description`, load the ones that match. The skill most relevant to this subproject is `micronaut-developer`, which covers DI, HTTP controllers, `@MicronautTest`+Spock, Picocli CLI commands, Rocker templating, and the `Feature` extension-point system in depth — read it before making non-trivial changes here.

```bash
for f in ../.agents/skills/*/SKILL.md; do awk -F': *' '/^description:/{print FILENAME": "$2; exit}' "$f"; done
```

## Technology Stack

| Component | Version |
|---|---|
| Micronaut | 4.10.16 |
| Picocli | 4.7.6 |
| JDK | 21+ |
| Testing | Spock via `@MicronautTest` |
| Templating | Rocker (`.rocker.raw`) |
| Code style | Checkstyle + Spotless |

## Project Structure

| Module | Role |
|---|---|
| `grails-forge-core` | Generation logic: the `Feature` system, templating, dependency/config assembly |
| `grails-forge-api` | HTTP API (Micronaut `@Controller`s) — backs start.grails.org |
| `grails-forge-web-netty` | Micronaut/Netty deployment of the API, shipped to Google Cloud Run |
| `grails-forge-cli` | Picocli command-line client |
| `grails-forge-analytics-postgres` | Separate Postgres-backed analytics service |
| `test-core` | End-to-end specs that generate a project and verify it builds |
| `grails-cli`, `grails-cli-shadow` | Legacy shell-packaging plumbing, unrelated to the generator logic |

See the `micronaut-developer` skill for code patterns (DI, controllers, testing, CLI, templating, the `Feature` interface) — this table is orientation only.

## CI

The `check_forge_projects` job ("Forge Projects") in `.github/workflows/codestyle.yml` runs `./gradlew codeStyle` from `grails-forge/` independently of the root project's style checks. There's a separate `.github/workflows/gradle.yml` job for the actual build/test matrix, and dedicated `forge-deploy-*.yml` workflows handle Cloud Run deployment (snapshot/prev/release channels) — none of which touch the rest of the monorepo.

## What's Unchanged From Root AGENTS.md

PR guidelines, branch naming, review process, adversarial self-review, security reporting, and the single-large-PR-over-reviewability-stack default all apply here exactly as documented in [`../AGENTS.md`](../AGENTS.md) — this file doesn't repeat them so they can't drift out of sync with the root copy. Read that file for anything not covered above.
