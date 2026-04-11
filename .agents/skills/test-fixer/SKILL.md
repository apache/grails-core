---
name: test-fixer
description: Guide for running, aggregating, and fixing test failures across all grails-core modules using GrailsTestPlugin — producing TEST_FAILURES.md from multi-module XML test results
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

- Explain how `GrailsTestPlugin` aggregates test failures from all 60+ modules into a single `TEST_FAILURES.md` report.
- Guide you through running tests across the entire repo with `--continue` so one failure doesn't stop the rest.
- Help interpret `TEST_FAILURES.md`, navigate to failing specs, and fix the underlying issues.

## When to Use Me

Activate this skill when:

- Running the full test suite and needing to see all failures in one place instead of tailing 60 separate reports.
- Preparing a commit — `TEST_FAILURES.md` must report `All tests passed! 🎉` before merging.
- Triaging which modules have regressions after a dependency upgrade or refactor.

---

## Plugin Overview

`GrailsTestPlugin` is applied to the **root project only** via the `org.apache.grails.gradle.test-aggregation` plugin ID (registered in `build-logic`).

It registers a single `aggregateTestFailures` task that:

1. Waits for (`mustRunAfter`) every `Test` task in every subproject.
2. Scans all `build/test-results/**/*.xml` directories across subprojects and all top-level repo directories.
3. Parses every `TEST-*.xml` JUnit report for `<failure>` and `<error>` elements.
4. Writes `TEST_FAILURES.md` to the repo root — a Markdown table of failures grouped by module.

Note: This plugin does **not** run tests itself. It only aggregates results already written to disk. Gradle's own test engine (JUnit Platform) handles test execution and XML report generation.

---

## Key Task

```bash
# Run all tests across all modules, continue even on failure, then aggregate
./gradlew test aggregateTestFailures --continue

# Run a single module's tests then aggregate
./gradlew :grails-data-hibernate7-core:test aggregateTestFailures --continue

# Re-run all tests (bypass Gradle's up-to-date caching)
./gradlew test aggregateTestFailures --continue --rerun-tasks
```

`--continue` is essential: without it, Gradle stops at the first failing module and `aggregateTestFailures` never runs.

---

## Output File

`TEST_FAILURES.md` is written to the repo root. Format:

```markdown
# Test Failures Summary
Generated on: 2026-04-10 12:00:00

Found 3 failures.

## Module: grails-data-hibernate7-core
| Class | Test | Type | Message |
| :--- | :--- | :--- | :--- |
| org.grails.orm.hibernate.FooSpec | some feature | org.spockframework.runtime.SpockAssertionError | expected: ... |
```

A clean run produces:
```markdown
All tests passed! 🎉
```

**A clean run should report no issues, and `TEST_FAILURES.md` is a generated artifact that must be removed before committing, whether it contains failures or not.**

---

## Interpreting Failures

Each row in the table gives you:

| Column | Meaning |
|--------|---------|
| `Class` | Fully-qualified Spock spec or JUnit class |
| `Test` | Feature method name (Spock) or test method name (JUnit) |
| `Type` | Exception class (e.g. `SpockAssertionError`, `IllegalStateException`) |
| `Message` | First 200 characters of the failure message or stack |

### Common Failure Patterns

| Type | Likely Cause |
|------|-------------|
| `ConditionNotSatisfiedException` | Spock `then:` assertion failed — check expected vs actual values |
| `SpockAssertionError` | Explicit `assert` failed inside a spec |
| `BeanCreationException` | Spring context failed to start — check `@SpringBootTest` config or missing beans |
| `HibernateException` | Schema/session issue — check entity mapping or test datasource |
| `IllegalStateException` | Missing setup, invalid test state, or static pollution from parallel tests |
| `NullPointerException` | Uninitialized mock or service — check `given:` block |
| `MissingMethodException` | Groovy dynamic dispatch failure — usually a missing method or wrong type |

---

## Running Tests for a Specific Module

```bash
# All tests in a module
./gradlew :grails-data-hibernate7-dbmigration:test

# A specific spec
./gradlew :grails-data-hibernate7-dbmigration:test \
  --tests "org.grails.plugins.databasemigration.command.DbmGenerateGormChangelogCommandSpec"

# A specific feature method (use the exact feature name)
./gradlew :grails-data-hibernate7-core:test \
  --tests "org.grails.orm.hibernate.FooSpec.my feature name"

# Force rerun even if Gradle thinks it's up-to-date
./gradlew :grails-data-hibernate7-core:test --rerun-tasks
```

---

## Test Source Layout

| Source set | Directory | Task |
|------------|-----------|------|
| Unit tests | `src/test/groovy/` | `test` |
| Integration tests | `src/integration-test/groovy/` | `integrationTest` |

Most specs in grails-core are **unit tests** (`src/test/groovy/`) run by the `test` task.
A small number of modules use `integrationTest` for full Spring context tests.

---

## Parallel Test Execution

Tests run in parallel (`maxParallelForks` defaults to `4` on CI and to `availableProcessors * 3/4` otherwise; override with `-PmaxTestParallel`). This can cause:

- **Static state pollution** — one test mutating a static field that another test reads.
- **Port conflicts** — multiple test JVMs binding the same port.
- **`@Shared` field contamination** — shared state not properly cleaned up between feature methods.

To diagnose flaky failures, re-run with forced serial execution:
```bash
./gradlew :module:test -PmaxTestParallel=1 --rerun-tasks
```

---

## Test Configuration Flags

| Property | Effect |
|----------|--------|
| `skipTests` | Skips all `Test` tasks (build only) |
| `maxTestParallel=N` | Override parallel fork count |
| `onlyFunctionalTests` | Run only functional test suites |
| `skipHibernate7Tests` | Skip H7-specific test suites |
| `onlyMongodbTests` | Run only MongoDB test suites |
| `onlyCoreTests` | Run only core module tests |

---

## XML Report Location

Gradle writes JUnit XML test results to:
```
<module>/build/test-results/
├── test/
│   └── TEST-org.grails.SomSpec.xml
└── integrationTest/
    └── TEST-org.grails.SomeIntegrationSpec.xml
```

`aggregateTestFailures` scans all these directories automatically.
