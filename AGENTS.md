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

# Agent Guide for grails-core

> **IMPORTANT**: This is the Grails Framework source repository (60+ modules), NOT a Grails application.
> For building Grails apps, see `.agents/skills/grails-developer/SKILL.md`.

## Quick Reference

```bash
# Build (no tests)
./gradlew build -PskipTests

# Build single module
./gradlew :grails-core:build

# Run tests
./gradlew :<module>:test
./gradlew :<module>:test --tests "com.example.SomeSpec"

# Style check
./gradlew codeStyle

# Dependency health (duplicate classes on a classpath fail the build)
./gradlew buildHealth

# Out of memory? Set:
export GRADLE_OPTS="-Xms2G -Xmx5G"
```

## Critical Rules

1. **Use `jakarta.*` NOT `javax.*`** - All packages migrated to Jakarta EE 10
2. **Use `@GrailsCompileStatic`** - Not plain `@CompileStatic` in Grails artefact classes
3. **Use `GrailsWebRequest.lookup()`** - For thread-safe request context in tests
4. **No wildcard imports** - Use explicit imports
5. **4 spaces, no tabs** - See `.editorconfig`
6. **Apache license header** - Required on all new source files
7. **New features require docs** - Any user-facing change must include or update documentation in `grails-doc`; do not merge features without corresponding doc coverage
8. **No internal APIs in docs** - Only document public APIs; never reference internal or package-private classes and methods in user-facing documentation
9. **Test via public APIs** - Tests must exercise behavior through the same APIs an end user calls; never invoke internal implementations, package-private methods, or bypass the public surface directly
10. **Always review and extend tests** - Review existing unit and functional tests before making changes; every code change must include new or enhanced tests that cover the affected behavior
11. **The BOM must manage the latest version** - `validateDependencyVersions` enforces that the BOM (`dependencies.gradle`) manages a version `>=` every transitively-resolved version. When it fails, **bump the version in `dependencies.gradle`** so the BOM wins — never silence it with `allowedBomOverrides` or an exclusion unless there is an explicit, documented conflict or an agreed-upon workaround. See [Dependency Management](#dependency-management).
12. **GitHub Actions must use ASF-approved references** - Every third-party action SHA must appear in the ASF allowlist; `actions/*` use the full tag of their latest release and `apache/*` use version or branch references, never SHAs. See [GitHub Actions](#github-actions).
13. **Workflows cache Gradle through one policy** - Every `setup-gradle` step takes `cache-read-only` from `.github/actions/gradle-cache-policy`; never write `true` or `false` there, never cache `~/.gradle` with `actions/cache`, and never pass `cache-writer` outside the core build. See [Gradle caching](#gradle-caching).
14. **Workflows run tests through one policy** - Every workflow that runs tests calls `.github/workflows/ci-policy.yml` as its first job, `policy`, and gates its test jobs on `needs.policy.outputs.run-tests`; never test `github.event.head_commit.message` in a workflow. Every job of every workflow that reacts to a pull request is skipped by the `no ci` label, and on a draft pull request unless it carries the `run ci` label; the one exception is `cancel-unneeded-pr-runs.yml`, which the `no ci` label runs. See [CI policy](#ci-policy).

## Available Skills

> **AI AGENTS - MANDATORY**: Before writing or modifying any code, you **MUST** read the relevant skill file(s) below. Do not write Groovy/Grails code without first loading these instructions:
> - Writing Grails code → Read `.agents/skills/grails-developer/SKILL.md`
> - Writing Groovy code → Read `.agents/skills/groovy-developer/SKILL.md`
> - Writing Java code → Read `.agents/skills/java-developer/SKILL.md`
> - Upgrading applications to or within Grails 7 → Read `.agents/skills/grails-upgrade/SKILL.md`
>
> Use your file reading capability to load the skill content before proceeding with any code changes.

| Skill | Path | Use For |
|-------|------|---------|
| **grails-developer** | `.agents/skills/grails-developer/SKILL.md` | Grails 7 apps, GORM, controllers, views |
| **groovy-developer** | `.agents/skills/groovy-developer/SKILL.md` | Groovy 4 syntax, closures, DSLs, Spock |
| **java-developer** | `.agents/skills/java-developer/SKILL.md` | Java 17 features, Groovy interop |
| **grails-upgrade** | `.agents/skills/grails-upgrade/SKILL.md` | Upgrading Grails applications from Grails 6 to 7, and between 7.x releases |

## Technology Stack

| Component | Version |
|-----------|---------|
| JDK | 17+ (baseline 17) |
| Groovy | 4.0.x |
| Spring Boot | 3.5.x |
| Spring Framework | 6.2.x |
| Spock | 2.3-groovy-4.0 |
| Gradle | 8.14.x |
| Jakarta EE | 10 |

## Project Structure

This repository contains multiple independent Gradle projects:

| Project | Description | Build Command |
|---------|-------------|---------------|
| **grails-core** (root) | Main framework with 60+ modules | `./gradlew build` |
| **build-logic/** | Gradle convention plugins for the build | `cd build-logic && ../gradlew build` |
| **grails-gradle/** | Grails Gradle plugins | `cd grails-gradle && ./gradlew build` |
| **grails-forge/** | Application generator (like Spring Initializr) | `cd grails-forge && ./gradlew build` |

Each project has its own `settings.gradle` and independent build. When working on a specific project, run Gradle commands from that project's directory.

## Dependency Management

All managed dependency versions live in `dependencies.gradle` (the single source of truth for the BOM projects). The `validateDependencyVersions` task — run automatically in CI — enforces the rules below.

- **The BOM must manage the latest (winning) version.** Validation fails when a transitive dependency resolves to a version *newer* than the BOM manages. The fix is to **bump the version in `dependencies.gradle`** so the BOM's version is `>=` everything on the classpath and stays authoritative. This is the *purpose* of the check — keeping the BOM ahead of its transitives.
- **Do not suppress validation to work around a bump.** `allowedBomOverrides` (per-project ext) and dependency exclusions are reserved for an explicit, documented conflict or an agreed-upon workaround — never as a shortcut to silence a version the BOM should simply manage. Comment the reason when you must use one.
- **A dependency managed in more than one BOM must use the *same* version everywhere.** Versions appear in `gradleBomDependencyVersions` (build tooling / `grails-gradle-bom`), `bomDependencyVersions` (`grails-bom`), and per-BOM `customBomVersions` blocks (e.g. `grails-micronaut-bom`). `grails-bom` re-declares the gradle-BOM constraints, and the Micronaut/Hibernate BOMs are consumed via `enforcedPlatform`. Declaring one coordinate (e.g. `org.ow2.asm:asm`) at two different versions across these maps produces irreconcilable strict constraints and breaks `enforcedPlatform` resolution. Pin it once, consistently.
- **Prefer inheriting from the Spring Boot BOM.** Do not re-pin a coordinate that `spring-boot-dependencies` (3.5.x) already manages unless you are intentionally overriding it to a newer version (e.g. a security fix); note the reason inline.
- **No two dependencies may provide the same class.** The `buildHealth` task (the `com.autonomousapps.build-health` settings plugin, configured in `gradle/dependency-analysis.settings.gradle` and applied to every build in the repository) fails when two dependencies on a classpath provide the same fully qualified class name that the module's own code references, because compile and runtime behavior then depend on classpath order. The Build Health workflow runs it on every push and pull request, alongside the code style checks. Resolve a report by removing or excluding the redundant dependency; its other categories (unused or transitive dependencies, wrong configurations) are advice only.

## Key Modules

**Core**: `grails-core`, `grails-bootstrap`, `grails-spring`, `grails-common`

**Web**: `grails-web-core`, `grails-web-mvc`, `grails-controllers`, `grails-url-mappings`, `grails-interceptors`

**GORM**: `grails-datastore-core`, `grails-datamapping-core`, `grails-domain-class`, `grails-validation`, `grails-databinding`

**Views**: `grails-views-core`, `grails-views-gson`, `grails-views-markup`

**Testing**: `grails-testing-support-core`, `grails-testing-support-web`, `grails-geb`

**Other**: `grails-bom` (dependency management), `grails-doc`, `grails-shell-cli`, `grails-forge`

## Artefact Types

| Type | Pattern | Handler |
|------|---------|---------|
| Domain | `**/domain/**/*.groovy` | `DomainClassArtefactHandler` |
| Controller | `**/*Controller.groovy` | `ControllerArtefactHandler` |
| Service | `**/*Service.groovy` | `ServiceArtefactHandler` |
| TagLib | `**/*TagLib.groovy` | `TagLibArtefactHandler` |
| Interceptor | `**/*Interceptor.groovy` | `InterceptorArtefactHandler` |

## Code Patterns

### Spock Test Structure
```groovy
class MyServiceSpec extends Specification implements ServiceUnitTest<MyService> {
    def "feature description"() {
        given: "preconditions"
        def input = "test"

        when: "action"
        def result = service.process(input)

        then: "assertions"
        result != null
        result.status == "OK"
    }
}
```

### Mocking
```groovy
def repo = Mock(BookRepository)
1 * repo.save(_) >> savedBook  // expect 1 call, return savedBook
```

### Data-Driven Tests
```groovy
@Unroll
def "#a + #b == #c"() {
    expect: a + b == c
    where:
    a | b || c
    1 | 2 || 3
    4 | 5 || 9
}
```

### Framework Access
```groovy
// Thread-safe request context
GrailsWebRequest webRequest = GrailsWebRequest.lookup()

// Artefact registry
grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)
```

## Groovy Style

```groovy
// DO: Safe navigation
book?.author?.name

// DO: Elvis operator
name ?: 'Unknown'

// DO: GStrings
"Hello ${user.name}"

// DO: Spread operator
books*.title

// DO: Static compilation
@GrailsCompileStatic  // or @CompileStatic for non-artefact classes
class MyService { }

// DON'T: Wildcard imports
// import java.util.*  ❌

// DON'T: javax packages
// import javax.servlet.*  ❌ → use jakarta.servlet.*

// DON'T: Section separator or grouping comments
// // --- Domain classes ---  ❌
// // ===== Helpers =====     ❌
```

## Test Isolation

> **WARNING**: Tests run in parallel (`maxParallelForks > 1`). Static state that is not properly reset in test cleanup can cause flaky tests in subsequent tests within the same fork.

- Use `GrailsWebRequest.lookup()` for thread-local context
- Clear artefacts: `grailsApplication.artefactInfo.clear()`
- Use `@Shared` for fields that should be reused by multiple feature methods in a Spec

## Build Commands

| Task | Command |
|------|---------|
| Build (no tests) | `./gradlew build -PskipTests` |
| Build module | `./gradlew :grails-core:build` |
| Test module | `./gradlew :grails-core:test` |
| Single test | `./gradlew :module:test --tests "pkg.MySpec"` |
| Single feature | `./gradlew :module:test --tests "pkg.MySpec.feature name"` |
| Force rerun | `./gradlew :module:test --rerun-tasks` |
| Style check | `./gradlew codeStyle` |
| Dependency health | `./gradlew buildHealth` |
| Build docs | `./gradlew :grails-doc:publishGuide -x aggregateGroovydoc` |
| Debug | `./gradlew bootRun --debug-jvm` |

## GitHub Actions

Apache GitHub Actions policy blocks third-party actions unless they are on the organization allowlist. A workflow that uses an unlisted `uses:` SHA fails at **startup** before any job runs.

The allowlist source of truth is:

https://github.com/apache/infrastructure-actions/blob/main/approved_patterns.yml

Rules:

- Pin every third-party action, including `github/*`, to a **full commit SHA** that appears in that file, with a trailing `# version` comment.
- `actions/*` and `apache/*` (including our own `apache/grails-github-actions`) are allowed by namespace, so every version is approved. Reference `actions/*` by the full tag of the latest release (for example `actions/checkout@v7.0.1`, not `@v7`), and our own actions by branch (for example `apache/grails-github-actions/pre-release@asf`). Never pin either by SHA.
- Do not use a newer third-party SHA, tag, or major version until it is on the allowlist. If you need a new pin, open a PR against `apache/infrastructure-actions` (`actions.yml`, not the generated `approved_patterns.yml`).
- Before adding or bumping a third-party `uses:` line, search `approved_patterns.yml` for that action and copy an approved SHA.

### CI policy

GitHub-hosted runner time is shared with every other Apache project, so the workflows run tests only where the result is worth the runner time:

- A draft pull request runs nothing unless it carries the `run ci` label, and a pull request labelled `no ci` runs nothing at all: every workflow that reacts to a pull request skips all of its jobs, except `cancel-unneeded-pr-runs.yml`, described below. The Release Drafter (`release-notes.yml`) does not react to pull requests: the action it pins cannot label them, and it builds the draft from merged pull requests, so it runs on pushes to a release branch. Marking a draft ready for review starts CI (the workflows list the `ready_for_review` trigger type, which is not a default). Labels and the draft flag are read when a run starts, so set them before pushing; adding `run ci` or removing `no ci` takes effect on the next push, or when the draft is marked ready, while adding `no ci` cancels the runs in flight (see below). A push to a draft, or to a `no ci` pull request, still cancels the run in flight for that pull request, as every push to a pull request or a branch does through the concurrency groups of the test workflows; the run that replaces it does nothing. A new workflow with a `pull_request` trigger lists the same `types` as its siblings and gates its jobs the same way.
- Merging a pull request, or labelling it `no ci`, cancels its runs in flight: `cancel-unneeded-pr-runs.yml` cancels the queued, pending and in-progress `pull_request` runs of every open pull request labelled `no ci`, and on a push to a release branch also those of each pull request the pushed commit merged that were created before the merge. A run is kept while an open pull request not labelled `no ci` has the same head commit, and the runs of a merged pull request labelled `run ci` are kept too: on a release branch below the default, the push after the merge builds without the tests, so the tests the label asked for on the pull request are the only ones the change gets there, and a late failure still calls for a follow-up fix. The workflow runs on every push to a release branch and when a pull request is labelled `no ci`, but a `pull_request` run from a fork gets a read-only token that cannot cancel runs, so labelling a fork's pull request skips the job, and that pull request's runs are cancelled by the next push to a release branch. For the same reason merges are found from the push rather than from the pull request being closed. It lists the unfinished `pull_request` runs of the repository once per sweep, not once per pull request, to spare the `GITHUB_TOKEN` rate limit.
- A push that changes a pull request dismisses the approvals given before it: `dismiss-stale-reviews.yml` dismisses every approval that was given to another commit than the head of the pull request. GitHub itself moves an approval to the new head after a push that leaves the diff of the pull request as it was, such as a rebase or a merge of the base branch, so those approvals are kept. The release branches do not require pull requests, so GitHub's own stale-review setting, which is part of that requirement, is not available. The workflow reacts to the Code Style workflow being requested for a pull request (`workflow_run`), not to the pull request, because a `pull_request` run from a fork gets a read-only token that cannot dismiss a review; it therefore runs from the default branch, also for a draft or a pull request labelled `no ci`, and it never checks out the pull request. Renaming the Code Style workflow, or dropping its `pull_request` trigger, stops it. The workflow compares no diff: an approval GitHub has not moved is dismissed unless its commit has the very tree of the head, so a push that only changes a space, moves an edit to another method, or changes the mode of a file dismisses it. GitHub starts no `pull_request` workflow for a commit whose message asks to skip them (`[skip ci]` and its variants) or for a pull request that conflicts with its base branch, so the workflow also looks at every open pull request once an hour (`schedule`); an approval can outlive such a push by at most that long. A manual run looks at every open pull request too and only reports, unless its `dry-run` input is cleared.
- Every workflow that runs tests calls `.github/workflows/ci-policy.yml` as its first job, `policy`, and gates its test jobs (and the test steps of its build jobs) on `needs.policy.outputs.run-tests`. Never test `github.event.head_commit.message` in a workflow: a `pull_request` payload has no such field, so that test silently ran the tests on every pull request. The policy reads the head commit of the push, of the pull request, or of the dispatched branch, and matches the marker against the start of the message's first line. The read is retried with a growing pause; when it still fails, the tests run, as they did before the policy existed, and a warning annotation and the step summary say so, so an API outage costs runner time rather than a test result.
- The tests run by default only on the default branch and the release branches above it, and there a commit message starting with `[skip tests]` skips them. Everywhere else, that is on a release branch below the default (7.0.x, 7.1.x and 7.2.x while 8.0.x is the default) and on a pull request whose base is a side branch rather than a release branch, the tests run only on request: when the commit message starts with `[run tests]`, when the pull request carries the `run ci` label, or when a manual run (`workflow_dispatch`) in `apache/grails-core` has its `run-tests` input ticked. Otherwise the run builds, and on a push publishes snapshots, without tests, exactly like a `[skip tests]` commit; a `[skip tests]` commit also wins over the label and the input. The label is the way to test a backport pull request without rewriting its head commit, and the input is how the commit a release is cut from gets tested (see RELEASE.md); both are read when the run starts. A marker anywhere but at the start of the first line is ignored. The rule follows the default branch: when a new one is promoted, the branches below it stop running tests by default without an edit. The older release branches are kept quiet on purpose: a backport lands as one push to the oldest branch and a merge-up to each branch above it, and its tests belong on the pull request, labelled `run ci`, not on every push after it. Side branches are unreviewed development work (see the review process in CONTRIBUTING.md); their tests run when the branch is proposed to a release branch. In a fork, where the runner time is the fork owner's and not the ASF's, a manual run runs the tests unless the commit message starts with `[skip tests]`.
- The jobs that run no tests (dependency validation, the build-without-tests steps, code style and build health) still run on every push to a release branch and on every non-draft pull request; RAT and CodeQL run on every push to a release branch and on every non-draft pull request into one, since their `pull_request` triggers list only release-branch bases. Only the `no ci` label, or a draft without `run ci`, stops them. The snapshot publish runs on every push to a release branch and on a manual run (the docs publish only on a push), never on a pull request.
- The publish jobs in `gradle.yml` (`publishGradle`, `publish`, `publishForge`, `verifyWrapper`, `docs`) run on `always()` and check the result of each job they need explicitly. The explicit checks are needed because the implicit `success()` check covers every job upstream, so a skipped test job would skip them although their own needs succeeded, which is what silently skipped the Forge and docs publish on every `[skip tests]` push. `always()` rather than `!cancelled()` because a newer push to the same branch cancels the run in flight: GitHub stops only the jobs whose `if` turns false, so the build and test jobs stop but a publish that has started completes, since an interrupted upload leaves the snapshot repository with modules from two commits. `publishGradle` waits for every build job (`buildGradle`, `buildForge` and `validateDependencies`), so a cancel that arrives before the builds have finished publishes nothing of that commit. A later cancel lets the publish jobs whose needs have already succeeded or been skipped run: on a run without tests that is the whole chain, and on a run whose tests are still running only `publishGradle`, because a cancelled test job fails the explicit checks of `publish` and of everything after it; a cancel after every test has passed lets the whole chain run as well. The replacing run is pending until the cancelled run has finished every job, so two publishes of the same branch never overlap, and it then publishes the newer commit over whatever the cancelled run published. A normal cancel (the Cancel button or `gh run cancel`) does not stop a running `always()` job either, so a publish stuck on a slow or failing upload, which the retry step around it lets run for hours, holds the run and the branch's concurrency group until it ends; force-cancel the run to stop it: `gh api -X POST repos/apache/grails-core/actions/runs/<run id>/force-cancel`. A job added after the tests follows the same pattern.
- A workflow that gains a test job gets `needs: [ policy ]` and `if: ${{ needs.policy.outputs.run-tests == 'true' }}` on that job; a workflow that gains a non-test job copies the label-and-draft `if` from its sibling jobs. In `gradle.yml`, a new test job also goes into the `needs` of `publish`, with a `success` or `skipped` check, and a new job that builds or validates without tests into those of `publishGradle`, with a `success` check; a job left out is not waited for, so a commit whose job failed or was cancelled is published anyway.

### Gradle caching

GitHub keeps about 10 GB of Actions cache per repository and evicts the least recently used entries. A run can restore entries only from its own branch and the default branch, and a pull request also from its base branch. The workflows are arranged around those two limits:

- The Gradle User Home is cached only by `gradle/actions/setup-gradle`, with its default provider. Do not add an `actions/cache` step for `~/.gradle`, `cache-disabled`, or `cache-provider: basic`; release verification is the one job left uncached on purpose.
- Every `setup-gradle` step is preceded by the local action `.github/actions/gradle-cache-policy` and takes `cache-read-only` from its `read-only` output. A job that fetches this repository with a sparse checkout must list `.github/actions` in it, or the action is not there to run. Do not write `true` or `false` there: the policy lives in that action, so changing it means editing one file on the oldest release branch and merging up.
- Only the core build job (`build` in `gradle.yml`) passes `cache-writer`, from the one matrix entry per OS flagged `cache_writer: true`. A new job or workflow never passes it.
- The action lets a writer save only on a push or dispatch, only on a branch whose major version is at least the default branch's, and, for Windows and macOS, only on that major's first branch (`x.0.x`). With 8.0.x as the default branch, 7.x never writes, Linux writes on every 8.x and 9.x branch, and Windows and macOS write on 8.0.x and 9.0.x. The rule follows the default branch: when a new one is promoted, older majors stop writing without an edit.

Why it is this way:

- When every job saved on every push (about 25 entries and 3.5 GiB per push on 8.x), the cache stayed over its limit and evicted entries within hours. Jobs then started with an empty Gradle User Home and downloaded everything, and a single transient "Could not find" from Maven Central or the Gradle plugin portal failed them. Gradle does not retry a "not found".
- Minor branches cannot read their major's entries, only the default branch's, so an older major's entries serve only the branch that wrote them. Writing them took space the active lines needed.
- `setup-gradle` restores the newest entry whose key starts with the job's id before it falls back to the newest entry for the OS. With many writers, the core `build` job restored the `buildForge` job's entry and lacked artifacts only the core build uses. One writer per OS leaves one candidate.
- A writer saves about 0.9 GiB per OS and branch, so the rule keeps the cache near 7 GiB.

## Branch Naming (Auto-Labels PRs)

| Prefix | Label |
|--------|-------|
| `fix/` | bug |
| `feat/`, `feature/` | feature |
| `docs/` | documentation |
| `chore/`, `refactor/`, `test/`, `ci/`, `perf/`, `build/` | maintenance |
| `deps/` | deps |

## Pull Request Guidelines

1. **Fork & branch** from the target release branch (e.g., `7.0.x`)
2. **Run tests** before submitting: `./gradlew build --rerun-tasks`
3. **Run code style checks**: `./gradlew codeStyle`
4. **Run dependency health checks**: `./gradlew buildHealth`
5. **Squash commits** into a single meaningful commit message
6. **Reference issues** in PR description (e.g., "Fixes #1234")

### Review Process

| Change Type | Review Policy | Reviewers | Wait Period |
|-------------|---------------|-----------|-------------|
| Build/CI changes | Commit then Review | - | - |
| Documentation | Commit then Review (obvious fixes) | 1 minimum | - |
| Groovy/Spring dependency changes | Review then Commit | 2-3 required | 3 days (weekend) / 1 day (weekday) |
| Fixes that change a result on a maintenance or the next minor branch | Review then Commit | 2 required | - |
| All other changes | Review then Commit | 1 required | - |

See `CONTRIBUTING.md` for full details.

## Common Issues

| Problem | Solution |
|---------|----------|
| Out of memory | `export GRADLE_OPTS="-Xms2G -Xmx5G"` |
| Container missing | Use `-PskipTests` or install Docker/Podman |
| Flaky tests | Check static state pollution, ensure proper cleanup in tests |
| Cache issues | `./gradlew --rerun-tasks` |
| Deprecation details | `./gradlew <task> --warning-mode all` |


### Reporting Vulnerabilities

Please see the page of the [ASF Security Team](https://www.apache.org/security/) for further information and contact information.

## Resources

- **Grails 7 Guide**: https://grails.apache.org/docs/latest/guide/single.html
- **Groovy 4 Docs**: https://groovy-lang.org/documentation.html#all-versions (select latest 4.0.x)
- **Spock 2.3 Docs**: https://spockframework.org/spock/docs/2.3/all_in_one.html
- **GORM Docs**: https://grails.apache.org/docs/latest/grails-data/
- **Issues**: https://github.com/apache/grails-core/issues
- **Slack**: https://grails.slack.com
