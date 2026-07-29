---
name: violation-fixer
description: Guide for running, interpreting, and fixing code style and analysis violations in grails-core using GrailsCodeStylePlugin, GrailsCodeAnalysisPlugin, and GrailsViolationAggregationPlugin - covering CodeNarc, Checkstyle, PMD, SpotBugs, and JaCoCo
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0. 
-->

## What I Do

- Explain how `GrailsCodeStylePlugin`, `GrailsCodeAnalysisPlugin`, and `GrailsViolationAggregationPlugin` enforce code quality across all 60+ modules.
- Guide you through running style and analysis checks, interpreting the per-tool Markdown violation reports, and fixing each class of violation.
- Describe which tools are always-on vs. opt-in, how to configure them via Gradle properties, and which violations can be auto-fixed.

## When to Use Me

Activate this skill when:

- Running `./gradlew aggregateViolations` and interpreting the resulting `*_VIOLATIONS.md` files.
- Fixing CodeNarc, Checkstyle, PMD, SpotBugs, or Spotless violations reported in those files.
- Configuring code style or analysis tools across the repo (enabling/disabling tools or adjusting rule files).
- Preparing a commit - the plugin output must be clean before merging.

---

## Plugin Overview

| Plugin | Applied to | Responsibility |
|--------|-----------|----------------|
| `org.apache.grails.gradle.grails-code-style` | Every subproject | Applies Checkstyle, CodeNarc, and code analysis; registers per-project `codeStyle` task; redirects XML reports to root `build/reports/code-style/` |
| `org.apache.grails.gradle.grails-code-analysis` | Every subproject | Applies PMD and SpotBugs (both opt-in); registers per-project `codeAnalysis` task; redirects XML reports to root `build/reports/code-analysis/` |
| `org.apache.grails.gradle.grails-jacoco` | Every subproject | Applies JaCoCo; wires `jacocoTestReport` to run after each `test` task |
| `org.apache.grails.gradle.grails-violation-aggregation` | **Root project only** | Registers `aggregateViolations` and `aggregateJacocoCoverage` tasks; writes Markdown summaries to `build/reports/violations/` |

---

## Key Tasks

| Task | Scope | Description |
|------|-------|-------------|
| `./gradlew codeStyle` | per-project | Runs Checkstyle and CodeNarc for that project |
| `./gradlew codeAnalysis` | per-project | Runs PMD and/or SpotBugs for that project (when enabled) |
| `./gradlew aggregateViolations` | root | Runs all checks across every module, then writes `*_VIOLATIONS.md` to `build/reports/violations/` |
| `./gradlew validateRepositoryConventions` | root | Validates canonical skill metadata, AGENTS paths, GitHub Action pins, and message keys. RAT provenance is the separate `rat` task, which `aggregateViolations` runs |
| `./gradlew aggregateJacocoCoverage` | root | Runs JaCoCo reports across every module, then writes `JACOCO_COVERAGE.md` to `build/reports/violations/` |
| `./gradlew codenarcFix` | per-project | Auto-fixes a subset of CodeNarc violations |

### Quick commands

```bash
# Check a single module (style only)
./gradlew :grails-core:codeStyle

# Check a single module (analysis - enable through the module extension or an override)
./gradlew :grails-core:codeAnalysis -Pgrails.code-analysis.enabled.pmd=true

# Full multi-module check + report (use --continue so the reports are written even when an analyzer fails)
./gradlew aggregateViolations --continue

# Repository conventions only
./gradlew validateRepositoryConventions

# Include test sources in style checks
./gradlew aggregateViolations -Pgrails.code-style.enabled.tests=true

# Include test sources in analysis
./gradlew aggregateViolations -Pgrails.code-analysis.enabled.tests=true

# Ignore failures (collect reports without failing the build)
./gradlew aggregateViolations -Pgrails.code-style.ignoreFailures=true -Pgrails.code-analysis.ignoreFailures=true

# Auto-fix some CodeNarc violations before running checks
./gradlew codenarcFix codeStyle

# JaCoCo coverage report
./gradlew aggregateJacocoCoverage
```

---

## Output Files

After running `aggregateViolations`, these files appear under `build/reports/violations/` in the **root project build directory**:

| File | Tool | Always generated |
|------|------|-----------------|
| `build/reports/violations/CODENARC_VIOLATIONS.md` | CodeNarc | Yes |
| `build/reports/violations/CHECKSTYLE_VIOLATIONS.md` | Checkstyle | Yes |
| `build/reports/violations/PMD_VIOLATIONS.md` | PMD | Yes - reports `PMD is disabled.` when PMD is disabled |
| `build/reports/violations/SPOTBUGS_VIOLATIONS.md` | SpotBugs | Yes - reports `SpotBugs is disabled.` when SpotBugs is disabled |
| `build/reports/violations/REPOSITORY_CONVENTIONS.md` | Repository conventions | Yes - lists skill, Action, or message-key failures. Ordered after `rat` in the `aggregateViolations` lane |

After running `aggregateJacocoCoverage`:

| File | Tool | Generated |
|------|------|-----------|
| `build/reports/violations/JACOCO_COVERAGE.md` | JaCoCo | Only when at least one subproject has a JaCoCo CSV report |

All reports are inside `build/` and are excluded from version control via `.gitignore`. A clean run produces `No violations found! 🎉` in each style file. **The build must be clean before committing.**

Each aggregated style or analysis report begins with `Modules analyzed:`, which names only modules that contributed data. Each file is a Markdown table grouped by module, with columns: **Class**, **Tool**, **Violation**, **Line**, **Message**.

Only the aggregate lane (`aggregateViolations`, `aggregateStyleViolations`, `aggregateAnalysisViolations`) writes these Markdown files. Running an analyzer task directly, such as `./gradlew :grails-core:checkstyleMain`, produces only that task's own XML report and deliberately leaves the aggregate Markdown untouched, so a partial run can never overwrite an authoritative full-repository report. Because the writer is part of that lane rather than a per-task finalizer, pass `--continue` when you expect violations, otherwise the failing analyzer stops the build before the report explaining the failure is written.

## Repository Conventions

Run `./gradlew validateRepositoryConventions` to write `build/reports/violations/REPOSITORY_CONVENTIONS.md`. Fix the reported source rather than suppressing the validation.

| Finding | Fix |
|---------|-----|
| Skill | Start `SKILL.md` with YAML front matter, supply string `name`, `description`, and `license` values, use a valid directory name that matches `name`, and keep names unique. Every skill path that `AGENTS.md` references must exist, but `AGENTS.md` is not required to index every skill. |
| GitHub Action | Pin third-party references to one lowercase 40-hex immutable reference for that action across workflows and repository-local `action.yml` or `action.yaml` manifests. A 40-hex value may be a commit or annotated-tag object SHA. `actions/*` and `apache/*` may use version or branch references, and local `./...` uses are permitted. Pin Docker `uses`, Docker action `runs.image`, and workflow job/service container images to literal immutable `name@sha256:<digest>` values. |
| Message key | Remove or rename the duplicate logical key in the reported `grails-app/i18n/**/*.properties` file, preserving escaped separators and continuation semantics. |

---

## Tool Details

### CodeNarc (Groovy - always enabled)

Rule file: `build/code-style/codenarc/codenarc.groovy` (generated by the plugin during setup; not intended to be edited directly).

Most common violations and how to fix them:

| Rule | Fix |
|------|-----|
| `UnnecessaryGString` | Replace `"plain string"` with `'plain string'` |
| `UnnecessarySemicolon` | Remove trailing `;` |
| `SpaceBeforeOpeningBrace` | Add space before `{` → `method() {` |
| `SpaceAroundMapEntryColon` | `[key: value]` not `[key:value]` |
| `ConsecutiveBlankLines` | Collapse 3+ blank lines to 2 |
| `ClassStartsWithBlankLine` | Remove blank line right after `class Foo {` |
| `NoWildcardImports` | Expand `import org.foo.*` to explicit imports |
| `UnusedImport` | Remove imports not referenced in the file |
| `MethodName` | Method names must be camelCase (not `snake_case`) |
| `VariableName` | Variable names must be camelCase |
| `LineLength` | Keep lines ≤ 200 chars (default) |

Auto-fixable via `codenarcFix`: `ClassStartsWithBlankLine`, `SpaceAroundMapEntryColon`, `UnnecessaryGString`, `UnnecessarySemicolon`, `SpaceBeforeOpeningBrace`, `ConsecutiveBlankLines`.

### Checkstyle (Java - always enabled)

Rule file: `build/code-style/checkstyle/checkstyle.xml`.

Common violations:

| Rule | Fix |
|------|-----|
| `ImportOrder` | Re-order imports: `java|javax`, then `groovy`, then `jakarta`, then blank, then `io.spring|org.springframework`, then `grails|org.apache.grails|org.grails`, then static imports |
| `AvoidStarImport` | Use explicit class imports |
| `UnusedImports` | Remove unused imports |
| `WhitespaceAround` | Add spaces around operators and keywords |
| `NeedBraces` | Add `{}` to single-statement `if`/`for`/`while` |
| `FileTabCharacter` | Replace tabs with 4 spaces |
| `NewlineAtEndOfFile` | Ensure file ends with `\n` |

### PMD (Java/Groovy - opt-in)

Enable PMD in each clean module's `build.gradle` with `grailsCodeAnalysis { pmdEnabled = true }`. Use `-Pgrails.code-analysis.enabled.pmd=true` to override every project, or `-Pgrails.code-analysis.enabled.pmd.projects=:project-a,:project-b` to override selected project paths for a baseline run. PMD excludes sources under each project's configured build directory.

PMD tasks are registered during `afterEvaluate`. Wrap per-task customization in `afterEvaluate { tasks.named('pmdMain') { ... } }`.

Rule file: `build/code-analysis/pmd/pmd.xml`.

### SpotBugs (Java bytecode - opt-in)

Enable SpotBugs in each clean module's `build.gradle` with `grailsCodeAnalysis { spotbugsEnabled = true }`. Use `-Pgrails.code-analysis.enabled.spotbugs=true` to override every project, or `-Pgrails.code-analysis.enabled.spotbugs.projects=:project-a,:project-b` to override selected project paths for a baseline run.

SpotBugs tasks are registered during `afterEvaluate`. Wrap per-task customization in `afterEvaluate { tasks.named('spotbugsMain') { ... } }`.

Runs at `Effort.MAX` / `Confidence.HIGH`. Only high-confidence bugs are reported.

### Spotless (Java auto-formatting - opt-in)

Enable: `-Pgrails.code-style.enabled.spotless=true`

Uses Palantir Java Format. Can auto-fix by running:
```bash
./gradlew spotlessApply
```

---

## Configuration Properties

All properties can be set in `gradle.properties` or passed as `-P` flags:

### `grails-code-style` plugin (Checkstyle + CodeNarc)

| Property | Default | Description |
|----------|---------|-------------|
| `grails.code-style.enabled.checkstyle` | `true` | Enable Checkstyle |
| `grails.code-style.enabled.codenarc` | `true` | Enable CodeNarc |
| `grails.code-style.enabled.spotless` | `false` | Enable Spotless |
| `grails.code-style.enabled.tests` | `false` | Also check test source sets |
| `grails.code-style.ignoreFailures` | `false` | Collect reports without failing build |
| `grails.code-style.codenarc.fix` | `false` | Run `codenarcFix` before CodeNarc tasks |
| `grails.codestyle.dir.checkstyle` | (auto) | Custom path to Checkstyle config dir |
| `grails.codestyle.dir.codenarc` | (auto) | Custom path to CodeNarc config dir |
| `skipCodeStyle` | unset | If present, all style tasks are skipped |

### `grails-code-analysis` plugin (PMD + SpotBugs)

Enable PMD and SpotBugs primarily in each module's `build.gradle`:

```groovy
grailsCodeAnalysis {
    pmdEnabled = true
    spotbugsEnabled = true
}
```

The Gradle properties below are all-project or selected-project overrides for baseline runs.

| Property | Default | Description |
|----------|---------|-------------|
| `grailsCodeAnalysis.pmdEnabled` | `false` | Primary per-project PMD opt-in set in the module's `build.gradle` |
| `grails.code-analysis.enabled.pmd` | `false` | Override to enable PMD for every project |
| `grails.code-analysis.enabled.pmd.projects` | unset | Override to enable PMD for comma-separated project paths |
| `grailsCodeAnalysis.spotbugsEnabled` | `false` | Primary per-project SpotBugs opt-in set in the module's `build.gradle` |
| `grails.code-analysis.enabled.spotbugs` | `false` | Override to enable SpotBugs for every project |
| `grails.code-analysis.enabled.spotbugs.projects` | unset | Override to enable SpotBugs for comma-separated project paths |
| `grails.code-analysis.enabled.tests` | `false` | Also analyse test source sets |
| `grails.code-analysis.ignoreFailures` | `false` | Collect ordinary findings without failing the build; missing expected XML always fails |
| `grails.code-analysis.dir.pmd` | (auto) | Custom path to PMD config dir |
| `skipCodeStyle` | unset | If present, all analysis tasks are also skipped |

---

## Fixing Violations Workflow

1. Run `./gradlew aggregateViolations -Pgrails.code-style.ignoreFailures=true -Pgrails.code-analysis.ignoreFailures=true`
2. Open `build/reports/violations/CODENARC_VIOLATIONS.md` and `build/reports/violations/CHECKSTYLE_VIOLATIONS.md` to see all issues by module
3. For CodeNarc, run `./gradlew codenarcFix` to auto-fix what it can
4. Fix remaining violations manually using the table above
5. Re-run `./gradlew aggregateViolations` and confirm files contain `No violations found! 🎉`
6. The reports are inside `build/` and do not need to be deleted before committing

---

## Reports Directory Structure

All XML reports are consolidated at:
```
build/reports/code-style/        ← XML inputs for style aggregation
├── checkstyle/
│   ├── <hex-project-path>-checkstyleMain.xml
│   ├── <hex-project-path>-checkstyleCli.xml
│   └── ...
└── codenarc/
    ├── <hex-project-path>-codenarcMain.xml
    └── ...

build/reports/code-analysis/     ← XML inputs for analysis aggregation (if enabled)
├── pmd/
└── spotbugs/

build/reports/violations/       ← Markdown summaries written by aggregateViolations
├── CODENARC_VIOLATIONS.md
├── CHECKSTYLE_VIOLATIONS.md
├── PMD_VIOLATIONS.md
├── SPOTBUGS_VIOLATIONS.md
├── REPOSITORY_CONVENTIONS.md
└── JACOCO_COVERAGE.md          ← written by aggregateJacocoCoverage
```

The filename prefix is the UTF-8 hexadecimal encoding of the full Gradle project path. Aggregation decodes it back to paths such as `:grails-core`, preventing nested projects with the same leaf name from colliding.
