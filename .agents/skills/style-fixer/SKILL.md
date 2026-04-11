---
name: style-fixer
description: Guide for running, interpreting, and fixing code style violations in grails-core using GrailsCodeStylePlugin — covering CodeNarc, Checkstyle, PMD, SpotBugs, and Spotless
license: Apache-2.0
---

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

## What I Do

- Explain how `GrailsCodeStylePlugin` enforces code quality across all 60+ modules via a single aggregation task.
- Guide you through running style checks, interpreting the per-tool Markdown violation reports, and fixing each class of violation.
- Describe which tools are always-on vs. opt-in, how to configure them via Gradle properties, and which violations can be auto-fixed.

## When to Use Me

Activate this skill when:

- Running `./gradlew aggregateStyleViolations` and interpreting the resulting `*_VIOLATIONS.md` files.
- Fixing CodeNarc, Checkstyle, PMD, SpotBugs, or Spotless violations reported in those files.
- Configuring code style tools across the repo (enabling/disabling tools or adjusting rule files).
- Preparing a commit — the plugin output must be clean before merging.

---

## Plugin Overview

`GrailsCodeStylePlugin` is applied to **every** subproject via the `org.apache.grails.gradle.grails-code-style` plugin ID. It:

1. Applies CodeNarc, Checkstyle, and (optionally) PMD, SpotBugs, Spotless, and JaCoCo.
2. Redirects all XML reports to a **shared** directory (`build/codestyle/<tool>/`) so they are visible from the root.
3. Registers a per-project `codeStyle` task and a root `aggregateStyleViolations` task.
4. Writes aggregated Markdown violation files to the repo root.

---

## Key Tasks

| Task | Scope | Description |
|------|-------|-------------|
| `./gradlew codeStyle` | per-project | Runs all enabled style checks for that project |
| `./gradlew aggregateStyleViolations` | root | Runs all checks across every module, then writes `*_VIOLATIONS.md` |
| `./gradlew codenarcFix` | per-project | Auto-fixes a subset of CodeNarc violations |

### Quick commands

```bash
# Check a single module
./gradlew :grails-core:codeStyle

# Full multi-module check + report
./gradlew aggregateStyleViolations

# Skip style in tests (default); include test sources
./gradlew aggregateStyleViolations -Pgrails.codestyle.enabled.tests=true

# Ignore failures (collect reports without failing the build)
./gradlew aggregateStyleViolations -Pgrails.codestyle.ignoreFailures=true

# Auto-fix some CodeNarc violations before running checks
./gradlew codenarcFix codeStyle
```

---

## Output Files

After running `aggregateStyleViolations`, these files appear in the repo root:

| File | Tool | Always generated |
|------|------|-----------------|
| `CODENARC_VIOLATIONS.md` | CodeNarc | Yes |
| `CHECKSTYLE_VIOLATIONS.md` | Checkstyle | Yes |
| `PMD_VIOLATIONS.md` | PMD | Yes — may contain `No violations found!` when `grails.codestyle.enabled.pmd=false` |
| `SPOTBUGS_VIOLATIONS.md` | SpotBugs | Yes — may contain `No violations found!` when `grails.codestyle.enabled.spotbugs=false` |
| `JACOCO_COVERAGE_VIOLATIONS.md` | JaCoCo | Only if `grails.codestyle.enabled.jacoco=true` |

Each file is a Markdown table grouped by module, with columns: **Class**, **Tool**, **Violation**, **Line**, **Message**.

A clean run produces `No violations found! 🎉` in each file. **A commit must not include any violations.**

---

## Tool Details

### CodeNarc (Groovy — always enabled)

Rule file: `build/codestyle/codenarc/codenarc.groovy` (auto-extracted from plugin JAR on first run).

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

Enable: `-Pgrails.codestyle.enabled.pmd=true`

Rule file: `build/codestyle/pmd/pmd.xml`.

### SpotBugs (Java bytecode — opt-in)

Enable: `-Pgrails.codestyle.enabled.spotbugs=true`

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

| Property | Default | Description |
|----------|---------|-------------|
| `grails.codestyle.enabled.checkstyle` | `true` | Enable Checkstyle |
| `grails.codestyle.enabled.codenarc` | `true` | Enable CodeNarc |
| `grails.codestyle.enabled.pmd` | `false` | Enable PMD |
| `grails.codestyle.enabled.spotbugs` | `false` | Enable SpotBugs |
| `grails.codestyle.enabled.spotless` | `false` | Enable Spotless |
| `grails.codestyle.enabled.jacoco` | `false` | Enable JaCoCo coverage |
| `grails.codestyle.enabled.tests` | `false` | Also check test source sets |
| `grails.codestyle.ignoreFailures` | `false` | Collect reports without failing build |
| `grails.codestyle.codenarc.fix` | `false` | Run `codenarcFix` before CodeNarc tasks |
| `grails.codestyle.dir.checkstyle` | (auto) | Custom path to Checkstyle config dir |
| `grails.codestyle.dir.codenarc` | (auto) | Custom path to CodeNarc config dir |
| `grails.codestyle.dir.pmd` | (auto) | Custom path to PMD config dir |
| `grails.codestyle.dir.spotless` | (auto) | Custom path to Spotless config dir |
| `skipCodeStyle` | unset | If present, all style tasks are skipped |

---

## Fixing Violations Workflow

1. Run `./gradlew aggregateStyleViolations -Pgrails.codestyle.ignoreFailures=true`
2. Open `CODENARC_VIOLATIONS.md` and `CHECKSTYLE_VIOLATIONS.md` to see all issues by module
3. For CodeNarc, run `./gradlew codenarcFix` to auto-fix what it can
4. Fix remaining violations manually using the table above
5. Re-run `./gradlew aggregateStyleViolations` and confirm files contain `No violations found! 🎉`
6. Delete the `*_VIOLATIONS.md` files before committing (clean state produces no output file)

---

## Reports Directory Structure

All XML reports are consolidated at:
```
build/codestyle/
├── checkstyle/
│   ├── grails-core-checkstyleMain.xml
│   ├── grails-web-mvc-checkstyleMain.xml
│   └── ...
├── codenarc/
│   ├── grails-core-codenarcMain.xml
│   └── ...
├── pmd/       (if enabled)
└── spotbugs/  (if enabled)
```

The module name is derived from the filename: everything before the last `-` (e.g. `grails-core-checkstyleMain.xml` → module `grails-core`).
