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
> The verbatim import is preserved in the first commit of this branch ("Document the Forge and CLI unification proposal"); this is revision 12.
> Implementation target: **9.0.x**. The Grails-application conversion landed in 8.1.x;
> profiles and the existing command behavior remain on that line.
> Reviewed code baseline: `8.1.x` at `abd7ea720d0e3db96a48a89ee82ce7e79e069524`,
> compared with `9.0.x` at `95495799582ccbfbf7246fb75aec62239a5676f9` for the relevant Forge/CLI sources.
> Older inventory pointers and external-repository observations retain their October 13 provenance;
> verify those locations against the implementation branch rather than treating line numbers as stable.

# One generator: Forge, feature packs, and a profile-free console

**Issue:** apache/grails-core #16348 · **Target:** 9.0.x · **Revision:** 12, 2026-11-19

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
6. [6. Decisions and implementation gates](#decisions)
7. [7. Testing and docs](#testing)
8. [A. Command tree](#appendix-a)
9. [B. Facts relied on](#appendix-b)
10. [Change log](#changelog)

This plan makes Forge the sole application-generation engine, lets plugins contribute descriptors and templates without compiling code into Forge or invoking Gradle during generation, and moves interactive and lifecycle commands into a profile-free command layer. The existing shell's useful services survive; its profile dispatcher, published shell entry point and artifact do not define the new architecture.

**Release boundary, decided 2026-10-26:** PR #16551, merged on 2026-10-14, converted Forge to a Grails plugin hosted by Grails applications and belongs to 8.1.x. Every new capability and removal described here belongs to 9.0.x: packs, presets, the catalog, project state, `feature add`, migration, the unified CLI and profile removal. There is no 8.1 implementation-replacement phase, dual-written manifest, or new command tree alongside the old dispatcher. The 8.1 guide continues to describe profiles and both existing CLI paths.

<a id="verdict"></a>
## 1. Verdict on the proposal

Profiles bought two things in Grails 3: a skeleton engine and an extensibility story, since anyone could publish a profile jar without a Grails pull request. Forge is the better skeleton engine (exclusive options, BOM switching, preview and diff, a website, version slots), but its features are compiled `@Component`s in `grails-forge-core`. A declarative descriptor plus executable GSP source templates restores extensibility without compiling a feature class into Forge or running Gradle during generation. The descriptor is data; the pack as a whole is trusted code, not a data-only format.

| Proposal item | Verdict | Adjustment |
| --- | --- | --- |
| 1. forge-cli searches plugin metadata and selects plugins | Yes | The metadata is `apache/grails-plugins-metadata`: 256 reviewed plugin entries with coordinates, labels, docs and an indexer that already downloads each released jar to read its descriptor. Nothing in the framework reads it yet: today's shell `list-plugins` command still scrapes an HTML directory listing of the legacy `org.grails.plugins` repository (`grails-profiles/base/commands/list-plugins.groovy:7`), and Forge reads only its own `pom.xml` for third-party versions. Both go away. The plan makes Forge read `grails-plugins-index.json` from the repository's `main` branch and adds one artifact: a **feature pack** (per-plugin descriptor plus templates) that the indexer learns to detect. |
| 2. forge-cli updates existing apps with a plugin's feature | Yes, scoped | `grails feature add` uses a committed generation baseline, locked inputs and a transactional three-way merge. Existing `FeatureDiffer` supplies text-diff presentation, not a ready-made merge or historical baseline. `project migrate` replaces the never-implemented `update-cli-config` guidance. |
| 3. Remove `create-app` from grails-shell-cli | Yes | Keep the command name; it becomes Forge's. Delete the dispatcher (`DelegatingShellApplication`), `-t/--type`, and `GRAILS_PREFERRED_SHELL`, which still defaults to the profile shell (`:56`). |
| 4. Remove profiles | Yes, staged | The widest blast radius. Every app build has a `profile` configuration with a default `org.apache.grails.profiles:web` dependency (`grails-gradle/plugins/.../GrailsGradlePlugin.groovy:461-490`); `grails.profile` is injected as an active Spring profile at runtime (`grails-core/.../GrailsApplicationPostProcessor.groovy:451`); 109 in-repo `application.yml` files set it; the BOM publishes profile constraints; the release verifier inspects the base profile jar (`etc/bin/verify-jar-artifacts.sh:208-236`). Nothing is removed in a minor release: the modules, shell code, `profile` Gradle configuration and runtime `grails.profile` injection all go in 9.0.x together, with `project migrate` and the upgrade guide carrying existing applications. |
| 5. Keep the shell's interactive capabilities | Yes, re-home them | In-project dispatch currently goes through a profile, and lifecycle commands live in profile scripts. Move the console services, built-ins and completion behind a profile-free command catalog and the unified CLI entry point. The existing `grails-shell-cli` artifact and `org.grails.cli.GrailsCli` remain for 8.1 projects and are retired on 9.0. |
| `.grails/` directory, gitignored | Partly | Commit `project.yml`, `forge.lock.yml` and `baseline/`; ignore caches, transaction journals, run state and history. Team members need the same generation inputs and merge ancestor, not just a list of feature names. |

<a id="architecture"></a>
## 2. Target architecture

Section 2 describes the 9.0 target. Section 5 sequences its implementation and defines the gates before profile removal. Unless explicitly labelled historical, proposed CLI behavior below is 9.0 behavior.

<a id="one-cli"></a>
### 2.1 One `grails` command tree

- Picocli root = Forge's `ForgeCommand`, named `grails`. Subcommands: `create-app`, `create-restapi`, `create-plugin`, `create-web-plugin`, `create-rest-api-plugin` (#14932), `feature list|search|info|diff|add`, `plugin search|info`, artefact commands, `project init|migrate`, and console commands. `plugin` searches all plugin metadata; `feature` searches usable generation features, so these are separate views over one index, not identical aliases. With no arguments inside a project the CLI opens the interactive console; outside it prints help.
- One `CommandCatalog` supplies dispatch, help, completion and IDE metadata. Register built-ins without application classpath resolution; load extension descriptors lazily. Refresh the catalog and completion after `feature add`, project initialization or explicit dependency refresh rather than freezing them for an entire interactive session.
- Delete `DelegatingShellApplication.groovy`, the shell/Forge alias start scripts, `-t/--type` dispatch and `GRAILS_PREFERRED_SHELL` on 9.0. The 8.1 distribution retains its existing entry points. A 9.0 CLI in an unmigrated 8.x project offers migration guidance rather than silently running the new generator against old metadata.
- `create-app --features` uses Forge's additive semantics and rejects unknown names; `--exclude-features` suppresses optional defaults. Document this major-version change, including the replacement of `--profile` by `--preset`. Align dependency intent with #14944/#14933, but do not promise byte-identical profile and Forge output.
- Startup budget: ordinary `--help`, `help --json` and migration preflight must not require Gradle, artifact downloads or a booted application. Load the Forge application context only when generation needs it. Measure cold and warm startup on documented reference machines; 500 ms for built-in help is a target to validate, not an existing guarantee. Dynamic catalog completeness is explicit (section 2.5).

<a id="engine"></a>
### 2.2 Generation engine: compiled features plus declarative feature packs

Forge keeps compiled `Feature` beans for framework-owned concerns: application types, build files, BOM switching and options. It gains a `DeclarativeFeature` adapter over a shared, identifier-based selection model. Construct a generation-scoped registry from built-ins and the resolved pack set; never mutate a global registry with a request's external packs. Packs are loaded from three places:

1. built-in packs bundled in `grails-forge-core` resources (official plugins migrate from compiled classes to packs wherever the feature is "dependencies plus config plus files");
2. packs fetched from plugin jars at generation time (third-party, section 2.3);
3. a local directory (`--feature-pack-dir`) for authoring and testing a pack before publishing.

Descriptor, `META-INF/grails-forge/feature.yml`:

```
format: 1
kind: feature
name: acme-audit
templateApi: 1
compatibility:
  grails: '[9.0.0,10.0.0)'
  forge: '[9.0.0,10.0.0)'
title: Acme Audit Trail
description: Adds the Acme audit plugin and its default configuration
category: Logging                      # a Forge Category string
documentation: https://acme.example/docs/grails
applicationTypes: [web, rest-api]      # default: all
requires: [gorm-hibernate7, database-migration] # resolved before contribution/rendering
conflicts: [gorm-mongodb]              # validated against the final selection
oneOf: com.acme:audit                  # optional, namespaced exclusive group
dependencies:
  - scope: implementation
    coordinates: com.acme:acme-audit-grails
    version: ${pack.version}           # explicit substitution; omit only when BOM-managed
  - scope: runtimeOnly
    coordinates: com.acme:acme-audit-h2 # version managed by acme-bom below
    when: { feature: h2 }              # conditional on another selected feature
    excludes: ['org.slf4j:slf4j-simple'] # new: Dependency has no exclusions today
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
| `dependencies`, `platforms` | Extend `GeneratorContext.addDependency` and the `Dependency`/Gradle models with exclusions and provenance-aware conflict checks | Collect contributions before the `BUILD` snapshot. Reject late contributions instead of silently dropping them. |
| `buildPlugins` | `GradlePlugin.builder().id().version()` |  |
| `build.properties` | `getBuildProperties().put` (`GeneratorContext.groovy:145`) | Snapshotted at `BUILD` like dependencies (`Gradle.groovy:80`). |
| `build.extensions`, `build.blocks`, `build.imports`, `build.settings` | a new keyed contribution API on `GradleBuild`, replacing the single `renderExtensions()` slot (`GradleBuild.groovy:122-134`) and `renderSettingsExtensions()` | See "Build-file contributions". |
| `configuration` | `getConfiguration()`, `getConfiguration(env)` (`GeneratorContext.groovy:152-188`) |  |
| `files` | `addTemplate` with `GspTemplate` over a pack-scoped page locator, or a byte-preserving copy | Destination tokens include `{packageName}`, `{packagePath}`, `{className}`, `{naturalName}`, `{propertyName}`, `{name}`. Record text/binary kind and executable mode separately from whether a file is templated. |
| `readme` | `addHelpTemplate` |  |
| `requires`, `conflicts`, `oneOf` | Identifier-based selection and group validation shared by compiled and declarative features | Existing class-based `isPresent` and `OneOfFeature` validation are insufficient. |
| `artefacts` | new entries in the codegen command set, which is a static list today (`ForgeCli.CODE_GEN_COMMANDS`, `ForgeCli.groovy:45-57`) | Must become dynamic. |

**Descriptor reading, as implemented so far (step 1).** `org.apache.grails.forge.core.pack.PackDescriptorParser` reads the descriptor into a `PackDescriptor` that cannot be changed, down to its nested configuration, and has value equality, in which the whole number 1 and the fraction 1.0 are different values. A descriptor is untrusted input, so:

- The YAML is loaded with SnakeYAML's safe constructor. A descriptor with an anchor, an alias or a tag anywhere is refused before anything is built: an alias repeats a value any number of times for a few characters, and a tag gives a value a type or a reading other than the one it is written in, and a descriptor needs neither. While the text is scanned, a key that is a mapping or a list is refused, and so is an unquoted key that would be read as a number, `true`, `false` or empty: with only text for keys, a mapping can order keys that share a hash code, and with anything else among them the loader compares each key with every other. Duplicate keys are then refused by the loader. A descriptor has at most 262,144 characters and mappings and lists at most 20 deep, counting the descriptor itself; the depth is checked while the text is scanned, so a long run of `[` is refused at its twenty-first. Whatever exception the loader throws is reported as a `PackDescriptorException`. Every later check works on a value only after its length is known to be within its limit. These are meant to keep the time and memory of reading a descriptor in proportion to its size; six review rounds each found a way around that and each is now a test; the sixth also went through every loop and pattern of the parser and found the others linear, and a seventh found nothing more: the slowest input it could build, 65,450 text keys sharing one hash code, is read in about 0.2 s and grows as n log n. What is known to remain: reading a descriptor at the size limit needs some 64 MB of heap or more for SnakeYAML's node tree and the maps built from it, which matters if a server reads several at once, and an `OutOfMemoryError` is not turned into a `PackDescriptorException`.
- A value without quotes is text unless it is `true`, `false`, empty (written as nothing, `~` or `null`) or a number in decimal digits that a `long` or a `double` keeps as written: a whole number of up to 18 digits, or a fraction of up to 15 digits in all. A fraction keeps its value but not its spelling: it loses a trailing zero (`1.10` is 1.1), which is why a version must be quoted, and the error for an unquoted one says so, and whatever later writes a configuration value has to print it plainly, since Java prints `0.0001` as `1.0E-4`. The other YAML 1.1 forms stay text: `yes` and `on`, octal and hexadecimal numbers, numbers in base sixty, dates, longer numbers, `-0`, and the `<<` merge key, which is a key like any other.
- Every key is text (a number, `true`, `false` or `null` as a key has to be quoted, and an entry cannot be without a key; the refusal comes from the scan and names the key but not its place), not blank, and one line of at most 200 characters without control, format or private-use characters, and the place of a problem shows at most 40 characters of each key on the way to it.
- Values that end up in a generated file as data are held to the characters such a value has, so they cannot carry code or a second line into `build.gradle` or `gradle.properties`: coordinates, exclusions, versions, plugin ids, build extension names, feature and group IDs, and the documentation address (http or https, URL characters only, without quotes, angle brackets or `$`). A build property has a name of letters, digits, `.`, `-` and `_` and a value, possibly empty (with or without quotes), of printable ASCII on one line with no backslash and no space at its ends, which `gradle.properties` reads back unchanged. The parser does not restrict which properties a pack sets; `org.gradle.jvmargs` is as valid as any. Build extension bodies, the readme, the instructions and the templates are the pack's content; the first three are limited in length and held to the same characters as the text people read, below.
- Text people read (title, description, readme, instructions) may not contain control characters (a tab is allowed in the readme and the instructions), private-use characters, unpaired surrogates, line or paragraph separators, the characters that reorder the text around them, or the invisible tag characters; it keeps the other format characters scripts and emoji need. Refusing the tag characters also refuses the three subdivision flag emoji (England, Scotland, Wales), which are spelled with them.
- A file path is relative with `/` between its at most 32 steps, and a step is not `.` or `..`, has no backslash, colon, wildcard, quote, angle bracket, bar, control, format or private-use character, does not start with `~` or contain `~` followed by a digit, as a Windows short name does, does not start or end with a space or end with a dot, is at most 255 characters, and is not a Windows device name or `.git`. A destination may use the documented placeholders; a source may not. Two destinations that differ only by case or Unicode form are duplicates (the comparison is cautious: `straße` and `strasse` count as one), and one destination cannot be a directory of another. The values substituted for placeholders are not known when the descriptor is read, so the code that writes the files has to check the resulting path again; whether a destination collides with a file the framework generates (`build.gradle`, the wrapper) is also for that code.
- What is refused while the text is scanned, and what SnakeYAML cannot load, is reported one problem at a time. Then the form of the keys is checked, and when that is right every other problem is reported, each with its place. At most 50 problems are listed, followed by how many more there are. A message quotes at most 80 characters of a value (300 of what SnakeYAML says, 200 of where the descriptor came from), never a whole map or list, with unsafe characters replaced.

It reads `format`, `kind` (required, `feature` only), `name`, `templateApi`, `compatibility.grails`/`forge` (required; each is one release or intervals of releases, see below), `title`, `description`, `category`, `documentation`, `applicationTypes`, `requires` and `conflicts` (feature names or `group:artifact#name`), `oneOf` (a group ID), `dependencies` (with `excludes`; one version, not a range), `platforms`, `buildPlugins`, `build.properties`, `build.extensions`, `configuration`, `files` (`from`/`to`), `readme` and `instructions`. Not read yet, and therefore errors: `when` predicates, the `oneOf` form of an entry of `requires`, `flags`, parameters, `artefacts`, `build.blocks`/`imports`/`settings`, `executable`/`binary` on a file, `${pack.version}` as a version, and `kind: preset`. The descriptor keeps a `tasks.<task>` extension name as it is; mapping it to `BuildFileContributions.task` is the adapter's job. 

**Packs in generation, as implemented so far (step 1).** `PackLoader.fromDirectory` reads a pack from a directory (`feature.yml` and, under `templates`, the files it names) once: the content of the files is kept as it was read, so what is written later is what was checked. The descriptor, `templates`, and every directory and file on the way to a named file must be real, not symbolic links, when they are looked at, which is before and again after a file is read, and a file with a second name (a hard link) is refused where the file system counts names; a file has at most 1 MiB and the entries of a pack 16 MiB together; a file whose name ends with `.gsp`, in any case of letters, is a template and has to be text in UTF-8.

`PackFeature` is the feature of a pack. It contributes its dependencies with their exclusions, platforms, build plugins, build properties, build extensions (`tasks.<task>` becomes the configuration of that task), configuration, files, and readme. A pack adds to an application and does not change what another feature gives it:

- The features it requires are selected with it, before the defaults: `ContextFactory` now selects what every selected feature names in `getDependentFeatures()`, transitively, and only then gives the features nothing has chosen their defaults. A pack that requires `postgres` therefore gets no `h2` next to it. A required name that is not available for the application type is an error.
- It refuses to be applied with a feature it conflicts with.
- A setting or a build property that the pack and another feature both declare must be the same in both, compared strictly (`true` is not `'true'`). The pack checks when it is applied and again once every feature is applied, so the outcome does not depend on which of the two comes first. A setting cannot be a value for one and a group of settings for the other (`a.b` and `a.b.c`), which the YAML writer would misplace; a mapping without settings sets nothing. The settings under `environments.<name>` are kept under that name in `application.yml`, where the framework keeps most of its own, and are also compared with the separate file the framework has for an environment (`application-development.yml` of DevTools, `application-test.yml` of Testcontainers); the parser holds the name of an environment to a name.
- A dependency the pack gives a version must not be in the application in another version or without one, in whatever scope, so a pack cannot pin what the application leaves to its platform, also not by declaring it in another scope.
- For every feature, declaring one dependency twice in one scope in different versions or kinds, or applying one build plugin in two versions or once in the `plugins` block and once with `apply plugin`, is now an error in `GeneratorContext`, as different exclusions already were. A pack therefore cannot repeat a plugin the framework applies with `apply plugin`, or set the properties the Gradle feature sets (`version`, `org.gradle.*`, `grailsVersion`) to another value. A context whose features could not be applied fails the same way when it is used again, where it generated a partial application.

A file whose name in the pack ends with `.gsp` is a template; any other file is written as it is. `PackPages` compiles a template from its source with the GSP engine Forge renders its own pages with, as if it started with `<%@ page trimLogicLines="true" expressionCodec="none" %>`, and renders it when the feature is applied, so every feature is chosen by then and a template that cannot be compiled or rendered, or writes more than 1 MiB, fails the generation before anything is written. One engine is made for a generation, shared by its packs and made only when one of them has a template; it reads the source as UTF-8 whatever the platform's encoding is, and a byte order mark is dropped. Every destination of a pack is checked before its first template is rendered, and what the files of a pack come to after rendering is bounded at 16 MiB like its sources. A context is generated only as the project it was made for. What a template can refer to is template API 1, `PackModel`: `project` (`name`, `packageName`, `packagePath`, `className`, `naturalName`, `propertyName`), `features` (the names of every feature of the application, sorted), `applicationType`, `options` (`jdk`, `data`, `servlet`, `reloading`), `grailsVersion` and `pack.name`, all of them text, numbers, lists and maps that cannot be changed, and none of them a class of Forge. The options are written as the create commands take them (`hibernate7`, `tomcat`, `devtools`). A name that template API 1 has not, at the top or inside `project`, `options` or `pack`, fails the template with the names there are; it does not render as nothing. That holds for the names a web page has too (`params`, `request`, `grailsApplication`), which a template has not; `out`, `it` and `pageScope` are the page's own and are there. A template asks whether a name is there with `containsKey` or `in`. An assertion, a linkage error and a stack overflow in a template fail it as an exception does, with the cause kept; an error of the virtual machine other than a stack overflow (running out of memory, an internal error) is passed on as it is. A template can still import a class of Forge or declare its own page directives, and is then outside the contract. A template is code of the pack and is not confined: it can do what the generator can.

The compatibility of a pack is evaluated when its features are made (`PackVersionRange`): `9.0.0` is that release only, `[9.0.0,10.0.0)` an interval with the square end in and the round end out, `[9.1,)` and `(,10)` open at one end, and several intervals are separated by commas. The versions of a range are releases of up to four numbers, a number left out being zero; a qualifier in a range is refused by the parser. The version that is compared is taken for the release it leads to, so `9.0.0-SNAPSHOT`, `9.0.0-M2` and `9.0.0-RC1` are in a range that has 9.0.0: a pack for a release can be tried on what leads to it. `grails` is compared with the version of Grails the generator generates for and `forge` with the version of the generator, which are one version today. A pack that is given and is not for this Grails or this Forge is refused, selected or not and whatever kinds of application it is for, with the range and the version that is not in it in the message. A version that is not a release with nothing or a qualifier after it (`9.0.1a`, five numbers) is in no range.

Forge has packs in itself (`BundledPackFeatures`): a feature of the framework that is only dependencies, settings and files is written in the format a plugin uses, under `META-INF/grails-forge-bundled/<name>/` of the resources of `grails-forge-core`, and named in the index `META-INF/grails-forge-bundled/packs`. Each is registered as a bean of the generator, so it is a feature like the compiled ones for the command line and for the hosted Forge: listed, selected, validated and counted among the names a pack of a plugin cannot take. A pack of Forge that cannot be read or is not for this Grails stops Forge from starting. One thing is different for such a pack: a dependency can say `versionOfForge: true` in place of a version and has the group and version Forge knows for the artifact, as a compiled feature gets with `lookupArtifactId`, so the version stays in one place. A dependency without that key and without a version has none, as before, and the parser refuses the key in the pack of a plugin. A pack of Forge can be in an exclusive group of the framework (`cache-ehcache` is in `cache-provider`); the registry asks a feature that is a pack for no class of its family. The descriptor has `thirdPartyDocumentation`, a link, for any pack. `grails-web-console`, `grails-quartz` and `cache-ehcache` are converted, and the applications generated with each, alone and beside every other visible feature, for the four kinds of application (23,142 files), are the same as before the conversion but for the time of day in the header of a `.properties` file. The conversion is checked with a temporary spec that dumps every such generation before and after; it is not kept in the tree. What is kept is `BundledPackGoldenSpec`: for every pack of the index a golden file under `src/test/resources/golden/bundled` with what the feature says of itself and every line an application has with it that it has not without it, for each kind of application, the versions of dependencies and of the documentation of Grails written as `VERSION` (a version inside a file a template wrote, or of a build plugin, is as it is), and from the descriptor what those lines cannot show: its compatibility, requirements, conflicts and group, where each version comes from, and where its build blocks go; and the exact list of the names of the index. A pack of Forge can also say `guideOfGrails: guide/<page>.html` in place of `documentation`; the link is then to that page in the documentation of the version of Grails this Forge generates for (`snapshot` for a snapshot), as the compiled features computed it. The default of the layout group, `sitemesh3`, stands aside for a feature of its group and not of its type, and a group whose compiled features say so with `OneOfFeature.isFoundByGroup` can have packs beside them. Converted so far, thirteen: `asciidoctor`, `cache`, `cache-ehcache`, `grails-layout`, `grails-quartz`, `grails-spring-ai`, `grails-web-console`, `hibernate-validator`, `openapi`, `shade`, `spring-ai-anthropic`, `spring-ai-ollama`, `spring-ai-openai`; each was compared, alone and beside every other visible feature for the four kinds of application, with what the compiled feature generated. One thing an application can see changed: the three Spring AI providers now name `grails-spring-ai` as a feature they depend on (`getDependentFeatures`, which the hosted Forge reports), where the compiled providers added it without saying so. Known and kept, because the compiled features did the same: a link added to the readme with `readmeLinks` has no line break after it (the view `markdownLink.gsp` ends without one), so two links, and the heading that follows, are on one line; and Forge knows no version for `asciidoctorj`, so the versions the Asciidoctor descriptor falls back to are always the ones used. A dependency that says `versionOfForge` has to name the group Forge knows the artifact by. An address in a descriptor cannot have square brackets, with which it could end the text of the link it is written into and start another. Looked at and left compiled, because the format cannot say it: the database drivers (their data is read from the driver's class by the Hibernate features, the default driver and Testcontainers), the servlet containers, DevTools and Neo4j (chosen by an option through `DefaultFeature.shouldApply`, and hidden), and every feature that takes part in selection or renders from computed values. An index that is missing, empty or names a pack twice stops Forge from starting. An exclusive group cannot have both packs and compiled features that are looked for by their type, since such a default would not see the pack: the registry refuses that at startup, and takes a group whose compiled features say they are found by the group (so the database drivers, whose data the Hibernate features and Testcontainers read from the driver's class, stay compiled). A feature a pack requires has to support the kind of application wherever the requirement is resolved. Not done for packs of Forge: their templates are compiled from source at each use and are not precompiled.

A pack of Forge can say four more things, each of which the parser reads only for such a pack (`parseOfForge`) and refuses, as a key it does not read, in the pack of a plugin. A build plugin can say `artifactOfForge: <artifact>` in place of a version and has the version Forge knows for that artifact, as `GradlePlugin.builder().id(…).lookupArtifactId(…)` gives; an artifact Forge does not know fails the generation. `versionsOfForge` maps up to 20 artifacts to a version each: the templates of the pack then have `versionsOfForge`, a name that is not of the template API, with the version Forge knows for each artifact or, when it knows none, the one written in the descriptor; asking it for an artifact the descriptor does not name is an error, and a pack without the key has no such name. `build.blocks` maps `afterPlugins`, `afterDependencies` and `end` to text that is added to the build file at that place as it is (`BuildFileContributions.block`); this is the `build.blocks` of the table above, not yet open to the pack of a plugin, and its text is not a template. `readmeLinks` is a list of up to 20 `label` and `href` pairs, each added to the readme with `GeneratorContext.addHelpLink`; `readme` cannot stand in for it, because it ends its text with an empty line and a help link ends with none. The label has no bracket, angle bracket or backslash, and the address is held to what `documentation` is. `shade` and `asciidoctor` are converted with these keys: the two GSP views of Asciidoctor are now templates of its pack, and the applications generated with each of the two features, alone and beside every other visible feature, for the four kinds of application (15,726 files), are the same as before the conversion but for the time of day in the header of a `.properties` file.

A pack with `oneOf` is in that exclusive group. `ProjectGenerator.createGeneratorContext(…, packs)` builds the features of one generation from the framework's and the given packs (`PackFeatures`), so a pack is never registered globally; a pack cannot take the name of a framework feature of any application type, hidden ones included, or of another pack, whatever application types the two are for, and cannot name an exclusive group of the framework yet.

A destination is checked again once its placeholders have their values, which come from the name the application is given (`.{name}/hooks/x` for an application named `git` is refused), by the feature and again by the generator for the project it writes. Before it writes anything the generator refuses, for all features: a path that is absolute or has a `..` step or a backslash; two templates for one file; and a file where another template needs a directory, with placeholders replaced, empty and `.` steps dropped, and the case and composed form of letters ignored. An application name with a backslash is refused by this, with a message about a path; `NameUtils.parse` should refuse it as a name. Before, the second of two templates was silently skipped. The zip output refuses a path that is not inside the archive on its own as well; it does not check the directory it is given as a prefix, which comes from the application name.

A review generated 557,239 selections of framework features without a pack (every single feature, pair and triple for the four application types, each also with every non-default option value) on the code before and after these rules: every output file was identical and nothing that generated before is refused.

The create commands of the Forge CLI (`create-app` and the others) take `--feature-pack-dir <directory>`, any number of times: the pack of each directory is read, named with where it came from before the application is created, and its feature can then be selected with `--features` and is listed by `--list-features`. A directory that holds no usable pack is a mistake in the command line (exit code 2, with every problem of the descriptor), and nothing is created. Only the packs the application is made with are named: the selected ones and those they require. The hosted Forge takes no packs. Under the line that names a pack the command says what it adds, before anything is written: first what of it runs as code, the templates and the build file blocks by name, then every file it writes outside the sources (outside `src/main`, `src/test`, `src/integration-test` and `grails-app` other than `grails-app/conf`) by destination, none left out, the source files it writes, its build plugins, build properties with their values, platforms, dependencies with their excludes, the number of its settings, whether it adds to the readme, and the features it requires and cannot be used with. Every other line names at most 20 things and says how many more there are; a thing longer than 120 characters is cut, and one with a comma in it is put in quotes. The option is in the help and in the guide (`plugins/forgeFeaturePacks.adoc`); `--feature-pack-jar` is hidden. What a descriptor says is shown by the command line, which reads `@|style text|@` as markup, so the parser refuses `@|` in every key and value of a descriptor.

A pack is build code the user trusts: the bodies of its build extensions are Gradle code, it can apply any build plugin, give a version to any dependency the application does not declare itself, and write any file the selected framework features do not write, `settings.gradle`, `buildSrc/**` and `.github/workflows/**` among them. The summary names every one of those files by destination. With `--inplace` a file of a pack that already exists is skipped without a message, as the framework's files are.

Not done yet, or known limits: a template is compiled every time it is used, with no cache between generations or between two destinations of one source, and its compiled pages are released only when memory is needed; there is no `templateApi` check beyond the parser's `1`, no precompiled templates for the packs of Forge, no class loader of its own for a pack, no author-side check compilation and no timeout; only the GSP tags that are compiled into the page run (`g:if`, `g:else`, `g:elseif`, `g:unless`, `g:each`, `g:while`, `g:collect`, `g:findAll`, `g:grep`, `g:def`); a tag of a tag library (`g:set`, `g:message`, `g:link`, `g:render`) is written out as text; only an error in an expression names the line of the template: one in a scriptlet names none, and a compile error names a line of the generated class; the pack's version and coordinates, the operating system and the language are not in the model; `flags`, parameters and `when` are not in the model or the descriptor; build extension bodies and the readme are not templates; `instructions` are not shown; a requirement on the feature of another pack by `group:artifact#name` does not resolve; conflicts are only found when the features are applied, so a caller that only builds the context does not see them; two framework features that set one setting differently are still not compared, only a pack's settings are; build extensions of a pack are added to the block of another feature without being compared; the executable bit of a pack's file is not kept; and a process that rewrites a pack's directory while it is read can still get a link past the loader, or stall it with a named pipe.

**Feature identity and selection.** The adapter is not the entire engine change. Before step 1 `FeatureContext.isPresent(Class)` and `OneOfFeatureValidator` used class assignability; `addFeature` still recurses immediately into selection. Step 1 gave every exclusive family a string group ID (`OneOfFeature.getGroup()`; the IDs are `cache-provider`, `configuration`, `data-nosql`, `database-driver`, `embedded-servlet`, `geb`, `gradle-build-src`, `gradle-settings-file`, `api-views`, `gsp-layout`, `logging`, `migration`, `reloading`, `spring-ai-provider`, `spring-security` and `threading`), which the validator, the registry and the web API's `oneOfGroup` now use, and added `FeatureContext.isPresent(String)` and `isGroupPresent(String)`. Defaults that look for a family by type (`H2`, `Yaml`, `Logback`, `GebWithTestcontainers`, `Sitemesh3`, `ViewJson`, the Hibernate driver check) still do, and must move to group IDs before a pack can join one of those groups. The new shared contract is:

- Framework feature IDs retain their existing names. External IDs are `<group>:<artifact>#<name>`; short names are accepted only when unambiguous. A pack cannot shadow a framework ID, and two versions of the same pack cannot coexist. Errors name the contributing artifacts. External requirements identify a canonical feature ID and an exact pack version or a unique catalog resolution, which is then locked.
- Exclusive groups are stable string IDs, with framework-owned IDs for existing groups and namespaced IDs for external groups. Adapt compiled features to the same group registry before converting any to packs; do not generate Java classes to represent descriptor groups. Retain typed access only where a compiled feature genuinely provides a typed implementation.
- Resolve explicit features and explicit option choices first, expand mandatory requirements with a visited set and cycle diagnostics, then choose optional defaults for unfilled groups. An explicit incompatible choice fails rather than being silently replaced. `--exclude-features` can suppress optional defaults but cannot remove a mandatory requirement. Preset defaults participate below explicit user choices.
- Finish selection, including requirements introduced by defaults, to a bounded fixed point; then validate compatibility, conflicts and exclusive groups before any template executes. Evaluate contribution predicates and flags against this final set. Feature-dependent negative predicates are allowed for contributions, not for adding/removing requirements, to avoid oscillating selection.
- Process each feature once per stage. Deterministic order is phase/order, then canonical ID; required collaborators must be resolved before contribution processing. Compiled hooks that add features must use this shared resolver rather than bypassing it. Test a cycle, a diamond dependency, excluded requirements and mixed compiled/pack groups.

**Template language and compatibility.** External packs use GSP source, compiled by the CLI in a pack-scoped engine; hosted Forge renders only bundled official templates. Keep `trimLogicLines="true"` and `expressionCodec="none"`. Introduce a versioned public pack model (`templateApi: 1`) exposing project, final feature IDs, options, flags and declared parameters. Existing templates that import internal Forge model classes must migrate to that API; source compilation alone does not make them version-independent.

Descriptor format, template API, supported Forge versions and target Grails versions are separate compatibility checks. Runtime `grailsVersion` metadata is an additional filter, not a substitute for generator compatibility. Publish the range/comparator rules, including prereleases; snapshots require explicit selection and a compatible declaration. A pack is supported only on the combinations its author tests. No promise is made that one source pack works on every runtime line its plugin supports.

Bundled official packs are precompiled with the matching Forge build. External source is compiled on first use and cached by artifact/source digest, exact Forge/GSP/Groovy versions, template API and Java compilation target. Local-directory content changes invalidate the cache. This is a separate source-capable engine/locator path: toggling `ForgePages` development mode does not itself supply file lookup, class isolation or persistent caching. Verbatim resources remain byte-preserving copies, with optional `executable: true` and explicit `binary: true`; text is UTF-8 unless declared binary.

GSP avoids a wholesale language rewrite, but conversion still needs model-API adaptation and selection/output tests. Build-time descriptor validation and GSP check compilation catch structural and type errors; generation tests catch rendering behavior. First-use compilation cost must be measured. Pack code must render deterministically from declared inputs; replay comparison in section 2.6 detects drift but is not a sandbox.

There is one template engine in v1. A future logic-less engine would require its own format/API decision and is not an automatic fallback for incompatible GSP packs.

**Precedents.** Quarkus extensions declare `metadata.codestart` in `quarkus-extension.yaml`, ship the codestart in a `codestarts` classifier jar (`codestart.yml` plus Qute templates), and users select them with `quarkus create app -x group:artifact:version`. Spring Initializr keeps its dependency catalog declarative (`initializr.dependencies` with `compatibilityRange`, `bom`, `repository`) and reserves code (`ProjectContributor`, `BuildCustomizer`) for the service itself. Grails 3 profiles' `feature.yml` plus `skeleton/` is the same shape as this proposal; it lived in the wrong engine.

**Conditional logic in packs.** Selection and model contributions use descriptor predicates; GSP templates may branch on the resolved model. These are three distinct uses:

1. *Auto-adding a collaborator*: Hibernate 7 requires a database driver, Geb requires Spock, Scaffolding requires GSP, and Spring Security UI requires Spring Security. Resolve these before rendering; a group requirement chooses its default only after explicit selections have been considered:

   ```
   requires:
     - gsp
     - oneOf: { group: database-driver, default: h2 }
   ```
2. *Conditional contributions* (`apply`): `TestContainers` adds `testcontainers-spock` only when Spock is present and `testcontainers-mongodb` only when a Mongo feature is (`database/TestContainers.groovy:81-87`). That is a `when` predicate on a dependency, file or configuration entry, so the entry exists or does not; no template logic is involved:

   ```
   dependencies:
      - { scope: testImplementation, coordinates: 'org.testcontainers:testcontainers-spock', when: { feature: spock } }
      - { scope: testImplementation, coordinates: 'org.testcontainers:testcontainers-mongodb', when: { anyOf: [gorm-mongodb, mongo-sync] } }
   files:
     - { from: MongoSpec.groovy.gsp, to: 'src/integration-test/groovy/{packagePath}/MongoSpec.groovy', when: { feature: gorm-mongodb } }
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

One predicate vocabulary serves contribution `when` and `flags`: `feature`, `anyOf`, `allOf`, `not`, `applicationType`, and `option` (for example `option: { data: hibernate7 }`). Requirements may be conditional on immutable inputs such as application type and explicit options, not on the absence of a feature still being resolved. Configuration supports both unconditional mappings and conditional entries with `when` and `values`; normalize both into the same configuration model. Declare parameter names, types, defaults and required status in the schema and reject unknown parameters before rendering. Parameters used for repeatable generation are non-secret values recorded in the lock.

Framework-owned decisions such as BOM and Gradle-plugin selection stay compiled, but contribute through the same build model as packs. Move feature-name branches out of the central build template into their owning contributors. Packs cannot replace another feature's templates implicitly; conflicting output paths are diagnosed before writing.

**How the template decision changed.** Revisions 1 to 5 of this plan recommended Mustache, on the grounds that a pack had to be safe to render on start.grails.org. Once external packs became a CLI-only capability (section 2.7), the remaining arguments were weighed again:

- *Precompiled external pages* bind to a Groovy/GSP binary stack. Shipping source avoids that artifact coupling, while the versioned model API and compatibility tests address source compatibility. Author-side check compilation still needs the compiler in an isolated tool process.
- *Code execution* is accepted in the CLI, including during `feature diff` and `--dry-run`. This executes author code earlier than building the generated project. The CLI identifies the source artifact and checksum before first use; it does not describe packs as sandboxed.
- *Reviewability and tooling*: descriptors can be inspected without executing templates. Check compilation and rendering execute compiler/template code; an in-memory output handler cannot prevent arbitrary GSP side effects. Dry run promises no application of the planned project changes, not no execution or no cache writes.
- *Whitespace* is handled by the `trimLogicLines` page directive that already exists for Forge's templates.

The hosted-service exclusion is unchanged. Supporting another engine or community packs on the hosted service is outside this design.

**Build-file contributions.** `build.gradle` is the file every pack wants to touch, and it is where the current design is weakest. Three mechanisms coexisted before step 1 (the line numbers are those of the template before it): the model (dependencies, plugins, repositories, buildscript, properties); one generic slot, `${gradleBuild.renderExtensions()}` after the dependencies block (`feature/build/gradle/templates/buildGradle.gsp:107`), fed by `GradlePlugin.extension(Writable)`; and eight branches hard-coded in the central template on feature names (`gradle-build-src`, `asciidoctor`, `jrebel`, `spock`, `geb-with-local-browsers`, `grails-compile-static`, `gsp`, `asset-pipeline-grails`, `buildGradle.gsp:40-122`) plus the BOM choice in `buildSrcBuildGradle.gsp:50-58`. The hard-coded branches are the "everyone edits the central file" problem in miniature, and a pack cannot use them at all. There was also a silent drop: `Dependency.Builder.extension(Writable)` was accepted and never rendered, so the `sourceSets` block `DatabaseMigrationPlugin` passed to it never reached the build file. That block is not needed, because the Grails Gradle plugin has made `grails-app/migrations` a resources directory since Grails 7, so the hook and its template were removed rather than wired up (step 1).

The rule for packs: no pack ships a `build.gradle` template or a text patch. The build file stays generated from a model, and packs contribute through typed channels that Forge merges:

| Channel | Renders | Merge rule |
| --- | --- | --- |
| `dependencies`, `platforms` | lines in `dependencies {}` | validate before deduplication; key by group, artifact, scope and dependency kind (ordinary/platform/enforced platform); conflicting versions or exclusion sets fail with provenance rather than silently losing a contribution |
| `buildPlugins` (`id`, `version`, `apply`) | a `plugins {}` entry or an `apply plugin:` line | same id collapses; different versions error |
| `build.repositories`, `build.buildscript` | `repositories {}`, `buildscript {}` | by URL or coordinate |
| `build.properties` | `gradle.properties` | same key and value collapse; different values error |
| `build.imports` | import lines at the top of the file | set |
| `build.settings` | settings contributions at named structural locations, including `pluginManagement` repositories | merge repository entries by normalized URL; keep `pluginManagement` in Gradle's required leading position; arbitrary fragments are restricted to the end |
| `build.extensions.<name>` | one `<name> { }` block per key with the bodies concatenated; `tasks.<task>` keys render `tasks.named('<task>') { }` | merged across packs and framework features, in feature order |
| `build.blocks` | a free-form fragment at an anchor: `afterPlugins`, `afterDependencies`, `end` | ordered, never merged; the escape hatch for `sourceSets {}` or a custom task |

Every fragment is a GSP with the versioned pack model. Ordering follows the shared phase/order and canonical-ID ordering, then descriptor declaration order. Since step 1 `FeatureContext` processes, returns and applies features by order and then name; before it, equal-order features came out of a hash set in an order that varied between runs. Existing `Dependency.equals` ignores versions and dependency kind; conflict detection must happen before insertion into the current set. Configuration mappings follow the same provenance rule: identical leaves collapse, conflicting leaves or scalar/map/list shapes fail; list replacement/concatenation is never inferred.

Implemented so far in step 1 (`BuildFileContributions`, reached through `GeneratorContext.getBuildContributions()`): `extension(name, body)` for `build.extensions`, where the name is the start of the block as written, and `task(taskName, body)` for `tasks.named('<task>') { }` (the descriptor's `tasks.<task>` key maps to it); `block(anchor, text)` for `build.blocks` at `AFTER_PLUGINS`, `AFTER_DEPENDENCIES` and `END`; and an error for a contribution made after the build file is rendered. `GeneratorContext.applyFeatures` now applies each feature once. Asciidoctor, JRebel, Spock (with the Geb local-browser lines), compile-static (with its GSP option) and asset-pipeline (with its web-plugin and OpenAPI lines) contribute their own blocks, and `buildGradle.gsp` no longer branches on them. The generated blocks are unchanged, but they now come in feature order (`assets`, `grails`, `tasks.named('bootRun')`, `tasks.withType(Test)`) where the template had them in a fixed order, and the Asciidoctor `apply from:` line is now followed by an empty line. The conditions on other features moved with the blocks: Spock adds the Geb lines, compile-static the GSP option, and asset-pipeline the OpenAPI exclusion, so no two framework features merge into one extension yet. The old `GradlePlugin` extension slot (`GradleBuild.renderExtensions()`) still exists next to the new channel and is rendered after it; no framework feature uses it. Still to move: `build.properties`, `build.imports`, `build.settings`, `build.repositories` and `build.buildscript` as channels, the `gradle-build-src` branch around the `buildscript` block, and the BOM choice in `buildSrcBuildGradle.gsp`.

The framework's own features move to these channels in step 1: asset-pipeline contributes `assets {}`, compile-static contributes `grails {}`, Spock and Geb contribute the test task configuration (`tasks.withType(Test).configureEach`), asciidoctor and jrebel contribute their blocks, and `GrailsBase` contributes the BOM. The central template owns structure only. Stable rendering reduces unrelated diffs; it does not guarantee that edits to a user's Gradle script merge cleanly merely because a block still exists.

The supported contribution API does not allow a pack to change framework BOM selection, reorder framework plugins, change the Java toolchain or delete another feature's contributions. Free-form Groovy fragments cannot enforce those semantic restrictions statically; they remain trusted author code and require generated-build tests. Prefer structured channels and provide targeted lint diagnostics for recognized patterns, not a promise to understand arbitrary Gradle code. Detect file-path collisions across all features, presets and framework outputs before rendering to the destination.

<a id="metadata"></a>
### 2.3 Plugin metadata: catalog plus pack

**Where a pack lives, and the Gradle plugin that publishes it.** Inside the plugin's main jar under `META-INF/grails-forge/`. Authoring and publishing are automated by the Grails Gradle plugins, the same way `grails-plugin-cli` automates `-cli` companions today: a plugin author keeps the pack in `src/main/forge/` (`feature.yml` plus `templates/`), and the `grails-plugin` Gradle plugin, when that directory exists, does the rest:

- validates `feature.yml` against the descriptor schema (unknown keys, predicates, compatibility ranges, parameter types, output paths and references). Validate framework references against the declared generator version; resolve external references from declared pack inputs or a test catalog, not just the bundled public index, so private packs remain authorable offline;
- check-compiles GSP sources against the declared template API in an isolated tool JVM. Gradle's embedded Groovy must not load Forge's application Groovy/GSP stack. Compiler dependencies are task tooling, not dependencies of the plugin's runtime publication;
- packages the directory into `META-INF/grails-forge/` of the main jar and stamps `Grails-Forge-Pack: <name>`. The manifest is a detection hint inside the jar, not a separate descriptor or a guarantee of a partial network download;
- registers an opt-in `forgePackTest` task that invokes Forge in an isolated JVM, generates supported application-type/option fixtures, and builds them using their wrapper. Task inputs include generator coordinates, source digests, options and fixture repositories; configuration-cache reuse must work. A preset-only publication uses the same JAR layout without implying an application runtime dependency;
- with `grails-publish` applied, needs nothing more: the pack travels with the plugin's existing publication.

Implemented so far (step 1): the packaging only. `GrailsPluginGradlePlugin.configureForgePack` registers `copyForgePack`, which stages `feature.yml` and the files under `templates` of `src/main/forge` (nothing else of that directory, and no empty directories) as `META-INF/grails-forge/` on the output of the main source set, in a directory of its own so the GSP templates are not dropped by the exclusion that keeps views out of `processResources`; the jar's manifest gets `Grails-Forge-Pack` with the `name` of the descriptor, read as YAML, and a descriptor that is not YAML or whose name is not one a pack can have fails the build. So does a pack Forge could not read from the jar: files without a `feature.yml`, a link under `templates`, and a file Gradle leaves out of every copy (`.gitignore`, `.DS_Store`, backup files), which is found by comparing what was staged with what is there. Gradle looks at neither those files nor links as inputs, and with the configuration cache reused nothing else would see one that is added, so `copyForgePack` is never up to date: the pack is staged and checked in every build. The descriptor is at most 256 KiB here as in Forge, and the name is read with every value as written, so `yes` and `on` are names as they are for Forge. The descriptor is read as an input of the configuration, so adding or renaming a pack is seen with the configuration cache on, and any edit of the descriptor discards the cache entry. The pack is on the runtime classpath of the plugin as a resource, as its templates are. Not done: validation of the descriptor and check-compilation of the templates in a tool JVM, and `forgePackTest`. Forge reads what this packages: `PackLoader.fromJar` takes `META-INF/grails-forge/feature.yml` and the files the descriptor names under `META-INF/grails-forge/templates/` from a jar, with the limits of a directory pack applied to what is read and not to the sizes the jar states, and reads nothing else of the jar; the manifest attribute is not looked at, a signature is not verified, and a jar with two entries of one name for the descriptor or a file of the pack is refused. Both loaders stop after 50 problems and once the pack is over its 16 MiB, and count a file each time the descriptor names it. The create commands take such a jar with the hidden `--feature-pack-jar`. Finding the jar from the coordinates of a plugin, and recording its checksum, is in the paragraph "Implemented: direct coordinates" below.

V1 uses the main JAR only. It never probes for a `forge` classifier. Future classifier support must extend artifact identity and record the checksum of that exact classifier artifact. `grails create-feature-pack` scaffolding is deferred; the 9.0 authoring guide supplies a complete working source layout and fixture instead.

**Presets.** A preset is a pack with `kind: preset`, using the same format, template API, compatibility checks, distribution and locking. It names one application type, supplies option and parameter defaults, requests features and may add files through the normal contribution model. For example:

```yaml
format: 1
kind: preset
name: acme-service
templateApi: 1
compatibility:
  grails: '[9.0.0,10.0.0)'
  forge: '[9.0.0,10.0.0)'
applicationType: rest-api
options: { data: hibernate7, servlet: tomcat, jdk: 21 }
features: [views-json, database-migration]
```

V1 permits one preset per project and no preset inheritance. Explicit CLI options override preset defaults, which override framework defaults; an incompatible application-type command fails. Requested features and mandatory requirements follow section 2.2. A preset cannot overwrite an existing framework output or replace the build template; it uses the shared contribution channels and collision rules. Full custom-profile capabilities such as inherited skeleton replacement and multi-project skeleton layouts are not claimed as automatic equivalents. Document those migration gaps and provide a manual conversion path before removing profile support. Preset schema, authoring, a published synthetic company-preset fixture and migration coverage are step-2/step-3 delivery gates, not deferred work.

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

1. **The indexer detects packs.** Read `META-INF/grails-forge/feature.yml` and record its format, kind, canonical feature ID, application types, template API and compatibility declarations, together with the main JAR's SHA-256 and exact artifact coordinates. Pack-only preset artifacts are indexed from their descriptor even without a runtime `grails-plugin.xml`. Extend the existing catalog schema with an explicit version, keeping ordinary plugin entries usable for `plugin search`.
2. **The CLI reads the index.** `labels` map to categories, `desc` and `docs` supply display metadata, and deprecated entries are hidden unless requested. Filter against both target Grails and executing Forge compatibility; use the newest compatible released external pack only for a new, unlocked selection. Refresh from `https://raw.githubusercontent.com/apache/grails-plugins-metadata/main/grails-plugins-index.json`, with a release-pinned copy bundled for offline discovery. Record the index content digest used for resolution. `forge-web` neither refreshes this index nor exposes external plugin search. Existing locks are never re-resolved merely because the index changed.
3. **Official versions remain BOM-aligned.** Bundled official features use the matching generator release. When selecting a separately distributed BOM-managed pack, resolve its exact artifact version from the target BOM's bundled version metadata, not from the catalog's newest entry. Omit the runtime dependency version in generated builds only when the chosen BOM actually manages it; still record the exact pack artifact and digest in the lock. Fail if the needed BOM version mapping is unavailable rather than silently choosing a newer pack.
4. **Explicit coordinates remain the escape hatch** for a plugin that is not in the index: `grails create-app shop --features com.acme:acme-audit-grails:2.3.0`. Users can add private indexes and repositories in `~/.grails/forge.yml`.

Weaknesses of the index that the plan has to live with or fix: `grailsVersion` is whatever the plugin descriptor says (grails-cache 8.0.0 reports `8.0.0-SNAPSHOT > *`), `docs` is missing on 134 entries, `labels` are free-form, there is no checksum today, and the older `sync-latest-version` action still refers to a `grails-plugins.json` file that no longer exists.

**Fetching without a build.** Fetch the main JAR by exact Maven-layout URL without resolving its runtime dependency graph. Follow descriptor requirements as a pack graph, separately from Maven transitive dependencies. Hash the complete downloaded artifact before extracting only `META-INF/grails-forge/**`; never put the plugin's runtime JAR on the template classpath. A checksum mismatch is a failure, not a reason to try a different artifact.

Explicit repository mappings in `~/.grails/forge.yml` take precedence, then configured `GRAILS_REPO_URL` overrides, catalog repository overrides and Maven Central. Repository IDs, group/content filters and exclusive mappings prevent a private coordinate from being sent to an unrelated repository. Honor per-version catalog coordinates and repositories. Credentials stay in user configuration, are scoped to their repository and are not forwarded across origins. Repository access used to fetch a pack is distinct from repositories required by the generated build; emit only declared, credential-free build repository configuration.

Use HTTPS for remote repositories; explicitly configured local `file:` repositories and loopback test servers support authoring fixtures. No catalog entry may enable those local exceptions. A small `settings.xml` reader supports documented proxy/server fields; do not imply full Maven mirror, profile or encrypted-password support. Unsupported required settings produce an actionable error. Do not reintroduce Maven Resolver.

`--offline` makes no network requests and requires all locked artifacts or reproducible local inputs to be available. `--refresh` refreshes discovery metadata and retries retrieval of the same locked identity; it does not upgrade a lock. Explicitly requested snapshots resolve to a timestamped artifact and digest, both recorded. Cache artifacts by repository identity plus exact coordinates and content digest, with atomic writes and locking; GAV alone is insufficient for mutable snapshots or two repositories serving different bytes. Catalog metadata has a 24-hour TTL. Local-directory inputs are fingerprinted; shared automatic replay requires a project-relative checked-in source directory or a published artifact, not a machine-specific absolute path.

**Trust and filesystem contract.** External packs are CLI-only trusted code. Catalog-backed packs require the indexed checksum; direct coordinates without one display their provenance and observed digest, which is then locked (integrity on subsequent use, not proof of authorship). Before extraction and generator writes, reject absolute/traversing paths, symlink escapes, duplicate normalized destinations and excessive entry count/expanded size. Apply equivalent checks after token substitution and to local directories. New-project generation never overwrites an existing file, including in-place generation. These guards protect the extraction/output mechanisms; arbitrary GSP code is not confined by them, and dry run executes that code. Hosted Forge never fetches or compiles an external pack on request. Document these precise guarantees and limitations in `THREAT_MODEL.md`.

Implemented: direct coordinates in the create commands of the Forge CLI. An entry of `--features` with a colon is the coordinates of a plugin, `group:artifact:version`, with the parts held to the characters a descriptor allows for coordinates and the version to one version (no range, no `+`, no `latest.*`, `LATEST` or `RELEASE`; a `-SNAPSHOT` version is allowed); anything else with a colon is a mistake in the command line (exit code 2). `PackArtifacts`, in `grails-forge-cli` so that `grails-forge-web`, which depends on `grails-forge-core` alone, cannot reach it, takes the main jar by its Maven-layout address with the HTTP client of the JDK (no resolver and no new dependency), with no POM, no classifier and no dependency of the plugin asked for, copies it to a temporary file that is deleted after `PackLoader.fromJar` has read it, and hashes the bytes it copies. The feature that is selected is the one of the pack, by the name of the pack; the same coordinates twice are one selection, and two plugins with one pack name or a pack named like a framework feature are refused as two directories are. The repositories are those of the repeatable `--feature-pack-repository` and no others when it is given; without it, those of `GRAILS_REPO_URL` in its order (`mavenLocal()` is the directory of `maven.repo.local` or `~/.m2/repository`) and then Maven Central. `repo.grails.org` and the snapshot repositories of a generated build are not asked. An address is `https`, or `file:` for a directory, without user, query or fragment; `http` is refused, at most 5 redirects are followed and each address is checked to be `https` before it is asked. A jar has at most 64 MiB, the `maven-metadata.xml` of a snapshot 256 KiB and a checksum file 1 KiB, each counted as it is read; the connection has 15 seconds, the answer 60 and one file 5 minutes. A repository that does not have the jar (no file, or HTTP 404 or 410) is passed over; every other failure ends the search without the next repository being asked, and the error is one line with the coordinates and each repository asked with its reason (not found, timeout, TLS failure, no connection, HTTP status, checksum mismatch, size), exit code 1, nothing created. The jar is checked against the `.sha256` of the repository, or the `.sha1` when there is no `.sha256`, and refused on a mismatch or when that file is not a checksum; `--feature-pack-sha256 group:artifact:version=<hex>` (repeatable) refuses a jar with another SHA-256 before it is opened, and is a mistake in the command line when it names coordinates that are not selected. The line that names the pack is `Feature pack <name> from <coordinates> (<repository>), sha256 <hex>`, followed by whether the SHA-256 was the one asked for and whether a checksum of the repository was matched or none is published, then the summary; a pack whose `dependencies` do not have the `group:artifact` it was fetched by gets a warning, and the dependency is not added for it. A snapshot is the build its `maven-metadata.xml` names for the main jar (`snapshotVersions`, else `snapshot` timestamp and build number), read with document types refused, and has to be a build of that version; a repository without that file gives the jar under the name of the version. `Pack.getArtifact()` (`PackArtifact`: name, coordinates, repository, file name, SHA-256, kind of checksum matched, whether pinned) carries this, and `grails-forge-cli.yml` records name, coordinates, repository, file name and SHA-256 under a new key `featurePacks`, which `CodeGenConfig` ignores. Not done: the catalog and names from it, `~/.grails/forge.yml` with repository mappings, content filters, credentials, the `settings.xml` reader and proxies beyond the JVM's own, loopback `http` test servers, `--offline` and `--refresh`, the artifact cache (every use downloads), `.grails/project.lock` (the record above is not a lock and nothing is checked against it later), signature verification, pack requirements followed to other artifacts, and the same options on `feature add`.

<a id="project-state"></a>
### 2.4 Project state: `.grails/`

```
.grails/
  project.yml      committed; project intent, type, options and requested features
  forge.lock.yml   committed; exact resolved generation inputs and baseline index digest
  baseline/       committed; pristine generated outputs, never copied from user-edited files
    index.yml     paths, content digests, text/binary kind and executable mode
    files/        generated file tree, excluding .grails state itself
  cache/          ignored; derived command metadata and compiled templates
  transactions/   ignored; staged updates, recovery journals and backups
  run/            ignored; lifecycle pid, port and stopping markers
  history         ignored; interactive console history
```

```
format: 1
grailsVersion: 9.0.0
applicationType: web
name: shop
defaultPackage: com.acme.shop
options: { jdk: 21, servlet: tomcat, data: hibernate7, reloading: devtools }
generationState: tracked
requestedFeatures:
  - name: gsp
  - name: grails-spring-security
  - name: acme-audit
    coordinates: com.acme:acme-audit-grails
    version: 2.3.0
excludedFeatures: []
parameters: {}
```

The manifest records user intent; the lock records the fully resolved selection, including defaults and requirements. Lock the exact generator release/build identity, target Grails/BOM metadata, template API, normalized options and output OS/line-ending policy, preset and pack artifacts/digests, non-secret parameters and template override digests. `grailsVersion` alone is not a generator identity, especially for snapshots. Do not include credentials, absolute user paths or runtime secrets in any committed generation state.

The baseline stores actual rendered bytes and modes so a fresh clone has the same merge ancestor even when an artifact disappears or the author changes a template. It contains only generator-owned outputs; it is not a backup of arbitrary application files. The storage cost is intentional: some generated source and binary resources are duplicated in Git. The baseline index and lock make tampering, missing files and manual state drift detectable. Generation is deterministic (no current timestamps or ambient machine inputs in managed output); non-repeatable packs cannot participate in automatic `feature add`.

The CLI validates the manifest/lock/baseline as a set. A manual change to generation inputs, the framework version or an override template does not silently update the baseline. V1 `feature add` requires the exact recorded generator identity; it directs the user to that CLI version on mismatch and does not automatically download and execute an old generator. Generator upgrades and changes to existing pack versions are separate migration/reconciliation operations, not side effects of adding a feature. All generated projects, including hosted Forge ZIPs, receive this state for their bundled features.

Implemented: writing and reading the state when an application is generated (`org.apache.grails.forge.core.state`). `DefaultProjectGenerator` renders every file of the application once, writes those bytes, and then writes, for every output (the directory of a create command, the ZIP of the hosted Forge, the map of a preview or a diff): `.grails/baseline/files/<path>` with the same bytes, `.grails/baseline/index.yml`, `.grails/forge.lock.yml` and `.grails/project.yml`, and with them `.grails/.gitattributes`, which is `* -text`: without it a Git that converts line endings (`core.autocrlf`) commits or checks out `gradlew.bat` of the baseline with other bytes than the index has the digest of. The reader does not ask for that file. The manifest has `format: 1`, `grailsVersion`, `applicationType` (`web`, `rest-api`, `web-plugin`, `plugin`), `name`, `defaultPackage`, `options` (`jdk`, `servlet`, `data`, `reloading`, written as the create commands take them), `generationState: tracked`, `requestedFeatures` (the features asked for by name, by their canonical names, sorted, each `- name:` with optional `coordinates` and `version`), `excludedFeatures` (the same shape, always empty) and `parameters` (always empty). The lock has `format: 1`, `manifest.sha256` (the digest of the bytes of the manifest), `generator.name` (`grails-forge`) and `generator.version`, `grailsVersion`, `templateApi`, `applicationType`, `options`, `operatingSystem` when the generation had one, `lineEndings: preserve` (the files have the line endings of their templates on every operating system), `features` (every feature of the application in the order it is applied, each with `name` and `source`: `framework`, `bundled-pack` or `pack`; one of `pack` can have `origin`, `coordinates`, `version` and `sha256`, which the reader takes and none of which is written today: the origin a pack knows is a path of the machine it was read on, which is not committed), `parameters` and `baselineIndex.sha256`. The index has `format: 1` and `files`, sorted by path, each with `path`, `sha256`, `kind` (`binary` when the template says so, as those of the wrapper scripts do, or when the content is not UTF-8 text or has a NUL, as an icon has that its template does not call binary; `text` otherwise) and `executable`; the copies under `files/` are never executable themselves. All three are YAML in block style, UTF-8, with line feeds and no time in them, so the same inputs give the same bytes; for that `PropertiesTemplate` no longer writes the time of day `java.util.Properties` starts a file with, and ends its lines with a line feed on every platform. A feature that writes below `.grails`, or a file whose path the index cannot name (the rules of a pack's destination, `PackDescriptorParser.isPathBelowRoot`), fails the generation before anything is written, and so does generating where `.grails/project.yml`, the lock or the index already is (`--inplace` over a generated project). With `--inplace`, a file that exists is kept as before and the baseline has the file as the generator rendered it: the existing file is to the baseline what an edited file is. `ProjectState.read(Path)` reads the three as a set and refuses, with a `ProjectStateException` of one line: no state, a part of it missing, a format other than 1, a lock whose `manifest.sha256`, `applicationType` or `grailsVersion` is not the manifest's, an index whose digest is not the lock's, a file or a directory on the way that is a symbolic link, a file over its bound (manifest 256 KiB, lock 1 MiB, index 8 MiB, 20,000 files, 1,000 features), text that is not UTF-8, YAML with an alias, an anchor, a tag, a duplicate key or more than six levels, a key it does not read, a value that is not what its key takes, and an index path that is absolute, has `..`, is below `.grails` or `.git`, or is there twice for a file system that ignores case. `ProjectState.verifyBaseline(Path)` checks the copies against the index: a missing, changed, oversized (16 MiB a file, 256 MiB together) or unlisted file is refused. The generated `.gitignore` ignores `.grails/cache/`, `.grails/transactions/`, `.grails/run/` and `.grails/history` and nothing else of `.grails`. `grails-forge-cli.yml` is still written, unchanged, and is in the baseline like any generated file; the code generation commands (`CodeGenConfig`) read the application type, default package, reloading and features from `.grails` when `.grails/project.yml` is there, and from `grails-forge-cli.yml` when it is not or when the state is refused, in which case the reason is a warning. The preview shows the manifest and the lock and leaves out `.grails/baseline/**`; `FeatureDiffer` leaves the baseline out as well and shows the difference of the manifest and the lock. `BundledPackGoldenSpec` ignores `.grails/**` as it ignores `grails-forge-cli.yml`; its golden files are unchanged.

Not done: nothing reads the state for more than the code generation commands: no `feature add`, `feature diff` against the baseline, `project init`, `project migrate`, replay, merge, transaction or recovery, and nothing writes `generationState: untracked`. The lock has no build identity of the generator beyond its version, so two builds of one snapshot are one generator to it; it has no BOM metadata beyond `grailsVersion`, no preset, no template override digests, and for an external pack no coordinates, version or digest, which the pack does not know yet. The options of the manifest are the options after the defaults were applied: what the user left out is not told from what the user chose. A `.gitattributes` among the generated files that sets `text` for a path is copied to `.grails/baseline/files/.gitattributes` and wins there over the one of `.grails`; no feature generates one today. `MapOutputHandler` still discards binary content, so a preview and a diff have no binary baseline to show either. Step 5's removal of `grails-forge-cli.yml` and the move of the legacy `CodeGenConfig` parsing into migration are not done.

Keep `grails.codegen.defaultPackage` in `application.yml` because runtime code reads it. In 9.0, `grails.profile` no longer drives runtime Spring-profile injection and new projects omit it; `.grails/project.yml` is tooling metadata, not runtime configuration. The 8.1 runtime and generator retain their existing keys and profile dependency.

**Migration to 9.0.** `grails project migrate --to 9.0 --dry-run` performs file-only preflight and displays a migration plan; it must work even when the old `profile` declaration prevents the new Gradle plugin from configuring. Applying that plan uses the same staging, conflict and recovery mechanism as section 2.6. Supported inputs and limits are explicit:

- Read `grails-forge-cli.yml` or legacy `grails-cli.yml`, known built-in profile names, declared framework version and recognizable build dependencies as evidence. A build-script scan is not evaluation of arbitrary Groovy and cannot establish a complete feature set. Report missing/ambiguous options, custom profile coordinates, convention-plugin indirection and multi-project ownership; require explicit mappings or manual edits instead of guessing.
- Remove a recognized profile dependency and obsolete marker only for an explicit 9.0 migration after the replacement metadata is staged. Do not modify an application that remains on 8.1. Version bumps, unrelated dependency upgrades and custom skeleton conversion remain guided upgrade work, not implicit actions of this command.
- Inventory the effective old Spring profile, including implicit `web`. Profile-conditional beans and configuration may depend on it. Produce guidance for deliberate Spring-native activation or removal; do not silently discard it or claim that adding `spring.profiles.include` is timing-equivalent to the old postprocessor. Fixtures must cover `@Profile`, profile-specific configuration and pre-existing explicit profiles. Ambiguous/dynamic activation needs a manual migration decision before completion.
- Legacy metadata does not prove a pristine Forge baseline. Mark migrated projects `generationState: untracked`; lifecycle commands and codegen work, and feature diffs are advisory, but automatic `feature add` is unavailable. `project init` establishes a 9.0 generation baseline from explicitly chosen type/options/features/preset and a review of the full generated-versus-current diff. It does not pretend current edited files are pristine generator output. Adoption records its origin and establishes the inputs for future three-way merges without replacing application source.
- Custom profile authors convert supported defaults, dependencies, configuration and added files into a preset plus feature packs, and commands into the command extension API. Unsupported inherited file replacement/multi-project layouts receive a documented manual recipe. Test both automatic built-in cases and refusal/diagnostics for unsupported cases.

<a id="console"></a>
### 2.5 The console after profiles

The catalog combines the following sources. Built-in names are reserved; extension collisions produce deterministic diagnostics with origin information instead of depending on classpath iteration order. Namespaced extension IDs allow explicit selection where short names collide. Retained script overrides require an explicit supported override declaration rather than silently shadowing a built-in.

1. **Built-in lifecycle commands**, moved from profile scripts into Picocli commands over the existing Tooling API services: `run-app`/`stop-app` (retain HTTPS/port/debug options and process handling), `test-app`, `compile`, `clean`, `assemble`/`war`/`package`, `console`, `shell`, `dependency-report`, `run-command`, `run-script`, `stats`, `bug-report`, and plugin-aware `publish-plugin`/`package-plugin` task adapters. Preserve test-app's unit/integration selection, filters and continuous/cancellation behavior rather than mapping it to `integrationTest` alone.
2. **Gradle passthrough**: `grails gradle <args>` with completion from `ReadGradleTasks` (exists). Fix: arguments are passed as one string today (`GradleCommand.groovy:62-65`).
3. **Plugin `ApplicationCommand`s** from `-cli` companions through `ApplicationContextCommandFactory` and `GradleTaskCommandAdapter` (exists, profile-free apart from the `ProfileCommand` interface). Fix the two known gaps: legacy Grails 7 commands are listed but have no named Gradle task (`GrailsCliGradlePlugin.groovy:411-414`), so route them to `runCommand`; app-local `grails-app/commands` are invisible because only `grailsCliClasspath` jars are scanned (`:450-480`).
4. **Artefact codegen**, one implementation in Forge: retain `create-controller`, `create-service`, `create-domain-class`, `create-taglib`, `create-interceptor`, `create-job` and `add-property`; add `create-unit-test`, `create-integration-test`, `create-functional-test`, `create-restful-controller`, `create-domain-resource`, `create-command` and `create-script`. Pack artefacts register dynamically from selected, locked packs. Template lookup is app `src/main/templates/<path>`, then the owning pack, then Forge's built-in template. An override is explicit and fingerprinted; it is not permission for unrelated packs to overwrite one another. Extend `install-templates` to copy these templates into the application.
5. **Script commands** (`src/main/scripts/*.groovy`, `META-INF/commands/*`, `GroovyScriptCommand`): retain the documented, profile-free DSL in 9.0 with deprecation, and remove it no earlier than the next major. Replace command chaining through `profile.hasCommand` with catalog dispatch and profile-template lookup with the new lookup order. Re-home the necessary script API types so the removed `grails-shell-cli` artifact is not a transitive requirement. Scripts that directly use `Profile` or custom profile inheritance need source migration; do not promise binary compatibility for those internals. Convert the inventory's script-only commands: Geb `create-functional-test`, Quartz `create-job`, `s2-create-acl-domains`, `s2ui-create-challenge-questions`, `s2ui-override`, dbmigration `dbm-create-changelog` and `dbm-changelog-to-groovy`, plus `sql-tail` if its script implementation is present on the implementation branch. Track explicit names, not the earlier inconsistent command count.
6. **Catalog services**: help, completion, abbreviation expansion (`rA` for `run-app`), "did you mean", and the legacy-command hint move from `AbstractProfile.groovy:361-499` into a profile-free `CommandCatalog`.

Project detection walks upward to the nearest `.grails/project.yml`, legacy marker or `grails-app`, stopping at a filesystem/repository boundary. The manifest identifies the owning Gradle project in multi-project builds; never silently execute a sibling project's task. Legacy markers permit migration diagnostics, not automatic adoption of an invented baseline.

**Historical command execution model.** These observations describe the reviewed pre-unification implementation. Profiles couple the dispatcher to generation metadata even when the command itself does not need a profile.

| Where a command is defined today | Author | Runs where | Needs a profile? |
| --- | --- | --- | --- |
| Profile jar `commands/*.yml` or `.groovy` | profile author | shell JVM | yes, by definition |
| App `src/main/scripts/*.groovy` | app developer | shell JVM | only because the shell dispatches every in-project command through the profile object (`GrailsCli.groovy:385`) |
| Plugin jar `META-INF/commands/*` (from `src/main/scripts`) | plugin author | shell JVM, found on the app's test runtime classpath | same |
| `ApplicationCommand` in a plugin's `-cli` companion (`src/cli/groovy`) | plugin author | one Gradle `JavaExec` task per command, created by `GrailsCliGradlePlugin`; the shell wraps each one in `GradleTaskCommandAdapter` | no on the Gradle path; the shell listing still goes through the profile |
| `ApplicationCommand` in the app (`grails-app/commands`) | app developer | `./gradlew runCommand -Pargs=...` only; no task, invisible to the shell | no |
| Legacy `grails.dev.commands.ApplicationCommand` with `legacyCommandSupport` | Grails 7 plugin | `runCommand` only; the shell lists it but calls a task that does not exist | no |

The runtime half of this system is `grails-console`, which the Gradle plugin auto-provisions into `grailsCli` because every command task's main class comes from it. `GrailsApplicationContextCommandRunner` (per-command tasks and `runCommand`) boots the application through `DevelopmentGrailsApplication`, which swaps the context class to `DevelopmentWebApplicationContext`: a `GenericWebApplicationContext` over a `MockServletContext` pointed at `src/main/webapp`. The command gets the full context, plugins, GORM, data sources and `BootStrap`, unless it carries `skipBootstrap = true` (the `SkipBootstrap` trait, currently copied into scaffolding and spring-security). No Tomcat, no port. `GrailsApplicationScriptRunner` (`runScript`), `GrailsSwingConsole` (`console`) and `GrailsShell` (`shell`) boot the same way with console-flavoured contexts; the profile commands named `console` and `shell` only invoked those Gradle tasks.

The inventoried `generate-open-api`, `dbm-*`, `schema-export`, `url-mappings-report`, `config-report`, `generate-*` and `s2-*` commands use an application context rather than an embedded server. `run-app` drives `bootRun` through the Tooling API, with asynchronous operation, readiness checks, PID handling and HTTPS setup; `stop-app` manages that process. `test-app` delegates to test tasks, whose JVM may start a server. Preserve these distinctions rather than introducing a second server-starting application-context runner.

**Target command extension contract.** Use one execution target per descriptor rather than two booleans with ambiguous combinations:

| Target | Execution and available services | Examples / Gradle exposure |
| --- | --- | --- |
| `console` | CLI host, no project application context; terminal, cancellation, project metadata, `GradleRunner`, readiness and running-process services | lifecycle orchestration, `sql-tail`, Forge codegen (generator context loaded on demand), migration; no per-command Gradle task that could launch a nested build |
| `standalone` | selected compatible command in the CLI, or an isolated plain `JavaExec`; project paths, declared configuration inputs, output and cancellation only | opt-in pure file-generation commands; may have a per-command task; no `GradleRunner` or implicit application boot |
| `application` | Gradle `JavaExec` with `GrailsApplicationContextCommandRunner`, mock servlet and full application context | existing `ApplicationCommand` default, including `dbm-*`, schema and mappings reports; named task or `runCommand` |
| `gradle` | Tooling API passthrough to a task, preserving argument tokens and cancellation | `gradle`, `compile`, `clean`, `assemble`, `war`, `test-app` |
| `session` | dedicated application JVM with appropriate stdin/stdout handling | `console`, `shell`, `run-script`; script execution need not be interactive |

The new profile-free extension API belongs to the CLI contract layer; Tooling API and terminal implementations stay in the CLI host. Existing `ApplicationCommand.handle(ExecutionContext)` implementations remain `application` commands unless explicitly adapted to the new contract. Do not infer safe in-process execution merely from `SkipBootstrap`: it suppresses `BootStrap`, not the application context. An old companion without new metadata takes the context-bound compatibility path, never an implicit fast path.

The console services supply project/base/build paths, declared configuration access, terminal output, cancellation registration, synchronous/asynchronous `GradleRunner`, readiness checks and `RunningApplication`. Configuration access does not pretend to be a fully booted Spring environment: values requiring application evaluation go through an application command. A console command that needs a context delegates to `runCommand` instead of booting the project inside the host. A server-oriented command starts or attaches through lifecycle services, records ownership and stops only a process it owns; cancellation must not kill an unrelated pre-existing server.

Use project-scoped, disposable extension loaders with shared CLI API types; restore the thread context classloader after execution and dispose loaders on project/classpath refresh. Isolate incompatible Groovy/application stacks in their appropriate worker JVM instead of merging them into the Forge application's classpath. Define and test linkage diagnostics and cleanup of listeners, threads and task handles. Keep a Tooling API connection per project session, close it on exit or refresh, and ensure a failing extension does not make built-in help unavailable. Runtime discovery currently instantiates commands (`ApplicationContextCommandRegistry`); that is a behavior to replace for metadata listing, not evidence of zero-cost execution.

**Discovery and JSON catalog.** Extend build-produced companion metadata with schema version, canonical command ID, name/aliases, description, argument/option schema, completion kind, execution target, API compatibility and origin. Read descriptors without loading implementation classes. Generate app-local command metadata from compiled `grails-app/commands`; invalidate cached metadata when source or classpath changes. Initial `help --json` uses built-ins, locked pack descriptors and valid cached companion/app metadata without invoking Gradle. It reports `complete: false` plus diagnostics when discovery is unavailable. An explicit refresh may resolve dependencies or compile app-local commands; failures leave the built-ins usable and are reported structurally. JSON stdout contains one versioned document, with ordinary logs on stderr. Refresh interactive completion from the same catalog after successful mutations.

**Lifecycle options.** A companion descriptor or selected pack may declare `runAppOptions`: name, type, description, default and the Gradle/system property mapping. Names are validated against built-ins and other providers; collisions fail with both origins. `run-app --sql-tail` is the reference use case. Translate values once through the existing property-forwarding rules and test the resulting application property; do not expose the doubled `grails.*` transport prefix as the public option contract. Help and completion use the declarations without instantiating commands. Pass-through `-D` and `-P` remain available.

Housekeeping: consolidate `SkipBootstrap` in the CLI contract layer, route legacy commands to `runCommand`, and surface app-local commands. Test supported already-compiled companion artifacts in addition to recompiling fixtures; source compatibility alone does not establish binary compatibility across Groovy/Grails versions.

**IDE integration.** The IntelliJ plugin is now `apache/grails-intellij-plugin` (donated by JetBrains, imported from `intellij-obsolete-plugins`), so its CLI coupling is work this plan schedules rather than a risk to confirm. What it does today, from the source:

- Commands run in a JVM the IDE starts itself, main class `org.grails.cli.GrailsCli`, classpath = the `org.apache.grails:grails-shell-cli:<grailsVersion>` artifact resolved through a detached Gradle configuration by the plugin's own tooling model builder (`libs/gradle-tooling/.../GrailsModuleModelBuilderImpl.java:54-78`, `plugin/.../runner/impl/Grails3GradleCommandExecutor.java:54`), with `--plain-output` appended. The `grails` launcher and `grails-forge-cli` are not involved.
- The command list is hard-coded: 34 names from the profile era (`bug-report`, `clean`, `compile`, `console`, `create-*`, `dependency-report`, `generate-*`, `gradle`, `install`, `install-templates`, `list-plugins`, `plugin-info`, `run-app`, `schema-export`, `shell`, `stats`, `stop-app`, `test-app`, `url-mappings-report`, `war`) in `plugin/.../commands/Grails3CommandProvider.java:31-70`.
- The Grails run configuration is `run-app` through the same executor (`runner/GrailsRunConfiguration.java:70`), so the IDE's Run button depends on the base profile's `run-app` script and its pid file.
- Project detection is the `grails-app` directory (`structure/GrailsApplicationManager.java:167`).
- The new-project wizard calls the hosted Forge API (`https://latest.grails.org`: `/select-options`, `/versions`, `/application-types/{type}/features`, `/create`) in `feature/forge/GrailsForgeModuleBuilder.kt:67-221`.
- The plugin's own `IMPROVEMENT-PLAN.md` section 2.6 already intends to move the run configuration to Gradle `bootRun`, rebuild the command layer from profile YAML plus forge-cli commands, and keep the wizard on the hosted API with an offline fallback via `grails-forge-cli`. The profile half of that plan is superseded by this one.

What changes on the Grails side so the plugin can follow:

1. *A stable programmatic entry point.* The proposed 9.0 contract is `org.apache.grails:grails-cli:<grailsVersion>` with a new `org.apache.grails.cli.GrailsCli` main class (distinct from the old `org.grails.cli.GrailsCli`). Keep the regular Maven artifact resolvable with its dependency metadata; the distribution may also supply its shaded artifact. The launcher supports the help-only fast path, `--plain-output`, `--non-interactive` and documented exit codes. The IntelliJ release selects the old shell contract for 8.1 and the new contract for 9.0. Test both project versions in that release; compatibility is not automatic merely because the artifact version varies.
2. *A machine-readable command catalog.* Use the versioned catalog above instead of hard-coded names, including its completeness and diagnostics fields. Unsupported descriptors or unavailable app-local compilation do not disable the IDE's built-in run/help actions.
3. *Project detection and type.* The plugin reads `.grails/project.yml` first (application type, default package, features), falling back to `grails-app`.
4. *Run configuration.* Use Gradle `bootRun` directly, as the plugin's plan proposes, while also testing CLI `run-app --non-interactive`. Both pass the same configured lifecycle-state location under `.grails/run/` on 9.0. Coordinate the PID writer, DevTools restart handling, port readiness and stop ownership; changing only the CLI's read path would break IDE launches. The 8.1 path remains `build/`.
5. *Wizard.* Retain the hosted API workflow for official features, with compatibility tests for the 9.0 type/feature metadata and generated project state. External packs and `feature add` remain CLI operations the IDE may surface through the same launcher.

Sequencing: the plugin release is lock-step with 9.0.0, which is why step 5 lists it.

<a id="feature-add"></a>
### 2.6 `feature add` on an existing app

Existing `FeatureDiffer` is presentation infrastructure: it compares a default project with a requested selection, recreates baseline options without preserving GORM/servlet choices, and uses `MapOutputHandler`, which discards binary contents. Extend it for arbitrary typed output trees; do not treat applying a unified patch as a three-way merge implementation.

The transaction is:

1. Read and validate the manifest, lock and committed baseline. Require `generationState: tracked`, matching generator identity and unchanged recorded inputs. A missing/corrupt baseline or stale lock fails before writing. Adding an already-selected identical feature is a no-op; requesting another version is an explicit upgrade request outside v1 `feature add`.
2. Re-render the locked selection with all recorded options, parameters and overrides. Compare bytes and modes with the committed baseline **A**. Abort on nondeterminism or unavailable locked inputs; never silently substitute current defaults, another artifact or a new generator version. Resolve only the new requested feature and its requirements, preserving existing selections and locks, to generate proposed tree **B**.
3. Compare **A**, current working-tree files **C**, and **B**. Use a true base/current/proposed text-merge engine (for example JGit's three-way merge APIs); use java-diff-utils for readable patch output. Explicitly test the library's conflict behavior. B does not contain the user's unrelated edits, so those edits must remain in C's merged result.
4. Plan every path before applying anything. A new B path already present in C is a collision unless content and mode are identical and adoption is recorded. User deletions, file/directory changes, symlinks, overlapping edits and binary changes on both sides are conflicts. Binary content and executable modes use whole-value comparisons; never decode binary bytes as text. Preserve unchanged user line endings and honor the locked policy for generated additions.
5. V1 is additive: reject implicit removal of an existing selected feature or generated path, and report the proposed diff instead. `--dry-run` and `--diff` render the plan without applying project changes. On any conflict return a nonzero status with structured path/reason diagnostics and rejected changes; do not write successful hunks or update the manifest. Rendering still executes trusted templates and may populate caches, as section 2.2 explains.
6. For a conflict-free apply, acquire the project mutation lock, verify the recorded C preimages are still current, and stage all file changes plus the new manifest, lock and baseline. The new baseline is pristine **B**, not the merged user working tree. Use per-file atomic replacement plus a recovery journal/backups for the multi-file operation; ordinary filesystems do not provide an atomic project-wide rename. Recheck affected preimages before replacing them and roll back ordinary write failures only where content still matches what the transaction wrote. Detected external edits become recovery conflicts, not overwrite targets. The lock serializes CLI mutations; it cannot provide transaction isolation against arbitrary editors writing during the apply window. An interrupted transaction is recovered or explicitly resolved before another mutation; never report success for a partially installed feature.
7. Complete the journal only after all project and generation-state files are installed, release resources, and refresh the command catalog. A second identical invocation makes no changes. The CLI performs no Git commit and does not require Git to implement the merge.

`feature diff` supports comparing an arbitrary recorded selection with proposed additions. For an untracked migrated project it is advisory and clearly says that there is no verified ancestor for automatic application. Feature removal and pack/generator upgrades are deferred; do not advertise an executable inverse-diff removal command in v1.

<a id="hosted"></a>
### 2.7 Hosted Forge and the UI

- The hosted service hosts the framework's own features only. External packs, the plugin index and `feature add` are CLI capabilities; the web API and `apache/grails-forge-ui` gain no plugin search and load nothing from outside their build.
- Official features converted to bundled packs retain their generated application output through golden tests; the ZIP additionally includes the 9.0 manifest, lock and baseline. Preview/diff presentation must explicitly handle that metadata and avoid recursively showing the baseline copy as application output. Keep existing routes and version slots; test response compatibility rather than claiming no API changes are necessary. `FeatureDTO` may expose `documentation` and stable string group IDs through a versioned/additive contract.
- The hosted 8.1 slots retain their released behavior. The 9.0 UI/wizard exposes new official application types/options and the new generation semantics; it does not expose external presets, catalog resolution or `feature add`.

<a id="gaps"></a>
## 3. What profiles offered and what replaces it

| Capability | Before unification | 9.0 target | Status |
| --- | --- | --- | --- |
| `create-app` with profiles | shell plus Aether, default path | Forge | replaced |
| Company or third-party skeletons (`--profile g:a:v`) | inheritable profile jar | `--preset g:a:v` for supported defaults/features/added files; inherited output replacement and multi-project skeletons require manual conversion | scoped replacement, removal gate |
| `create-profile` and the seven profile-authoring commands | profile profile | Not in v1; `grails create-feature-pack <name>` scaffolds a pack skeleton later | dropped, small gap |
| `list-profiles`, `profile-info`, `list-plugins`, `plugin-info` | shell; the last two scrape the legacy repository | `feature list`, `feature info`, `plugin search`, `plugin info`, backed by `grails-plugins-index.json` | replaced |
| `--features` replaces defaults; unknown names warn | profiles | Forge semantics: additive, unknown names fail. Add `--exclude-features` so a default such as a Geb variant can be dropped | gap, new flag |
| `rest-api-plugin` application type | profile only | Add to Forge (#14932) | planned gap |
| Profile features without direct Forge equivalents | `less-asset-pipeline`, `rx-mongodb`, rest-api `security`, `maven-publish` | asset-pipeline and Spring Security REST need 9.0-compatible packs or an explicitly documented unsupported case; legacy `rx-mongodb` generation is retired; publishing defaults use grails-publish | named parity/retirement decisions before cutover |
| Lifecycle commands (run-app, test-app, war, ...) | base profile scripts | Console built-ins | replaced |
| Plugin publication commands | `publish-plugin` → `publish`, `package-plugin` → `jar`, legacy `install` → `install` | Keep the first two as plugin-aware Gradle adapters; replace obsolete `install` with a migration hint to `grails gradle publishToMavenLocal` | explicit major-version change |
| Artefact `create-*` with web/rest-api/test variants | profile YAML and templates | Forge commands enumerated in section 2.5, with type-specific templates and auxiliary outputs such as controller view directories | parity fixtures required |
| rest-api scaffolding | `generate-all`, `generate-controller`, `generate-views`, `generate-unit-test`, `generate-functional-test` scripts, including gson output | Extend scaffolding/JSON views commands and templates to cover every named operation and view type; choose one canonical command per operation | removal gate, not just a views-template change |
| `src/main/templates` override of artefact templates | shell lookup | Forge codegen honours it first | replaced |
| App scripts and plugin script commands | shell DSL, sometimes direct profile APIs | Profile-free documented DSL retained/deprecated; context-bound commands use `ApplicationCommand`, fast/orchestrating commands use the new extension API | supported subset and migration documented |
| `~/.grails/settings.groovy` profile repositories and defaults | shell | `~/.grails/forge.yml` repositories and catalogs; proxies from JVM properties and `settings.xml` | replaced |
| Post-create `instructions` | `profile.yml` (unused in-repo) | Pack `instructions` | replaced |
| YAML `execute` / `gradle` command steps | profile YAML | Not needed: Gradle tasks and `ApplicationCommand`s | dropped |
| Welcome page shows `grails.profile` | `index.gsp:141-144` | Show `info.app.*`; the type lives in `project.yml`, which the runtime does not read | adjust |
| `grails.profile` as an active Spring profile | runtime, default `web` | Remove injection; migration explicitly addresses conditional beans/configuration and the implicit default | behavior change with upgrade fixtures |
| IntelliJ Grails plugin | old shell artifact/main class and hard-coded commands | Version-selective launcher contract, JSON catalog, manifest detection, coordinated run-state paths and hosted wizard compatibility | coordinated 9.0 release |
| `profile` Gradle configuration | default dependency and tooling-model input | Remove configuration/resolution; file-only migration and actionable legacy-declaration diagnostics | 9.0 removal |
| Hibernate 7 BOM switch, servlet, JDK, exclusive groups, preview, diff, website | Forge only | Kept | gain |

<a id="removal"></a>
## 4. Removal inventory

All removals are on 9.0.x after their replacement gates. This is the October 13 inventory, not a claim that its line numbers and counts remain current. Re-scan on the implementation branch and classify each reference as production dependency, compatibility adapter, migration fixture or historical documentation; migration tests must retain representative old inputs.

**Forge** (`grails-forge/`): `GrailsProfiles` is replaced by project-state production and the retained runtime default-package setting. Write the baseline only after application rendering, excluding generation state from its own snapshot. Remove `Scope.PROFILE`, `Phase.PROFILE`, `GradleConfiguration.PROFILE`; move legacy `CodeGenConfig` parsing into migration; replace tests coupled to profile skeletons with owned golden fixtures. Remove Forge's own profile configuration, obsolete dispatcher comments and profile-specific build workarounds after checking their remaining consumers.

**Shell** (`grails-shell-cli/`): everything under `profile/repository/`, `Profile`, `AbstractProfile`, `ResourceProfile`, `FileSystemProfile`, `Feature`, `DefaultFeature`, `ProfileRepository*`, `ProfileRepoConfig`, `ProfileUtil`, the `Create*Command`, `ListProfilesCommand`, `ProfileInfoCommand`, `ArgumentCompletingCommand`, `io/GradleDependency`, their service-file entries, `GrailsCli` members `:87-89,129-133,195-201,260-271,307-311,324-332,386-394,518-534`; the Spring Boot CLI port and the maven-resolver dependencies (`grails-shell-cli/build.gradle:86-100`). Re-home `CompilerAutoConfiguration`, which the hibernate boot-plugins compile against (`grails-data-hibernate5/boot-plugin/build.gradle:50`). Rewrite rather than delete: `CommandRegistry.findCommands`, the four `CommandFactory` implementations, `GradleTaskCommandAdapter`, `HelpCommand`, `GroovyScriptCommand`, `TemplateRendererImpl`, `RenderStep`, `DefaultStepFactory`'s `ProfileCommand` gate (`:44-47`), `populateContextLoader` (keep the classpath part, drop `:571,582-584`).

**Gradle plugins** (`grails-gradle/`): `GrailsGradlePlugin.groovy:166,461-490,782-784`; `GrailsPluginGradlePlugin.groovy:138-140`; `GrailsClasspathToolingModelBuilder.groovy:35,51-56` and `GrailsClasspath.getProfileDependencies`; the `grails-profile` and `grails-publish-profile` plugins (`plugins/build.gradle:143-160`, `profiles/*`, `ProfileCompilerTask`; `GroovyScriptCommandTransform` stays while scripts are supported); `ProfileCompilerTaskSpec`; `BomPlatformFunctionalSpec.groovy:76-98` and its fixture `test-projects/bom-platform-default-dependencies/build.gradle:98`.

**Root build**: the seven `grails-profiles/*` modules (`settings.gradle:465-485`), `grails-spring-security/rest/testapp-profile` (convert its features to packs or test fixtures), `gradle/publish-root-config.gradle:172-191`, `build.gradle:76,163-165` (`profileProjects`, unused), `PublishPlugin.groovy:58,146-151,191-197`, `gradle/rat-root-config.gradle:73-76,161-166`, `settings.gradle:661-666` and `end-to-end/settings.gradle:132-137` (DirectoryScanner workaround), `grails-bom/base/build.gradle:78-94` and the `forcedGroupPrefixes` entries in all five BOMs, `etc/bin/verify-jar-artifacts.sh:208-236`, `etc/bin/reset-verify.sh:47`, `.github/dependency-graph/external-references.yml:42-48,197-203` (the purls are wrong today anyway), `RENAME.md:164-182`, `etc/bin/rename_gradle_artifacts.sh:305-347`, `INSTALL:98`.

**In-repo consumers**: profile dependencies in example builds; `grails.profile` keys in application configuration; plugin-descriptor `profiles` declarations and Forge's plugin template. Audit active-profile behavior before removing configuration keys: they are not harmless metadata while the runtime injection exists. Preserve representative legacy inputs in migration fixtures.

**Docs** (`grails-doc/src/en/guide/`): the whole `profiles` chapter (`toc.yml:91-106`, twelve pages) is replaced by a "Feature packs and presets" chapter; `commandLine.adoc:20-71,112-122`, `interactiveMode.adoc:19-41`, `creatingCustomScripts.adoc`, `reusingGrailsScripts.adoc` and `gradleBuild.adoc` are deleted or rewritten; `creatingProject.adoc:19-37`, `creatingCustomCommands.adoc:21-69,219-272` (the `executionContext.environment` example at `:248` is wrong today), `gradleDependencies.adoc:27,61`, `gradleTasks.adoc`, `gradlePlugins.adoc:63-64`, `downloadingAndInstalling.adoc:24-93`, `creatingAnApplication.adoc`, `aHelloWorldExample.adoc`, `usingInteractiveMode.adoc`, `creatingArtefacts.adoc`, `generatingAnApplication.adoc`, `REST/restProfile.adoc`, `plugins/creatingAndInstallingPlugins.adoc:390-466`, `plugins/repositories.adoc:21-40`, `plugins/providingBasicArtefacts.adoc:21-68,127-141`; the 47 command reference pages (five are profile-only); `index.adoc:736` includes a path that does not exist. `GRAILS_PREFERRED_SHELL` and the wrapper environment variables are undocumented today and need a home in the new chapter.

<a id="phases"></a>
## 5. Delivery

The release decision is settled: **all steps after the completed Grails-application conversion target 9.0.x**. Intermediate development commits may coexist with old code on that branch, but the release has one generator and one command tree. Do not backport these new contracts or removals to 8.1. Implementation may proceed incrementally; profile removal is gated by migration, command coverage and distribution/IDE readiness, not by a calendar estimate.

### Step 0 — completed conversion and implementation baseline

PR #16551 merged on 2026-10-14 into 8.1.x, and the reviewed 9.0 branch contains the conversion. This is historical prerequisite work, not an outstanding merge. Before implementation, record the selected 9.0 commit, run the Forge build/tests there and refresh the inventory of old commands and profile consumers.

**Exit:** a reproducible 9.0 baseline and command/capability matrix; the 8.1 conversion remains the only part of this unification effort assigned to that line.

### Step 1 — selection, contribution and template contracts (9.0.x)

Implement canonical feature/group IDs, deterministic selection, dependency closure/cycle/conflict rules and the versioned descriptor/template API. Add the pack adapter, source-capable scoped GSP engine/cache, local-directory mode, exclusions, provenance-aware contribution merging and dynamic codegen descriptors. Move central build-template branches into their owning features and remove the `Dependency.extension` hook that was never rendered. Convert a small proof set (Quartz, cache-ehcache, Asciidoctor, web-console), adapting their template models as required.

**Exit:** mixed compiled/pack selection passes the edge-case matrix; converted features retain golden application outputs; build templates contain structure rather than feature-name branches; source compilation and generated-build checks pass on the declared supported stack. Hosted rendering remains bundled-only.

### Step 2 — pack/preset distribution and authoring (9.0.x)

Extend `grails-plugins-metadata` for versioned pack metadata, exact main-JAR checksums and pack-only presets. Implement CLI discovery, direct coordinates, repository routing, compatibility filtering and content-addressed caches. Add Grails Gradle authoring support with isolated check compilation/generation workers and configuration-cache tests. Publish synthetic plugin and company-preset fixtures into `build/local-maven` and exercise both catalog and direct-coordinate selection. Include the complete preset schema and precedence rules.

**Exit:** fixtures generate/build online from local repositories and offline from locked inputs; the main-JAR digest matches what the index records; private mappings, timestamped snapshots, incompatible APIs and checksum mismatches behave as specified. No live public catalog is required for deterministic tests. No external pack reaches hosted Forge.

### Step 3 — project state, migration and feature transactions (9.0.x)

Implement manifest, lock and baseline writers/readers, typed byte-preserving output trees, `project init`, file-only `project migrate --to 9.0`, arbitrary-selection diffs and transactional `feature add`. Add `.gitignore` entries only for machine-local state. Existing markers are migration inputs; new 9.0 projects write only the new state. Exercise built-in 8.0/8.1 profile and Forge projects, explicit custom-preset mappings, untracked/adopted baselines and active-Spring-profile migration guidance.

**Exit:** create → edit → add → build/run preserves user edits; conflicts leave project/state unchanged; repeated application is a no-op; binary/mode handling, interrupted recovery and fresh-clone replay pass. Missing ancestry and unsupported migration cases produce actionable diagnostics rather than guessed state.

### Step 4 — command API, unified launcher and functional coverage (9.0.x)

Implement execution targets, metadata-only discovery, profile-free `CommandCatalog`, lazy startup, lifecycle services, `runAppOptions`, command refresh and the stable launcher/JSON API. Adapt context-bound/legacy companions, add app-local discovery and supported script-DSL compatibility. Complete `rest-api-plugin`, the named artefact commands, all REST scaffolding operations and plugin publication adapters. Coordinate the IntelliJ 8.1/9.0 version switch and lifecycle state paths. Build a table for every old command: retained with tested behavior, intentionally changed with an upgrade note, or retired with a replacement hint.

**Exit:** every command in that table has coverage; context commands boot no server; console cancellation/process ownership and standalone execution are verified; built-in help survives broken application builds; supported precompiled companions work or receive explicit incompatibility diagnostics. CLI/IDE smoke tests cover both version contracts. Profile-versus-Forge generation semantics are documented major-version changes, not asserted equivalent output.

### Step 5 — cutover and removals (9.0.x)

After steps 1–4 and their documentation are ready, remove profile modules/plugins, profile resolution and BOM constraints, old dispatcher/entry point/alias scripts and the Spring Boot CLI port. Re-home remaining supported services and script types before deleting their old artifact. Stop emitting `grails.profile`, the profile dependency and `grails-forge-cli.yml`; remove active-profile injection after its migration fixtures pass. Update release/distribution scripts, SDKMAN packaging and project wrapper entry points. Ship the coordinated IntelliJ support with 9.0.0.

The removed `profile` configuration must not continue resolving artifacts. Supply an actionable migration diagnostic for old declarations, including direct Gradle usage; choose and test a rejection-only diagnostic mechanism during implementation rather than assuming Gradle's missing-method exception will contain the upgrade guide. That mechanism is not a supported dependency configuration.

**Exit:** no production build/BOM/distribution dependency on `org.apache.grails.profiles` or the retired shell artifact; migration fixtures and historical docs may still name them. A migrated 8.1 app builds/runs on 9.0, an unmigrated one receives guidance, and the 8.1 distribution retains its original behavior. Measured startup and release artifact verification pass.

### Step 6 — documentation, website and hosted rollout (9.0.x; alongside each step)

Replace the 9.0 profiles chapter with "Feature packs and presets", rewrite command-line and command-reference pages, and put all additions/removals/semantic changes in `upgrading90x`. Document committed baseline storage, migration limits, trusted template execution, offline behavior and the descriptor/command APIs. Update plugin-authoring and metadata-repository guides, website index references, hosted preview/diff handling and the official-feature UI. No 8.1 pack instructions or `upgrading81x` unification additions are introduced.

**Exit:** the 9.0 guide and executable examples build; official hosted generation matches the CLI's locked generation state; the 8.1 guide and hosted version slots continue to describe/support profiles. Documentation ships with each user-facing implementation step rather than waiting until the end.

Deferred: pack-scaffolding convenience commands, classifier artifacts, additional template engines, automatic feature removal and generator/pack upgrades. The supported deprecated script DSL remains throughout 9.x and is eligible for removal in the next major. Size work after the selection, replay and command-API prototypes; steps 2–5 span multiple builds/repositories and are not "Forge-only" tasks.

<a id="decisions"></a>
## 6. Decisions and implementation gates

The release boundary is a confirmed maintainer decision. The other entries state the working design adopted in this revision; their proof obligations are explicit, not claims that the implementation already exists.

| # | Topic | Decision / gate |
| --- | --- | --- |
| 1 | Template language | GSP source for external packs; matching precompiled official packs. Versioned public model API and declared/tested generator compatibility; no logic-less fallback in v1. |
| 2 | Pack publication | Main JAR only under `META-INF/grails-forge/`; validate/check-compile in isolated Gradle tool workers. Classifier support is deferred and would need separate artifact checksums. |
| 3 | Catalog | Extend `apache/grails-plugins-metadata`; CLI-only external discovery, release-pinned offline copy, immutable locked resolutions. Ordinary plugin and usable-pack searches have different filters. |
| 4 | Project state | Commit manifest, exact lock and rendered baseline; ignore only derived caches/run/history/transaction state. Accept duplicated generated content in exchange for a portable merge ancestor. |
| 5 | Scripts | Keep the documented profile-free DSL deprecated through 9.x; convert inventoried commands and document direct-profile-API migration before removal. |
| 6 | Release lines | **Confirmed:** 8.1 contains the Grails-app conversion and retains profiles; all unification additions, cutover and removals target 9.0.x. |
| 7 | Hosted Forge | Official bundled features only. No external catalog fetch, pack execution, private repositories or `feature add` in the hosted API. |
| 8 | Fetcher | Exact-artifact transport, scoped repositories, complete-artifact hashing, explicit local test exceptions and documented settings.xml subset; no Maven runtime dependency resolver. |
| 9 | Runtime Spring profiles | Remove `grails.profile` injection on 9.0; test and document deliberate migration of conditional runtime behavior, including the implicit `web` profile. |
| 10 | Presets | One preset, no inheritance in v1; same compatibility/contribution model, explicit precedence and named unsupported custom-profile cases. Delivery gate before profile removal. |
| 11 | CLI/IDE API | `org.apache.grails:grails-cli` and new `org.apache.grails.cli.GrailsCli`; schema-versioned JSON catalog with completeness/diagnostics. Test the IDE against both old 8.1 and new 9.0 contracts. |
| 12 | Build fragments | Typed contribution channels first; deterministic escape-hatch fragments at defined anchors. No arbitrary build-script semantic validation or guaranteed conflict-free merge is promised. |
| 13 | Feature selection | Canonical feature/group IDs, explicit choice precedence, cycle detection and deterministic closure shared by compiled and declarative features. |
| 14 | Feature application | Verify replay against the committed ancestor, true three-way merge, whole-operation conflict detection and journaled recovery. Update baseline to pristine proposed output, not user edits. |
| 15 | Migration ancestry | Existing marker/build evidence creates an untracked manifest unless an exact ancestor is known. Explicit reviewed adoption establishes a new baseline; scanning Gradle cannot reconstruct one. |
| 16 | Command execution | Explicit targets instead of `requiresApplicationContext`/`requiresConsole` combinations; context-bound compatibility default, scoped service capabilities and tested classloader/process cleanup. |

Implementation spikes must settle the exact JSON/YAML schemas and supported version-comparison rules, prove source compilation/caching, demonstrate text/binary transaction recovery on supported OSes, and verify the proposed launcher publication and precompiled-companion compatibility. Record any resulting contract changes here before publishing the APIs. These gates do not reopen the 9.0 release assignment.

<a id="testing"></a>
## 7. Testing and documentation

Tests exercise public generator/CLI/build contracts, not private implementation helpers. In addition to the repository's affected-module checks, the implementation needs:

- **Selection/schema:** unknown keys, namespaced IDs, ambiguous aliases, duplicate versions, cycles/diamonds, default/explicit/required/excluded precedence, mixed compiled/declarative groups, final predicates and deterministic order. Validate the descriptor examples against the implemented schema.
- **Templates/contributions:** supported/unsupported template APIs and generator ranges, first-use/cache replay, modified local sources, deterministic bytes/modes, typed/static model failures, GSP rendering failures, dependency versions/exclusions/kind conflicts, configuration shape conflicts and normalized path collisions. Golden tests cover each converted official feature; generated Gradle builds cover free-form fragments.
- **Transport/catalog:** main-JAR identity and digest, private/exclusive repository routing, scoped credentials/redirects, malformed metadata, timestamped snapshots, offline success/failure, refresh without unlock, failed/partial downloads, concurrent cache access and zip/local-path guards. Use local synthetic repositories/catalogs rather than depending on the live public index.
- **Gradle authoring:** isolated compiler/generator classpaths, source-only packaging, preset-only publication, `forgePackTest`, supported application-type/option fixtures and configuration-cache reuse. Publish the fixtures through `build/local-maven` using the repository's normal test-publication chain.
- **Project state/transactions:** fresh-clone replay, all options including non-default GORM/servlet, missing/changed inputs, user text edits and deletions, binary files, executable bits, CRLF/LF, new-file collisions, concurrent edits, failed writes and interrupted recovery. Assert that conflicts preserve every existing project/state file and that repeat application is a no-op. Rendered baseline state must not recursively include itself.
- **Migration:** 8.0/8.1 Forge and built-in-profile fixtures, absent/legacy markers, custom preset mapping, dynamic/indirect Gradle declarations, multi-project roots and unsupported skeletons. Cover untracked/adopted state and active-profile behavior with conditional beans and configuration. Migration preflight works when Gradle configuration is broken.
- **Commands:** reserved/duplicate names, aliases/abbreviations, legacy `runCommand` routing, app-local discovery, supported precompiled companions, stale/incomplete catalogs, clean JSON stdout, lazy help, non-interactive exits, classloader refresh and cancellation/process ownership. Test every retained/changed/retired command in the step-4 matrix, including REST scaffolding tests and plugin publication adapters.
- **End-to-end/distribution:** generate → edit → feature add → build/run; migrate an 8.1 project to 9.0; script-DSL compatibility; CLI wrapper and SDKMAN artifact contents; IDE 8.1/9.0 runs and lifecycle state; Windows/macOS/Linux paths and permissions. Hosted tests prove that external packs are never resolved and that ZIP/preview/diff metadata handling is consistent.
- **Documentation:** descriptor/template/command API references, authoring fixture, migration/adoption guide, trusted execution and offline semantics, complete command reference and `upgrading90x` additions/removals. The 8.1 guide continues to document profiles and its existing CLI behavior.

<a id="appendix-a"></a>
## Appendix A: command tree

```
grails
  [--plain-output] [--non-interactive] [--offline] [--refresh]
  create-app | create-restapi | create-plugin | create-web-plugin | create-rest-api-plugin
      [--features a,b,g:a:v] [--exclude-features x] [--preset g:a:v]
      [--data|--servlet|--jdk|--reloading] [--inplace] [--list-features] [--feature-pack-dir <path>]
  feature   list | search <text> | info <id> | diff <id|gav> | add <id|gav> [--dry-run|--diff]
  plugin    search <text> | info <name|coordinates>
  project   init [--dry-run] | migrate --to 9.0 [--dry-run]
  create-controller | create-service | create-domain-class | create-taglib | create-interceptor
  create-unit-test | create-integration-test | create-functional-test | create-restful-controller
  create-domain-resource | create-command | create-script | create-job | add-property | <pack artefacts>
  run-app | stop-app | test-app | compile | clean | assemble | war | package | console | shell
  dependency-report | run-command | run-script | stats | bug-report | install-templates
  publish-plugin | package-plugin
  gradle <args> | open <path> | help [command] [--json]
  <discovered application/standalone/console commands: dbm-*, schema-export, generate-*, s2-*, ...>
```

Commands are available only when their application type or selected feature supplies them. Offline mode controls CLI transport; an explicitly invoked Gradle task needs its corresponding offline argument forwarded too and cannot be promised network-free if its own code makes network calls. `--refresh` never changes locked pack versions. `project init` is baseline adoption, not a second application generator. Authoring scaffolding, automatic removal and upgrade commands are not part of this v1 tree.

<a id="appendix-b"></a>
## Appendix B: facts the plan relies on

The first group was checked against the local code baseline for revision 11. External repository counts/paths and the SQL-tail branch observations retain the original inventory date and must be refreshed by the corresponding implementation work.

- Forge has no declarative mechanism; features are `@Component`s found by scanning `org.grails.forge` (`ForgeCoreConfiguration.groovy:35-50`), registered by `FeatureRegistry` (`feature/FeatureRegistry.groovy:43-64`).
- The build file snapshots dependencies at `FeaturePhase.BUILD` (`GradleBuildCreator.groovy:34-74`); later additions are lost.
- `Dependency` had no exclusions before step 1 added `Dependency.Builder.exclude`; equality compares group/artifact/scope but not version, platform kind or exclusions. Since step 1 a second declaration with different exclusions is an error; a second declaration with a different version or platform kind is still dropped silently, and no error names the contributing features yet. `Builder.extension`, which `buildInternal` dropped, is removed. Versions come from bundled BOM metadata, literals or POM lookup.
- `FeatureContext.isPresent` is class-based and `addFeature` immediately recurses (`FeatureContext.groovy:110-127`); `OneOfFeatureValidator` validates by group ID since step 1. Neither provides a descriptor-ID dependency solver. `ContextFactory` already computes compiled defaults to a fixed point; the new resolver must preserve that intent while incorporating requirements/exclusions.
- Codegen commands are a static list (`ForgeCli.groovy:45-57`) gated only by a marker file in the current directory (`:115-128`).
- `FeatureDiffer` is exposed through web `DiffController`; its baseline uses an empty feature selection and an `Options` constructor that restores default GORM/servlet (`FeatureDiffer.groovy:89-100`). `MapOutputHandler.write` stores null for binary outputs (`:40-47`), which the differ skips. This cannot supply the proposed byte-preserving historical merge ancestor unchanged.
- Inside a project the shell dispatches everything through the profile (`GrailsCli.groovy:385`); the profile jars come from the Gradle `profile` configuration via the `GrailsClasspath` tooling model (`GrailsClasspathToolingModelBuilder.groovy:43-60`); Aether is used only outside a project.
- The proposed committed `.grails/` generation-state contract does not exist in the reviewed generator; existing lifecycle PID state is under `build/`.
- `grails.codegen.defaultPackage` has runtime readers; `grails.profile` becomes an active Spring profile with a `web` default.
- Gradle discovers `-cli` names from manifests/factories without loading implementation classes (`GrailsCliGradlePlugin.groovy:451-479`). The runtime `ApplicationContextCommandRegistry` does load and instantiate commands (`:53-90`); metadata-only catalog discovery is new work. `ApplicationCommand` currently requires the context-oriented `handle(ExecutionContext)` contract, with no standalone/console flags.
- The IntelliJ plugin (`apache/grails-intellij-plugin`, `main`, 2026-10-11) starts its own JVM with main class `org.grails.cli.GrailsCli` on the resolved `grails-shell-cli` artifact (`Grails3GradleCommandExecutor.java:54`, `GrailsModuleModelBuilderImpl.java:54-78`), hard-codes 34 shell command names (`Grails3CommandProvider.java:31-70`), runs `run-app` the same way (`GrailsRunConfiguration.java:70`), detects projects by `grails-app` (`GrailsApplicationManager.java:167`), and creates projects through the hosted Forge API (`GrailsForgeModuleBuilder.kt:67-221`).
- `sql-tail` (branch `feat/sql-tail-command`, PR #16631 on 9.0.x) is a `GroovyScriptCommand` script in `grails-datamapping-core/src/cli/resources/META-INF/commands/sql-tail.groovy` that follows a capture file written by the running application and never boots it; `run-app --sql-tail` is a flag added to `grails-profiles/base/commands/run-app.groovy`. `DbmGormDiffCommand` is an `ApplicationCommand` that needs the booted context (`withGormDatabase(applicationContext, dataSource)`).
- The plugin portal's data source is `apache/grails-plugins-metadata`: one YAML per plugin under `grails-plugins/<group path>/<artifactId>.yml` (`name`, `desc`, `coords`, `owner`, `vcs`, `docs`, `maven-repo`, `labels`, `licenses`, `versions`, optional `deprecated`), `index-plugins.groovy` run every eight hours by `.github/workflows/updateIndex.yml`, which downloads each new version's jar by exact URL and reads `META-INF/grails-plugin.xml`, and `grails-plugins-index.json` consumed by `apache/grails-static-website` (`buildSrc/.../PluginsTask.groovy:67`). 256 entries on 2026-10-13.

<a id="changelog"></a>
## Change log

Earlier entries describe earlier proposals, including release assignments superseded by revision 11.

| Revision | Date | Change |
| --- | --- | --- |
| 12 | 2026-11-19 | Rebased onto 9.0.x. First step 1 changes: features are applied and listed by order and then name; dependencies can declare exclusions; the unrendered `Dependency.extension` hook is removed, since the migrations `sourceSets` block it carried is no longer needed. | Later the same day: string group IDs for the exclusive feature groups, used by the validator, the registry and the web API; `BuildFileContributions` with named extensions and anchored blocks, and five feature-name branches moved out of `buildGradle.gsp`. Also the feature pack descriptor parser (`PackDescriptorParser`), not yet used by generation.
| 11 | 2026-10-26 | Assign all unification work to 9.0; record the completed 8.1 Grails-app conversion and retention of profiles. Replace the transitional 8.1 phase with gated 9.0 delivery. Define identifier-based feature selection, versioned pack APIs, main-JAR identity, CLI-only catalog use, preset limits, committed lock/baseline state, replay and journaled three-way application, explicit migration/adoption limits and runtime-profile guidance. Replace ambiguous command flags with execution targets and metadata discovery; specify launcher/IDE contracts, lifecycle-state coordination and omitted command coverage. Reconcile GSP trust/dry-run statements, authoring isolation, hosted metadata handling, deferred scaffolding, tests and contents anchors. |
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

Working design for apache/grails-core issue #16348. Originally drafted against PR #16551; implementation targets 9.0.x. Related: #15301, #14081, #14944, #14933, #14932.
