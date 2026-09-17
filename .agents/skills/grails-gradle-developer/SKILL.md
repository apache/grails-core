---
name: grails-gradle-developer
description: Guide for working in grails-gradle (grails-gradle-bom, -common, -model, -plugins, -tasks) — the Gradle plugins a Grails application applies (org.apache.grails.gradle.grails-app etc.), tested via Gradle TestKit, not Spock/GORM mocking. A hybrid module — own settings.gradle and test harness, but shares dependency versions with root. Use this when changing code or tests under grails-gradle.
license: Apache-2.0
paths: grails-gradle/**
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Provide repository-specific guidance for `grails-gradle` — the Gradle plugins that turn a plain Gradle project into a "Grails app" project (`org.apache.grails.gradle.grails-app`, `grails-web`, `grails-plugin`, etc.), not the framework runtime itself.
- Explain the hybrid nature of this module: independent `settings.gradle` and test harness, but *not* an independent version/BOM story like `grails-forge` — it deliberately shares root's `dependencies.gradle`/`gradle.properties`.
- Guide changes around the `Plugin<Project>` implementations in `plugins/`, and their Gradle TestKit tests.

## When to Use Me

Activate this skill instead of `grails-developer` when working on:

- Anything under `grails-gradle/**`.
- A Gradle plugin ID → implementation class mapping (adding/changing a plugin an app's `build.gradle` applies).
- TestKit-based functional tests for a Gradle plugin.

## Module Context: A Hybrid, Not a Clean Fit for Either Template

`grails-gradle` is neither fully root-governed (like `grails-data-hibernate7`/`grails-data-mongodb`) nor fully independent (like `grails-forge`):

**Shares with root:**
- Dependency *versions* — `grails-gradle/build.gradle` does `allprojects { apply from: rootProject.layout.projectDirectory.file('../dependencies.gradle') }`. Same single source of truth (`gradleBomDependencyVersions`, `bomDependencyVersions`, etc.) the main build uses.
- `javaVersion`/`projectVersion` and other root `gradle.properties` keys — `grails-gradle/gradle.properties` itself defines *no* version properties at all, only Gradle daemon/cache flags. Values reach it via `SharedPropertyPlugin` (`org.apache.grails.buildsrc.properties`, in `build-logic`), whose class doc says exactly why it exists: *"Gradle can't share properties across buildSrc or composite projects."* It walks up from `grails-gradle/` to the repo root loading every `gradle.properties` found, filling in any key not already set.
- The entire `build-logic` convention-plugin set (code style, jacoco, sbom, publish) via `includeBuild('../build-logic')` — see the `build-logic-developer` skill.

**Diverges from root:**
- Its own `settings.gradle`, own local `gradle/test-config.gradle` (measurably different from root's — no cache-disable-in-CI block, injects `projectVersion`/`currentJdk` system properties TestKit specs depend on).
- Its own `grails-gradle-bom` (a `java-platform`, distinct from the app-facing `grails-bom`, though populated from the same `dependencies.gradle`).
- **Compiles its own module source against Gradle's embedded Groovy 4.0.32**, not root's Groovy 5.0.x (`groovy-gradle-plugin`; explicit comment in every subproject's `build.gradle`: *"compile with the Groovy version provided by Gradle"*). Verified at runtime: `cd grails-gradle && ./gradlew -v` reports Groovy 4.0.32.
- Testing: Gradle TestKit (`GradleRunner`), not root's Spock/GORM-mock conventions (see Testing Rules).

## Where Root AGENTS.md Rules Don't Apply

- **`@GrailsCompileStatic` is never used on grails-gradle's own classes** — plain `groovy.transform.CompileStatic` throughout (47 files). Every `@GrailsCompileStatic` string that appears in this module's source is inside javadoc/comments *describing the feature the plugin implements for a consumer app* (e.g. `GrailsCompileStaticOptions.groovy`'s "Lazy opt-ins for compiling Grails artefacts with `@GrailsCompileStatic`"), never a real annotation on a grails-gradle class. Don't "fix" this — it's build-tooling code, not a Grails artefact.
- **`javax.*` usage is legitimate here, not stale migration debt.** `javax.inject.Inject` (JSR-330 DI, unrelated to the Jakarta EE migration) and `javax.xml.parsers.*`/`javax.xml.XMLConstants` (permanent JDK APIs, never had a jakarta equivalent) both appear and are correct. The `jakarta.*` references that do exist in this module (e.g. `jakarta.servlet:jakarta.servlet-api:6.0.0` in `GroovyPagePlugin`) are about *configuring a downstream Grails application's* dependencies, not this module's own runtime.
- **Dependency versions are NOT independently pinned** (unlike `grails-forge`) — don't add a hardcoded version here; it should come from root's `dependencies.gradle` the same way the rest of the module does.

## Module Structure

Five Gradle projects (directory name ≠ Gradle project name in every case — check before assuming):

| Directory | Gradle project | Role |
|---|---|---|
| `bom/` | `:grails-gradle-bom` | `java-platform` only, no source — constrains sibling subprojects, re-exports spring-boot/groovy/spock BOMs |
| `common/` | `:grails-gradle-common` | Tiny — one class, `PropertyFileUtils.groovy` |
| `model/` | `:grails-gradle-model` | Vendored/shared subset of core Grails runtime classes needed at build time before the framework is on the classpath: `grails.util.{BuildSettings,Environment,Metadata}`, `org.grails.io.support.*` resource loading, Gradle Tooling API model classes (`GrailsClasspath`) |
| `plugins/` | `:grails-gradle-plugins` | The actual `Plugin<Project>` implementations — see table below. By far the largest subproject. |
| `tasks/` | `:grails-gradle-tasks` | Only 2 files: `FindMainClassTask.groovy`, `SourceSets.groovy` |

`plugins` depends on `common`, `tasks`, and `model` — with an explicit `exclude group: 'org.spockframework'` on the `model` dependency in both `plugins/build.gradle` and `tasks/build.gradle`, because "spock is leaking from the grails-gradle-bom through grails-gradle-model" (real comment, not a hypothetical).

## Plugin ID → Implementation Class

The actual entry points an app's `build.gradle` applies:

| Plugin ID | Class |
|---|---|
| `org.apache.grails.gradle.grails-app` | `org.grails.gradle.plugin.core.GrailsGradlePlugin` |
| `org.apache.grails.gradle.grails-gsp` | `org.grails.gradle.plugin.views.gsp.GroovyPagePlugin` |
| `org.apache.grails.gradle.grails-gson` | `org.grails.gradle.plugin.views.json.GrailsGsonViewsPlugin` |
| `org.apache.grails.gradle.grails-markup` | `org.grails.gradle.plugin.views.markup.GrailsMarkupViewsPlugin` |
| `org.apache.grails.gradle.grails-plugin` | `org.grails.gradle.plugin.core.GrailsPluginGradlePlugin` (extends `GrailsGradlePlugin`) |
| `org.apache.grails.gradle.grails-profile` | `org.grails.gradle.plugin.profiles.GrailsProfileGradlePlugin` |
| `org.apache.grails.gradle.grails-web` | `org.grails.gradle.plugin.web.GrailsWebGradlePlugin` (extends `GrailsGradlePlugin`) |
| `org.apache.grails.gradle.grails-publish-profile` | `org.grails.gradle.plugin.profiles.GrailsProfilePublishGradlePlugin` |
| `org.apache.grails.gradle.grails-exploded` | `org.grails.gradle.plugin.exploded.GrailsExplodedPlugin` |
| `org.apache.grails.gradle.grails-test-phases` | `org.grails.gradle.plugin.core.TestPhasesGradlePlugin` |
| `org.apache.grails.gradle.grails-integration-test` | `org.grails.gradle.plugin.core.IntegrationTestGradlePlugin` |
| `org.apache.grails.gradle.bom-property-overrides` | `org.grails.gradle.plugin.bom.BomPropertyOverridesPlugin` |

`GrailsGradlePlugin.apply(Project)` is the real entry point that turns a plain Gradle project into a "Grails app" project: applies core `GroovyPlugin`, enforces "cannot be both a Grails application and a Grails plugin" via a marker extension, resets `grails.util.Environment` per invocation.

## Testing Rules

Gradle TestKit (`GradleRunner`) is the dominant pattern in `plugins/` — **not** Spock/GORM-mock conventions from the rest of the repo.

- **`GradleSpecification`** (`plugins/src/test/groovy/org/grails/gradle/plugin/core/GradleSpecification.groovy`) is the abstract Spock base for functional tests, documented as adapted from `apache/grails-gradle-publish`'s own `GradleSpecification`. It sets up `GradleRunner.create().withPluginClasspath().withTestKitDir(...)`, copies a fixture project from `src/test/resources/test-projects/<name>/`, does `__CURRENT_JDK__`/`__PROJECT_VERSION__` token substitution, and exposes `executeTask(String taskName, ...)`.
  ```groovy
  class GrailsGradlePluginJavaCompatSpec extends GradleSpecification {
      def "Java 24 toolchain adds both native-access and sun-misc-unsafe-memory-access args"() {
          given:
          setupTestResourceProject('java-compat-toolchain-24')
          when:
          def result = executeTask('inspectCompatArgs')
          then:
          result.output.contains('HAS_NATIVE_ACCESS=true')
          result.output.contains('HAS_UNSAFE_ACCESS=true')
      }
  }
  ```
  `__CURRENT_JDK__`/`__PROJECT_VERSION__` come from JVM system properties injected specifically by `grails-gradle/gradle/test-config.gradle`'s `systemProperty 'projectVersion', ...` / `systemProperty 'currentJdk', ...` — this is exactly why grails-gradle's local `test-config.gradle` differs from root's.
- **28 real fixture project directories** under `plugins/src/test/resources/test-projects/` (e.g. `java-compat-toolchain-24`, `bom-platform-hibernate7-micronaut-auto`) — each a minimal, real Gradle project TestKit builds and inspects. Add a new fixture directory here for a new functional test, don't try to synthesize a project inline.
- Non-TestKit unit specs also exist for pure-unit-testable classes with no `Project` interaction (e.g. `BomManagedVersionsSpec`, `GrailsCompileStaticOptionsSpec`) — plain `spock.lang.Specification`, no `GradleRunner`. Use this lighter pattern when the logic under test doesn't touch a real Gradle build/task graph.

## Pitfalls to Avoid

- Do not add `@GrailsCompileStatic` to grails-gradle's own classes — see "Where Root AGENTS.md Rules Don't Apply."
- Do not hardcode a dependency version here — pull it from root's `dependencies.gradle` the way the rest of the module does; don't create a third, module-local version source.
- Do not assume a fixture-project functional test can be synthesized inline — the `GradleSpecification` pattern expects a real directory under `test-projects/`.
- Remember `plugins` depends on `model` with `org.spockframework` explicitly excluded — don't remove that exclusion without understanding it leaks transitively through `grails-gradle-bom`.

## Build & Test

```bash
cd grails-gradle
./gradlew build --continue --stacktrace
./gradlew build -PskipTests -PskipCodeStyle    # fast build
./gradlew validateDependencyVersions --continue --stacktrace   # BOM validation, run separately from root's own
./gradlew aggregateStyleViolations --continue   # CodeNarc/Checkstyle
./gradlew jacocoAggregateReport --continue --stacktrace -PskipCodeStyle   # coverage
```
All verified against actual CI invocations (`.github/workflows/{gradle,codestyle,coverage,codeanalysis,release}.yml`, all `working-directory: grails-gradle`).

## Source of Truth

This skill is the repository guidance for `grails-gradle` work. When module conventions change, update this skill directly so agents load current rules from `.agents/skills/grails-gradle-developer/SKILL.md`.
