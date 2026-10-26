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

> Working design for review, not an implemented specification.
> Imported on 2026-10-26 from [issue #16348, comment 6150508865](https://github.com/apache/grails-core/issues/16348#issuecomment-6150508865),
> revision 10 (last updated 2026-10-13T17:07:30Z).
> The proposal below is preserved as the starting point for local iteration.
> Its code references and status statements describe the original proposal's base.
> Local review baseline: `upstream/8.1.x` at `abd7ea720d0e3db96a48a89ee82ce7e79e069524`.
> PR #16551 merged on 2026-10-14; its pending-merge references below are historical.

Contents

1. [1. Verdict on the proposal](#verdict)
2. [2. Target architecture](#architecture)
   1. [2.1 One command tree](#one-cli)
   2. [2.2 Feature packs](#engine)
   3. [2.3 Catalog and fetch](#metadata)
   4. [2.4 The .grails directory](#project-state)
   5. [2.5 The console](#console)
   6. [2.6 feature add](#feature-add)
   7. [2.7 Hosted Forge](#hosted)
3. [3. What profiles offered](#gaps)
4. [4. Removal inventory](#removal)
5. [5. Delivery](#phases)
6. [6. Open decisions](#decisions)
7. [7. Testing and docs](#testing)
8. [A. Command tree](#appendix-a)
9. [B. Facts relied on](#appendix-b)
10. [Change log](#changelog)

apache/grails-core #16348

# One generator: Forge, feature packs, and a profile-free console

**Base** feat/forge-grails-composite (PR #16551, target 8.1.x) **Verified** 2026-10-13 **PR state** approved, conflicting with the 8.1.x tip **Revision** 10, 2026-10-13 (see [change log](#changelog))

This plan evaluates the proposal to make `grails-forge-cli` the only application generator, let plugins extend generation without compiling code into Forge or running Gradle, remove profiles, and keep `grails-shell-cli` as the interactive console. File pointers are relative to the PR #16551 branch. Merging that PR, after resolving its conflict with the current `8.1.x` tip, is step zero.

## 1. Verdict on the proposal

The direction is right. Profiles bought two things in Grails 3: a skeleton engine and an extensibility story, since anyone could publish a profile jar without a Grails pull request. Forge is now the better skeleton engine (exclusive options, Hibernate 7 BOM switching, preview and diff, a website, version slots) but has no extensibility story. Every feature is a compiled `@Component` in `grails-forge-core` found by component scan, and the shipped CLI is one 48 MB shadow jar with no extension directory (`grails-forge/grails-cli/build.gradle:117`). A declarative, data-only feature format that plugins ship inside their jars restores what profiles offered without compiling anything into Forge and without Gradle at generation time.

| Proposal item | Verdict | Adjustment |
| --- | --- | --- |
| 1. forge-cli searches plugin metadata and selects plugins | Yes | The metadata is `apache/grails-plugins-metadata`: 256 reviewed plugin entries with coordinates, labels, docs and an indexer that already downloads each released jar to read its descriptor. Nothing in the framework reads it yet: today's shell `list-plugins` command still scrapes an HTML directory listing of the legacy `org.grails.plugins` repository (`grails-profiles/base/commands/list-plugins.groovy:7`), and Forge reads only its own `pom.xml` for third-party versions. Both go away. The plan makes Forge read `grails-plugins-index.json` from the repository's `main` branch and adds one artifact: a **feature pack** (per-plugin descriptor plus templates) that the indexer learns to detect. |
| 2. forge-cli updates existing apps with a plugin's feature | Yes, scoped | Make it `grails feature add`, built on the existing `FeatureDiffer` (web-only today) and applied as a three-way patch with a dry run. Not a general rewriter. The `update-cli-config` command the code tells users to run was never implemented (`grails-forge-cli/.../CodeGenConfig.groovy:181`); a `project migrate` command replaces it. |
| 3. Remove `create-app` from grails-shell-cli | Yes | Keep the command name; it becomes Forge's. Delete the dispatcher (`DelegatingShellApplication`), `-t/--type`, and `GRAILS_PREFERRED_SHELL`, which still defaults to the profile shell (`:56`). |
| 4. Remove profiles | Yes, staged | The widest blast radius. Every app build has a `profile` configuration with a default `org.apache.grails.profiles:web` dependency (`grails-gradle/plugins/.../GrailsGradlePlugin.groovy:461-490`); `grails.profile` is injected as an active Spring profile at runtime (`grails-core/.../GrailsApplicationPostProcessor.groovy:451`); 109 in-repo `application.yml` files set it; the BOM publishes profile constraints; the release verifier inspects the base profile jar (`etc/bin/verify-jar-artifacts.sh:208-236`). Nothing is removed in a minor release: the modules, shell code, `profile` Gradle configuration and runtime `grails.profile` injection all go in 9.0.x together, with `project migrate` and the upgrade guide carrying existing applications. |
| 5. grails-shell-cli stays as the interactive console | Yes, but a rewrite | The shell codes almost nothing itself. Inside a project every command, including `help`, `gradle` and plugin `ApplicationCommand`s, is dispatched through the resolved profile (`grails-shell-cli/.../GrailsCli.groovy:385`, `AbstractProfile.groovy:461-499`), and roughly 40 user-facing commands (run-app, test-app, clean, compile, war, console, every artefact `create-*`) are profile scripts. The console needs a profile-free command catalog and built-in lifecycle commands. |
| `.grails/` directory, gitignored | Partly | Use `.grails/`, but **commit** the manifest (`.grails/project.yml`) and ignore only caches and run state. `feature add`, codegen gating, IDE detection and teammates' clones all need the manifest. Forge already renders a `.gitignore` (`grails-forge-core/grails-app/views/forge/feature/build/gitignore.gsp`), so the ignore rules are a template edit. |

## 2. Target architecture

Section 2 describes the end state. Section 5 says what can land in `8.1.x` (additions, and replacements that function the same) and what waits for `9.0.x` (every removal).

### 2.1 One `grails` command tree

- Picocli root = Forge's `ForgeCommand`, renamed `grails`. Subcommands: `create-app`, `create-restapi`, `create-plugin`, `create-web-plugin`, `create-rest-api-plugin` (#14932), `feature list|search|info|diff|add`, `plugin search|info` (catalog aliases), the artefact `create-*` commands, `project init|migrate`, and the console commands (`run-app`, `test-app`, `gradle`, `open`, `help`, plugin commands). With no arguments inside a project it opens the interactive console; outside a project it prints help.
- The console's project commands are registered into the same Picocli tree at startup from the three sources in section 2.5, so completion and help are unified. Forge's `InteractiveShell` already fixes its completion set at startup (`grails-forge-cli/.../ForgeCli.groovy:103-106`); that stays.
- Delete `DelegatingShellApplication.groovy`, the `grails-shell-cli` and `grails-forge-cli` start scripts (`grails-forge/grails-cli/build.gradle:166-192`), and the `-t` handling (9.0.x; through 8.x they stay and the new tree sits beside them).
- Startup budget. The profile shell resolves `grails-bom` through Aether in a constructor even for `grails --help` outside a project (`grails-shell-cli/.../GrailsDependencyVersions.groovy:65-82`) and resolves the project classpath through the Tooling API before `help` inside one. Removing the Spring Boot CLI port (`run`, `S/boot`, `S/compiler`, `S/archive`, maven-resolver) and all profile resolution is the main speed win. The Forge CLI still boots a full Grails context per invocation (37 auto-configurations merged into the shadow jar); set a measured target such as 500 ms for `--help` and track it with the init-script timing recipe used for #16337.

### 2.2 Generation engine: compiled features plus declarative feature packs

Forge keeps compiled `Feature` beans for framework-owned concerns: application types, build files, BOM switching (`GrailsBase`), `OneOfFeature` groups, options. It gains a `DeclarativeFeature` adapter that turns a descriptor plus a template directory into a `Feature` when the registry is built. Packs are loaded from three places:

1. built-in packs bundled in `grails-forge-core` resources (official plugins migrate from compiled classes to packs wherever the feature is "dependencies plus config plus files");
2. packs fetched from plugin jars at generation time (third-party, section 2.3);
3. a local directory (`--feature-pack-dir`) for authoring and testing a pack before publishing.

Descriptor, `META-INF/grails-forge/feature.yml`:

```
format: 1
name: acme-audit
title: Acme Audit Trail
description: Adds the Acme audit plugin and its default configuration
category: Logging                      # a Forge Category string
documentation: https://acme.example/docs/grails
applicationTypes: [web, rest-api]      # default: all
requires: [gorm-hibernate7]            # auto-selected, like processSelectedFeatures
conflicts: [gorm-mongodb]              # fails validatePreProcessing with a message
oneOf: audit                           # optional exclusive group
dependencies:
  - scope: implementation
    coordinates: com.acme:acme-audit-grails
    version: ${pack.version}           # default; omit for BOM-managed coordinates
  - scope: runtimeOnly
    coordinates: com.acme:acme-audit-h2
    when: { feature: h2 }              # conditional on another selected feature
    excludes: [org.slf4j:slf4j-simple] # new: Dependency has no exclusions today
platforms:
  - coordinates: com.acme:acme-bom
    version: 2.3.0
buildPlugins:
  - id: com.acme.audit
    version: 2.3.0
build:                                 # typed, merged build-file contributions (see "Build-file contributions")
  properties:
    acme.audit.verbose: 'false'        # gradle.properties
  extensions:
    acmeAudit: |                       # one acmeAudit { } block, merged with other packs' entries
      retention = 90
    tasks.bootRun: |                   # tasks.named('bootRun') { }
      systemProperty 'acme.audit.console', 'true'
configuration:                         # merged through Forge's configuration model
  acme.audit.enabled: true
  environments:
    production:
      acme.audit.flush: 30s
files:                                 # relative to META-INF/grails-forge/templates/
  - from: AuditConfig.groovy.gsp
    to: grails-app/conf/{packagePath}/AuditConfig.groovy
  - from: audit.css                    # no suffix: copied verbatim, binary-safe
    to: grails-app/assets/stylesheets/audit.css
readme: |
  Audit events are written to the `audit_event` table. See the documentation link.
instructions: |                        # printed after create-app, as profiles did
  Run `./gradlew dbmGormDiff` to generate the audit tables migration.
artefacts:                             # adds `grails create-audit-listener <Name>`
  - command: create-audit-listener
    description: Creates an audit listener and its specification
    files:
      - from: artefact/AuditListener.groovy.gsp
        to: src/main/groovy/{packagePath}/{className}AuditListener.groovy
      - from: artefact/AuditListenerSpec.groovy.gsp
        to: src/test/groovy/{packagePath}/{className}AuditListenerSpec.groovy
```

Mapping onto what exists in `grails-forge-core`:

| Descriptor key | Forge call | Notes |
| --- | --- | --- |
| `dependencies`, `platforms` | `GeneratorContext.addDependency` with `Dependency.builder()` scopes, `.pom(true)` for platforms (`build/dependencies/Dependency.groovy:187-266`, `GradleDependency.groovy:102-122`) | Add `excludes` to the model. Declarative features run at `FeaturePhase.DEFAULT`; anything after `BUILD` is silently dropped by the build-file snapshot (`feature/build/gradle/Gradle.groovy:73`). |
| `buildPlugins` | `GradlePlugin.builder().id().version()` |  |
| `build.properties` | `getBuildProperties().put` (`GeneratorContext.groovy:145`) | Snapshotted at `BUILD` like dependencies (`Gradle.groovy:80`). |
| `build.extensions`, `build.blocks`, `build.imports`, `build.settings` | a new keyed contribution API on `GradleBuild`, replacing the single `renderExtensions()` slot (`GradleBuild.groovy:122-134`) and `renderSettingsExtensions()` | See "Build-file contributions". |
| `configuration` | `getConfiguration()`, `getConfiguration(env)` (`GeneratorContext.groovy:152-188`) |  |
| `files` | `addTemplate` with `GspTemplate` over a pack-scoped page locator, or `BinaryTemplate` for verbatim files | Destination tokens are the existing `{packageName}`, `{packagePath}`, `{className}`, `{naturalName}`, `{propertyName}`, `{name}` (`DefaultTemplateRenderer.groovy:44-62`). |
| `readme` | `addHelpTemplate` |  |
| `requires`, `conflicts`, `oneOf` | `FeatureContext.addFeature`, a `FeatureValidator`, a synthetic `OneOfFeature` group |  |
| `artefacts` | new entries in the codegen command set, which is a static list today (`ForgeCli.CODE_GEN_COMMANDS`, `ForgeCli.groovy:45-57`) | Must become dynamic. |

**Template language: GSP source, compiled by the CLI.** External packs are a CLI-only capability; start.grails.org hosts the framework's own features and never renders a third-party pack (section 2.7). That removes the reason to keep pack templates logic-less, so packs use the same language as Forge's own 62 templates: GSP, with `<%@ page trimLogicLines="true" expressionCodec="none" %>` and the same model (`project`, `features`, `options`). Bundled official packs are precompiled by the monorepo build as today. A third-party pack ships GSP *source*, and the CLI compiles it at generation time through `GroovyPagesTemplateEngine` with development mode on for the pack's directory (today `ForgePages` forces it off, `template/ForgePages.groovy:62-67`), caching the compiled classes under `~/.grails/forge/cache/<gav>/classes/<forge version>/`. Source packs are tied to GSP syntax, not to a Groovy or GSP binary version, so one pack serves every Grails line its plugin supports. Files without a `.gsp` suffix are copied as bytes. A pack therefore runs the plugin author's code at generation time; that is the same trust the user already extends to the plugin's Gradle plugin and runtime, and the CLI says so when it fetches a pack.

What this buys: converting an official compiled feature into a pack is a file move plus a descriptor, not a template rewrite; plugin authors write the template language they already know; and the `features.contains('gorm-mongodb')` branches in today's templates carry over unchanged. What it costs: a few hundred milliseconds of Groovy compilation per template on first use; type errors in a statically compiled page (`<%@ model %>`) surface at generation time rather than at the author's build; and no static validation of a pack beyond its descriptor.

Mustache (jmustache, logic-less, no code execution) stays the fallback if a logic-less format is ever needed, for example if the hosted service were ever to render community packs; the descriptor's `format` version leaves room for a `templateEngine` field. An earlier revision of this plan recommended Mustache for exactly that hosted-safety reason; with the hosted service official-only, the trade favours GSP.

**Precedents.** Quarkus extensions declare `metadata.codestart` in `quarkus-extension.yaml`, ship the codestart in a `codestarts` classifier jar (`codestart.yml` plus Qute templates), and users select them with `quarkus create app -x group:artifact:version`. Spring Initializr keeps its dependency catalog declarative (`initializr.dependencies` with `compatibilityRange`, `bom`, `repository`) and reserves code (`ProjectContributor`, `BuildCustomizer`) for the service itself. Grails 3 profiles' `feature.yml` plus `skeleton/` is the same shape as this proposal; it lived in the wrong engine.

**Conditional logic in packs.** Forge features routinely add code depending on which other features are selected. On this branch 22 of the 118 feature classes and 10 of the 47 GSP templates branch on other features, in three patterns, and each has a declarative home. The rule: logic lives in the descriptor as data that Forge evaluates, and templates stay logic-less.

1. *Auto-adding a collaborator* (`processSelectedFeatures`): Hibernate 7 adds H2 when no driver feature is selected (`database/GrailsDataHibernate7.groovy:64`), Geb adds Spock, Scaffolding adds GSP, Spring Security UI adds Spring Security. Most of the 22 classes do only this. It is the descriptor's `requires` list, evaluated during selection, before anything renders. The "add a default if nothing of this kind is present" variant needs a group form:

   ```
   requires:
     - gsp
     - oneOf: { group: database-driver, default: h2 }
   ```
2. *Conditional contributions* (`apply`): `TestContainers` adds `testcontainers-spock` only when Spock is present and `testcontainers-mongodb` only when a Mongo feature is (`database/TestContainers.groovy:81-87`). That is a `when` predicate on a dependency, file or configuration entry, so the entry exists or does not; no template logic is involved:

   ```
   dependencies:
     - { scope: testImplementation, coordinates: org.testcontainers:testcontainers-spock, when: { feature: spock } }
     - { scope: testImplementation, coordinates: org.testcontainers:testcontainers-mongodb, when: { anyOf: [gorm-mongodb, mongo-sync] } }
   files:
     - { from: MongoSpec.groovy.gsp, to: src/integration-test/groovy/{packagePath}/MongoSpec.groovy, when: { feature: gorm-mongodb } }
   ```
3. *Branching inside a file*: the security `user.gsp` emits a `collection 'users'` mapping for MongoDB and a `table 'users'` mapping for either Hibernate (`feature/security/template/user.gsp:41-50`). Pack templates are GSP, so this is written exactly as it is today, and selection finishes before rendering starts, so the `features` list a template sees is final:

   ```
   <% if (features.contains('gorm-mongodb')) { %>
       static mapping = {
           collection 'users'
       }
   <% } else if (features.contains('gorm-hibernate5') || features.contains('gorm-hibernate7')) { %>
       static mapping = {
           table 'users'
       }
   <% } %>
   ```

   A descriptor may still name a condition once as a flag and reuse it in several files (`<% if (flags.sql) { %>`), which keeps templates short, but nothing requires it:

   ```
   flags:
     sql: { anyOf: [gorm-hibernate5, gorm-hibernate7] }
   ```

One predicate language serves `when`, `requires` and `flags`: `feature`, `anyOf`, `allOf`, `not`, `applicationType`, and `option` (for example `option: { data: hibernate7 }`). Predicates matter most for the parts a pack does not write as a template: the build file and `application.yml` are generated by Forge from the dependency and configuration models, so a conditional dependency or configuration key has to be declared, not templated. The render model is the one today's GSPs use (`project` with `name`, `className`, `propertyName`, `naturalName`, `packagePath`; `features`; `options`) plus the pack's `flags` and any `params` the pack declares (the user and role class names of an `s2-quickstart`-style pack).

Two things in the inventory stay compiled: features that take over a slot another feature owns (`GrailsBase` choosing the BOM, the Gradle plugin picking `grails-web` versus `grails-plugin`, `buildGradle.gsp` branching per feature name) and features that reshape another feature's templates. A pack declares `conflicts` and `requires`; it does not rewrite someone else's output.

**How the template decision changed.** Revisions 1 to 5 of this plan recommended Mustache, on the grounds that a pack had to be safe to render on start.grails.org. Once external packs became a CLI-only capability (section 2.7), the remaining arguments were weighed again:

- *Precompiled GSP in the plugin jar* was rejected on its own merits: compiled pages bind to one Groovy and GSP version, so a pack built on Grails 8.0 is not guaranteed to load in the 8.1 CLI and will not load in a Groovy 6 CLI, and precompiling drags `grails-web-gsp` plus the servlet API into every plugin's build. Source packs compiled by the CLI avoid both.
- *Code execution* is accepted in the CLI. A user who selects a plugin already runs that plugin's Gradle plugin at build time and its code at runtime; a pack adds nothing new to that trust, and the CLI names the pack and its checksum before first use.
- *Reviewability and tooling* are weaker with GSP: a pack cannot be validated or previewed without executing it. `feature diff` and `--dry-run` still work, because rendering in memory is how Forge previews anyway.
- *Whitespace* is handled by the `trimLogicLines` page directive that already exists for Forge's templates.

Mustache, Handlebars or Pebble remain available later through a `templateEngine` field if a logic-less format is wanted for a hosted use. Framework-owned features keep their precompiled GSPs regardless.

**Build-file contributions.** `build.gradle` is the file every pack wants to touch, and it is where the current design is weakest. Three mechanisms coexist today: the model (dependencies, plugins, repositories, buildscript, properties); one generic slot, `${gradleBuild.renderExtensions()}` after the dependencies block (`feature/build/gradle/templates/buildGradle.gsp:107`), fed by `GradlePlugin.extension(Writable)`; and eight branches hard-coded in the central template on feature names (`gradle-build-src`, `asciidoctor`, `jrebel`, `spock`, `geb-with-local-browsers`, `grails-compile-static`, `gsp`, `asset-pipeline-grails`, `buildGradle.gsp:40-122`) plus the BOM choice in `buildSrcBuildGradle.gsp:50-58`. The hard-coded branches are the "everyone edits the central file" problem in miniature, and a pack cannot use them at all. There is also a silent bug: `Dependency.Builder.extension(Writable)` is accepted and then dropped (`build/dependencies/Dependency.groovy:315,361-373`), so the `sourceSets` block `DatabaseMigrationPlugin` tries to add never reaches the build file (`feature/migration/DatabaseMigrationPlugin.groovy:72`).

The rule for packs: no pack ships a `build.gradle` template or a text patch. The build file stays generated from a model, and packs contribute through typed channels that Forge merges:

| Channel | Renders | Merge rule |
| --- | --- | --- |
| `dependencies`, `platforms` | lines in `dependencies {}` | same group, artifact and scope collapse (existing `Dependency` equality); the same coordinate with two explicit versions is an error naming both packs |
| `buildPlugins` (`id`, `version`, `apply`) | a `plugins {}` entry or an `apply plugin:` line | same id collapses; different versions error |
| `build.repositories`, `build.buildscript` | `repositories {}`, `buildscript {}` | by URL or coordinate |
| `build.properties` | `gradle.properties` | same key and value collapse; different values error |
| `build.imports` | import lines at the top of the file | set |
| `build.settings` | `settings.gradle` fragments, for example `pluginManagement` repositories | ordered |
| `build.extensions.<name>` | one `<name> { }` block per key with the bodies concatenated; `tasks.<task>` keys render `tasks.named('<task>') { }` | merged across packs and framework features, in feature order |
| `build.blocks` | a free-form fragment at an anchor: `afterPlugins`, `afterDependencies`, `end` | ordered, never merged; the escape hatch for `sourceSets {}` or a custom task |

Every fragment is a GSP with the pack's model, so it can branch on features. Ordering is deterministic: feature order, then feature name, then pack name. Today ties within a phase come from a `HashSet` walk (`FeatureContext.groovy:90`), so the same selection can render blocks in a different order between runs; the sort fixes that as a side effect.

The framework's own features move to the same channels in phase 1: the asset-pipeline feature contributes `assets {}`, compile-static contributes `grails {}`, Spock and Geb contribute `tasks.test`, asciidoctor and jrebel contribute their blocks, and `GrailsBase` contributes the BOM. The central template then owns structure only. That is the proof that the channels are sufficient, and it is what makes `feature add` tractable: the diff between the baseline build and the pack-applied build is a set of insertions inside existing blocks rather than a rewrite, which the three-way merge in section 2.6 applies cleanly as long as the user kept the block.

Limits: a pack cannot change the BOM selection, reorder the framework's plugins, change the Java toolchain, or remove another feature's lines; `conflicts` is the tool for incompatibility. The Gradle plugin's validation warns when a `build.blocks` fragment could have been a typed channel, so free-form blocks stay rare.

### 2.3 Plugin metadata: catalog plus pack

**Where a pack lives, and the Gradle plugin that publishes it.** Inside the plugin's main jar under `META-INF/grails-forge/`. Authoring and publishing are automated by the Grails Gradle plugins, the same way `grails-plugin-cli` automates `-cli` companions today: a plugin author keeps the pack in `src/main/forge/` (`feature.yml` plus `templates/`), and the `grails-plugin` Gradle plugin, when that directory exists, does the rest:

- validates `feature.yml` against the descriptor schema at build time (unknown keys, bad predicates, destination paths that escape the project, references to unknown feature names from the bundled index);
- compiles every `.gsp` in the pack with the same GSP compiler Forge uses, as a check task, so type errors in a `<%@ model %>` page fail the author's build instead of a user's `create-app`; the shipped artifact stays source;
- packages the directory into `META-INF/grails-forge/` of the runtime jar and stamps a `Grails-Forge-Pack: <name>` manifest attribute, which the indexer and the CLI can read without opening the whole jar;
- registers a `forgePackTest` task that generates a sample application for each supported application type with the pack applied, through forge-core in process, and runs `./gradlew classes` on it (opt-in, since it needs the local Maven publish chain the Forge tests already use);
- with `grails-publish` applied, needs nothing more: the pack travels with the plugin's existing publication.

A `grails create-feature-pack` command scaffolds `src/main/forge/` in an existing plugin. The alternative layout, a `forge` classifier artifact as Quarkus uses, downloads less but adds a publication to sign and release; the fetcher prefers a `-forge.jar` classifier when present and falls back to the main jar, so the Gradle plugin can switch to it later without changing the format.

**Catalog.** The catalog already exists. `apache/grails-plugins-metadata` is the data source of the plugin page: one YAML file per plugin at `grails-plugins/<group path>/<artifactId>.yml`, submitted by pull request and reviewed by the Grails team.

```
name: Cache
desc: Provides AST transformations for caching method calls
coords: org.apache.grails:grails-cache
owner: Apache Software Foundation
vcs: https://github.com/apache/grails-core
docs: https://grails.apache.org/docs/latest/guide/cache.html
maven-repo: https://repo1.maven.org/maven2
labels: [cache]
licenses: [Apache-2.0]
versions:                     # maintained by the indexer, newest first
  - version: 8.0.0
    date: 2026-10-04T18:46:18Z
    grailsVersion: 8.0.0-SNAPSHOT > *
```

Every eight hours `index-plugins.groovy` reads each plugin's `maven-metadata.xml`, downloads every new version's jar by its exact URL, opens `META-INF/grails-plugin.xml` inside it to read `grailsVersion`, rewrites the YAML, and regenerates `grails-plugins-index.json` (a JSON array of those maps) through a pull request. The website's `PluginsTask` fetches that file at build time. As of 2026-10-13 the index has 256 entries (157 under `org.grails.plugins`, 29 under `org.apache.grails`, 12 under `io.github.gpc`, 9 under `cloud.wondrify`), 40 marked `deprecated`, and 61 with at least one version whose `grailsVersion` starts with 7 or 8.

The plan extends this pipeline instead of adding a second catalog:

1. **The indexer detects packs.** While it has a version's jar open it also looks for `META-INF/grails-forge/feature.yml` and records `forge: { name, applicationTypes }` plus the jar's `sha256` on that version entry. Same download, no extra cost. A plugin opts in by shipping the file; the YAML submission process does not change.
2. **Forge reads the index.** forge-cli and forge-web consume `grails-plugins-index.json`: `labels` map to Forge categories through a small table, `desc` and `docs` fill description and documentation, `deprecated` hides an entry from `feature search` unless asked for, and `versions[].grailsVersion` (the `7.0.0 > *` range syntax from the plugin descriptor) filters candidates against the CLI's Grails version; the newest compatible non-snapshot version wins by default. The index for the matching release is bundled into `grails-forge-core` as the offline fallback and refreshed straight from the repository: `https://raw.githubusercontent.com/apache/grails-plugins-metadata/main/grails-plugins-index.json`. Whatever is merged to `main` is the current list; the indexer's pull requests land there every eight hours, so there is no separate publishing step and no mirror. The website already reads the same file, through the pre-ASF `grails/` organisation path that GitHub redirects; switching it to `apache/` is a one-line fix.
3. **Official plugins need no second source.** The 29 `org.apache.grails` entries are already listed; the BOM stays the version authority for official coordinates inside generated builds, so those packs omit versions.
4. **Explicit coordinates remain the escape hatch** for a plugin that is not in the index: `grails create-app shop --features com.acme:acme-audit-grails:2.3.0`. Users can add private indexes and repositories in `~/.grails/forge.yml`.

Weaknesses of the index that the plan has to live with or fix: `grailsVersion` is whatever the plugin descriptor says (grails-cache 8.0.0 reports `8.0.0-SNAPSHOT > *`), `docs` is missing on 134 entries, `labels` are free-form, there is no checksum today, and the older `sync-latest-version` action still refers to a `grails-plugins.json` file that no longer exists.

**Fetching without a build.** The indexer already shows the pattern: `createArtifactUrl` builds the exact jar URL from `maven-repo`, `coords` and the version, and one zip entry is read from the download. The pack fetcher does the same with plain HTTPS GETs, no dependency graph: try `<repo>/<group path>/<artifact>/<version>/<artifact>-<version>-forge.jar`, then the main jar; read only the `META-INF/grails-forge/**` entries from the stream; cache under `~/.grails/forge/cache/<group>/<artifact>/<version>/`. Repositories: the entry's `maven-repo` (honouring per-version `coords` and `maven-repo` overrides), then Maven Central, then `GRAILS_REPO_URL` (the existing semicolon list, already validated by `GrailsRepositoryOverrides`), then `~/.grails/forge.yml` entries with credentials. Snapshots read `maven-metadata.xml` for the timestamped file. Proxies come from the JVM's proxy properties and `~/.m2/settings.xml`; a small dependency-free reader of that file avoids pulling Maven Resolver back into the CLI. `--offline` and `--refresh` flags; the catalog cache has a 24-hour TTL.

**Security model.** External packs exist only in the CLI. HTTPS only; sha256 verification against the index when it carries one; destination paths must be relative and must not escape the project (`..`, absolute, symlinks); entry size and count caps on the zip; existing files are never overwritten at `create-app` time; and because a GSP pack executes the plugin author's code at generation time, the CLI prints the pack's coordinates and checksum before first use, and `--offline` serves only cached packs. The hosted Forge (start.grails.org) never loads a pack from outside its own build: it serves the framework's features, some of which may be implemented as bundled packs, and never fetches coordinates on request. This belongs in `THREAT_MODEL.md`.

### 2.4 Project state: `.grails/`

```
.grails/
  project.yml   committed; replaces grails-forge-cli.yml and grails.profile
  cache/        ignored; packs resolved for this project
  run/          ignored; run-app pid, port and stopping markers (build/run-app.pid today)
  history       ignored; console history (~/.grails_history is global today)
```

```
format: 1
grailsVersion: 8.1.0
applicationType: web
defaultPackage: com.acme.shop
options: { jdk: 21, servlet: tomcat, data: hibernate7, reloading: devtools }
features:
  - name: gsp
  - name: grails-spring-security
  - name: acme-audit
    coordinates: com.acme:acme-audit-grails
    version: 2.3.0
    applied: 2026-10-13
```

Why commit it: `feature add` must know the current feature set and options to compute a diff; artefact codegen is gated on it (today the gate is `grails-forge-cli.yml` in the current directory only, no parent walk, `ForgeCli.groovy:115-128`); IDEs can detect a Grails project and its type from it; CI can validate it. Precedents: Micronaut's `micronaut-cli.yml`, `.sdkmanrc`, `.mvn/`. Everything else under `.grails/` is per-machine and goes into the generated `.gitignore`.

Two keys stay in `application.yml` because runtime code reads them: `grails.codegen.defaultPackage` (`grails-testing-support-web/.../WebSetupSpecInterceptor.groovy:111`, `grails-console/.../GrailsApplicationScriptRunner.groovy:69`) and, through 8.x, `grails.profile`, because `GrailsApplicationPostProcessor` adds it as an active Spring profile with a `web` default; 9.0 removes that injection and Forge stops writing the key.

`grails project migrate` creates `.grails/project.yml` for an existing app from `grails-forge-cli.yml` or legacy `grails-cli.yml` (its `profile:` key maps to a type exactly as `CodeGenConfig` does today), or from `grails.profile` plus a scan of `build.gradle` for known coordinates, and removes the `profile "..."` line from the build.

### 2.5 The console after profiles

Command sources, in precedence order (first registration wins, as today):

1. **Built-in lifecycle commands**, moved from the base profile's Groovy scripts into the console as Picocli commands over the existing Tooling API plumbing (`grails-shell-cli/.../gradle/GradleUtil.groovy`, `GradleInvoker`, `GradleAsyncInvoker`): `run-app`/`stop-app` (keep `--https`, `--port`, `--debug-jvm`, pid handling via `RunningApplicationProcess`), `test-app`, `compile`, `clean`, `assemble`/`war`/`package`, `console`, `shell`, `dependency-report`, `run-command`, `run-script`, `stats`, `bug-report`.
2. **Gradle passthrough**: `grails gradle <args>` with completion from `ReadGradleTasks` (exists). Fix: arguments are passed as one string today (`GradleCommand.groovy:62-65`).
3. **Plugin `ApplicationCommand`s** from `-cli` companions through `ApplicationContextCommandFactory` and `GradleTaskCommandAdapter` (exists, profile-free apart from the `ProfileCommand` interface). Fix the two known gaps: legacy Grails 7 commands are listed but have no named Gradle task (`GrailsCliGradlePlugin.groovy:411-414`), so route them to `runCommand`; app-local `grails-app/commands` are invisible because only `grailsCliClasspath` jars are scanned (`:450-480`).
4. **Artefact codegen**, one implementation in Forge, called in-process: the five existing commands plus `create-unit-test`, `create-integration-test`, `create-functional-test`, `create-restful-controller`, `create-domain-resource`, `create-command`, `create-script`, and pack-provided artefacts. Template lookup order: `src/main/templates/<path>` in the app, then the pack, then Forge's built-in GSPs. Extend scaffolding's `install-templates` idea so users can copy Forge's artefact templates into `src/main/templates`.
5. **Script commands** (`src/main/scripts/*.groovy` in the app, `META-INF/commands/*` in plugin jars, the `GroovyScriptCommand` DSL): unchanged in 8.x; in 9.0 the profile lookups go (`methodMissing` chaining through `profile.hasCommand`, `GroovyScriptCommand.groovy:170-184`, and the profile branch of `TemplateRendererImpl.groovy:241-266`) and the scripts stay, deprecated, until the major after 9.0. Six in-repo commands exist only as scripts and need conversion to `ApplicationCommand`s or pack artefacts in 9.0: geb `create-functional-test`, quartz `create-job`, `s2-create-acl-domains`, `s2ui-create-challenge-questions`, `s2ui-override`, and dbmigration's `dbm-create-changelog` / `dbm-changelog-to-groovy`, whose `-cli` jar depends on grails-shell-cli for `org.grails.cli.profile.ExecutionContext` (`grails-data-hibernate7/dbmigration/build.gradle:87-92`).
6. **Catalog services**: help, completion, abbreviation expansion (`rA` for `run-app`), "did you mean", and the legacy-command hint move from `AbstractProfile.groovy:361-499` into a profile-free `CommandCatalog`.

Project detection walks up from the current directory to find `.grails/project.yml` or `grails-app` (today: current directory only, `GrailsCli.groovy:351-355`).

**Command execution model.** Commands already live outside profiles today, and three of the ways are code. Profiles are not what commands depend on; they are what the shell's dispatcher depends on.

| Where a command is defined today | Author | Runs where | Needs a profile? |
| --- | --- | --- | --- |
| Profile jar `commands/*.yml` or `.groovy` | profile author | shell JVM | yes, by definition |
| App `src/main/scripts/*.groovy` | app developer | shell JVM | only because the shell dispatches every in-project command through the profile object (`GrailsCli.groovy:385`) |
| Plugin jar `META-INF/commands/*` (from `src/main/scripts`) | plugin author | shell JVM, found on the app's test runtime classpath | same |
| `ApplicationCommand` in a plugin's `-cli` companion (`src/cli/groovy`) | plugin author | one Gradle `JavaExec` task per command, created by `GrailsCliGradlePlugin`; the shell wraps each one in `GradleTaskCommandAdapter` | no on the Gradle path; the shell listing still goes through the profile |
| `ApplicationCommand` in the app (`grails-app/commands`) | app developer | `./gradlew runCommand -Pargs=...` only; no task, invisible to the shell | no |
| Legacy `grails.dev.commands.ApplicationCommand` with `legacyCommandSupport` | Grails 7 plugin | `runCommand` only; the shell lists it but calls a task that does not exist | no |

The runtime half of this system is `grails-console`, which the Gradle plugin auto-provisions into `grailsCli` because every command task's main class comes from it. `GrailsApplicationContextCommandRunner` (per-command tasks and `runCommand`) boots the application through `DevelopmentGrailsApplication`, which swaps the context class to `DevelopmentWebApplicationContext`: a `GenericWebApplicationContext` over a `MockServletContext` pointed at `src/main/webapp`. The command gets the full context, plugins, GORM, data sources and `BootStrap`, unless it carries `skipBootstrap = true` (the `SkipBootstrap` trait, currently copied into scaffolding and spring-security). No Tomcat, no port. `GrailsApplicationScriptRunner` (`runScript`), `GrailsSwingConsole` (`console`) and `GrailsShell` (`shell`) boot the same way with console-flavoured contexts; the profile commands named `console` and `shell` only invoked those Gradle tasks.

No command in the repository needs a running server: a search of every `src/cli` source set finds nothing that starts one or makes HTTP calls. `generate-open-api` builds its model from beans (`GrailsOpenApiGenerator`, `GroupedOpenApiContributor`); `dbm-*`, `schema-export`, `url-mappings-report`, `config-report`, `generate-*` and `s2-*` all work on the context. The server-bound operations are lifecycle: `run-app` is `bootRun` under the Tooling API, asynchronous in interactive mode, with port polling, the pid file the application writes through `grails.cli.pid.file`, and `--https` keystore generation; `stop-app` reads that pid; `test-app` runs `integrationTest`, where the test JVM starts the server itself.

After profiles, every command maps to exactly one of five execution modes:

| Mode | Runs where | Examples |
| --- | --- | --- |
| Console-side, no application context | the `grails` JVM, with the console services; Forge in process for codegen | `create-*` artefacts, pack artefacts, `feature add`, `project migrate`, `sql-tail` |
| Gradle task passthrough | Gradle, Tooling API | `gradle <args>`, `compile`, `clean`, `assemble`, `war` |
| Context-bound command | Gradle `JavaExec` plus `GrailsApplicationContextCommandRunner`, full context, mock servlet, no server | `dbm-*`, `schema-export`, `url-mappings-report`, `generate-open-api` |
| Server-bound lifecycle | Gradle, Tooling API, asynchronous | `run-app`, `stop-app`, `test-app` |
| Interactive session | Gradle `JavaExec` into `grails-console` | `console`, `shell`, `run-script` |

What follows from the table:

1. *Commands that need the application context keep the default.* `dbm-gorm-diff` opens the GORM metamodel through `withGormDatabase(applicationContext, dataSource)` (`grails-data-hibernate7/dbmigration/src/cli/.../DbmGormDiffCommand.groovy:50`), `schema-export` needs the `SessionFactory`, `url-mappings-report` needs the mappings holder. All of them are `ApplicationCommand`s run by `GrailsApplicationContextCommandRunner` with the full context, and nothing in this plan changes that path; the `requiresApplicationContext` flag in the next item defaults to `true`.
2. *A home for fast, console-side plugin commands.* Profile and plugin scripts ran instantly in the shell JVM with the console's services; an `ApplicationCommand` always pays a Gradle invocation plus a full boot, even for pure file generation (`s2-quickstart`, `create-scaffold-*`, `install-templates`; `SkipBootstrap` only skips `BootStrap.groovy`). The answer is a `requiresApplicationContext = false` flag on the contract, and a **console services** API injected into such commands that carries what the script DSL offers today: project configuration (`navigateConfig`), `baseDir` and `buildDir`, console output with ANSI and terminal width, a cancellation listener, a `GradleRunner` (synchronous and asynchronous task invocation with cancellation, the existing `GradleInvoker` and `GradleAsyncInvoker`), `ServerInteraction` (wait for a port or a health endpoint) and `RunningApplication` (the pid and port files under `.grails/run/`, `stop()`). The console already builds the class loader and instantiates every command to list them (`ApplicationContextCommandRegistry`), so running one there costs nothing extra; Gradle runs the same command in a plain `JavaExec` without booting the application. App-local `grails-app/commands` become runnable from the console too. `sql-tail` is the reference case: on `feat/sql-tail-command` (PR #16631) it is a script in `grails-datamapping-core`'s `-cli` jar that reads `grails.gorm.sqlTail.file` from the project configuration, waits for the capture file the running application writes under `build/`, follows it until cancelled, and uses `flag`, `grailsConsole` and `addCancelledListener`. It converts one to one onto those services, and with `GradleRunner` it could start the application itself (`bootRun` with the capture property) instead of asking the user to.
3. *A console command that needs the context part-way delegates rather than half-booting.* It calls `GradleRunner.runCommand('dbm-gorm-diff', args)` (the existing `runCommand` task and runner) or is split into a console-side command and a context-bound one. There is no mode that boots the context inside the console JVM: the console runs against the project's classpath through a class loader, not a Spring context, and that stays so.
4. *Server-bound work goes through the services, not through a second context.* A command that needs the running application starts it the standard way (`GradleRunner.async('bootRun', ...)`, DevTools-aware, the same pid file as the Run button and `stop-app`), waits with `ServerInteraction`, works over HTTP, and stops it. Such a command declares `requiresConsole = true`: no per-command Gradle task is registered for it, because a Gradle task that launches a second build of the same project is not something to offer. A `requiresServer` mode that boots `GrailsApp` with an embedded container inside the runner stays off the table.
5. *Lifecycle built-ins need a plugin extension point.* `run-app --sql-tail` exists on that branch only because the base profile's `run-app.groovy` was edited to add the flag and pass `-Dgrails.grails.gorm.sqlTail.enabled=true` to `bootRun` (`grails-profiles/base/commands/run-app.groovy:9,36-37`; the doubled prefix is the Gradle plugin's forwarding rule for `grails.*` system properties). A plugin cannot do that. The console's built-in `run-app` therefore accepts declared options: a `-cli` command or a pack declares `runAppOptions` (name, description, the system or Gradle property to set), `run-app` registers them, and `help` and completion show them. Pass-through `-D` and `-P` arguments remain for the undeclared case.

Housekeeping the inventory found: move `SkipBootstrap` into `grails-core-cli`; route legacy commands to `runCommand`; list app-local commands; keep the Tooling API connection open across console commands instead of reconnecting per call (`GradleUtil.groovy:54-76`).

**IDE integration.** The IntelliJ plugin is now `apache/grails-intellij-plugin` (donated by JetBrains, imported from `intellij-obsolete-plugins`), so its CLI coupling is work this plan schedules rather than a risk to confirm. What it does today, from the source:

- Commands run in a JVM the IDE starts itself, main class `org.grails.cli.GrailsCli`, classpath = the `org.apache.grails:grails-shell-cli:<grailsVersion>` artifact resolved through a detached Gradle configuration by the plugin's own tooling model builder (`libs/gradle-tooling/.../GrailsModuleModelBuilderImpl.java:54-78`, `plugin/.../runner/impl/Grails3GradleCommandExecutor.java:54`), with `--plain-output` appended. The `grails` launcher and `grails-forge-cli` are not involved.
- The command list is hard-coded: 34 names from the profile era (`bug-report`, `clean`, `compile`, `console`, `create-*`, `dependency-report`, `generate-*`, `gradle`, `install`, `install-templates`, `list-plugins`, `plugin-info`, `run-app`, `schema-export`, `shell`, `stats`, `stop-app`, `test-app`, `url-mappings-report`, `war`) in `plugin/.../commands/Grails3CommandProvider.java:31-70`.
- The Grails run configuration is `run-app` through the same executor (`runner/GrailsRunConfiguration.java:70`), so the IDE's Run button depends on the base profile's `run-app` script and its pid file.
- Project detection is the `grails-app` directory (`structure/GrailsApplicationManager.java:167`).
- The new-project wizard calls the hosted Forge API (`https://latest.grails.org`: `/select-options`, `/versions`, `/application-types/{type}/features`, `/create`) in `feature/forge/GrailsForgeModuleBuilder.kt:67-221`.
- The plugin's own `IMPROVEMENT-PLAN.md` section 2.6 already intends to move the run configuration to Gradle `bootRun`, rebuild the command layer from profile YAML plus forge-cli commands, and keep the wizard on the hosted API with an offline fallback via `grails-forge-cli`. The profile half of that plan is superseded by this one.

What changes on the Grails side so the plugin can follow:

1. *A stable programmatic entry point.* The unified CLI keeps one main class and one artifact the IDE can resolve by coordinates for the project's Grails version, with `--plain-output` and `--non-interactive` honoured (the shell declares `--non-interactive` today and ignores it). Through 8.x the shell, its artifact and `org.grails.cli.GrailsCli` stay untouched, so the current plugin keeps working; 9.0 removes them, and the plugin release that supports the new entry point ships with 9.0.0. The plugin resolves the CLI artifact per project Grails version, so 8.x projects are unaffected by that release.
2. *A machine-readable command catalog.* `grails help --json` (name, description, synonyms, arguments, flags, completer kind, execution mode) replaces the hard-coded list, so plugin `-cli` commands, pack artefacts and app-local commands appear in the IDE without a plugin release. The same catalog feeds shell completion, so it is not IDE-only work.
3. *Project detection and type.* The plugin reads `.grails/project.yml` first (application type, default package, features), falling back to `grails-app`.
4. *Run configuration.* `bootRun` directly, as the plugin's own plan says, or the CLI's `run-app` in non-interactive mode; either way the pid and port files move to `.grails/run/`.
5. *Wizard.* Unchanged for official features, since the hosted API keeps serving them. External packs and `feature add` are CLI operations the IDE can surface by invoking the same entry point, which is why they need no web API.

Sequencing: the plugin release is lock-step with 9.0.0, which is why step 5 lists it.

### 2.6 `feature add` on an existing app

1. Read `.grails/project.yml`.
2. Generate two projects in memory with the same type, options and Grails version: A with the recorded features, B with the recorded features plus the requested ones. This is what the web `DiffController` does through `FeatureDiffer`.
3. Turn the difference into a unified diff. New files are written directly. Hunks against existing files (`build.gradle`, `application.yml`, `settings.gradle`, `README.md`) are applied as a three-way merge with context (java-diff-utils or JGit's apply); a hunk that does not apply is reported with the rejected text, and `--dry-run` / `--diff` print the patch instead of applying it.
4. Record the feature in `project.yml`.

This is deliberately additive. Removing a feature prints the inverse diff only. The approach is simpler than parsing Gradle scripts and more useful than Micronaut's `feature-diff`, which only prints the diff. `feature diff` also becomes a CLI command; today the diff exists only as a web endpoint.

### 2.7 Hosted Forge and the UI

- The hosted service hosts the framework's own features only. External packs, the plugin index and `feature add` are CLI capabilities; the web API and `apache/grails-forge-ui` gain no plugin search and load nothing from outside their build.
- Official features that become bundled packs render exactly as compiled features do (precompiled GSP), so the API, the preview and diff endpoints and the version slots need no change. `FeatureDTO` may expose `documentation`, which today is only written into the README.

## 3. What profiles offered and what replaces it

| Capability | Today | After | Status |
| --- | --- | --- | --- |
| `create-app` with profiles | shell plus Aether, default path | Forge | replaced |
| Company or third-party skeletons (custom profile jars, `--profile g:a:v`) | profile jar | **Preset packs**: a pack with `kind: preset` naming a type, option defaults, a feature list and extra files; `grails create-app --preset com.acme:acme-starter:1.0 shop` | replaced, new work |
| `create-profile` and the seven profile-authoring commands | profile profile | Not in v1; `grails create-feature-pack <name>` scaffolds a pack skeleton later | dropped, small gap |
| `list-profiles`, `profile-info`, `list-plugins`, `plugin-info` | shell; the last two scrape the legacy repository | `feature list`, `feature info`, `plugin search`, `plugin info`, backed by `grails-plugins-index.json` | replaced |
| `--features` replaces defaults; unknown names warn | profiles | Forge semantics: additive, unknown names fail. Add `--exclude-features` so a default such as a Geb variant can be dropped | gap, new flag |
| `rest-api-plugin` application type | profile only | Add to Forge (#14932) | planned gap |
| Profile features with no Forge equivalent | `less-asset-pipeline`, `rx-mongodb`, rest-api `security` (`grails-spring-security-rest:7.0.0-SNAPSHOT`), `maven-publish` | less-asset-pipeline becomes a pack owned by the asset-pipeline plugin; rx-mongodb is dropped (legacy coordinates, RxJava 1/2 deprecated on 9.0.x); spring-security-rest ships a pack once it is on 8.x; publishing becomes the plugin type's default via grails-publish | mixed |
| Lifecycle commands (run-app, test-app, war, ...) | base profile scripts | Console built-ins | replaced |
| Artefact `create-*` with web and rest-api variants, test variants | profile YAML plus templates | Forge codegen; new templates for the eight missing commands | replaced, work |
| rest-api `generate-all` / `generate-views` producing **gson** views | rest-api Groovy scripts | Scaffolding's `-cli` `generate-*` produce GSP only. Add gson scaffolding templates to grails-views-gson (`src/main/templates/scaffolding/*.gson`) and a `--views gson` option or a views-gson `-cli` command | real gap |
| `src/main/templates` override of artefact templates | shell lookup | Forge codegen honours it first | replaced |
| App scripts and plugin script commands | shell | Unchanged in 8.x; profile-free and deprecated in 9.0; `ApplicationCommand` is the path | deprecated |
| `~/.grails/settings.groovy` profile repositories and defaults | shell | `~/.grails/forge.yml` repositories and catalogs; proxies from JVM properties and `settings.xml` | replaced |
| Post-create `instructions` | `profile.yml` (unused in-repo) | Pack `instructions` | replaced |
| YAML `execute` / `gradle` command steps | profile YAML | Not needed: Gradle tasks and `ApplicationCommand`s | dropped |
| Welcome page shows `grails.profile` | `index.gsp:141-144` | Show `info.app.*`; the type lives in `project.yml`, which the runtime does not read | adjust |
| `grails.profile` as an active Spring profile | runtime, default `web` | Unchanged in 8.x, removed in 9.0 with an upgrade note | 9.0 removal |
| IntelliJ Grails plugin (`apache/grails-intellij-plugin`) | launches `org.grails.cli.GrailsCli` from a resolved `grails-shell-cli` jar; hard-coded list of 34 shell commands; `run-app` run configuration through the same path; new-project wizard on the hosted Forge API | In-scope work, section 2.5 "IDE integration": stable CLI entry point and artifact, machine-readable command catalog, `.grails/project.yml` detection, `bootRun` run configuration, wizard unchanged for official features | replaced, coordinated release |
| `profile` Gradle configuration in existing builds | default dependency, resolved by the tooling model | Unchanged in 8.x; removed in 9.0, where `project migrate` deletes the line and a declared `profile` dependency fails configuration with a pointer to the upgrade guide | 9.0 removal |
| Hibernate 7 BOM switch, servlet, JDK, exclusive groups, preview, diff, website | Forge only | Kept | gain |

## 4. Removal inventory

Grouped by area; each item is a known dependency on profiles or the dispatcher.

**Forge** (`grails-forge/`): `feature/grailsProfiles/GrailsProfiles.groovy` (replace with a `ProjectManifest` feature that writes `.grails/project.yml` and `grails.codegen.defaultPackage`); `Scope.PROFILE`, `Phase.PROFILE`, `GradleConfiguration.PROFILE`; `CodeGenConfig` legacy `profile:` handling (moves to `project migrate`); the drift spec `GrailsGspSpec.groovy:326-350`, which asserts byte identity with `grails-profiles/web` skeleton files; `GrailsProfilesSpec`; `grails-forge-web/grails-app/conf/application.yml:18`; stale comments in `grails-cli/build.gradle:70` and `grails-cli-shadow/build.gradle:50`; the `.gitignore` DirectoryScanner workaround in `grails-forge/settings.gradle:88-93`.

**Shell** (`grails-shell-cli/`): everything under `profile/repository/`, `Profile`, `AbstractProfile`, `ResourceProfile`, `FileSystemProfile`, `Feature`, `DefaultFeature`, `ProfileRepository*`, `ProfileRepoConfig`, `ProfileUtil`, the `Create*Command`, `ListProfilesCommand`, `ProfileInfoCommand`, `ArgumentCompletingCommand`, `io/GradleDependency`, their service-file entries, `GrailsCli` members `:87-89,129-133,195-201,260-271,307-311,324-332,386-394,518-534`; the Spring Boot CLI port and the maven-resolver dependencies (`grails-shell-cli/build.gradle:86-100`). Re-home `CompilerAutoConfiguration`, which the hibernate boot-plugins compile against (`grails-data-hibernate5/boot-plugin/build.gradle:50`). Rewrite rather than delete: `CommandRegistry.findCommands`, the four `CommandFactory` implementations, `GradleTaskCommandAdapter`, `HelpCommand`, `GroovyScriptCommand`, `TemplateRendererImpl`, `RenderStep`, `DefaultStepFactory`'s `ProfileCommand` gate (`:44-47`), `populateContextLoader` (keep the classpath part, drop `:571,582-584`).

**Gradle plugins** (`grails-gradle/`): `GrailsGradlePlugin.groovy:166,461-490,782-784`; `GrailsPluginGradlePlugin.groovy:138-140`; `GrailsClasspathToolingModelBuilder.groovy:35,51-56` and `GrailsClasspath.getProfileDependencies`; the `grails-profile` and `grails-publish-profile` plugins (`plugins/build.gradle:143-160`, `profiles/*`, `ProfileCompilerTask`; `GroovyScriptCommandTransform` stays while scripts are supported); `ProfileCompilerTaskSpec`; `BomPlatformFunctionalSpec.groovy:76-98` and its fixture `test-projects/bom-platform-default-dependencies/build.gradle:98`.

**Root build**: the seven `grails-profiles/*` modules (`settings.gradle:465-485`), `grails-spring-security/rest/testapp-profile` (convert its features to packs or test fixtures), `gradle/publish-root-config.gradle:172-191`, `build.gradle:76,163-165` (`profileProjects`, unused), `PublishPlugin.groovy:58,146-151,191-197`, `gradle/rat-root-config.gradle:73-76,161-166`, `settings.gradle:661-666` and `end-to-end/settings.gradle:132-137` (DirectoryScanner workaround), `grails-bom/base/build.gradle:78-94` and the `forcedGroupPrefixes` entries in all five BOMs, `etc/bin/verify-jar-artifacts.sh:208-236`, `etc/bin/reset-verify.sh:47`, `.github/dependency-graph/external-references.yml:42-48,197-203` (the purls are wrong today anyway), `RENAME.md:164-182`, `etc/bin/rename_gradle_artifacts.sh:305-347`, `INSTALL:98`.

**In-repo consumers**: `profile "..."` in seven `grails-test-examples/*/build.gradle`; `grails.profile` in 109 `application.yml` files (harmless at runtime, remove for hygiene); `def profiles = ['web']` in plugin descriptors (dead: `DefaultGrailsPlugin.profiles` is never assigned, `DefaultGrailsPlugin.java:117,139-147`) and in Forge's `plugin.gsp:44-47`.

**Docs** (`grails-doc/src/en/guide/`): the whole `profiles` chapter (`toc.yml:91-106`, twelve pages) is replaced by a "Feature packs and presets" chapter; `commandLine.adoc:20-71,112-122`, `interactiveMode.adoc:19-41`, `creatingCustomScripts.adoc`, `reusingGrailsScripts.adoc` and `gradleBuild.adoc` are deleted or rewritten; `creatingProject.adoc:19-37`, `creatingCustomCommands.adoc:21-69,219-272` (the `executionContext.environment` example at `:248` is wrong today), `gradleDependencies.adoc:27,61`, `gradleTasks.adoc`, `gradlePlugins.adoc:63-64`, `downloadingAndInstalling.adoc:24-93`, `creatingAnApplication.adoc`, `aHelloWorldExample.adoc`, `usingInteractiveMode.adoc`, `creatingArtefacts.adoc`, `generatingAnApplication.adoc`, `REST/restProfile.adoc`, `plugins/creatingAndInstallingPlugins.adoc:390-466`, `plugins/repositories.adoc:21-40`, `plugins/providingBasicArtefacts.adoc:21-68,127-141`; the 47 command reference pages (five are profile-only); `index.adoc:736` includes a path that does not exist. `GRAILS_PREFERRED_SHELL` and the wrapper environment variables are undocumented today and need a home in the new chapter.

## 5. Delivery

Project policy: nothing is removed in a minor release. Every removal in this plan (the profile modules and Gradle plugins, the shell's `create-*`, `list-profiles` and `profile-info`, the dispatcher with `-t` and `GRAILS_PREFERRED_SHELL`, the Spring Boot CLI port, the `profile` Gradle configuration, the `grails.profile` active-profile injection, the alias start scripts, `org.grails.cli.GrailsCli`) therefore lands in `9.0.x`, together. What `8.1.x` can carry is additions, and replacements of an implementation that functions the same: the declarative engine, packs, the index reader, `.grails/`, `feature add`, the unified `grails` command tree added alongside the profile shell, and existing commands re-implemented on Forge or on the console where the result is equivalent for the user. Whether `8.1.x` carries that work or everything waits for `9.0.x` and ships at once is decision 6; the steps are the same either way, only the line moves.

0

#### Prerequisites

8.1.x

Merge PR #16551 after resolving the conflict with the current `8.1.x` tip; merge `8.1.x` up to `9.0.x`.

**Exit:** Forge builds and tests green inside the composite build on both lines.

1

#### Declarative engine

8.1.x, additive, Forge only

`DeclarativeFeature`, descriptor schema and validator, `ForgePages` development-mode compilation for pack directories with a compiled-class cache, `Dependency.excludes`, dynamic codegen command registration, `--feature-pack-dir`. The keyed build-file contribution API (`extensions`, `blocks`, `imports`, `settings`) with deterministic ordering; the eight feature-name branches in `buildGradle.gsp` and the BOM choice in `buildSrcBuildGradle.gsp` move into the owning features; the dropped `Dependency.extension` bug fixed. Convert three to five simple compiled features (quartz, cache-ehcache, asciidoctor, web-console) to bundled packs as proof, with golden-output specs proving identical generation.

**Exit:** packs render byte-identical output to the classes they replace; `buildGradle.gsp` contains no `features.contains` branch; hosted Forge unchanged.

2

#### Catalog, fetch and authoring

8.1.x, additive, plus grails-gradle and grails-plugins-metadata

Extend `index-plugins.groovy` to detect `META-INF/grails-forge/feature.yml` (or the `Grails-Forge-Pack` manifest attribute) and record `forge` and `sha256` per version; an index reader in forge-core with the label-to-category table and `grailsVersion` compatibility filter; `feature list/search/info` and `plugin search/info`; the pack fetcher with cache, repositories, proxies, checksums, path guards and the first-use notice; `--features g:a:v`; the `src/main/forge/` support in the `grails-plugin` Gradle plugin (descriptor validation, GSP check compilation, packaging, manifest attribute, `forgePackTest`); a fixture plugin in `grails-gradle` `test-projects/` that uses it and is published to `build/local-maven` and consumed by test-core through a local index file.

**Exit:** `grails create-app x --features <gav>` works offline from the cache and online from a local Maven repository; `plugin search cache` lists the real index entry; the fixture plugin's pack is built and validated by the Gradle plugin alone.

3

#### `.grails/` and `feature add`

8.1.x, additive

`project.yml` writer and reader, `.gitignore` entries, `project migrate`, `feature diff` CLI, `feature add` with dry run and three-way apply; codegen gating accepts `project.yml` and `grails-forge-cli.yml`; Forge keeps writing `grails-forge-cli.yml` on 8.x so the existing codegen commands are unchanged.

**Exit:** round trip in test-core: create, `feature add`, build, run.

4

#### Unified command tree and equivalent replacements

8.1.x, additions and same-behaviour replacements

The `grails` launcher gains the Picocli tree (`feature`, `plugin`, `project`, `help --json`, the Forge create and codegen commands) alongside the profile shell; the dispatcher, `-t` and `GRAILS_PREFERRED_SHELL` keep working. Replacements allowed because they function the same: `create-app`, `create-plugin` and `create-web-plugin` implemented by Forge once the dependency drift between the two generators is closed (#14944, #14933), with `-t shell` still reaching the profile implementation; the shell's artefact `create-*` delegated to Forge codegen where the files produced are the same (the web `create-controller` also creates the views directory); lifecycle commands re-implemented as console built-ins behind the same names and flags while the profile scripts remain loadable. Profiles, `grails.profile` and the `profile` dependency are still emitted and resolved.

**Exit:** parity specs: for every replaced command, the same invocation produces the same files and effects on an 8.1 application before and after; the new commands work alongside.

5

#### Console rework and removals

9.0.x

Profile-free `CommandCatalog`; built-in lifecycle commands; codegen delegated to Forge; script commands kept with the profile lookups removed and deprecated; dispatcher, `-t`, `GRAILS_PREFERRED_SHELL`, alias start scripts, the Spring Boot CLI port and `org.grails.cli.GrailsCli` removed; `grails-profiles/*`, the profile Gradle plugins, shell profile code, BOM constraints and release-script checks deleted; the `profile` Gradle configuration deleted (a declared `profile` dependency fails configuration with a pointer to the upgrade guide and `project migrate`); the `grails.profile` active-profile injection removed; Forge stops emitting `grails.profile`, the `profile` dependency and `grails-forge-cli.yml`; `rest-api-plugin` type, the eight missing artefact commands, gson scaffolding templates, the six script-only commands converted; a `grails-intellij-plugin` release supporting the 9.0 entry point ships with 9.0.0; startup measured.

**Exit:** no `org.apache.grails.profiles` coordinate anywhere in the build; `grails` inside and outside a project needs no profile and no Aether; all shell specs rewritten against the catalog; a migrated 8.x application builds and runs; an unmigrated one fails with the pointer.

6

#### Docs, website, UI

8.1.x for additions, 9.0.x for removals

A "Feature packs and presets" chapter and `upgrading81x` notes with the additions; the "Command line" chapter rewrite, the profiles chapter removal, the command reference pages and `upgrading90x` notes with the removals; the website's index URL switched to the `apache/` organisation path; SDKMAN and distribution scripts; a `THREAT_MODEL.md` section; "ship a feature pack" in the `grails-plugins-metadata` README and the plugin authoring guide.

**Exit:** the 8.1 guide documents packs next to profiles; the 9.0 guide builds without the profiles chapter.

Deferred to the major after 9.0: removal of script commands (`src/main/scripts`, `META-INF/commands`, `GroovyScriptCommand`), which 9.0 keeps profile-free and deprecated.

Rough sizing: steps 1 to 3 are each a few weeks of Forge-only work; step 5 is the largest single item because it rewrites the shell's dispatch and tests and carries every removal; step 6 is sustained documentation work that can run alongside the others.

## 6. Open decisions

| # | Decision | Recommendation |
| --- | --- | --- |
| 1 | Pack template language | GSP source, compiled by the CLI at generation time; bundled official packs precompiled. One language for Forge and for packs, and existing templates convert by moving. Mustache stays the fallback if a logic-less format is ever needed for a hosted use. |
| 2 | Pack location and authoring | Main jar, `META-INF/grails-forge/`, produced by the `grails-plugin` Gradle plugin from `src/main/forge/` with validation, GSP check compilation and `forgePackTest`. Add a `forge` classifier later only if download size becomes a problem. |
| 3 | Catalog source of truth | `apache/grails-plugins-metadata` as it is, with its indexer extended to flag feature packs and record checksums; Forge reads `grails-plugins-index.json` from the repository's `main` branch, with the release's copy bundled as the offline fallback. No second catalog, no mirror. |
| 4 | Is `.grails/project.yml` committed? | Yes. Ignore `cache/`, `run/`, `history`. |
| 5 | Script commands (`src/main/scripts`, `META-INF/commands`) | Unchanged in 8.x; profile-free and deprecated in 9.0 once the six script-only commands are converted; removed in the major after 9.0. |
| 6 | Release lines | Settled by policy: every removal lands in 9.0.x together; nothing is removed in 8.1.x. Open: whether 8.1.x ships the additions and same-behaviour replacements of steps 1 to 4, or everything waits for 9.0.x. |
| 7 | Third-party packs on start.grails.org | Settled 2026-10-13: none. The hosted service hosts the framework's own features; external packs are CLI-only. |
| 8 | Fetcher implementation | Plain HTTPS plus a small `settings.xml` reader; do not reintroduce Maven Resolver. |
| 9 | `grails.profile` as a Spring active profile | Unchanged in 8.x; removed in 9.0 with an upgrade note. |
| 10 | Naming | "feature pack" for the per-plugin descriptor, "preset" for a type-level bundle, "catalog" for the index. |
| 11 | Programmatic entry point for the IDE | One main class and one artifact resolvable by coordinates per Grails version, `--plain-output` and `--non-interactive` honoured, `grails help --json` as the command catalog; `org.grails.cli.GrailsCli` and the `grails-shell-cli` artifact stay untouched through 8.x and go in 9.0, with a plugin release that supports the new entry point shipping with 9.0.0 (the plugin resolves the CLI per project version, so 8.x projects are unaffected). |
| 12 | Free-form build-file fragments | Allowed at three anchors (`afterPlugins`, `afterDependencies`, `end`) as an escape hatch; typed channels preferred, and the Gradle plugin warns when a fragment could have been one. No pack ships a `build.gradle` template or a text patch. |

## 7. Testing and documentation

- `grails-forge-core`: specs for descriptor parsing and validation, dependency and platform mapping, environment configuration merging, GSP source compilation from a pack directory with the class cache, verbatim copies, path-escape rejection, `requires`/`conflicts`/`oneOf` behaviour through `ContextFactory`, and golden-output comparisons for every converted feature using `CommandOutputFixture.generate`.
- `grails-gradle`: functional tests for the `src/main/forge/` support (descriptor validation failures, GSP check compilation failures, packaging and manifest attribute, `forgePackTest`), on the fixture plugin.
- `test-core`: TestKit builds of generated apps that include bundled packs; a fixture plugin in `test-projects/forge-feature-pack-plugin` published to `build/local-maven` and consumed both by coordinates and through a catalog file; `feature add` round trips.
- Console: `CommandCatalog` precedence, synonyms, abbreviations and hints; interactive dispatch with the existing `TestTerminal`; the legacy-command and app-local-command fixes in `grails-gradle` functional tests.
- End-to-end: generate, `feature add`, build, run, and `project migrate` on an 8.0-generated app.
- Docs: a "Command line" chapter (install, create, options, console, Gradle tasks, custom commands via `-cli` companions) and a "Feature packs and presets" chapter (descriptor reference, catalog, authoring and testing a pack, publishing, security notes), plus `upgrading81x` entries for the additions and same-behaviour replacements and `upgrading90x` entries for every removed surface.

## Appendix A: command tree

```
grails
  create-app | create-restapi | create-plugin | create-web-plugin | create-rest-api-plugin
      [--features a,b,g:a:v] [--exclude-features x] [--preset g:a:v]
      [--data|--servlet|--jdk|--reloading] [--inplace] [--list-features]
  feature   list | search <text> | info <name> | diff <name> | add <name|gav> [--dry-run|--diff]
  plugin    search <text> | info <name>                      (catalog aliases)
  project   init | migrate
  create-controller | create-service | create-domain-class | create-taglib | create-interceptor
  create-unit-test | create-integration-test | create-functional-test | create-restful-controller
  create-domain-resource | create-command | create-script | add-property | <pack artefacts>
  run-app | stop-app | test-app | compile | clean | assemble | war | package | console | shell
  dependency-report | run-command | run-script | stats | bug-report | install-templates
  gradle <args> | open <path> | help [command]
  <every ApplicationCommand from -cli companions: dbm-*, schema-export, generate-*, s2-*, ...>
```

## Appendix B: facts the plan relies on

- Forge has no declarative mechanism; features are `@Component`s found by scanning `org.grails.forge` (`ForgeCoreConfiguration.groovy:35-50`), registered by `FeatureRegistry` (`feature/FeatureRegistry.groovy:43-64`).
- The build file snapshots dependencies at `FeaturePhase.BUILD` (`GradleBuildCreator.groovy:34-74`); later additions are lost.
- `Dependency` has no exclusions; versions come from the BOM, a literal, or a `pom.xml` lookup (`PomDependencyVersionResolver.groovy:103-119`).
- Codegen commands are a static list (`ForgeCli.groovy:45-57`) gated only by a marker file in the current directory (`:115-128`).
- `FeatureDiffer` exists in core but is exposed only through the web `DiffController`.
- Inside a project the shell dispatches everything through the profile (`GrailsCli.groovy:385`); the profile jars come from the Gradle `profile` configuration via the `GrailsClasspath` tooling model (`GrailsClasspathToolingModelBuilder.groovy:43-60`); Aether is used only outside a project.
- The shell writes nothing into a project except under `build/`; no `.grails/` project directory exists anywhere in the repository.
- `grails.codegen.defaultPackage` has runtime readers; `grails.profile` becomes an active Spring profile with a `web` default.
- `-cli` companions are discovered from the `Grails-Cli-Artifact` manifest attribute and `META-INF/grails-cli.factories` without loading classes (`GrailsCliGradlePlugin.groovy:203-307,451-476`); that is the model the pack fetcher follows.
- The IntelliJ plugin (`apache/grails-intellij-plugin`, `main`, 2026-10-11) starts its own JVM with main class `org.grails.cli.GrailsCli` on the resolved `grails-shell-cli` artifact (`Grails3GradleCommandExecutor.java:54`, `GrailsModuleModelBuilderImpl.java:54-78`), hard-codes 34 shell command names (`Grails3CommandProvider.java:31-70`), runs `run-app` the same way (`GrailsRunConfiguration.java:70`), detects projects by `grails-app` (`GrailsApplicationManager.java:167`), and creates projects through the hosted Forge API (`GrailsForgeModuleBuilder.kt:67-221`).
- `sql-tail` (branch `feat/sql-tail-command`, PR #16631 on 9.0.x) is a `GroovyScriptCommand` script in `grails-datamapping-core/src/cli/resources/META-INF/commands/sql-tail.groovy` that follows a capture file written by the running application and never boots it; `run-app --sql-tail` is a flag added to `grails-profiles/base/commands/run-app.groovy`. `DbmGormDiffCommand` is an `ApplicationCommand` that needs the booted context (`withGormDatabase(applicationContext, dataSource)`).
- The plugin portal's data source is `apache/grails-plugins-metadata`: one YAML per plugin under `grails-plugins/<group path>/<artifactId>.yml` (`name`, `desc`, `coords`, `owner`, `vcs`, `docs`, `maven-repo`, `labels`, `licenses`, `versions`, optional `deprecated`), `index-plugins.groovy` run every eight hours by `.github/workflows/updateIndex.yml`, which downloads each new version's jar by exact URL and reads `META-INF/grails-plugin.xml`, and `grails-plugins-index.json` consumed by `apache/grails-static-website` (`buildSrc/.../PluginsTask.groovy:67`). 256 entries on 2026-10-13.

## Change log

| Revision | Date | Change |
| --- | --- | --- |
| 10 | 2026-10-13 | Command execution model in 2.5 reworked around two real examples: the `dbm-*` commands keep the context-bound default (`DbmGormDiffCommand`), and `sql-tail` defines the console-side mode, which gets a console services API (configuration, build directory, terminal, cancellation, `GradleRunner`, `ServerInteraction`, `RunningApplication`). Server-bound work goes through those services with `requiresConsole`; part-way context needs delegate to `runCommand`; the built-in `run-app` accepts declared options so a plugin can add a flag such as `--sql-tail`. Appendix B updated. |
| 9 | 2026-10-13 | Release policy applied: nothing is removed in a minor. Section 5 rewritten as "Delivery": steps 1 to 4 are additions and same-behaviour replacements that may ship in 8.1.x (open, decision 6); every removal, including the `profile` configuration, `grails.profile` injection, dispatcher, Spring Boot CLI port and `org.grails.cli.GrailsCli`, lands together in 9.0.x; the no-op configuration shim and the `GrailsCli` façade are gone. Section 1, 2.1, 2.4, 2.5, section 3 rows, decisions 5, 6, 9 and 11, and testing updated. |
| 8 | 2026-10-13 | "Build-file contributions" in 2.2: typed, merged channels (`build.properties`, `extensions`, `blocks`, `imports`, `settings`) with merge rules and deterministic ordering; the central template's feature-name branches and the dropped `Dependency.extension` bug move into phase 1; descriptor example, mapping table and decision 12 updated. |
| 7 | 2026-10-13 | IntelliJ plugin is `apache/grails-intellij-plugin`: new "IDE integration" under 2.5 with how it couples to the shell today and what the unified CLI must provide (stable entry point and artifact, `help --json` catalog, `.grails/project.yml` detection, `bootRun`). Section 3 row, phase 4, decision 11 and appendix B updated. |
| 6 | 2026-10-13 | Hosted Forge is official-only; external packs are CLI-only. Pack template language switched from Mustache to GSP source compiled by the CLI, with the decision history kept in 2.2. The `grails-plugin` Gradle plugin automates pack validation, GSP check compilation, packaging and `forgePackTest` from `src/main/forge/`. New "Command execution model" under 2.5. Sections 2.3, 2.7, phases 1, 2 and 6, decisions 1, 2 and 7, and testing updated. |
| 5 | 2026-10-13 | Revision stamp in the header and this change log. |
| 4 | 2026-10-13 | Section 2.2: "Why not GSP or another dynamic engine", with the two ways GSP could be opened to packs, the reasons against, and the middle options. |
| 3 | 2026-10-13 | Section 2.2: "Conditional logic in packs" (`requires`, `when`, `flags`, Mustache sections). Section 2.3: Forge reads `grails-plugins-index.json` straight from the repository's `main` branch; the mirror step was dropped from phase 6 and decision 3. |
| 2 | 2026-10-13 | Section 2.3 rebuilt on `apache/grails-plugins-metadata` as the catalog: YAML schema, indexer behaviour, index statistics, pack detection in the indexer, no second catalog. Section 1, section 3, decision 3, phases 2 and 6, appendix B updated to match. |
| 1 | 2026-10-13 | First draft: verdict, architecture, gap analysis, removal inventory, phases, decisions, testing, appendices. |

Plan for apache/grails-core issue #16348, drafted against PR #16551. Related: #15301, #14081, #14944, #14932.
