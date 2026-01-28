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

This file summarizes how to build/test the repo and the coding conventions
observed in this codebase. It is intended for automated coding agents.

Note: Do not commit this file to the repository; keep it local-only for agent use.

## Repository Overview
- Multi-module Gradle build; root Gradle wrapper at `./gradlew`.
- Primary languages: Groovy and Java (Spock for tests).
- JDK 17+ required (up to 21 supported); container runtime needed for full test suite.
- Root style settings live in `.editorconfig`.

## Technology Stack (Grails 7)
- **Spring Boot**: 3.5.x
- **Spring Framework**: 6.2.x
- **Groovy**: 4.0.x
- **Gradle**: 8.14.x
- **Spock**: 2.3-groovy-4.0 (main modules via BOM)
- **Jakarta EE**: 10 (migrated from javax.*)
- **Micronaut**: Optional via `grails-micronaut` plugin

### Spock Version Notes
- Main grails-core modules use **Spock 2.3-groovy-4.0** via the `grails-bom`
- Some standalone subprojects have older versions:
  - `grails-data-neo4j`: Spock 2.1-groovy-3.0 (defined in `gradle.properties`)
  - `grails-data-graphql`: Spock 2.1-groovy-3.0 (defined in `gradle.properties`)
- Always check the module's `build.gradle` or `gradle.properties` for actual Spock version

## Environment Setup

### Prerequisites
- A git client
- An IDE (IntelliJ IDEA recommended)
- JDK 17 or higher (use SDKMAN for easy setup: `sdk env .`)
- A container runtime (Docker/Podman) for running full test suite

### Initial Setup
```bash
# Clone the repository
git clone https://github.com/apache/grails-core.git
cd grails-core

# Set up JDK via SDKMAN (reads .sdkmanrc)
sdk env .

# Build without tests (faster initial build)
./gradlew build -PskipTests
```

### Memory Settings
If you encounter out-of-memory errors:
```bash
export GRADLE_OPTS="-Xms2G -Xmx5G"
```

## Build, Lint, and Test Commands
Use the Gradle wrapper from the repo root unless a module has its own wrapper.

### Build
- Build without tests: `./gradlew build -PskipTests`
- Full build with tests: `./gradlew build --rerun-tasks`
- Build a single module: `./gradlew :<module>:build`
- Run a single task with stacktrace: `./gradlew <task> --stacktrace`

### Test
- Run all tests for a module: `./gradlew :<module>:test`
- Run a single test class:
  `./gradlew :<module>:test --tests "com.example.SomeSpec"`
- Run a single test method/feature:
  `./gradlew :<module>:test --tests "com.example.SomeSpec.some feature"`
- Re-run tests even if up-to-date: `./gradlew :<module>:test --rerun-tasks`

Test reports are typically under:
- `build/reports/tests/test/index.html`
- `build/test-results/test` (XML)

### Code Style / Lint
- Run style checks: `./gradlew codeStyle`
- Java style is enforced by Checkstyle; Groovy by CodeNarc.

### Docs
- Build the user guide (skip groovydoc):
  `./gradlew :grails-doc:publishGuide -x aggregateGroovydoc`

### Runtime
- Run a Grails app in this repo (when applicable): `./gradlew bootRun`

## Code Style Guidelines
Follow the existing file patterns and the `.editorconfig` rules.

### Formatting
- Indentation: 4 spaces, no tabs.
- Insert a final newline at the end of files.
- Use spaces around operators.
- Keep line length reasonable; match surrounding file style.

### Imports
- Avoid wildcard imports (limit set to 999; explicit imports preferred).
- Group imports consistently within each file (typically by package groups).
- Keep static imports separate from regular imports.

### Naming
- Classes/interfaces/traits: UpperCamelCase.
- Methods/fields/locals: lowerCamelCase.
- Constants: UPPER_SNAKE_CASE (often `static final`).
- Spock specs: class name ends with `Spec` and feature names are descriptive.

### Types and Declarations
- Prefer explicit types for public APIs and fields used across modules.
- Use `def` in Groovy only where dynamic typing is intentional.
- Use `@CompileStatic` when adding performance-sensitive Groovy code and
  when matching existing patterns in the module.

### Groovy 4 Best Practices
- **Safe Navigation**: Use `?.` for null-safe property access: `book?.author?.name`
- **Elvis Operator**: Use `?:` for default values: `name ?: 'Unknown'`
- **Spread Operator**: Use `*.` for collection property access: `books*.title`
- **GStrings**: Use `"Hello ${user.name}"` for string interpolation
- **Closures**: Prefer closures for callbacks and DSLs; use `@DelegatesTo` for IDE support
- **Static Compilation**: Use `@CompileStatic` or `@GrailsCompileStatic` in production code
- **Traits**: Prefer traits over mixins (mixins are deprecated)
- **Type Checking**: Apply `@TypeChecked` when full static compilation isn't possible
- **Equality**: Remember `==` calls `equals()` in Groovy; use `is` for identity comparison
- **Records**: Use Groovy records (incubating) for immutable DTOs:
  ```groovy
  record Point(int x, int y) {}
  ```

### Groovy / Spock Conventions
- Use `given/when/then` or `setup/when/then` blocks in Spock tests.
- Use `thrown(SomeException)` in `then` blocks for exception assertions.
- Keep test data setup minimal and reuse shared fixtures with `@Shared`.

### Spock 2.3 Testing Patterns

**Basic test structure:**
```groovy
class BookServiceSpec extends Specification {
    def service = new BookService()

    def "find book by title"() {
        given: "a book title to search for"
        def title = "Grails Guide"

        when: "searching for the book"
        def book = service.findByTitle(title)

        then: "the book is found"
        book != null
        book.title == title
    }
}
```

**Data-driven tests with `@Unroll`:**
```groovy
@Unroll
def "addition #a + #b == #c"() {
    expect:
    a + b == c

    where:
    a | b || c
    1 | 3 || 4
    7 | 4 || 11
}
```

**Mocking and interaction testing:**
```groovy
def "save book calls repository"() {
    given:
    def repo = Mock(BookRepository)
    def service = new BookService(repo)

    when:
    service.save(new Book(title: "Test"))

    then:
    1 * repo.save(_)
}
```

**Spock 2.3 features:**
- Parallel execution: Enable with `runner.parallel.enabled=true` in config
- Static mocking: Use `SpyStatic()` for static methods
- Utilities: `MutableClock`, `verifyEach` for enhanced testing
- Use `@ResourceLock` to prevent parallel conflicts on shared resources

**Grails test mixins:**
- `ServiceUnitTest<T>` for service unit tests
- `ControllerUnitTest<T>` for controller unit tests
- `@Integration` and `@Rollback` for integration tests

### Error Handling
- Throw specific exceptions with clear messages when possible.
- Avoid swallowing exceptions; if caught, log or rethrow with context.
- Preserve existing exception types and messages used by public APIs.

### Logging
- Use existing logging mechanisms in each module (e.g., SLF4J in Java,
  `log` in Groovy classes) rather than `System.out`.

### Documentation and Headers
- New source files should include the Apache license header used elsewhere.
- Add comments only when behavior is non-obvious or subtle.

## Gradle and Module Guidance
- Prefer the root `./gradlew` unless a subproject explicitly requires its
  own wrapper.
- Keep module-specific changes inside their module unless a shared API or
  dependency is involved.
- Avoid changing build logic without understanding the multi-module layout.

## Practical Examples
- Single spec (as used in CI debugging):
  `./gradlew :grails-datamapping-core-test:test --tests "grails.gorm.services.multitenancy.database.DatabasePerTenantSpec"`
- Build only the core framework module:
  `./gradlew :grails-core:build`

## Environment Notes
- If tests fail due to missing container runtime, re-run with `-PskipTests`
  or set up a supported container runtime as described in CONTRIBUTING.md.
- Use `--warning-mode all` for Gradle deprecation details when needed.

## Parallel Test Execution and Test Isolation

Tests in this repo run with `maxParallelForks > 1` in CI, meaning multiple test
classes execute concurrently. This can cause flaky tests when static state leaks
between tests.

### Key Architecture Notes

- **`GrailsUnitTest` uses static fields** (`_grailsApplication`, `_servletContext`)
  which are shared across parallel tests. The `GrailsWebRequest.lookup()` method
  provides thread-local access to the current test's context.
  
- **MIME type lookups should use `GrailsWebRequest.lookup()`** before falling back
  to servlet context attributes to prevent test environment pollution.

- **URL mapping artefacts** can pollute parallel tests. Clear with:
  ```groovy
  grailsApplication.artefactInfo.clear()
  ```

## Debugging and Troubleshooting

### Debug Mode
Run applications with debugger attached:
```bash
./gradlew bootRun --debug-jvm
```
Then attach your IDE debugger to the forked JVM process.

### Common Issues
- **Container runtime missing**: Tests requiring containers will fail. Use `-PskipTests` or install Docker/Podman.
- **Out of memory**: Set `GRADLE_OPTS="-Xms2G -Xmx5G"`.
- **Flaky tests**: Check for static state pollution (see Test Isolation Patterns above).
- **Gradle cache issues**: Use `--rerun-tasks` to force re-execution.

### Deprecation Warnings
For detailed Gradle deprecation information:
```bash
./gradlew <task> --warning-mode all
```

## Commit Messages and Pull Requests

### Commit Message Guidelines
- Grails uses [Release Drafter](https://github.com/release-drafter/release-drafter) for release notes.
- Write clear, meaningful commit messages.
- Pull requests should be squashed into a single, meaningful commit.

### Branch Naming Conventions
Use these branch name prefixes to auto-label PRs for release notes:

| Prefix | Label | Description |
|--------|-------|-------------|
| `fix/` | bug | Bug fixes |
| `feat/` or `feature/` | feature | New features |
| `docs/` | documentation | Documentation changes |
| `chore/` | maintenance | Maintenance tasks |
| `refactor/` | maintenance | Code refactoring |
| `test/` | maintenance | Test improvements |
| `ci/` | maintenance | CI/CD changes |
| `perf/` | maintenance | Performance improvements |
| `build/` | maintenance | Build system changes |
| `deps/` | deps | Dependency updates |
| `revert/` | revert | Reverted changes |

### PR Title Conventions
PR titles containing these keywords will also trigger auto-labeling:
- Contains "fix" → bug label
- Contains "feat" → feature label
- Contains "docs" → documentation label
- Contains "chore", "refactor", "test", "ci", "perf", "build" → maintenance label

### Change Review Process
Different review policies apply based on the change type:

| Change Type | Policy | Reviewers | Wait Period |
|-------------|--------|-----------|-------------|
| Build-related | Commit then Review | - | - |
| Documentation (obvious fixes) | No review needed | - | - |
| Documentation (significant) | Commit then Review | 1 | - |
| Groovy/Spring dependency changes | Review then Commit | 2-3 | 3 days (weekend) / 1 day (weekday) |
| Other code changes | Review then Commit | 1 | - |

### Security Issues
Never report security vulnerabilities publicly. Send sensitive bugs to: `security@grails.org`

## Documentation

### User Guide Structure
- Source files: `grails-doc/src/en/guide/`
- Table of Contents: `grails-doc/src/en/guide/toc.yml`
- Format: Asciidoctor (`.adoc` files)

### Building Documentation
```bash
# Build user guide (skip groovydoc for speed)
./gradlew :grails-doc:publishGuide -x aggregateGroovydoc

# View the built guide
# Open: grails-doc/build/original-guide/index.html
```

### Documentation Types
- **API docs**: Javadoc/Groovydoc in source code
- **User Guide**: `grails-doc` module
- **How-to Guides**: [grails-guides](https://github.com/grails-guides) organization

## Resources

### Key Documentation Links
- **Grails 7 User Guide**: https://docs.grails.org/latest/guide/single.html
- **Groovy 4 Documentation**: https://docs.groovy-lang.org/docs/groovy-4.0.30/html/documentation/
- **Spock 2.3 Documentation**: https://spockframework.org/spock/docs/2.3/all_in_one.html
- **GORM Documentation**: https://gorm.grails.org/latest/

### Getting Help
- **Stack Overflow**: https://stackoverflow.com/questions/tagged/grails
- **Slack**: https://grails.slack.com
- **GitHub Issues**: https://github.com/apache/grails-core/issues
