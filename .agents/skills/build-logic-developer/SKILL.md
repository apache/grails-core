---
name: build-logic-developer
description: Guide for working in build-logic (build-logic-root, containing :build-logic and :grails-docs-core) — the Gradle convention-plugin layer every other build in this repo (root, grails-forge, grails-gradle) consumes transitively via includeBuild. Implements GrailsJacocoPlugin, GrailsViolationAggregationPlugin, GrailsCodeStylePlugin, PublishPlugin, SbomPlugin, and 8 others, all cited by other skills without documentation until this one. Use this when changing code style/coverage/publish/SBOM/compile conventions, or anything under build-logic/.
license: Apache-2.0
paths: build-logic/**
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Provide repository-specific guidance for `build-logic` — the Gradle convention-plugin layer, not application or framework code. Every convention referenced by other skills in this repo (violation-fixer's `aggregateViolations`, the `Coverage` section in root `AGENTS.md`, the Hibernate5/7 JaCoCo class-name-collision exclusion cited in `hibernate-developer`) is *implemented* here.
- Guide changes to the 13 registered convention plugins and their two test patterns (Gradle TestKit for task-graph behavior, plain `ProjectBuilder` for pure-logic methods).
- Flag known coverage gaps: only 4 of 13 plugins have dedicated specs.

## When to Use Me

Activate this skill instead of `grails-developer` when working on:

- Anything under `build-logic/**`.
- Code style, code analysis, coverage aggregation, publishing, SBOM generation, or vulnerability-scan conventions used across the whole monorepo.
- A change to how any of the 13 convention plugins registers tasks or extensions.

## Module Context: A Third Pattern, Not Either Existing Template

`build-logic` has its own `settings.gradle` (like `grails-forge`), but it is **not independently versioned** — it explicitly reaches across the filesystem into root's `dependencies.gradle`/`gradle.properties` by relative path (`../../dependencies.gradle` from `plugins/build.gradle` and `docs-core/build.gradle`), the same `SharedPropertyPlugin` walk-up-the-tree mechanism `grails-gradle` uses. Neither `mongodb-developer` (fully root-governed) nor `micronaut-developer`/`grails-forge` (fully independent) is a clean fit — see `grails-gradle-developer`'s "hybrid" framing, same situation here.

- `build-logic/gradle.properties` defines **no version properties**, only Gradle daemon/cache flags.
- No `sourceCompatibility`/`targetCompatibility`/toolchain config anywhere — compiles with whatever JDK/Groovy runs the Gradle daemon itself (Gradle 9.6.0, matching `.sdkmanrc`/root `gradle.properties`). `plugins/` applies `groovy-gradle-plugin` (Gradle's embedded Groovy, not the app-level 5.0.x); `docs-core/` explicitly depends on `"org.apache.groovy:groovy:${GroovySystem.version}"`.
- **Zero `@GrailsCompileStatic` anywhere** — all 19 plugin/extension classes use plain `@CompileStatic` (some also `@CompileDynamic` for dynamic Groovy/XML-map parsing, e.g. `GrailsViolationAggregationPlugin.groovy`). This is genuinely not Grails artefact code.
- **Zero `jakarta.*` anywhere.** `javax.inject.Inject` appears in 7 files, but it's Gradle's own DSL constructor-injection mechanism (`@Inject ObjectFactory`/`ExecOperations`, required by Gradle's plugin API, which still uses `javax.inject`), not an application-level jakarta/javax choice — don't conflate the two.
- **No dedicated CI job** — this is expected, not a dormancy signal (unlike `grails-data-neo4j`). It's exercised transitively: root, `grails-gradle`, and `grails-forge` all `includeBuild('../build-logic')`, and root's own CI jobs invoke tasks (`aggregateViolations`, `jacocoAggregateReport`, `codeStyle`) that these plugins implement. Check git log recency and README accuracy for real staleness signals instead — this module received a substantive commit (`f2c1244436`, GROOVY-12146 workaround) the day before this skill was written.

## Module Structure

Directory names don't match Gradle project names:

| Directory | Gradle project | Role |
|---|---|---|
| `plugins/` | `:build-logic` | The 13 convention `Plugin<Project>` implementations (see catalog below) |
| `docs-core/` | `:grails-docs-core` | The Grails user-guide generation engine (gdoc/asciidoc → HTML/PDF), BOM-extraction tooling for docs, the version-picker dropdown generator. **README is a one-line stub** (`## grails-docs`, no body) despite containing a nontrivial BOM-extraction subsystem — a real documentation gap, not just an example. |

## Convention Plugin Catalog

All registered in `plugins/build.gradle` under `gradlePlugin { plugins { ... } }`. This table exists because other skills already cite several of these classes without documenting what they actually do — that's the gap this skill closes.

| Plugin ID | Class | What it actually wires up |
|---|---|---|
| `org.apache.grails.buildsrc.compile` | `CompilePlugin` | Pins `JavaCompile.options.release`; enables sources/javadoc jars; sets manifest attrs, `duplicatesStrategy = FAIL`; applies the GROOVY-12146 reproducible-build workaround (`gradle/groovy-compile-configscript.groovy` — sorts annotation members alphabetically, since Groovy's copy-from-precompiled-class handling orders them by `Class.getDeclaredMethods()`, which varies between JVM runs); pins Javadoc/archive timestamps for reproducibility. |
| `org.apache.grails.buildsrc.publish` | `PublishPlugin` | Maven-publish + ASF-policy plumbing: `GrailsPublishExtension`, the full ASF developers/contributors POM list, Gradle Module Metadata version mapping, SHA512 checksums, fallback `LICENSE`/`NOTICE` injection, disables GPG signing when `TEST_BUILD_REPRODUCIBLE` is set. |
| `org.apache.grails.buildsrc.sbom` | `SbomPlugin` | Per-module `CyclonedxDirectTask` (deliberately *not* the full `CyclonedxPlugin`, to avoid an unwanted aggregate SBOM and Spring Boot 4's own `CycloneDxPluginAction` colliding with it), curated `LICENSE_MAPPING`/`LICENSE_EXCEPTIONS` for dependencies CycloneDX mis-detects, byte-reproducible JSON output, embeds `META-INF/sbom.json`. |
| `org.apache.grails.gradle.grails-code-style` | `GrailsCodeStylePlugin` | Applies Checkstyle+CodeNarc; materializes bundled default rule files on first access; registers `codeStyle` and `codenarcFix` (a **hand-rolled regex auto-fixer** for exactly 6 named violation types — not a real CodeNarc API, brittle by construction, covered by its own spec). |
| `org.apache.grails.gradle.grails-code-analysis` | `GrailsCodeAnalysisPlugin` | Applies PMD/SpotBugs, both **opt-in** (`grails.code-analysis.enabled.{pmd,spotbugs}` properties — returns early otherwise); SpotBugs `effort=MAX`, `reportLevel=HIGH`. |
| `org.apache.grails.gradle.grails-jacoco` | `GrailsJacocoPlugin` | Applies `JacocoPlugin`, `finalizedBy('jacocoTestReport')` on every `Test`; lazily registers the root `jacocoAggregateReport` task; **excludes Hibernate 7-suffixed subprojects' source/class dirs from the aggregate** because H7 support classes share fully-qualified names with H5 (`if (!project.path.contains('hibernate7')) { ... }`) — exec data is still included, so H7 test coverage attributes to H5's class definitions in the aggregate. |
| `org.apache.grails.gradle.grails-violation-aggregation` | `GrailsViolationAggregationPlugin` | **Root-project-only** (throws otherwise). Registers `aggregateStyleViolations`, `aggregateAnalysisViolations`, `aggregateJacocoCoverage` (with its own, separately-configured Hibernate7 exclusion prefix list, `-Pgrails.jacoco.aggregation.excludedClassPrefixes`), and umbrella `aggregateViolations`. Parses tool XML with a hardened `XmlSlurper` (external-entity/DOCTYPE disabled). This is what produces the `*_VIOLATIONS.md`/`JACOCO_COVERAGE.md` files documented in `violation-fixer`. |
| `org.apache.grails.gradle.grails-ij-formatter` | `GrailsIJFormatterPlugin` | Root-only: `installGitHooks`. All projects: `formatCode`, shells out to IntelliJ's headless CLI formatter against `.idea/codeStyles/Project.xml`, using an isolated IDE instance so it can run alongside an already-open IntelliJ. |
| `org.apache.grails.buildsrc.groovydoc-enhancer` | `GroovydocEnhancerPlugin` | Generic Groovydoc task defaults; optional direct-Ant-taskdef execution path; throws if a published module has no source dirs — "every published module must produce a groovydoc jar for Maven Central." |
| `org.apache.grails.buildsrc.groovydoc` | `GrailsGroovydocPlugin` | Grails-specific wrapper around the enhancer above — injects a hardcoded Matomo analytics footer (ASF's `analytics.apache.org`, siteId 79) into every generated Groovydoc page. |
| `org.apache.grails.buildsrc.repo` | `GrailsRepoSettingsPlugin` | `Plugin<Settings>` (applied from `settings.gradle`, not a project build script) — centralizes repository lists, sets `repositoriesMode = FAIL_ON_PROJECT_REPOS`. |
| `org.apache.grails.buildsrc.properties` | `SharedPropertyPlugin` | Walks parent directories up to the ASF root (detected via `.asf.yaml`, not `.git` — `.git`-related dirs are purged from source releases) loading every `gradle.properties` found, plus `local.properties` overrides. Must be applied before any other property lookup in a composite sub-build. |
| `org.apache.grails.buildsrc.dependency-validator` | `GrailsDependencyValidatorPlugin` | Registers `validateDependencyVersions`. Auto-detects which BOM a project uses by scanning configurations (excluding `documentation`, which pulls in `grails-bom` purely for groovydoc tooling versions), fails the build if a transitive dependency silently upgraded past what the BOM pins. Opt-out via `project.ext.allowedBomOverrides`. |
| `org.apache.grails.buildsrc.vulnerability-scan` | `VulnerabilityScanPlugin` | Applies Sonatype OSS Index scanning; a shared `BuildService` (`OssIndexAuditThrottle`, `maxParallelUsages=1`) serializes `ossIndexAudit` tasks repo-wide, because the plugin's on-disk cache is file-lock guarded and throws `OverlappingFileLockException` under Gradle's default parallel execution; maintains a hand-curated CVE `excludeCoordinates` allowlist, each entry documenting the CVE and its removal condition. |

## Testing Patterns

Two distinct, both real — pick based on what you're testing:

### Pattern A — Gradle TestKit (`GradleRunner`), for task-graph/execution behavior

```groovy
class GrailsJacocoPluginSpec extends Specification {
    @TempDir
    Path testProjectDir

    def setup() {
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
        """
    }

    def "jacocoAggregateReport is registered on the root project in a multi-project build"() {
        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--group=verification')
                .withPluginClasspath()
                .build()
        then:
        result.output.contains('jacocoAggregateReport')
    }
}
```
Builds a real, temporary Gradle project (`@TempDir`) with a synthetic `settings.gradle`/`build.gradle`, applies the plugin by its published ID via `withPluginClasspath()`, asserts on real task graph/output.

### Pattern B — Plain Spock + `ProjectBuilder`, for pure/static logic

```groovy
private static Project rootWithBoms() {
    Project root = ProjectBuilder.builder().withName('root').build()
    ProjectBuilder.builder().withName('grails-bom').withParent(root).build()
    ProjectBuilder.builder().withName('grails-micronaut-bom').withParent(root).build()
    root
}

void "detectBomPath ignores the documentation configuration when a variant BOM is used elsewhere"() {
    given:
    Project root = rootWithBoms()
    Project project = ProjectBuilder.builder().withName('grails-micronaut').withParent(root).build()
    addBomPlatform(project, 'api', ':grails-micronaut-bom')
    addBomPlatform(project, 'documentation', ':grails-bom')
    expect:
    GrailsDependencyValidatorPlugin.detectBomPath(project) == ':grails-micronaut-bom'
}
```
No real Gradle process — construct an in-memory project tree with `ProjectBuilder`, call the static logic directly. Faster; use it when the thing under test doesn't need real task execution.

**Coverage gap, worth knowing before you assume a plugin is tested:** only 4 of 13 registered plugins have dedicated specs (`GrailsCodeStylePlugin`, `GrailsDependencyValidatorPlugin`, `GrailsJacocoPlugin`, `GrailsViolationAggregationPlugin`). `PublishPlugin`, `SbomPlugin`, `CompilePlugin`, `VulnerabilityScanPlugin`, `GrailsCodeAnalysisPlugin`, `GrailsIJFormatterPlugin`, the two Groovydoc plugins, `GrailsRepoSettingsPlugin`, and `SharedPropertyPlugin` have none under `plugins/src/test/groovy/`. If you touch one of the untested plugins, that's a real gap you're inheriting, not a false negative to ignore.

## Pitfalls to Avoid

- Do not add `@GrailsCompileStatic` anywhere in this module — see Module Context.
- Do not remove the Hibernate7 class-exclusion logic in `GrailsJacocoPlugin`/`GrailsViolationAggregationPlugin` without understanding why it exists — H5/H7 support classes share fully-qualified names, and JaCoCo cannot aggregate two different classes with the same name.
- Do not touch `gradle/groovy-compile-configscript.groovy` (referenced from `CompilePlugin`) without understanding GROOVY-12146 — it exists specifically to make annotation-member ordering deterministic across JVM runs for reproducible builds. This is recent (added 2026-07-10); check `etc/bin/normalize-annotations.groovy` and `etc/bin/verify-reproducible.sh` (added in the same commit) before assuming it's dead weight.
- Do not assume `GradleUtils.findRootGrailsCoreDir`/`findAsfRootDir` can walk via `.git` — it deliberately uses `.asf.yaml` as the root marker, because `.git`-related directories are purged from ASF source releases. `SharedPropertyPlugin`, `PublishPlugin`'s license/NOTICE fallback, and `CompilePlugin`'s config-script lookup all depend on this.
- Do not remove the `org.spockframework` exclusion when depending on `grails-gradle-model` from another module — it leaks transitively through `grails-gradle-bom`.
- If you touch `VulnerabilityScanPlugin`'s CVE `excludeCoordinates` allowlist, each entry documents *why* and *when to remove it* — don't add a bare exclusion without the same discipline.

## Build & Test

```bash
cd build-logic
./gradlew build                     # builds both :build-logic and :grails-docs-core
./gradlew :build-logic:test         # plugin Spock/TestKit specs
./gradlew :grails-docs-core:test    # docs-core Spock specs
./gradlew projects                  # confirms the two-project layout (build-logic-root -> :build-logic, :grails-docs-core)
```
Root `AGENTS.md` documents `cd build-logic && ../gradlew build` (the *root's* wrapper, not build-logic's own `./gradlew`) — both wrappers pin the same Gradle 9.6.0 and either works, but that's the form root `AGENTS.md` uses.

## Source of Truth

This skill is the repository guidance for `build-logic` work. When a convention plugin's behavior changes, update this skill directly so agents load current rules from `.agents/skills/build-logic-developer/SKILL.md` — and check whether other skills that cite these classes (`hibernate-developer`'s Hibernate7 exclusion mention, root `AGENTS.md`'s `Coverage` section, `violation-fixer`) need updating too, since this is the module they're all quietly depending on.
