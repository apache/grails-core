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

# Development

## Useful Custom Gradle tasks

These tasks can be run like so:

`./gradlew publishGuide`

* `aggregateViolations` - runs every code style and code analysis check and writes Markdown summaries to `build/reports/violations`; pass `--continue` so the summaries are written even when a check fails
* `cleanViolationReports` - deletes the code style and code analysis reports so the next `aggregateViolations` re-analyzes every module; the root `clean` runs it. Otherwise, checks for unchanged modules stay up to date and their previous results are reused
* `codeStyle` - runs all code style checks
* `publishGuide` - generates the user guide in the `grails-doc/build/original-guide`
* `validateRepositoryConventions` - validates agent skill metadata, GitHub Action references, container image digests, and duplicate i18n message keys, writing `build/reports/violations/REPOSITORY_CONVENTIONS.md`

## Various properties that control which tasks to run

These can be set on the command line like so:

`./gradlew check -PskipCodeStyle`

* `gebAtCheckWaiting` - enables Geb atCheckWaiting
* `onlyCoreTests` - runs tests that do not include mongo, hibernate, or functional
* `onlyFunctionalTests` - runs only grails-test-examples/* tests
* `onlyHibernate5Tests` - runs only a hibernate5 related test
* `onlyHibernate7Tests` - runs only a hibernate7 related test
* `onlyMongodbTests` - runs only a mongodb related test
* `onlyNeo4jTests` - runs only a neo4j related test
* `onlyRedisTests` - runs only redis related tests
* `onlySpringSecurityTests` - runs only spring security related tests
* `serializeMongoTests` - if true, only integration tests from one mongo project will run at a time
* `skipCodeAnalysis` - does not run code analysis checks (PMD and SpotBugs); code style checks still run
* `skipCodeStyle` - does not run code style checks (CodeNarc and Checkstyle) or code analysis checks (PMD and SpotBugs)
* `skipCoreTests` - does not run the "core" tests
* `skipFunctionalTests` - does not run the functional tests
* `skipHibernate5Tests` - does not run hibernate5 related tests
* `skipHibernate7Tests` - does not run hibernate7 related tests
* `skipMongodbTests` - does not run mongo related tests
* `skipNeo4jTests` - does not run neo4j related tests
* `skipRedisTests` - does not run redis related tests
* `skipSpringSecurityTests` - does not run spring security related tests
* `skipTests` - no tests will run

## Code analysis

PMD and SpotBugs run only in modules that opt in from their own `build.gradle`:

```groovy
grailsCodeAnalysis {
    enablePmd()
    enableSpotbugs()
}
```

Each call configures the tool immediately, so the rest of the module's build script can customize its tasks directly, for example `tasks.named('pmdMain') { ... }`.

These properties change that for a single run:

* `grails.code-analysis.enabled.pmd` - `true` or `false` turns PMD on or off for every module. When set, it wins over both the module opt-ins and `grails.code-analysis.enabled.pmd.projects`
* `grails.code-analysis.enabled.pmd.projects` - comma-separated project paths that also run PMD, for example `:grails-core,:grails-web-core`
* `grails.code-analysis.enabled.spotbugs` - `true` or `false` turns SpotBugs on or off for every module. When set, it wins over both the module opt-ins and `grails.code-analysis.enabled.spotbugs.projects`
* `grails.code-analysis.enabled.spotbugs.projects` - comma-separated project paths that also run SpotBugs
* `grails.code-analysis.ignoreFailures` - collects the reports without failing the build

```bash
./gradlew aggregateAnalysisViolations --continue -Pgrails.code-analysis.enabled.spotbugs=true -Pgrails.code-analysis.ignoreFailures=true
```

## Test slicing

Test classes tagged with `@spock.lang.Tag('some-tag')` can be run separately.\
Tags are inherited from superclasses, so a test class is also included if any of its ancestors is tagged.

Example:

```bash
./gradlew iT -PincludeTestTags=geb
```

Available project properties:

* `includeTestTags` - comma-separated list of test tags to include
* `excludeTestTags` - comma-separated list of test tags to exclude

Example with multiple tags:

```bash
./gradlew iT -PincludeTestTags=geb,api
```

## Environment variables

* `DO_NOT_CACHE_TESTS` - set to `1` (or any truthy value) to force every `Test` task to run
  every invocation, without needing `--rerun-tasks`. This skips both Gradle's build cache
  and the `up-to-date` check for tests, while leaving the rest of the build (compilation,
  resource processing, etc.) cacheable. Useful when chasing flaky tests that depend on
  test execution order across runs.

  ```
  DO_NOT_CACHE_TESTS=1 ./gradlew :grails-gsp:test
  ```

## Start a mongo docker container (containers will start by default)
`docker run -d  --name mongo-on-docker  -p 27017:27017 mongo`