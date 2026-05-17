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
# Plan: Aggregate Violations

Aggregates CodeNarc, Checkstyle, PMD, and SpotBugs violations from all submodules into per-tool Markdown reports under `build/reports/violations/`. JaCoCo coverage is aggregated separately.

## Architecture

Three convention plugins divide the responsibility:

| Plugin | Applied to | Responsibility |
|--------|-----------|----------------|
| `grails-code-style` (`GrailsCodeStylePlugin`) | Every subproject | Checkstyle + CodeNarc; redirects XML to `build/reports/codestyle/{checkstyle,codenarc}/` |
| `grails-code-analysis` (`GrailsCodeAnalysisPlugin`) | Every subproject | PMD + SpotBugs (both opt-in); redirects XML to `build/reports/codestyle/{pmd,spotbugs}/` |
| `grails-violation-aggregation` (`GrailsViolationAggregationPlugin`) | Root project only | Parses all XML reports; writes Markdown summaries to `build/reports/violations/` |

## Key Files

- `build-logic/plugins/src/main/groovy/org/apache/grails/buildsrc/GrailsCodeStylePlugin.groovy` — Checkstyle + CodeNarc per-subproject config
- `build-logic/plugins/src/main/groovy/org/apache/grails/buildsrc/GrailsCodeAnalysisPlugin.groovy` — PMD + SpotBugs per-subproject config
- `build-logic/plugins/src/main/groovy/org/apache/grails/buildsrc/GrailsCodeAnalysisExtension.groovy` — extension for analysis plugin (PMD config dir, reports dir)
- `build-logic/plugins/src/main/groovy/org/apache/grails/buildsrc/GrailsCodeStyleExtension.groovy` — extension for style plugin (Checkstyle/CodeNarc config dirs, reports dir)
- `build-logic/plugins/src/main/groovy/org/apache/grails/buildsrc/GrailsViolationAggregationPlugin.groovy` — root aggregation task logic
- `build-logic/plugins/src/main/groovy/org/apache/grails/buildsrc/GradleUtils.groovy` — `booleanProvider()` helper for lazy property resolution

## Report Directories

```
build/reports/codestyle/          ← XML inputs (written by subprojects, code style only)
├── checkstyle/
│   └── <module>-checkstyleMain.xml
└── codenarc/
    └── <module>-codenarcMain.xml

build/reports/codeanalysis/       ← XML inputs (written by subprojects, analysis only)
├── pmd/                          (only when PMD enabled)
│   └── <module>-pmdMain.xml
└── spotbugs/                     (only when SpotBugs enabled)
    └── <module>-spotbugsMain.xml

build/reports/violations/         ← Markdown summaries (written by root aggregation tasks)
├── CODENARC_VIOLATIONS.md
├── CHECKSTYLE_VIOLATIONS.md
├── PMD_VIOLATIONS.md
└── SPOTBUGS_VIOLATIONS.md

build/codeanalysis/pmd/           ← Generated PMD rule config
└── pmd.xml
```

## Configuration Properties

### Code Style (`GrailsCodeStylePlugin`)

| Property | Default | Description |
|----------|---------|-------------|
| `grails.codestyle.enabled.checkstyle` | `true` | Enable Checkstyle |
| `grails.codestyle.enabled.codenarc` | `true` | Enable CodeNarc |
| `grails.codestyle.enabled.spotless` | `false` | Enable Spotless auto-formatting |
| `grails.codestyle.enabled.tests` | `false` | Also check test source sets |
| `grails.codestyle.ignoreFailures` | `false` | Collect reports without failing the build |
| `skipCodeStyle` | unset | Skips all style tasks when present |

### Code Analysis (`GrailsCodeAnalysisPlugin`)

| Property | Default | Description |
|----------|---------|-------------|
| `grails.codeanalysis.enabled.pmd` | `false` | Enable PMD |
| `grails.codeanalysis.enabled.spotbugs` | `false` | Enable SpotBugs |
| `grails.codeanalysis.enabled.tests` | `false` | Also analyse test source sets |
| `grails.codeanalysis.ignoreFailures` | `false` | Collect reports without failing the build |
| `skipCodeStyle` | unset | Skips all analysis tasks when present |

## Aggregation Tasks

| Task | Description |
|------|-------------|
| `aggregateViolations` | Depends on all style/analysis tasks; parses XML reports; writes `*_VIOLATIONS.md` |
| `aggregateJacocoCoverage` | Depends on all `jacocoTestReport` tasks; parses CSV reports; writes `JACOCO_COVERAGE.md` |

`aggregateViolations` uses separate test-inclusion flags per tool group:
- `checkStyleTests` from `grails.codestyle.enabled.tests` — applies to CodeNarc and Checkstyle
- `checkAnalysisTests` from `grails.codeanalysis.enabled.tests` — applies to PMD and SpotBugs

## Lazy Configuration

All property lookups use `GradleUtils.booleanProvider(project, propertyName)`, which returns a `Provider<Boolean>` resolved at task-configuration time (inside `configureEach` closures), not at `apply()` time. This ensures `-P` flags passed on the command line are honoured correctly.

## Status

**Implemented.** All steps below are complete.

- [x] `GrailsCodeStylePlugin` configures Checkstyle + CodeNarc with lazy providers; XML reports redirected to `build/reports/codestyle/`
- [x] `GrailsCodeAnalysisPlugin` extracted from `GrailsCodeStylePlugin`; PMD + SpotBugs opt-in via `grails.codeanalysis.*` properties
- [x] `grails-code-analysis` plugin applied alongside `grails-code-style` in all 102 subproject `build.gradle` files
- [x] `GrailsViolationAggregationPlugin` (renamed from `GrailsReportAggregationPlugin`) registers `aggregateViolations` and `aggregateJacocoCoverage` on root project
- [x] `aggregateViolations` uses separate `checkStyleTests` / `checkAnalysisTests` flags per tool group
- [x] Resource directories renamed: `grails-code-style/` and `grails-code-analysis/` under `META-INF/`
- [x] PMD build output moved to `build/codeanalysis/pmd/` (separate from `codestyle/`)
- [x] `violation-fixer` skill documents all tools and configuration properties
