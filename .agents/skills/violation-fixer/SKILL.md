---
name: violation-fixer
description: Guide for running, interpreting, and fixing code style and analysis violations in grails-core using GrailsCodeStylePlugin, GrailsCodeAnalysisPlugin, and GrailsViolationAggregationPlugin — covering CodeNarc, Checkstyle, PMD, SpotBugs, and JaCoCo
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
- Preparing a commit — the plugin output must be clean before merging.

---

## Plugin Overview

| Plugin | Applied to | Responsibility |
|--------|-----------|----------------|
| `org.apache.grails.gradle.grails-code-style` | Every subproject | Applies Checkstyle and CodeNarc; registers per-project `codeStyle` task; redirects XML reports to root `build/reports/codestyle/` |
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
| `./gradlew aggregateJacocoCoverage` | root | Runs JaCoCo reports across every module, then writes `JACOCO_COVERAGE.md` to `build/reports/violations/` |
| `./gradlew codenarcFix` | per-project | Auto-fixes a subset of CodeNarc violations |

### Quick commands

```bash
# Check a single module (style only)
./gradlew :grails-core:codeStyle

# Check a single module (analysis — must be enabled via properties)
./gradlew :grails-core:codeAnalysis -Pgrails.code-analysis.enabled.pmd=true

# Full multi-module check + report
./gradlew aggregateViolations

# Include test sources in style checks
./gradlew aggregateViolations -Pgrails.codestyle.enabled.tests=true

# Include test sources in analysis
./gradlew aggregateViolations -Pgrails.code-analysis.enabled.tests=true

# Ignore failures (collect reports without failing the build)
./gradlew aggregateViolations -Pgrails.codestyle.ignoreFailures=true -Pgrails.code-analysis.ignoreFailures=true

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
| `build/reports/violations/PMD_VIOLATIONS.md` | PMD | Yes — contains `No violations found!` when PMD is disabled |
| `build/reports/violations/SPOTBUGS_VIOLATIONS.md` | SpotBugs | Yes — contains `No violations found!` when SpotBugs is disabled |

After running `aggregateJacocoCoverage`:

| File | Tool | Generated |
|------|------|-----------|
| `build/reports/violations/JACOCO_COVERAGE.md` | JaCoCo | Only when at least one subproject has a JaCoCo CSV report |

All reports are inside `build/` and are excluded from version control via `.gitignore`. A clean run produces `No violations found! 🎉` in each style file. **The build must be clean before committing.**

Each file is a Markdown table grouped by module, with columns: **Class**, **Tool**, **Violation**, **Line**, **Message**.

---

## Tool Details

### CodeNarc (Groovy — always enabled)

Rule file: `build/codestyle/codenarc/codenarc.groovy` (generated by the plugin during setup; not intended to be edited directly).

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

### Checkstyle (Java — always enabled)

Rule file: `build/codestyle/checkstyle/checkstyle.xml`.

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

### PMD (Java/Groovy — opt-in)

Enable: `-Pgrails.code-analysis.enabled.pmd=true`

Rule file: `build/code-analysis/pmd/pmd.xml`.

### SpotBugs (Java bytecode — opt-in)

Enable: `-Pgrails.code-analysis.enabled.spotbugs=true`

Runs at `Effort.MAX` / `Confidence.HIGH`. Only high-confidence bugs are reported.

### Spotless (Java auto-formatting — opt-in)

Enable: `-Pgrails.codestyle.enabled.spotless=true`

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
| `grails.codestyle.enabled.checkstyle` | `true` | Enable Checkstyle |
| `grails.codestyle.enabled.codenarc` | `true` | Enable CodeNarc |
| `grails.codestyle.enabled.spotless` | `false` | Enable Spotless |
| `grails.codestyle.enabled.tests` | `false` | Also check test source sets |
| `grails.codestyle.ignoreFailures` | `false` | Collect reports without failing build |
| `grails.codestyle.codenarc.fix` | `false` | Run `codenarcFix` before CodeNarc tasks |
| `grails.codestyle.dir.checkstyle` | (auto) | Custom path to Checkstyle config dir |
| `grails.codestyle.dir.codenarc` | (auto) | Custom path to CodeNarc config dir |
| `skipCodeStyle` | unset | If present, all style tasks are skipped |

### `grails-code-analysis` plugin (PMD + SpotBugs)

| Property | Default | Description |
|----------|---------|-------------|
| `grails.code-analysis.enabled.pmd` | `false` | Enable PMD |
| `grails.code-analysis.enabled.spotbugs` | `false` | Enable SpotBugs |
| `grails.code-analysis.enabled.tests` | `false` | Also analyse test source sets |
| `grails.code-analysis.ignoreFailures` | `false` | Collect reports without failing build |
| `grails.code-analysis.dir.pmd` | (auto) | Custom path to PMD config dir |
| `skipCodeStyle` | unset | If present, all analysis tasks are also skipped |

---

## Fixing Violations Workflow

1. Run `./gradlew aggregateViolations -Pgrails.codestyle.ignoreFailures=true -Pgrails.code-analysis.ignoreFailures=true`
2. Open `build/reports/violations/CODENARC_VIOLATIONS.md` and `build/reports/violations/CHECKSTYLE_VIOLATIONS.md` to see all issues by module
3. For CodeNarc, run `./gradlew codenarcFix` to auto-fix what it can
4. Fix remaining violations manually using the table above
5. Re-run `./gradlew aggregateViolations` and confirm files contain `No violations found! 🎉`
6. The reports are inside `build/` and do not need to be deleted before committing

---

## Reports Directory Structure

All XML reports are consolidated at:
```
build/reports/codestyle/        ← XML inputs for style aggregation
├── checkstyle/
│   ├── grails-core-checkstyleMain.xml
│   ├── grails-web-mvc-checkstyleMain.xml
│   └── ...
└── codenarc/
    ├── grails-core-codenarcMain.xml
    └── ...

build/reports/code-analysis/     ← XML inputs for analysis aggregation (if enabled)
├── pmd/
└── spotbugs/

build/reports/violations/       ← Markdown summaries written by aggregateViolations
├── CODENARC_VIOLATIONS.md
├── CHECKSTYLE_VIOLATIONS.md
├── PMD_VIOLATIONS.md
├── SPOTBUGS_VIOLATIONS.md
└── JACOCO_COVERAGE.md          ← written by aggregateJacocoCoverage
```

The module name is derived from the filename: everything before the last `-` (e.g. `grails-core-checkstyleMain.xml` → module `grails-core`).
