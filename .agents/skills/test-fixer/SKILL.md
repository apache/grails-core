---
name: test-fixer
description: Guide for running, reviewing, and fixing test failures across grails-core modules using Gradle test reports
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide targeted and full test runs across grails-core modules.
- Help interpret Gradle HTML and XML test reports.
- Help triage failing specs, static state pollution, and module-specific regressions.

## When to Use Me

Activate this skill when:

- Running a failing module or specific spec.
- Preparing a commit and verifying affected module tests.
- Triaging regressions after a dependency upgrade or refactor.
- Reviewing aggregate test results from `:grails-test-report`.

---

## Key Tasks

### Targeted Testing Recommended

To save time, run only related tests:

```bash
./gradlew :grails-data-hibernate7-core:test --tests "grails.gorm.tests.BasicCollection*"
```

### Full Run

```bash
./gradlew test --continue
```

Use `--continue` when you need later modules to run after an earlier module fails.

---

## Aggregate Test Reports

The `grails-test-report` project owns repository test aggregation.

```bash
./gradlew :grails-test-report:check --continue
```

This generates:

- Unit HTML: `grails-test-report/build/reports/tests/test/index.html`
- Unit Markdown: `grails-test-report/build/reports/tests/test.md`
- Integration HTML: `grails-test-report/build/reports/tests/integrationTest/index.html`
- Integration Markdown: `grails-test-report/build/reports/tests/integrationTest.md`
- Combined HTML: `grails-test-report/build/reports/tests/combined/index.html`
- Combined Markdown: `grails-test-report/build/reports/tests/combined.md`

The aggregate report includes root subprojects that define matching `test` or `integrationTest` tasks.

The collected unit and integration `Test` tasks are finalized by their matching aggregate report tasks. Reports are attempted after failures and after targeted module test runs, but they summarize the XML results currently available on disk. Use `--continue` for full-suite runs so Gradle keeps scheduling later test tasks after an earlier failure; without it, reports can be partial. Run `clean` first when stale XML results should be excluded.

---

## Running Tests for a Specific Module

```bash
# All tests in a module
./gradlew :grails-data-hibernate7-dbmigration:test

# A specific spec
./gradlew :grails-data-hibernate7-dbmigration:test \
  --tests "org.grails.plugins.databasemigration.command.DbmGenerateGormChangelogCommandSpec"

# A specific feature method
./gradlew :grails-data-hibernate7-core:test \
  --tests "org.grails.orm.hibernate.FooSpec.my feature name"

# Force rerun even if Gradle thinks it is up-to-date
./gradlew :grails-data-hibernate7-core:test --rerun-tasks
```

---

## Test Source Layout

| Source set | Directory | Task |
|------------|-----------|------|
| Unit tests | `src/test/groovy/` | `test` |
| Integration tests | `src/integration-test/groovy/` | `integrationTest` |

Most specs in grails-core are unit tests (`src/test/groovy/`) run by the `test` task. A small number of modules use `integrationTest` for full Spring context tests.

---

## Parallel Test Execution

Tests run in parallel (`maxParallelForks` defaults to `4` on CI and to `availableProcessors * 3/4` otherwise; override with `-PmaxTestParallel`). This can cause:

- Static state pollution: one test mutating a static field that another test reads.
- Port conflicts: multiple test JVMs binding the same port.
- `@Shared` field contamination: shared state not properly cleaned up between feature methods.

To diagnose flaky failures, rerun with forced serial execution:

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

```text
<module>/build/test-results/
├── test/
│   └── TEST-org.grails.SomeSpec.xml
└── integrationTest/
    └── TEST-org.grails.SomeIntegrationSpec.xml
```
