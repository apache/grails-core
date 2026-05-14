# Grails Test Report

This project aggregates test results for Grails projects in this repository. It uses Gradle `TestReport` tasks for HTML reports and adds local Markdown summary tasks for quick review in terminals and CI artifacts.

## Included Projects

The aggregation automatically includes root subprojects that apply the Java plugin and define matching `Test` tasks. The report project itself is excluded.

## Test Suites

This project defines three report variants:

- `test`: aggregates `test` tasks only.
- `integrationTest`: aggregates `integrationTest` tasks only.
- `combined`: aggregates both `test` and `integrationTest` tasks.

Projects that do not define an `integrationTest` task are skipped for the integration report.

## Run Tests And Generate Reports

Run the aggregation project check task from the repository root to execute all configured phases and generate all reports:

```bash
./gradlew :grails-test-report:check --continue
```

Use `--continue` so Gradle keeps running remaining test tasks when one project has test failures. The collected unit and integration `Test` tasks are finalized by their matching report tasks, and the phase runner tasks are finalized by their selected report tasks, so reports are still generated from XML results that were written before a failure. Without `--continue`, Gradle stops scheduling remaining independent test tasks after the first failure, so the generated aggregate report can be partial.

The reports are written to:

```text
grails-test-report/build/reports/tests/test/index.html
grails-test-report/build/reports/tests/test.md
grails-test-report/build/reports/tests/integrationTest/index.html
grails-test-report/build/reports/tests/integrationTest.md
grails-test-report/build/reports/tests/combined/index.html
grails-test-report/build/reports/tests/combined.md
```

## Unit Tests Only

To run the standard unit tests and generate the unit test reports:

```bash
./gradlew :grails-test-report:test --continue
```

To regenerate the reports from existing unit test results without running tests:

```bash
./gradlew :grails-test-report:testAggregateTestReport --continue
./gradlew :grails-test-report:markdownAggregateTestReport --continue
```

## Integration Tests Only

To run integration tests and generate the integration test reports:

```bash
./gradlew :grails-test-report:integrationTest --continue
```

To regenerate the reports from existing integration test results without running tests:

```bash
./gradlew :grails-test-report:integrationTestAggregateTestReport --continue
./gradlew :grails-test-report:markdownIntegrationTestAggregateTestReport --continue
```

## Combined Tests

To run both `test` and `integrationTest` tasks and generate the combined reports:

```bash
./gradlew :grails-test-report:combined --continue
```

To regenerate reports from existing `test` and `integrationTest` results without running tests:

```bash
./gradlew :grails-test-report:combinedAggregateTestReport --continue
./gradlew :grails-test-report:markdownCombinedAggregateTestReport --continue
```

## Refresh Stale Results

If you want the reports to reflect a clean run rather than previously generated XML files, run `clean` first and use `check` or one of the phase runner tasks:

```bash
./gradlew clean :grails-test-report:check --continue
```

## Targeted Runs

For a faster workflow, run a specific project or spec first, then generate the aggregate report:

```bash
./gradlew :grails-data-hibernate7-core:test --tests "grails.gorm.tests.BasicCollection*" --continue
./gradlew :grails-test-report:check --continue
```

The Markdown summaries use available XML results from each included project's `build/test-results/test` and `build/test-results/integrationTest` directories.

Because the source `Test` tasks are finalized by the aggregate report tasks, running a targeted task such as `./gradlew :grails-data-hibernate7-core:test` also attempts to refresh the matching aggregate reports. Those reports include all XML files currently present under the included projects, so run `clean` first when stale results should be excluded.
