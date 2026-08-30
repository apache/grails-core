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
11. **Every code touch must update all tests for the changed class** - When a class is modified, find and update every test that covers it — unit, integration, and TCK. Do not leave any existing test out of sync with the new code.
12. **Clean violations before commit** - Before every automated commit, run `./gradlew clean aggregateViolations :grails-test-report:check --continue` from the root and ensure that `build/reports/violations/CHECKSTYLE_VIOLATIONS.md`, `build/reports/violations/CODENARC_VIOLATIONS.md`, `build/reports/violations/PMD_VIOLATIONS.md`, and `build/reports/violations/SPOTBUGS_VIOLATIONS.md` report no issues. Also review the test result reports under `grails-test-report/build/reports/tests/` and ensure there are no failures. The aggregate reports are wired as test finalizers and will be attempted after failures, but `--continue` is required for comprehensive full-suite reports.
13. **Mandatory test coverage** - Any class touched in a commit MUST be covered with tests that verify all behavior. You must run ALL tests in the affected module(s) and ensure they pass before committing.
14. **The BOM must manage the latest version** - `validateDependencyVersions` enforces that the BOM (`dependencies.gradle`) manages a version `>=` every transitively-resolved version. When it fails, **bump the version in `dependencies.gradle`** so the BOM wins — never silence it with `allowedBomOverrides` or an exclusion unless there is an explicit, documented conflict or an agreed-upon workaround. See [Dependency Management](#dependency-management).
15. **GitHub Actions must use ASF-approved pins** - Every third-party action SHA must appear in the ASF allowlist. See [GitHub Actions](#github-actions).

## Available Skills

> **AI AGENTS - MANDATORY**: Before writing or modifying any code, you **MUST** read the relevant skill file(s) below. Do not write Groovy/Grails/Gradle code without first loading these instructions:
> - Writing Grails code → Read `.agents/skills/grails-developer/SKILL.md`
> - Writing Groovy code → Read `.agents/skills/groovy-developer/SKILL.md`
> - Writing Java code → Read `.agents/skills/java-developer/SKILL.md`
> - Writing or changing Gradle builds → Read `.agents/skills/gradle-developer/SKILL.md`
> - Upgrading applications to Grails 8 → Read `.agents/skills/grails-8-upgrade/SKILL.md`
> - Writing Hibernate code → Read `.agents/skills/hibernate-developer/SKILL.md`
> - Fixing style/analysis violations → Read `.agents/skills/violation-fixer/SKILL.md`
> - Fixing broken test → Read `.agents/skills/test-fixer/SKILL.md`
>
> Use your file reading capability to load the skill content before proceeding with any code changes.

| Skill | Path | Use For |
|-------|------|---------|
| **grails-developer** | `.agents/skills/grails-developer/SKILL.md` | Current Grails apps, GORM, controllers, views |
| **groovy-developer** | `.agents/skills/groovy-developer/SKILL.md` | Groovy 5 syntax, closures, DSLs, Spock |
| **gradle-developer** | `.agents/skills/gradle-developer/SKILL.md` | Gradle 9 builds, BOM/platforms, convention plugins, wrappers |
| **grails-8-upgrade** | `.agents/skills/grails-8-upgrade/SKILL.md` | Upgrading Grails applications from 7.x to 8 |
| **java-developer** | `.agents/skills/java-developer/SKILL.md` | Java 21 features, Groovy interop |
| **hibernate-developer** | `.agents/skills/hibernate-developer/SKILL.md` | Hibernate 7 mapping, binders, generators |
| **violation-fixer** | `.agents/skills/violation-fixer/SKILL.md` | Fix style/analysis violations (CodeNarc, Checkstyle, PMD, SpotBugs) |
| **test-fixer** | `.agents/skills/test-fixer/SKILL.md` | Aggregate and fix test failures |
| **mono-repo-integration** | `.agents/skills/mono-repo-integration/SKILL.md` | Merge a standalone Grails plugin repository into this monorepo |

## Technology Stack

| Component | Version |
|-----------|---------|
| JDK | 21+ (baseline 21) |
| Groovy | 5.0.x |
| Spring Boot | 4.1.x |
| Spring Framework | 7.0.x |
| Spock | 2.4-groovy-5.0 |
| Gradle | 9.6.x |
| Jakarta EE | 10 |

## Project Structure

This repository contains multiple independent Gradle projects:

| Project | Description | Build Command |
|---------|-------------|---------------|
| **grails-core** (root) | Main framework with 60+ modules | `./gradlew build` |
| **build-logic/** | Gradle convention plugins for the build | `cd build-logic && ./gradlew build` |
| **grails-gradle/** | Grails Gradle plugins | `cd grails-gradle && ./gradlew build` |
| **grails-forge/** | Application generator (like Spring Initializr) | `cd grails-forge && ./gradlew build` |
| **end-to-end/** | End-to-end tests consuming published Grails artifacts (see `end-to-end/README.md` for required setup) | `cd end-to-end && ./gradlew check` |

Each project has its own `settings.gradle` and independent build. When working on a specific project, run Gradle commands from that project's directory.

## Dependency Management

All managed dependency versions live in `dependencies.gradle` (the single source of truth for the BOM projects). The `validateDependencyVersions` task — run automatically in CI — enforces the rules below.

- **The BOM must manage the latest (winning) version.** Validation fails when a transitive dependency resolves to a version *newer* than the BOM manages. The fix is to **bump the version in `dependencies.gradle`** so the BOM's version is `>=` everything on the classpath and stays authoritative. This is the *purpose* of the check — keeping the BOM ahead of its transitives.
- **Do not suppress validation to work around a bump.** `allowedBomOverrides` (per-project ext) and dependency exclusions are reserved for an explicit, documented conflict or an agreed-upon workaround — never as a shortcut to silence a version the BOM should simply manage. Comment the reason when you must use one.
- **A dependency managed in more than one BOM must use the *same* version everywhere.** Versions appear in `gradleBomDependencyVersions` (build tooling / `grails-gradle-bom`), `bomDependencyVersions` (`grails-bom`), and per-BOM `customBomVersions` blocks (e.g. `grails-micronaut-bom`). `grails-bom` re-declares the gradle-BOM constraints, and the Micronaut/Hibernate BOMs are consumed via `enforcedPlatform`. Declaring one coordinate (e.g. `org.ow2.asm:asm`) at two different versions across these maps produces irreconcilable strict constraints and breaks `enforcedPlatform` resolution. Pin it once, consistently.
- **Prefer inheriting from the Spring Boot BOM.** Do not re-pin a coordinate that `spring-boot-dependencies` (4.1.x) already manages unless you are intentionally overriding it to a newer version (e.g. a security fix); note the reason inline.

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
| Build docs | `./gradlew :grails-doc:publishGuide -x aggregateGroovydoc` |
| Debug | `./gradlew bootRun --debug-jvm` |

## GitHub Actions

Apache GitHub Actions policy blocks third-party actions unless they are on the organization allowlist. A workflow that uses an unlisted `uses:` SHA fails at **startup** before any job runs.

The allowlist source of truth is:

https://github.com/apache/infrastructure-actions/blob/main/approved_patterns.yml

Rules:

- Pin every third-party action to a **full commit SHA** that appears in that file, with a trailing `# version` comment.
- `actions/*`, `github/*`, and `apache/*` are allowed by namespace. Still SHA-pin them for supply-chain consistency.
- Do not use a newer SHA, tag, or major version until it is on the allowlist. If you need a new pin, open a PR against `apache/infrastructure-actions` (`actions.yml`, not the generated `approved_patterns.yml`).
- Before adding or bumping a `uses:` line, search `approved_patterns.yml` for that action and copy an approved SHA.

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
4. **Clean violations**: Before committing, run `./gradlew clean aggregateViolations` from the root and ensure that `build/reports/violations/CHECKSTYLE_VIOLATIONS.md`, `build/reports/violations/CODENARC_VIOLATIONS.md`, `build/reports/violations/PMD_VIOLATIONS.md`, and `build/reports/violations/SPOTBUGS_VIOLATIONS.md` have no issues.
5. **Verify test coverage**: Ensure any touched class is covered by tests verifying all behavior. You must run ALL tests in the affected module(s) and ensure they pass before submission.
6. **Squash commits** into a single meaningful commit message
6. **Reference issues** in PR description (e.g., "Fixes #1234")

### Review Process

| Change Type | Review Policy | Reviewers | Wait Period |
|-------------|---------------|-----------|-------------|
| Build/CI changes | Commit then Review | - | - |
| Documentation | Commit then Review (obvious fixes) | 1 minimum | - |
| Groovy/Spring dependency changes | Review then Commit | 2-3 required | 3 days (weekend) / 1 day (weekday) |
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

Security model: [SECURITY.md](./SECURITY.md) → [THREAT_MODEL.md](./THREAT_MODEL.md). Agents that
scan this repository should consult `SECURITY.md` and the linked `THREAT_MODEL.md` for the project's
threat model — in-scope / out-of-scope declarations, the security properties claimed and disclaimed,
and known non-findings — before reporting issues.

## Resources

- **Grails Guide**: https://grails.apache.org/docs/latest/guide/single.html
- **Groovy 5 Docs**: https://groovy-lang.org/documentation.html#all-versions (select latest 5.0.x)
- **Spock 2.4 Docs**: https://spockframework.org/spock/docs/2.4/all_in_one.html
- **GORM Docs**: https://grails.apache.org/docs/latest/grails-data/
- **Issues**: https://github.com/apache/grails-core/issues
- **Slack**: https://grails.slack.com
