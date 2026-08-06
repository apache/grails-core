---
name: gradle-developer
description: Expert guide for Gradle 9 builds in apache/grails-core on the 8.0.x line - multi-project topology, convention plugins, BOM platforms, dependency rules, task configuration hygiene, and repo-specific patterns that override generic Gradle docs
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Write and change Gradle build scripts the way **this repository** already does them on `8.0.x` (Gradle **9.6.x**).
- Keep agents off generic Gradle "best practice" when it conflicts with established monorepo patterns.
- Cover composite builds (`build-logic`, `grails-gradle`, `grails-forge`, `end-to-end`), convention plugins, BOM/`platform()` dependency management, test wiring, publishing hooks, and Gradle 9 task-configuration traps learned from recent PRs.
- Make Gradle changes boring, copy-paste consistent, and correct on the first try.

## When to Use Me

**MANDATORY** before any of the following:

- Editing `build.gradle`, `settings.gradle`, `gradle.properties`, `dependencies.gradle`, or anything under `gradle/`, `build-logic/`, `grails-gradle/`
- Adding or renaming a module / subproject
- Bumping a dependency version or the Gradle wrapper
- Changing test, publish, SBOM, code-style, or JaCoCo Gradle wiring
- Touching Grails Gradle plugins used by apps (`org.apache.grails.gradle.*`)
- Diagnosing configuration-cache, resolution, `afterEvaluate`, or task-graph failures

Also load when a change *looks* like application code but requires build script updates (new module, new published artifact, CLI companion jar, functional test app).

Related skills (do not substitute this one):

| Need | Skill |
|------|-------|
| CodeNarc / Checkstyle / PMD / SpotBugs reports | `violation-fixer` |
| Failing tests / aggregate reports | `test-fixer` |
| Merging an external plugin repo into the monorepo | `mono-repo-integration` |
| App-facing Grails 8 upgrade guidance | `grails-8-upgrade` |

---

## Prime Directive: This Repo Wins

1. **Match neighboring modules.** Before inventing structure, open 2-3 similar `build.gradle` files and copy their plugin block, dependency style, and `apply { from ... }` scripts.
2. **Prefer existing convention plugins** over inline configuration. If `CompilePlugin` already sets encoding, release, jars, and reproducibility, do not re-declare those in the module script.
3. **Gradle docs are secondary.** Official Gradle 9 docs are useful for APIs and deprecations, but this monorepo intentionally diverges (configuration cache off, no Spring DM plugin, heavy `projectDir` remapping, presence-based `-P` flags, custom BOM validator). When docs and this repo disagree, **follow this repo** unless you are deliberately fixing a known issue with a tracked reason.
4. **Do not use** `io.spring.dependency-management` / Spring Dependency Management plugin in core modules or applications. The intentional regression fixture at `grails-test-examples/spring-dependency-management` is the only exception. Grails 8 otherwise uses native `platform()` / `enforcedPlatform()` plus `org.apache.grails.gradle.bom-property-overrides` (see PR #15467).
5. **Scope Gradle invocations** to the touched subproject (`:module:test`, not root `test`) unless the change is cross-cutting.

### Change workflow

1. Inspect 2-3 sibling modules and the relevant convention plugin or shared script.
2. Confirm the Gradle project path in `settings.gradle`, including any `projectDir` mapping.
3. Edit the smallest appropriate build file or convention plugin.
4. Run scoped compile, test, and `validateDependencyVersions` tasks for the changed project.
5. For `grails-gradle` changes, run the relevant plugin TestKit tests from `grails-gradle/` with its wrapper.

---

## Topology (Know Where You Are)

This git repo is **several independent Gradle builds**, not one flat multiproject:

| Build | Path | Role | How to run |
|-------|------|------|------------|
| **Root framework** | repo root | 60+ published modules, BOMs, test-examples, profiles, docs | `./gradlew …` from root |
| **build-logic** | `build-logic/` | Shared **convention plugins** via `includeBuild` | `cd build-logic && ./gradlew …` (or root pluginManagement includeBuild) |
| **grails-gradle** | `grails-gradle/` | **Published** Grails Gradle plugins for apps | `cd grails-gradle && ./gradlew …` |
| **grails-forge** | `grails-forge/` | App generator (own wrapper, own deps) | `cd grails-forge && ./gradlew …` |
| **end-to-end** | `end-to-end/` | Tests against **published** artifacts in `build/local-maven` | Full 3-step flow below (see `end-to-end/README.md`) |
| **gradle-bootstrap** | `gradle-bootstrap/` | Regenerates shared wrappers from `.sdkmanrc` | `gradle -p gradle-bootstrap` (see wrapper section) |

**end-to-end is not a composite consumer of the root build.** It resolves real published coordinates from **`<repo>/build/local-maven`** (the TestCaseMavenRepo), **not** `~/.m2` and **not** via `publishAllToMavenLocal`. Do not add `includeBuild('..')` substitution - that defeats validating consumer metadata, CLI companions, and POM/BOM shape.

Full local run (three steps, in order):

```bash
# 1) Publish grails-gradle + root into build/local-maven (both required)
(cd grails-gradle && ./gradlew publishAllPublicationsToTestCaseMavenRepoRepository)
./gradlew publishAllPublicationsToTestCaseMavenRepoRepository

# 2) Build the standalone Grails 7 fixture jar (JDK 17 / Gradle 8.x via its .sdkmanrc)
cd end-to-end/legacy-g7-command-plugin
sdk env
./gradlew jar
cd ../..

# 3) Run the suite on the root JDK 21 environment
sdk env   # from repository root (.sdkmanrc)
cd end-to-end
./gradlew check
```

Leave `legacy-g7-command-plugin`'s wrapper on its pinned Grails 7 Gradle version when bumping the main line.

Root `settings.gradle` wires:

```groovy
pluginManagement {
    includeBuild('./grails-gradle') { name = 'grails-gradle' }
    includeBuild('./build-logic') { name = 'build-logic-root' }
    // ...
}
```

`build-logic` exists because composite builds do **not** share `buildSrc` plugins. Internal conventions live there so root, grails-gradle, and forge can consume them.

### Project path != directory name

Root settings heavily remaps `projectDir` (100+ entries). Examples:

| Gradle path | Directory |
|-------------|-----------|
| `:grails-bom` | `grails-bom/default` |
| `:grails-base-bom` | `grails-bom/base` |
| `:grails-hibernate7-bom` | `grails-bom/hibernate7` |
| `:grails-controllers` | `grails-controllers` (often 1:1) |
| `:grails-data-hibernate7-core` | `grails-data-hibernate7/core` |
| `:grails-test-examples-app1` | `grails-test-examples/app1` |

Always use the **Gradle project path** in task names (`./gradlew :grails-data-hibernate7-core:test`). Confirm with `settings.gradle` `include` + `projectDir` when unsure. Do not invent paths from folder names alone.

### Micronaut "island"

`grails-micronaut*`, micronaut BOMs, and related test-examples are gated in `settings.gradle`:

- Auto-**excluded** on JDK &lt; 25 (Micronaut 5 targets JVM 25 bytecode)
- Auto-**included** on JDK 25+
- `-PskipMicronautProjects` forces exclude (used by groovy-joint CI)
- `-PincludeMicronautProjects` forces include on older JDKs (still may not compile)

Presence-based flags (property **present**, value optional) match `skipFunctionalTests` / `skipCodeStyle` style elsewhere.

---

## Gradle Version Sync (Hard Rule)

Current line: **Gradle 9.6.0** (`distributionUrl` + `gradleToolingApiVersion=9.6.0`). Upstream may already ship a newer 9.6.x patch - this repo rides close to latest **only after** a deliberate multi-location bump PR. Do not "helpfully" jump one wrapper ahead of the rest.

**Two Groovy stacks:** Gradle itself embeds **Groovy 4** for build logic. Application/runtime code on 8.0.x is **Groovy 5**. That is why `dependencies.gradle` keeps separate maps:

- `gradleBomDependencyVersions` / `gradle-groovy.version` / `gradle-spock.version` → build tooling (Groovy 4 / Spock groovy-4)
- `bomDependencyVersions` / `groovy.version` / `spock.version` → apps and framework modules (Groovy 5 / Spock groovy-5.0)

Never unify those casually.

### Preferred bump workflow

1. Set the new Gradle version in **`.sdkmanrc`** (`gradle=…`).
2. Run the bootstrap project (uses a system `gradle` to regenerate shared wrappers from `.sdkmanrc`):

   ```bash
   gradle -p gradle-bootstrap
   ```

   Bootstrap generates the wrapper under `gradle-bootstrap/`, **copies** it to **grails-forge**, **grails-gradle**, and **end-to-end**, then **moves** the generated wrapper into the **repository root**. It also runs `legacyG7Wrapper` so **`end-to-end/legacy-g7-command-plugin` stays on its pinned Grails 7 Gradle version** (currently 8.x - do **not** force it to 9). Root is therefore bootstrap-covered; do not re-list it as a manual step.

3. Manually refresh the locations bootstrap does **not** cover. For each tree, keep the **full** wrapper set in sync (`gradle-wrapper.properties`, `gradle-wrapper.jar`, `gradlew`, `gradlew.bat`) - not properties alone:

   - `build-logic/` - run its wrapper task or copy the complete set from root after bootstrap
   - `grails-profiles/base/skeleton/` and `grails-profiles/profile/skeleton/`
   - `grails-shell-cli/src/test/resources/gradle-sample/` (and `bin/test` copy if present)
   - Forge **generated-app** wrapper assets (all of these - properties alone is not enough):
     - `grails-forge/grails-forge-core/.../gradleWrapperProperties.rocker.raw` (properties template)
     - `grails-forge/grails-forge-core/src/main/resources/gradle/gradlew`
     - `grails-forge/grails-forge-core/src/main/resources/gradle/gradlew.bat`
     - `grails-forge/grails-forge-core/src/main/resources/gradle/wrapper/gradle-wrapper.jar`
   - `gradle.properties` → `gradleToolingApiVersion` (must match the new Gradle version)

4. Verify every **main-line** tree matches on properties **and** scripts/jars. The only intentional holdout is `end-to-end/legacy-g7-command-plugin` (Gradle 8.x).

Also keep `gradlew.bat` LF line endings on this line (PR #15709). Comment at top of root `gradle-wrapper.properties` remains a human checklist.

---

## Root `gradle.properties` Flags (Do Not "Fix" Blindly)

| Property | Value / note |
|----------|----------------|
| `org.gradle.caching` | `true` |
| `org.gradle.parallel` | `true` |
| `org.gradle.daemon` | `true` |
| `org.gradle.configuration-cache` | **`false`** until #15497 resolved - do not enable casually |
| `org.gradle.configureondemand` | **commented off** - Gradle issue #9489 |
| `org.gradle.jvmargs` | `-Xmx5G` (raise only with reason; groovydoc is hungry) |
| `javaVersion` | `21` (CompilePlugin reads this for `--release`) |
| `projectVersion` | framework version |
| `slf4jPreventExclusion` | `true` - Grails Gradle plugin POM behavior |

CI vs local behavior is branched on `System.getenv('CI')` and `SOURCE_DATE_EPOCH` (reproducible builds disable remote cache).

---

## Standard Published Library Module

Canonical pattern (see `grails-core/build.gradle`, `grails-controllers/build.gradle`, `grails-services/build.gradle`):

```groovy
/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  ... Apache header ...
 */

plugins {
    id 'groovy'
    id 'java-library'
    id 'project-report'                                      // optional but common
    id 'org.apache.grails.buildsrc.properties'
    id 'org.apache.grails.buildsrc.dependency-validator'
    id 'org.apache.grails.buildsrc.compile'
    id 'org.apache.grails.buildsrc.publish'
    id 'org.apache.grails.buildsrc.sbom'
    id 'org.apache.grails.buildsrc.vulnerability-scan'      // when appropriate
    id 'org.apache.grails.gradle.grails-code-style'
    id 'org.apache.grails.gradle.grails-jacoco'
}

version = projectVersion
group = 'org.apache.grails'   // or org.apache.grails.web / .data / etc. - match siblings

dependencies {
    implementation platform(project(':grails-bom'))   // or :grails-hibernate7-bom, etc.

    api project(':grails-core')
    api 'org.apache.groovy:groovy'
    // versions come from the platform - do NOT hardcode versions here

    compileOnly 'jakarta.servlet:jakarta.servlet-api'

    testImplementation 'org.spockframework:spock-core'
    testImplementation 'org.apache.groovy:groovy-test-junit5'
    testImplementation 'org.junit.jupiter:junit-jupiter-api'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine'
    // junit-platform-launcher is added by gradle/test-config.gradle
}

apply {
    from rootProject.layout.projectDirectory.file('gradle/docs-config.gradle')
    from rootProject.layout.projectDirectory.file('gradle/test-config.gradle')
}
```

### Rules for module scripts

- **Apache license header** on every new `.gradle` / `.gradle.kts` file.
- Prefer **Groovy DSL** (this repo is almost entirely `.gradle`, not `.kts`).
- `version = projectVersion` and explicit `group` - do not invent version schemes per module.
- Use `rootProject.layout.projectDirectory.file('gradle/…')` for shared scripts (lazy layout API), not brittle `rootProject.file` string soup in new code.
- Prefer `tasks.named('x')` / `tasks.withType(T).configureEach` over eager `task x <<` or bare `tasks.x { }` mutation when touching existing modernized code.
- Configuration avoidance: do not call `.get()` on providers during configuration unless required; do not resolve configurations at configuration time.
- `api` vs `implementation` vs `compileOnly` vs `runtimeOnly` vs `testImplementation` - follow Java Library plugin semantics; public types in your API surface → `api`.
- Project deps: `project(':grails-foo')` using the **settings path**.
- External deps: coordinate **without version** when the BOM manages them.

### CLI companion modules

Command-bearing modules may apply `org.apache.grails.gradle.grails-plugin-cli` and declare `cliApi` / `cliImplementation` configurations (PR #15948). Framework modules that wire CLI with project deps set `ext.grailsCliAutoProvision = false` (root `build.gradle` does this for non-test-example projects). Do not dump CLI-only deps back onto the main runtime classpath.

---

## Functional / Test-Example Apps

See `grails-test-examples/app1/build.gradle`:

- Apply Grails app plugins: `org.apache.grails.gradle.grails-web`, often `org.apache.grails.gradle.grails-gsp`, and `cloud.wondrify.asset-pipeline`
- Still use `implementation platform(project(':grails-bom'))`
- Depend on published coordinates (`org.apache.grails:grails-dependencies-starter-web`) - root applies **dependency substitution** via `gradle/functional-test-config.gradle` so local projects replace Maven coordinates
- Apply `gradle/functional-test-config.gradle` (and datastore-specific scripts like `hibernate7-test-config.gradle` when needed)
- Do not disable substitution without understanding multi-project resolution

---

## Dependency Management (BOM Is Law)

### Single source of versions

| File | What it owns |
|------|----------------|
| **Root** `dependencies.gradle` | Application/runtime BOM versions (`bomDependencyVersions`, `bomDependencies`, `bomPlatformDependencies`) **and** gradle-tooling maps (`gradleBomDependencyVersions`, etc.). `grails-gradle` applies this same file via `../dependencies.gradle` - there is no separate `grails-gradle/dependencies.gradle` on this line |
| **`gradle.properties`** | Non-BOM pins (tool versions, javaVersion, gradleToolingApiVersion, checkstyle/codenarc/pmd/jacoco versions) |

**No `gradle/libs.versions.toml`.** This monorepo does **not** use Gradle version catalogs. Do not introduce a catalog "because Gradle docs recommend it." Dependabot and the published BOM pipeline are built around the root `dependencies.gradle` maps (see comment at top of that file).

### Map naming contract

For POM property generation, **map key must be the dependency name prefix**:

```groovy
bomDependencyVersions = [
    'groovy.version': '5.0.7',
]
bomDependencies = [
    'groovy': "org.apache.groovy:groovy:${bomDependencyVersions['groovy.version']}",
]
```

Break this and published BOM properties / docs extraction break.

### Platform usage in modules

```groovy
// Default
implementation platform(project(':grails-bom'))

// Hibernate 7 stack
implementation platform(project(':grails-hibernate7-bom'))

// Micronaut variants use enforcedPlatform in app-facing plugin logic
```

`grails-bom/base` (`:grails-base-bom`) is a `java-platform` that:

- Imports Spring Boot BOM via `api platform(...)` (with deliberate excludes for groovy/spock/hibernate/liquibase where Grails owns the line)
- Adds constraints from `dependencies.gradle` maps
- Adds constraints for published subprojects
- Applies `gradle/cli-companion-bom-constraints.gradle` for CLI companion versions under `enforcedPlatform`

### `validateDependencyVersions` (AGENTS.md rule 14 / dependency-validator plugin)

Applied via `org.apache.grails.buildsrc.dependency-validator`.

Implementation detail (`GrailsDependencyValidatorPlugin`): for each resolved coordinate that the BOM also manages, it fails when **`bomVersion != resolvedVersion`** - any mismatch, not only "resolved is newer."

How to fix by direction:

| Situation | Fix |
|-----------|-----|
| Transitive resolved **newer** than BOM | **Bump** the pin in `dependencies.gradle` so the BOM is `>=` the winner (usual case; AGENTS.md rule 14) |
| Resolved **older** / forced / strict conflict | Find the force, strict constraint, or second platform pulling the other version; remove the force, align platforms, or document a deliberate override |
| Intentional divergence that must stay | `ext.allowedBomOverrides = ['group:name', …]` with a **commented reason** - last resort |
| Whole project cannot validate | Prefer `ext.skipDependencyValidation = true` in the build script. CLI: `-PskipDependencyValidation` is presence-based and skips validation; `-PskipDependencyValidation=true` is also valid. Use the documented form that best communicates intent. |

Also:

- Prefer **inheriting Spring Boot managed versions** over re-pinning duplicates (PR #15730). Only pin when diverging (security override, missing from Boot BOM, lockstep companion like graphql-java-extended-scalars).
- Same coordinate managed in multiple BOM maps must use the **same** version everywhere or `enforcedPlatform` resolution explodes.
- Do **not** silence validation with exclusions as a shortcut to avoid a BOM bump.

### Adding or bumping a dependency

1. Decide if Spring Boot already manages it - if same version, omit pin.
2. If Grails must manage it, add/bump in the correct map in `dependencies.gradle`.
3. Use the unversioned coordinate in module `dependencies {}`.
4. Run `./gradlew :that-module:validateDependencyVersions` (and affected consumers).
5. Security overrides: comment with CVE and previous Boot version (see existing httpcore5/jackson/logback pins).

### Exclusions

Use sparingly, always with a reason. Common pattern for Hibernate:

```groovy
api 'org.hibernate.orm:hibernate-core', {
    exclude group: 'commons-logging', module: 'commons-logging'
    // ...
}
```

Do not exclude your way out of a BOM version fight. Prefer the direction-aware fixes in the validator table above (usually bump the BOM when a transitive is newer; otherwise align forces/platforms).

---

## Convention Plugins (`build-logic`)

Plugin IDs (implementation under `build-logic/plugins/…/buildsrc/`):

| Plugin ID | Purpose |
|-----------|---------|
| `org.apache.grails.buildsrc.properties` | Load root/`local.properties` into `ext` |
| `org.apache.grails.buildsrc.compile` | Java 21 `--release`, UTF-8, fork memory, parameters, sources/javadoc jars, reproducible archives, Groovy config script, isolated build, per-project `base.dir` |
| `org.apache.grails.buildsrc.dependency-validator` | `validateDependencyVersions` |
| `org.apache.grails.buildsrc.publish` | Publishing conventions (grails-publish integration) |
| `org.apache.grails.buildsrc.sbom` | CycloneDX / SBOM reproducibility |
| `org.apache.grails.buildsrc.vulnerability-scan` | OSS Index style scanning hooks |
| `org.apache.grails.buildsrc.groovydoc` | Groovydoc |
| `org.apache.grails.buildsrc.groovydoc-enhancer` | Groovydoc enhancer |
| `org.apache.grails.buildsrc.repo` | Settings plugin: Apache snapshot/staging repo content filters |
| `org.apache.grails.gradle.grails-code-style` | Checkstyle + CodeNarc |
| `org.apache.grails.gradle.grails-code-analysis` | PMD + SpotBugs (opt-in props) |
| `org.apache.grails.gradle.grails-jacoco` | JaCoCo per project |
| `org.apache.grails.gradle.grails-violation-aggregation` | **Root only** - `aggregateViolations` |
| `org.apache.grails.gradle.grails-ij-formatter` | IntelliJ formatter wiring |

### CompilePlugin behaviors you must not fight

- `JavaCompile.options.release` from `javaVersion` (21) - not outdated `sourceCompatibility`/`targetCompatibility` pairs in new code
- UTF-8 everywhere
- `-parameters` for reflection/IDE
- Forked compilation with `-Dgrails.isolated.build=true` and a **per-project** `BaseDirArgumentProvider` supplying `-Dbase.dir=<projectDir>`. It is marked `@Internal`, not `@InputDirectory`, because `projectDir` contains build outputs. Do not remove it: it prevents `grails.factories` leaking between compiler daemons (#15799 / `CompilePlugin` comments).
- The compile-time `base.dir` provider is required, but absolute `base.dir` values are forbidden in `Test.systemProperties`: they make cache keys machine-specific. Never "simplify" by removing the compiler provider or adding an absolute test property.
- `Jar.duplicatesStrategy = FAIL` - duplicate entries are configuration bugs
- Reproducible archives: no timestamps, fixed order, unix 0644/0755
- Groovy `configurationScript` → `gradle/groovy-compile-configscript.groovy` (annotation member order / GROOVY-12146 workaround, PR #15963)

### Published Grails Gradle plugins (`grails-gradle`)

Use the **fully qualified** plugin IDs in `plugins { id '…' }` blocks. Most are registered in `grails-gradle/plugins/build.gradle`. `org.apache.grails.gradle.grails-publish` is supplied by the external `grails-publish-plugin` implementation dependency (not listed in that file's `gradlePlugin {}` block) but is still the correct ID for publishing.

| Plugin ID |
|-----------|
| `org.apache.grails.gradle.grails-app` |
| `org.apache.grails.gradle.grails-web` |
| `org.apache.grails.gradle.grails-plugin` |
| `org.apache.grails.gradle.grails-gsp` |
| `org.apache.grails.gradle.grails-gson` |
| `org.apache.grails.gradle.grails-markup` |
| `org.apache.grails.gradle.grails-profile` |
| `org.apache.grails.gradle.grails-publish-profile` |
| `org.apache.grails.gradle.grails-cli` |
| `org.apache.grails.gradle.grails-plugin-cli` |
| `org.apache.grails.gradle.grails-cli-library` |
| `org.apache.grails.gradle.grails-exploded` |
| `org.apache.grails.gradle.grails-integration-test` |
| `org.apache.grails.gradle.grails-test-phases` |
| `org.apache.grails.gradle.bom-property-overrides` |
| `org.apache.grails.gradle.grails-publish` (via grails-publish-plugin dependency) |

Never paste bare suffixes like `grails-web` into a `plugins` block - resolution will fail.

When changing these plugins:

- Prefer lazy task configuration; **never resolve configurations inside `configureEach` at configuration time** (PR #16076 - Gradle 9.5+ `markAsObserved` failures).
- Be careful with nested `afterEvaluate` ordering (PR #16009 - BOM apply vs CLI detect race).
- Declare Copy/processResources filter values as **task inputs** (PR #16006 - ReplaceTokens up-to-date bug).
- Functional tests live under `grails-gradle/plugins/src/test` with TestKit projects - update them with behavior changes.
- Build/test from `grails-gradle/` directory with **its** wrapper.

---

## Shared Scripts Under `gradle/`

Apply with:

```groovy
apply {
    from rootProject.layout.projectDirectory.file('gradle/test-config.gradle')
}
```

| Script | Use |
|--------|-----|
| `test-config.gradle` | JUnit Platform, parallel forks, heap, cache policy, skip flags, launcher deps |
| `functional-test-config.gradle` | Dependency substitution for test-examples |
| `docs-config.gradle` / `docs-dependencies.gradle` | Groovydoc / docs classpaths |
| `publish-root-config.gradle` | Root publishing orchestration |
| `rat-root-config.gradle` | Apache RAT |
| `cli-companion-bom-constraints.gradle` | CLI artifact constraints on BOMs |
| `hibernate5-test-config.gradle` / `hibernate7-test-config.gradle` | Datastore test stacks |
| `spring-security-test-config.gradle` | Spring Security functional and integration tests |
| `grails-data-tck-config.gradle` | GORM data TCK wiring and test filters |
| `grails-extension-gradle-config.gradle` | Gradle extension module conventions |
| `test-webjar-asset-config.gradle` | WebJar asset test setup |
| `mongodb-forked-test-config.gradle` | Forked MongoDB test configuration |
| `mongodb-*-test-config.gradle` / `redis-test-config.gradle` | External service tests |
| `plugin-repositories.gradle` | Shared plugin repo config for settings |
| `groovy-compile-configscript.groovy` | Groovy compiler config script (not applied via `apply from` in modules - referenced by CompilePlugin) |

### Test flags (`test-config.gradle`)

Presence of project properties skips/selects suites, e.g.:

`skipTests`, `skipCoreTests`, `onlyFunctionalTests`, `onlyHibernate5Tests`, `onlyHibernate7Tests`, `onlyMongodbTests`, `onlyRedisTests`, `onlySpringSecurityTests`

Parallelism: `configuredTestParallel` from `-PmaxTestParallel` or CI default **3** / local `availableProcessors * 3/4`.

Env:

- `DO_NOT_CACHE_TESTS=1` - force test re-run without full `--rerun-tasks`
- `debug.tests` system prop - attach debugger args
- `SUPPRESS_DEPRECATION_WARNINGS=true` - strip some `-Xlint` noise

CI disables build cache for `GroovyCompile` and `Test` so AST transforms and tests stay honest.

---

## Configuration Hygiene (Gradle 9.x Landmines)

These bit this repo in production PRs. Treat as hard rules when writing plugin or build code:

1. **No configuration-time classpath resolution** in task `configureEach` callbacks. Defer probes to execution (`doFirst` / task actions) or use proper providers (PR #16076).
2. **No nested `afterEvaluate` that mutates configurations** after another plugin may have resolved a related configuration (PR #16009). Prefer `withPlugin` / `plugins.withId` / lazy `configurations.configureEach` before observation.
3. **Task inputs must include filter/token maps** and any other non-file data that affects outputs (PR #16006).
4. **All custom task types** must declare caching intent (`@DisableCachingByDefault` or correct cacheable annotations) - required since Gradle 9 upgrade (PR #15365).
5. Prefer **`JavaPluginExtension`** over deprecated `JavaPluginConvention`; **`destinationFile`** over `outputFile` on `WriteProperties`; avoid `ConfigureUtil` / old `convention` APIs.
6. Tests need **`junit-platform-launcher`** on `testRuntimeOnly` (shared script adds it) - Gradle 9 requirement.
7. Do not enable **configuration cache** or **configure-on-demand** in this repo without an issue-linked plan.
8. **`evaluationDependsOn`** appears in BOM/docs scripts for a reason - do not cargo-cult it into random modules; it couples configuration order and slows builds.

### Build Cacheability

The local build cache is enabled. Treat cacheability as a correctness constraint, not a later optimization.

| Rule | Why / practice |
|------|----------------|
| Do not put absolute machine paths, especially `base.dir`, in `Test.systemProperties` | They poison cache keys across machines (#15483). The compiler's `@Internal` `BaseDirArgumentProvider` is the separate, required compile-time case. |
| Avoid `doFirst` and custom actions on cacheable tasks | They can make a task ineligible for the build cache. Prefer a dedicated task with declared inputs and outputs or model the I/O properly. Some tradeoffs are intentional: GroovyDoc compatibility with configuration cache and selected `GroovyCompile` `doFirst` actions in `GrailsGradlePlugin`. |
| Give compiler configuration and reports task-specific paths | Overlapping outputs disable caching. Use names such as `grailsGroovyCompilerConfig-{taskName}.groovy` and separate Checkstyle / CodeNarc report paths (#15532). |
| Do not use `outputs.upToDateWhen` to bypass work when it also prevents cache loading | Model inputs and outputs instead. Keep GSP outputs separate, for example `gsp-classes/main` versus `webapp` (#15537). |
| Normalize generated unstable files packed into jars or classpaths | `SbomPlugin` uses `normalization.runtimeClasspath.ignore("META-INF/sbom.json")`. Add comparable normalization so unstable generated contents do not cascade cache misses. |

Use Develocity experiments to prove a change: populate the cache, then delete task outputs (or `clean`) and rebuild - cacheable tasks should report `FROM-CACHE`. A plain second build with outputs still present usually reports `UP-TO-DATE`, which does not prove remote/local cache loading.

---

## Adding a New Subproject (Checklist)

1. Choose directory layout consistent with family (`grails-foo/…` or nested under existing tree).
2. `include 'grails-foo'` (or nested name) in root `settings.gradle`.
3. If path != default, set `project(':grails-foo').projectDir = file('…')`.
4. Copy a sibling `build.gradle` plugin/dependency skeleton; set `group`/`ext.pom*` as needed.
5. Wire `platform(project(':grails-bom'))` or the correct variant BOM.
6. If published: ensure publish plugin + BOM constraint inclusion (base BOM auto-discovers published projects with grails-publish plugins).
7. If it has commands: plan CLI companion artifact (`grails-plugin-cli`), not runtime leakage.
8. If test-example: use functional-test-config + external coordinates + substitution.
9. Register in any root aggregators if required (docs, publish-root-config, CI matrices).
10. Run:
    ```bash
    ./gradlew :grails-foo:compileGroovy :grails-foo:test :grails-foo:validateDependencyVersions
    ```

For importing an entire external plugin repository, use **`mono-repo-integration`** skill instead of this checklist alone.

---

## Commands Cheatsheet

```bash
# Always from the owning build root (usually repo root)
./gradlew :grails-core:compileGroovy
./gradlew :grails-core:test
./gradlew :grails-core:test --tests 'org.example.SomeSpec'
./gradlew :grails-core:validateDependencyVersions

# Build without tests
./gradlew build -PskipTests

# Style / analysis (see violation-fixer)
./gradlew :grails-core:codeStyle
./gradlew aggregateViolations

# Publish to ~/.m2 (maintainers / forge local cascade) - NOT what end-to-end uses
./gradlew publishAllToMavenLocal   # grails-gradle → root → forge

# Publish to build/local-maven for end-to-end (and forge TestCaseMavenRepo consumers)
(cd grails-gradle && ./gradlew publishAllPublicationsToTestCaseMavenRepoRepository)
./gradlew publishAllPublicationsToTestCaseMavenRepoRepository

# Force re-run tests
./gradlew :module:test --rerun-tasks
# or
DO_NOT_CACHE_TESTS=1 ./gradlew :module:test

# Parallelism override / flake bisect
./gradlew :module:test -PmaxTestParallel=1
./gradlew :module:test -PtestBisect

# Memory
export GRADLE_OPTS='-Xms2G -Xmx5G'
```

Work in `grails-gradle` or `grails-forge` only with **that** directory's `./gradlew`.

Develocity: `https://develocity.apache.org` - build scans publish when authenticated; remote cache push is CI-only.

---

## Anti-Patterns (Reject These)

| Do not | Do instead |
|--------|------------|
| Apply Spring Dependency Management plugin | `platform` / `enforcedPlatform` + bom-property-overrides |
| Apply Spring Dependency Management outside `grails-test-examples/spring-dependency-management` | Native platforms + bom-property-overrides |
| Hardcode versions in module `dependencies {}` | BOM maps in root `dependencies.gradle` |
| Introduce `libs.versions.toml` catalogs | Keep `dependencies.gradle` maps |
| Silence `validateDependencyVersions` without comment | Fix the mismatch (usually bump BOM if transitive is newer; else remove force / align platforms / document override) |
| Root `./gradlew test` after a one-module edit | `./gradlew :module:test` |
| Enable configuration cache "because Gradle says so" | Leave off until #15497 |
| Resolve `configuration.files` in `configureEach` | Defer to execution / providers |
| Add an absolute machine path to `Test.systemProperties` | Use portable task inputs; keep compiler-only `base.dir` in its `@Internal` argument provider |
| Give multiple tasks the same compiler config or report output path | Make every output path task-specific |
| Use `outputs.upToDateWhen` in a way that prevents cache loading | Declare inputs and outputs or use a dedicated task |
| Pack unstable generated files without classpath normalization | Add an explicit normalization ignore when appropriate |
| Duplicate CompilePlugin settings in module scripts | Trust convention plugins |
| Invent new plugin IDs without build-logic registration | Add descriptor + tests in build-logic |
| Bump one wrapper only | Sync all wrapper locations |
| Put CLI-only deps on runtime classpath | CLI companion artifact / cli* configurations |
| `buildscript { classpath … }` for plugins already on pluginManagement | `plugins { id '…' }` |
| Kotlin DSL for a one-off module in a Groovy DSL repo | Groovy DSL `build.gradle` |
| Wildcard imports in buildsrc Groovy | Explicit imports (same as app code style) |
| Add `repositories {}` in a subproject | Root/settings repo management (`FAIL_ON_PROJECT_REPOS`) |
| `includeBuild` the root into `end-to-end` | Publish to `build/local-maven` via `publishAllPublicationsToTestCaseMavenRepoRepository` |
| Use `publishAllToMavenLocal` to feed end-to-end | That fills `~/.m2`; end-to-end reads `<repo>/build/local-maven` |
| Bump `legacy-g7-command-plugin` wrapper to Gradle 9 | Leave it on the Grails 7-pinned Gradle 8.x |
| Mix Gradle-embedded Groovy 4 pins into app Groovy 5 BOM | Keep `gradleBom*` vs `bom*` maps separate |

---

## Gradle 9 Doc Notes (Useful, Not Absolute)

Use official docs for API signatures and deprecations (pin URLs to the version you are bumping toward):

- [Gradle 9.6 release notes](https://docs.gradle.org/9.6.0/release-notes.html)
- [Upgrading major version 9](https://docs.gradle.org/9.6.0/userguide/upgrading_major_version_9.html)
- [Upgrading within Gradle 9.x](https://docs.gradle.org/9.6.0/userguide/upgrading_version_9.html)
- [Java Library plugin](https://docs.gradle.org/9.6.0/userguide/java_library_plugin.html)
- [Java Platform / BOM](https://docs.gradle.org/9.6.0/userguide/java_platform_plugin.html)
- [Platforms](https://docs.gradle.org/9.6.0/userguide/platforms.html)
- [Sharing build logic via included builds](https://docs.gradle.org/9.6.0/userguide/sharing_build_logic_between_subprojects.html)
- [Task configuration avoidance](https://docs.gradle.org/9.6.0/userguide/task_configuration_avoidance.html)
- [Configuration cache](https://docs.gradle.org/9.6.0/userguide/configuration_cache.html) (read for compatibility - still **off** here)
- [Version catalogs](https://docs.gradle.org/9.6.0/userguide/version_catalogs.html) (docs like them - **this repo does not**)

### Docs say X - we do Y

| Gradle docs lean | This repo |
|------------------|-----------|
| Turn on configuration cache | Off (`#15497`) |
| Version catalogs (`libs.versions.toml`) | `dependencies.gradle` maps + published BOM |
| `enforcedPlatform` is dangerous for libraries | Used deliberately for Micronaut-variant BOMs + validator |
| Prefer toolchains everywhere | `options.release` from `javaVersion=21` via CompilePlugin (toolchains optional elsewhere) |
| JVM Test Suite plugin for extra suites | Existing `test` / `integrationTest` + shared `gradle/*-test-config.gradle` |
| Avoid `afterEvaluate` | Still present in plugins - change carefully; prefer `plugins.withId` for new code |
| Configure on demand | Explicitly disabled (Gradle #9489) |

Removed/deprecated APIs AI still emits - **reject on sight** in new code: `jcenter()`, `Project.exec` / `Project.javaexec` (use injected `ExecOperations`), `JavaPluginConvention`, `ConfigureUtil`, old `convention` APIs, `WriteProperties.outputFile` (use `destinationFile`), bare `tasks.create` / eager `getByName` when `register`/`named` suffice.

### Repositories

`GrailsRepoSettingsPlugin` configures settings-level repos and **`RepositoriesMode.FAIL_ON_PROJECT_REPOS`**. Adding `repositories { mavenCentral() }` inside a random subproject will fail the build. Fix repo needs in settings / the repo settings plugin, not per-module.

---

## Lessons From Recent 8.0.x Gradle PRs

| PR | Takeaway |
|----|----------|
| #15467 | Spring DM removed; platforms + bom-property-overrides |
| #15483 | Cacheable tasks need portable inputs; avoid absolute test properties and normalize packed unstable SBOM metadata |
| #15365 | Gradle 9.4 API cleanup; caching annotations; junit launcher |
| #15532 | Overlapping task outputs disable caching; use per-task compiler and report paths |
| #15537 | Bad `outputs.upToDateWhen` predicates block cache loads; keep GSP output directories distinct |
| #15672 / #15763 | Wrapper multi-location sync discipline |
| #15730 | Prefer Boot BOM inheritance over duplicate pins |
| #15686 / #15687 | code-style / analysis / jacoco / violation aggregation plugins |
| #15948 | CLI split from runtime; companion artifacts |
| #15963 | Groovy compile config script / annotation order |
| #16006 | processResources tokens must be task inputs |
| #16009 | afterEvaluate ordering vs configuration observation |
| #16076 | No compile classpath probes at GroovyCompile configuration time |
| #16069 | Dependency substitution for functional tests is hot-path config work - keep it cheap and cached |
| #16078 | Keep CLI/test-tier deps off the production runtime classpath; do not ship unfixed native tooling (Jansi) |

When fixing a new Gradle failure, search merged PR titles for the exception text before inventing a workaround.

---

## Agent Quality Bar

Strong 8.0.x Gradle PRs are small, sibling-consistent, and evidence-backed:

- State the **Problem**, **Fix**, **Why**, and **Verification** in the PR or handoff.
- Match the closest existing module, script, or convention plugin before adding a new pattern.
- Avoid drive-by reformatting, dependency churn, or unrelated modernization.
- Verify with the narrowest commands that cover the changed project: compile, targeted tests, and dependency validation as applicable.
- For `grails-gradle`, include the relevant TestKit coverage.
- Use dual review when available, especially for build, dependency, and publishing changes.
- Report exact commands and results. Evidence beats confidence.

---

## Files to Read First (By Task)

| Task | Read |
|------|------|
| New library module | Sibling `build.gradle`, `settings.gradle` include section, `CompilePlugin.groovy` |
| Version bump | `dependencies.gradle`, maybe Spring Boot BOM notes in PR #15730 |
| Wrapper bump | `gradle/wrapper/gradle-wrapper.properties` comment checklist |
| Test wiring | `gradle/test-config.gradle`, `gradle/functional-test-config.gradle` |
| BOM / platform | `grails-bom/base/build.gradle`, `GrailsDependencyValidatorPlugin.groovy` |
| App plugin behavior | `grails-gradle/plugins/.../GrailsGradlePlugin.groovy` |
| Convention plugin change | `build-logic/plugins/...` + its Spock tests |
| Publishing | `PublishPlugin.groovy`, `gradle/publish-root-config.gradle` |
| Style gates | `violation-fixer` skill + code-style plugins |

---

## Definition of Done (Gradle Change)

- [ ] Matches sibling module patterns (plugins, group, platform, apply scripts)
- [ ] No hardcoded versions that belong in `dependencies.gradle`
- [ ] `validateDependencyVersions` clean for touched modules (or documented override)
- [ ] Scoped Gradle verify command green (`:module:compileGroovy`, `:module:test`, plugin TestKit as applicable)
- [ ] Wrapper/version sync complete if Gradle version changed
- [ ] No configuration-time resolution / nested afterEvaluate hazards introduced
- [ ] No absolute machine paths in `Test.systemProperties`; compiler `base.dir` remains isolated in `BaseDirArgumentProvider`
- [ ] No overlapping task outputs; classpath normalization considered for packed generated files
- [ ] Apache headers on new files
- [ ] User-facing behavior documented if it affects app builds (`grails-doc`)
- [ ] For commit-ready work, use `violation-fixer` to run the AGENTS.md gate: `./gradlew clean aggregateViolations :grails-test-report:check --continue`. This is not required merely to read this skill.

If you only remember three things: **copy siblings**, **BOM owns versions**, **never resolve configurations while configuring tasks**.
