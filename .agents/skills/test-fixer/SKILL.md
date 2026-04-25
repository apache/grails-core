---
name: test-fixer
description: Guide for running, aggregating, and fixing test failures across all grails-core modules using GrailsTestPlugin — producing TEST_FAILURES.md from multi-module XML test results
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0. 
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

## Key Tasks

### Targeted Testing (Recommended)
To save time, clean old results and run only related tests:

```bash
# 1. Clear stale XML reports
./gradlew clean

# 2. Run related tests and aggregate
./gradlew :grails-data-hibernate7-core:test --tests "grails.gorm.tests.BasicCollection*" aggregateTestFailures --continue
```

### Full Run
```bash
# Run all tests across all modules, continue even on failure, then aggregate
./gradlew test aggregateTestFailures --continue
```

`--continue` is essential: without it, Gradle stops at the first failing module and `aggregateTestFailures` never runs.
**ALWAYS run `clean` before a targeted run if you want `TEST_FAILURES.md` to reflect only the current run.**

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

**A clean run should report no issues. Note that `TEST_FAILURES.md` is a generated artifact and is ignored by git, so it will not be committed.**

---

## Isolated Testing (local.properties)

The project supports an **Isolation Mode** for focused development. By configuring `local.properties`, you can limit test execution to specific modules and enable aggressive heap settings for those tests.

### Configuration

In `local.properties`:
```properties
# List the modules you want to focus on
grails.test.modules=:grails-datamapping-core-test,:grails-data-simple
```

### Usage

```bash
# Runs ONLY the modules defined in grails.test.modules
# Automatically aggregates failures and style violations
# MANDATORY: Must include the local-init.gradle initialization script
./gradlew -I local-init.gradle testSelected
```

When `testSelected` is run:
- Only tests in the specified modules are enabled.
- `maxParallelForks` is set to `1` (serial) for stability.
- Heap size is increased to `3g`.
- `ignoreFailures` is set to `true` to ensure aggregation tasks run.
- Non-selected modules have their test and quality tasks disabled.

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
