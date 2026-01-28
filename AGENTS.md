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
2. **Use `@GrailsCompileStatic`** - Not plain `@CompileStatic` in Grails classes
3. **Use `GrailsWebRequest.lookup()`** - For thread-safe request context in tests
4. **No wildcard imports** - Use explicit imports
5. **4 spaces, no tabs** - See `.editorconfig`
6. **Apache license header** - Required on all new source files

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
@GrailsCompileStatic
class MyService { }

// DON'T: Wildcard imports
// import java.util.*  ❌

// DON'T: javax packages
// import javax.servlet.*  ❌ → use jakarta.servlet.*
```

## Test Isolation

> **WARNING**: Tests run in parallel (`maxParallelForks > 1`). Static state causes flaky tests.

- Use `GrailsWebRequest.lookup()` for thread-local context
- Clear artefacts: `grailsApplication.artefactInfo.clear()`
- Use `@ResourceLock` for shared resources

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

## Common Issues

| Problem | Solution |
|---------|----------|
| Out of memory | `export GRADLE_OPTS="-Xms2G -Xmx5G"` |
| Container missing | Use `-PskipTests` or install Docker/Podman |
| Flaky tests | Check static state pollution, use `@ResourceLock` |
| Cache issues | `./gradlew --rerun-tasks` |
| Deprecation details | `./gradlew <task> --warning-mode all` |

## Security

Report vulnerabilities to: `security@grails.org` (NOT public issues)

## Resources

- **Grails 7 Guide**: https://docs.grails.org/latest/guide/single.html
- **Groovy 4 Docs**: https://docs.groovy-lang.org/docs/groovy-4.0.30/html/documentation/
- **Spock 2.3 Docs**: https://spockframework.org/spock/docs/2.3/all_in_one.html
- **GORM Docs**: https://gorm.grails.org/latest/
- **Issues**: https://github.com/apache/grails-core/issues
- **Slack**: https://grails.slack.com
