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

## Available Skills

> **AI AGENTS - MANDATORY**: Before writing or modifying any code, you **MUST** read the relevant skill file(s) below. Do not write Groovy/Grails code without first loading these instructions:
> - Writing Grails code → Read `.agents/skills/grails-developer/SKILL.md`
> - Writing Groovy code → Read `.agents/skills/groovy-developer/SKILL.md`
> - Writing Java code → Read `.agents/skills/java-developer/SKILL.md`
>
> Use your file reading capability to load the skill content before proceeding with any code changes.

| Skill | Path | Use For |
|-------|------|---------|
| **grails-developer** | `.agents/skills/grails-developer/SKILL.md` | Grails 7 apps, GORM, controllers, views |
| **groovy-developer** | `.agents/skills/groovy-developer/SKILL.md` | Groovy 4 syntax, closures, DSLs, Spock |
| **java-developer** | `.agents/skills/java-developer/SKILL.md` | Java 17 features, Groovy interop |

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
4. **Squash commits** into a single meaningful commit message
5. **Reference issues** in PR description (e.g., "Fixes #1234")

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

## Resources

- **Grails 7 Guide**: https://grails.apache.org/docs/latest/guide/single.html
- **Groovy 4 Docs**: https://docs.groovy-lang.org/docs/groovy-4.0.30/html/documentation/
- **Spock 2.3 Docs**: https://spockframework.org/spock/docs/2.3/all_in_one.html
- **GORM Docs**: https://grails.apache.org/docs/latest/grails-data/
- **Issues**: https://github.com/apache/grails-core/issues
- **Slack**: https://grails.slack.com
