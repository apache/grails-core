---
name: grails-upgrade
description: "Upgrades a Grails application to Grails 8.0 (releases 8.0.0 to 8.0.1). Base version: Grails 7; use it for an application on Grails 7 or on an earlier Grails 8.0 release. An application on Grails 6 or earlier first upgrades to the last Grails 7 release. Covers the changes of each release, Java 21, Groovy 5 name resolution and static compilation, Spring Boot 4.1, Spring Framework 7, dependency management, Micronaut, Jackson 3, Hibernate 7, TagLibs, testing, content negotiation, asset pipeline wildcard paths, and validation behavior changes"
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide upgrades of Grails applications from Grails 7.x to Grails 8.
- Guide upgrades from one Grails 8 release to a later one, with the changes of each release listed under that release.
- Turn the Grails 8 upgrade guide into a practical migration checklist.
- Identify Groovy 5, Spring Boot 4.1, Spring Framework 7, Jackson 3, Gradle platform, Micronaut, Hibernate, TagLib, testing, validation, and content negotiation changes that can break an application.
- Keep migration work focused on public application behavior, not internal Grails framework implementation details.

## When to Use Me

Activate this skill when:

- Updating an application to Grails 8.
- Updating a Grails 8 application to a later Grails 8 release.
- Reviewing a Grails 8 upgrade branch or pull request.
- Fixing tests, build failures, runtime failures, or behavior changes after moving from Grails 7.x to Grails 8.
- Deciding whether an upgrade should remain on Hibernate 5 or opt in to Hibernate 7.
- Updating application build files, configuration, custom Spring Boot integration, TagLibs, JSON rendering, or content negotiation behavior for Grails 8.

## Which Parts Apply

Read the Grails version the application is on, the `grailsVersion` in its `gradle.properties`, and the version it is going to.

The base version of this skill is Grails 7. After [Primary Sources](#primary-sources), the skill has one part per release, in ascending order: [Grails 8.0.0](#grails-800) and [Grails 8.0.1](#grails-801). It ends with [After the Upgrade](#after-the-upgrade).

- Apply the part of every release after the application's current version, up to and including the target version.
- An application on Grails 7 starts at [Grails 8.0.0](#grails-800).
- An application on Grails 6 or earlier first upgrades to the last Grails 7 release, with the `grails-upgrade` skill of that release.
- Whatever the starting version, finish with [After the Upgrade](#after-the-upgrade).

Each change is listed under the release that first carried it. A change that adds a configuration setting names the setting and its default. Where there is a way to keep the previous result, the entry gives it.

## Primary Sources

Use the published Grails documentation as the source of truth before changing an application. The URLs below use `<version>`, which stands for the Grails 8 version the application is upgrading to, such as the `grailsVersion` in its `gradle.properties`; use `snapshot` for a snapshot version.

| URL | Use for |
|-----|---------|
| `https://grails.apache.org/docs/<version>/guide/upgrading.html#upgrading80x` | Main Grails 7 to Grails 8 upgrade guide |
| `https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide` | Boot module splits, Jackson, session, Batch, and test infrastructure changes; also review the target 4.1 release notes |
| `https://grails.apache.org/docs/<version>/guide/introduction.html#whatsNew` | Grails 8 feature and platform overview |
| `https://grails.apache.org/docs/<version>/guide/introduction.html#dependencyUpgrades` | Platform dependency baseline |
| `https://grails.apache.org/docs/<version>/ref/Versions/Grails%20BOM.html` | Dependency versions of the Grails BOM, with links to the Hibernate 5, Hibernate 7, and Neo4j BOM variants |
| `https://grails.apache.org/docs/<version>/guide/upgrading.html#micronaut-integration` | Micronaut integration, which points to the Grails Micronaut project's own upgrade documentation |
| `https://grails.apache.org/docs/<version>/guide/theWebLayer.html#contentNegotiation` | MIME defaults and Accept header behavior |
| `https://grails.apache.org/docs/<version>/grails-data/hibernate7/manual/index.html#upgradeNotes` | Hibernate 7 GORM query and tenant-schema notes |
| `https://grails.apache.org/docs/<version>/grails-data/hibernate7/manual/index.html#databaseMigration` | Database migration plugin for Hibernate 7, which uses the same version as Grails |

Do not assume `https://grails.apache.org/docs/latest/` points at Grails 8. Check the version printed on the snapshot documentation too: `snapshot` can track a newer development branch. When testing a local `8.0.x` checkout, compare its `grails-doc/src/en/guide/upgrading/upgrading80x.adoc` and `dependencies.gradle` with the published guide before applying guidance from a later branch.

# Grails 8.0.0

Apply this part when the application is on Grails 7.

## Upgrade Baseline

Start every Grails 8 upgrade by checking these platform requirements:

- Use JDK 21 or later to build and run ordinary Grails 8 applications.
- Use JDK 25 or later if the application uses `grails-micronaut`, `micronaut-http-client`, or other Micronaut features.
- Update the Gradle wrapper to the Grails 8 managed line, currently Gradle 9.9.2. The Grails 8 Gradle plugins require Gradle 9.7 or later.
- Expect Spring Boot 4.1.x, Spring Framework 7.0.x, Spring Security 7.1.x, Spring Data 2026.0.x, Micrometer 1.17.x, Jackson 3.1.x, Tomcat 11.0.x, and Jakarta Servlet 6.1.
- Keep using `jakarta.*` APIs. Do not reintroduce `javax.*` packages.
- Add `runtimeOnly 'org.springframework.boot:spring-boot-properties-migrator'` temporarily during the migration, boot once, fix reported configuration properties, then remove it.

## Migration Workflow

Use this order so failures are isolated and reversible:

1. Move the build, CI, and deployment runtime to the required JDK.
2. Update the Grails version and apply the verified dependency, build and configuration changes below, checking the target version's guide and BOM.
3. Boot with the Spring Boot properties migrator and fix configuration warnings.
4. Fix dependency management and starter changes before code changes.
5. Compile and fix direct Spring Boot, Spring Framework, Jackson, Hibernate, and testing API removals.
6. Run application tests and manually exercise public behavior that depends on rendering, validation, content negotiation, persistence, and custom Spring integration.
7. Remove temporary migration dependencies and compatibility toggles once the application is clean.

For large applications, batch syntax-only checks across source files with the target Groovy compiler before repeatedly compiling the whole application. A build can stop at the first parser failure even when dozens of files share it. Parsing is a diagnostic step, not a substitute for compilation or tests.

Before compensating for an apparent framework/compiler regression, reproduce the same public behavior on both the old and target stacks with neutral fixtures. Distinguish an intentional migration, a pre-existing assumption exposed by the upgrade, and a pre-release defect. For overload or proxy failures, compare concrete instances with proxies and inspect the compiled call target; an `Object` fallback does not by itself prove that runtime type matching is broken. Prefer a framework fix for a confirmed regression, and document narrowly scoped application workarounds beside the code.

## Build and Dependency Management

### Verified Generated-Application Differences

The following differences were verified against Grails Forge-generated **7.2.4** and **8.0.0-SNAPSHOT** web applications, both using **Hibernate 5 and Undertow**. The Grails 8 baseline selected `logback-config` to make its optional logging file explicit. These are generated defaults; retain deliberate application customizations where they remain supported.

| Area | Grails 7.2.4 baseline | Grails 8.0.0-SNAPSHOT baseline | Upgrade action |
|------|-----------------------|-------------------------------|----------------|
| Undertow | `spring-boot-starter-undertow` | `org.apache.grails:grails-undertow` | Use the Grails Undertow module and its managed servlet dependencies. |
| Undertow thread library | Explicit `runtimeOnly 'org.jboss.threads:jboss-threads:3.9.2'` | No direct declaration | Remove the old generated pin and let the new stack resolve it. |
| Layouts | `org.apache.grails:grails-layout` | `org.apache.grails:grails-sitemesh3` | SiteMesh 3 is the new generated default; migrate configuration and custom taglib integrations when adopting it. |
| Jansi | `runtimeOnly 'org.fusesource.jansi:jansi'` | No application runtime declaration | Remove an inherited dependency together with any custom `withJansi=true` settings. |
| Logback | Explicit console appender, `withJansi=false`, Boot encoder defaults, root `ERROR` | Optional `logback-config` file includes Boot's `defaults.xml` and `console-appender.xml`, root `INFO`, no Jansi setting | Preserve intentional log levels and custom appenders, but remove obsolete Jansi wrapping. Boot's `%clr` supplies ANSI color without it. |
| Mockito | No explicit Mockito dependency in the generated web baseline | `testImplementation 'org.mockito:mockito-core'` | Keep Mockito test-scoped where tests need it. |
| Console | Explicit `console 'org.apache.grails:grails-console'` | No explicit declaration in the generated baseline | Review separate CLI/tooling requirements; this is not an application runtime dependency. |
| Java compilation | Release 17 | Release 21 | Raise the baseline to at least 21; retaining a supported higher application target is valid. |

### Native Dependency Management

Grails 8 no longer applies the `io.spring.dependency-management` plugin by default.

- Grails now uses Gradle native `platform()` dependency management with the Grails BOM.
- The Grails Gradle Plugin applies one Grails BOM as a `platform()` to eligible declarable configurations. `grails { bom = ... }` (the `grails.bom` property) selects it: the value is the BOM artifact name within the `org.apache.grails` group, resolved as `org.apache.grails:$bom:$grailsVersion`, and defaults to `grails-bom`. Exactly one Grails BOM is ever applied; the plugin never layers two.
- The platform is contributed lazily, when a configuration's dependencies are observed (through resolution, publication, or `incoming.dependencies`), so configurations populated on demand through `defaultDependencies`, such as the Grails `profile` configuration, keep their defaults. Until then the platform is not visible in a configuration's declared `dependencies` or `allDependencies`: build logic that looks for it must inspect `incoming.dependencies` or the resolution result.
- Dedicated tool and annotation-processor classpaths, including the `jacoco` plugin's `jacocoAgent` and `jacocoAnt` configurations, are excluded from the automatic platform, so the application BOM does not change their transitive versions.
- Existing version overrides in `gradle.properties` and `ext['property.version']` still work through the bundled `org.apache.grails.gradle.bom-property-overrides` plugin, which the Grails Gradle Plugin applies automatically.
- An explicit property override is applied as a strict constraint and always wins, including when it downgrades a version, as it did under Spring DM. Overriding a property that selects an imported BOM's version, such as `spring-boot.version`, re-imports that BOM at the overridden version and applies its managed set.
- The BOM's default managed versions are a regular `platform()`, which takes part in Gradle's conflict resolution: the highest version wins, so a transitive dependency can pull a managed version that has no override above the BOM default.
- Grails BOMs now follow the upstream BOM property names: an override of a version Spring Boot also manages uses Spring Boot's property name, and for a module Spring Boot manages through an imported BOM, the property Spring Boot imports that BOM with. Rename Grails-specific overrides, because a property no BOM declares is silently ignored:

| Grails 7 or earlier Grails 8 property | Grails 8 property |
|---------------------------------------|-------------------|
| `jackson.version`, `jackson2.version` | `jackson-2-bom.version` (Jackson 2, `com.fasterxml.jackson.*`) |
| `jackson3.version` | `jackson-bom.version` (Jackson 3, `tools.jackson.*`, the default) |
| `jackson-bom.version` set to a 2.x version (Spring Boot 3's name for Jackson 2) | `jackson-2-bom.version`; in Grails 8 `jackson-bom.version` sets Jackson 3, so a 2.x value makes dependency resolution fail |
| `neo4j-driver.version` (`grails-neo4j-bom`) | `neo4j-java-driver.version` |

- Replace `grails { springDependencyManagement = false }` with `grails { bom = null }` for new builds that intentionally opt out. The `springDependencyManagement` flag is deprecated; `false` is still honored and is equivalent to `bom = null`.
- With `bom = null` the plugin declares no Grails `platform()` or `enforcedPlatform()` on any configuration and does not apply the `org.apache.grails.gradle.bom-property-overrides` plugin. Declare the BOM in the build, and apply `id 'org.apache.grails.gradle.bom-property-overrides'` explicitly if `gradle.properties` / `ext['...']` overrides must keep working.
- Two Spring DM features have no automatic migration. Search `build.gradle` for a `dependencyManagement { dependencies { dependency 'group:artifact:version' } }` block that pins dependencies no BOM manages: replace it with `dependencies { implementation 'group:artifact:version' }` for a direct dependency, or `configurations.all { resolutionStrategy.force 'group:artifact:version' }` to force a transitive version.
- Version-range strings such as `1.+` or `[1.0,2.0)` in the `<properties>` of a custom BOM were resolved by Spring DM's own resolver. They are now applied as Gradle strict version constraints, with Gradle's range semantics. Concrete versions are unaffected, so this matters only to a build that consumes such a BOM.
- If Spring DM's "BOM always wins" behavior is required for the default managed versions, explicitly use `enforcedPlatform("org.apache.grails:grails-bom:$grailsVersion")` instead of relying on the auto-applied platform.
- Check requested and selected versions with `dependencyInsight` on the configuration that actually supplies the failing code. A direct dependency version is not a force: a BOM can select a newer version, and browser-test, runtime, asset-development, and build-plugin configurations can select different versions. Keep application version properties consistent with the verified BOM baseline or deliberate newer overrides; do not infer the loaded jQuery or other library version from `gradle.properties` alone.

For builds with `buildSrc` or an included `build-logic` project:

- Use `org.apache.grails:grails-gradle-bom` for the Gradle plugin classpath. Gradle 9 embeds Groovy 4, while the application BOM manages Groovy 5; do not import the application runtime stack into convention-plugin compilation.
- Keep application plugins such as database migration on the application's dependencies. Grails 8 provisions their CLI companions; they do not belong on the convention-plugin classpath just to expose commands.
- Audit third-party Gradle plugins for Gradle 9 support. For example, JRebel Gradle plugin 1.2.1 calls the removed `org.gradle.util.ConfigureUtil`; 1.2.3 works with the Gradle 9 build configuration.
- For a local framework build, publish both the Grails Gradle plugins and framework artifacts. The root `publishAllToMavenLocal` task also publishes Forge in order. Enable Maven Local separately for plugin management, application dependency resolution, and any included build's repositories.
- `grails-gradle-plugins` transitively resolves the Grails publish plugin even when no publishing task runs. A pre-release framework checkout may require ASF staging for that dependency; use a repository content filter and make this local-validation setup explicit.
- Replace `org.apache.grails.data:grails-datamapping-async` with `org.apache.grails:grails-datamapping-async`. Remove an old unversioned `org.fusesource.jansi:jansi` runtime dependency if it was only inherited from generated Grails 7 build files; it is no longer managed for application runtime.
- Remove matching `<withJansi>true</withJansi>` directives from every profile in custom Logback configuration, including custom `ConsoleAppender` subclasses. Otherwise Logback still tries to load `org.fusesource.jansi.AnsiConsole` and warns before falling back to the ordinary stream. Grails 8's generated logging configuration uses Spring Boot's console defaults without Jansi; native ANSI output can still use Boot's `%clr` converter. Jansi's global stream replacement can also break logging after a DevTools restart (Grails issue #15663).

Spring Boot 4 renamed common starters:

| Spring Boot 3 starter | Spring Boot 4 starter |
|-----------------------|-----------------------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `spring-boot-starter-web-services` | `spring-boot-starter-webservices` |
| `spring-boot-starter-oauth2-authorization-server` | `spring-boot-starter-security-oauth2-authorization-server` |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |

Spring Boot 4 also ships `spring-boot-starter-classic` and `spring-boot-starter-test-classic`, which pull in all Spring Boot modules without picking starters one by one. They are a transitional fallback for a faster migration; treat them as a stopgap and move to the modular starters.

For WAR deployment to an external servlet container, replace `providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat'` with `providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat-runtime'`.

Spring Boot 4 dropped `spring-boot-starter-undertow`; Grails 8 provides Undertow through the Grails Undertow plugin instead. For an application that runs on Undertow, make the build match what Grails Forge generates for `--servlet=undertow`:

- Replace `spring-boot-starter-undertow` with `implementation 'org.apache.grails:grails-undertow'`, without a version; the Grails BOM manages it.
- Remove direct `io.undertow:undertow-servlet` and `io.undertow:undertow-websockets-jsr` dependencies from the old stack. Servlet 6.1 support uses Undertow core 2.4.x with servlet/websocket artifacts in `io.undertow.ee`; let the Grails Undertow plugin and BOM supply the compatible set.
- Do not declare `spring-boot-starter-tomcat` or `spring-boot-tomcat`; those are what Forge adds for Tomcat applications instead.
- Keep the `server.undertow.*` properties; they continue to work. `server.undertow.max-http-post-size` now defaults to 2MB, Undertow's hardened default; set it to `-1` only if the application relied on the previous unlimited request size.
- Undertow now deletes the temporary directories it creates under `java.io.tmpdir` when the application stops, as Spring Boot's Tomcat and Jetty servers do: one for uploaded files and, when the application has no document root, one for the document root. Uploaded files left in them are deleted with them, so do not rely on a file there outliving the application.

Spring Retry is no longer managed by Spring Boot. If the application directly uses its `RetryTemplate`, `@Retryable`, `@EnableRetry`, or `@Recover`, declare `org.springframework.retry:spring-retry` directly. The Grails 8 BOM manages its version; otherwise supply an explicit version or migrate to Spring Framework's retry API. A transitive dependency is not a declaration of the application's own use.

### CLI Commands, Developer Tooling, and the Buildscript Classpath

Grails commands no longer ship inside runtime plugin artifacts. Each module with commands publishes a companion artifact with a `-cli` suffix, and commands are registered in `META-INF/grails-cli.factories` instead of `META-INF/grails.factories`.

- In `grails-app/commands` classes, change imports from `grails.dev.commands.*` (for example `ApplicationCommand`, `ExecutionContext`) to `org.apache.grails.core.cli.*`, which the `org.apache.grails:grails-core-cli` artifact carries.
- The Grails Gradle plugin registers a `grailsCli` configuration. It is compile-visible for `grails-app/commands` sources and on the command-runner classpath, never on `runtimeClasspath`, so commands and their CLI-only dependencies stay out of `bootRun`, `bootJar` and `bootWar`.
- `cliAutoProvision` (default `true`) adds `grails-core-cli`, `org.apache.grails:grails-console` (the command runner) and every companion advertised by a jar in the resolved dependency graph, transitive plugins included, to `grailsCli`. The per-command Gradle tasks, such as `dbmUpdate`, are registered from those companions. Most applications need no build change to keep plugin commands working.
- Remove the `console "org.apache.grails:grails-console"` dependency earlier versions generated; the `console` and `shell` tasks draw from the provisioned CLI tier. The `console` configuration itself remains for additional console-only dependencies.
- With `grails { cliAutoProvision = false }`, declare every CLI dependency on `grailsCli`: `org.apache.grails:grails-core-cli`, `org.apache.grails:grails-console`, and each companion, such as `org.apache.grails:grails-data-hibernate7-dbmigration-cli`. `grails-console` is not optional there: without it `console`, `shell`, `runCommand`, `runScript` and `urlMappingsReport` fail with `Could not find or load main class`.
- A released Grails 7 plugin whose commands implement `grails.dev.commands.ApplicationCommand` or `GrailsApplicationCommand` needs the deprecated bridge: set `grails { legacyCommandSupport = true }` (default `false`), or the project property `grailsLegacyCommandSupport=true` (`-PgrailsLegacyCommandSupport=true`). It is consulted only while `cliAutoProvision` is enabled, and adds `org.apache.grails:grails-core-cli-legacy` to the execution-only `grailsCliLegacy` configuration; with `cliAutoProvision` disabled, declare `grailsCliLegacy 'org.apache.grails:grails-core-cli-legacy'` yourself. The bridge logs a one-time deprecation warning.
- Legacy commands get no Gradle task of their own. Run them through `runCommand`, as `./gradlew runCommand "-Pargs=generate-controller example.Book"`, or through the shell. With the bridge disabled, Grails logs an error naming the plugin artifact that still publishes `grails.dev.commands.ApplicationCommand` entries, and an unknown command names the remedy.
- Scripts created with `create-script`, including scripts packaged as `META-INF/commands/*.groovy` such as `s2-quickstart`, and YAML or JSON multi-step commands, keep working without the bridge.
- Framework command classes moved with their artifacts. Search for imports of the old names:

| Grails 7 | Grails 8 (`-cli` artifact) |
|----------|----------------------------|
| `grails.dev.commands.*` (`grails-core`) | `org.apache.grails.core.cli.*` (`grails-core-cli`) |
| `org.grails.web.mapping.reporting.UrlMappingsReportCommand` | `org.apache.grails.web.mapping.cli.UrlMappingsReportCommand` (`grails-web-url-mappings-cli`) |
| `scaffolding.*` commands | `org.apache.grails.scaffolding.cli.*` (`grails-scaffolding-cli`) |
| `grails.plugin.hibernate.commands.SchemaExportCommand` | `org.apache.grails.data.hibernate5.cli` / `org.apache.grails.data.hibernate7.cli` (`grails-data-hibernate5-cli` / `grails-data-hibernate7-cli`) |
| `org.grails.plugins.databasemigration.command.*` | `org.apache.grails.data.hibernate5.dbmigration.cli` / `org.apache.grails.data.hibernate7.dbmigration.cli` |
| `grails.plugin.springsecurity` s2 commands | `org.apache.grails.security.cli.*` (`grails-spring-security-cli`) |

For a plugin that ships commands:

- Migrate commands to `org.apache.grails.core.cli.*`, register them in `META-INF/grails-cli.factories`, and publish them in a companion `-cli` artifact; only those commands get named Gradle tasks. A plugin that deliberately keeps authoring against the deprecated contract declares `compileOnly 'org.apache.grails:grails-core-cli-legacy'`.
- Applying `org.apache.grails.gradle.grails-plugin-cli` routes `src/main/scripts` into the companion; leave scripts there or place them under `src/cli/resources/META-INF/commands`. After first applying it to a project built without it, run `./gradlew clean` once to drop the empty `build/resources/main/META-INF/commands` directories.
- `copyCommands` and `copyTemplates` stage into a directory of their own instead of the `processResources` output, and are registered when the plugin is applied, not in `afterEvaluate`. They are still `Copy` tasks with paths relative to `META-INF/templates` or `META-INF/commands`: drop the `afterEvaluate { }` wrapper around their configuration and use `tasks.named('copyTemplates', Copy) { ... }`.

Developer tooling left the application's runtime classpath together with Jansi:

- `org.apache.grails:grails-console`, the `org.apache.groovy:groovy-console`, `groovy-groovysh` and `groovy-swing` libraries it brings, the JLine family (including `org.jline:jansi`) and FuseSource Jansi are provisioned on `grailsCli`, which is on the compile and test classpaths but not on `runtimeClasspath`. If application code, or a `runtimeOnly` dependency of it, needs the Groovy shell, the Swing console or JLine at runtime, declare it explicitly, for example `runtimeOnly "org.apache.groovy:groovy-groovysh"`.
- Search for `org.fusesource.jansi` imports. There is no Grails-managed replacement coordinate: use Spring Boot's `org.springframework.boot.ansi.AnsiOutput`, or JLine's `org.jline.utils.AttributedStringBuilder` if the project already depends on JLine.
- Replace `-Dorg.fusesource.jansi.Ansi.disable=true`, which no longer has any effect, with `-Dspring.output.ansi.enabled=never`. `spring.output.ansi.enabled` takes `always`, `detect` (the default: color only when the terminal supports it) or `never`, in `application.yml` or as a system property, which is how the CLI reads it (`grails -Dspring.output.ansi.enabled=never run-app`). A `-D` passed to `gradlew` sets it on the Gradle daemon, not on the JVM `bootRun` forks: use `application.yml`, or `tasks.named('bootRun') { systemProperty 'spring.output.ansi.enabled', 'never' }`.
- These types moved from `org.apache.grails.bootstrap:grails-bootstrap` to `org.apache.grails.bootstrap:grails-bootstrap-cli`, with their package names unchanged: `grails.build.logging.GrailsConsole`, `grails.build.logging.ConsoleLogger`, `grails.build.logging.GrailsEclipseConsole`, `org.grails.build.logging.GrailsConsolePrintStream`, `org.grails.build.logging.GrailsConsoleErrorPrintStream`, `org.grails.build.logging.GrailsConsoleAntBuilder`, `org.grails.build.logging.GrailsConsoleBuildListener` and `org.grails.build.interactive.CandidateListCompletionHandler`. `org.grails.web.mapping.reporting.AnsiConsoleUrlMappingsRenderer` moved from `org.apache.grails:grails-url-mappings` to `org.apache.grails:grails-url-mappings-cli`.
- Command code in `grails-app/commands` or a plugin's `src/cli` still sees those types. Application code that references them should use a logger or `AnsiOutput`; if it must keep them, declare `implementation "org.apache.grails.bootstrap:grails-bootstrap-cli"`. Test code that extended `GrailsConsolePrintStream` to capture output should extend `java.io.PrintStream`.

### Split Auto-Configuration Modules

- Inventory the technologies the application actually uses and inspect each application's resolved runtime graph. Having the third-party library no longer guarantees Boot auto-configuration is present: Spring Session needs `spring-boot-session`, Spring Integration needs `spring-boot-integration`, and JSON mapping needs `spring-boot-jackson` (or their appropriate starters). Check actual beans and behavior after adding a module; successful compilation cannot detect missing auto-configuration.
- Distinguish Boot-managed infrastructure from application/Grails-owned configuration. Do not add Boot Liquibase or Batch auto-configuration merely because those libraries are present when the Grails migration plugin or an explicit JDBC Batch configuration already owns their lifecycle. Use technology-specific test starters when relying on their Boot test integration.
- Trace deprecated starters to their declaring dependency before changing them. A renamed application dependency does not remove an old starter brought in by a plugin; avoid excluding its supporting modules without verifying the replacement graph.
- Keep the properties migrator until the deployed/development configuration, including private external overrides, has been exercised. A clean TEST startup does not certify those branches. Record the removal follow-up if deployed verification is still pending.

### Spring Session Cookies, Filters, and Stored Sessions

- Verify both native-container and Spring Session cookies. Missing Boot session auto-configuration can make the fallback cookie serializer adopt `JSESSIONID` instead of `SESSION`; decoding a native cookie as a Base64 session ID can send binary/NUL bytes to a JDBC lookup. A database error on the session-ID query parameter is not proof of corrupt stored attributes. Test a native cookie, a valid Spring Session cookie, and a cookie-free first request.
- Adding the missing session module also introduces Boot's filter registration. Remove a duplicate application registration and preserve its ordering through `spring.session.servlet.filter-order`. Audit dispatcher types too: an ERROR dispatch can re-enter Spring Session despite an API filter marking the request as already filtered. Where the application deliberately excludes error dispatches, configure `spring.session.servlet.filter-dispatcher-types=REQUEST,ASYNC` and test real API error responses. A comma-separated scalar worked with the checked Grails 8 configuration; a Groovy list of strings bound without converting its elements to Boot's dispatcher enum.
- Audit serialized session attributes independently of cookie handling. Changed framework token-holder serialization can make pre-upgrade sessions unreadable. When compatible restoration is not possible, invalidate sessions once during deployment, deleting child attributes before parent sessions, and communicate reauthentication. Do not reset sessions on every startup or assume pinning an old serial UID makes incompatible state compatible.

## Spring Boot 4 and Spring Framework 7 Code Changes

Check for direct imports or references to Spring Boot auto-configuration classes. Spring Boot 4 split the old monolithic auto-configure module into domain modules, and many classes moved.

Check both the package and the supplying module rather than guessing package renames. `FilterRegistrationBean` and `ServletContextInitializer` remain in `org.springframework.boot.web.servlet`. `DataSourceHealthIndicator` moves to `org.springframework.boot.jdbc.health` and needs an explicit `spring-boot-jdbc` dependency when application code references it. For an optional auto-configuration used only in an exclusion, `@EnableAutoConfiguration(excludeName = ['org.springframework.boot.ldap.autoconfigure.LdapAutoConfiguration'])` avoids adding a module just to reference its class.

Examples:

```groovy
// Before
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration

// After
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration
```

If the application contributes an `EnvironmentPostProcessor`, update both imports and `META-INF/spring.factories` keys from `org.springframework.boot.env.EnvironmentPostProcessor` to `org.springframework.boot.EnvironmentPostProcessor`.

`BootstrapRegistry` and its related types moved from `org.springframework.boot` to `org.springframework.boot.bootstrap`: `org.springframework.boot.bootstrap.BootstrapRegistry`, `org.springframework.boot.bootstrap.BootstrapRegistryInitializer` and `org.springframework.boot.bootstrap.ConfigurableBootstrapContext`. The old locations of these and of `EnvironmentPostProcessor` are deprecated but retained. If the application or a plugin contributes a `BootstrapRegistryInitializer`, update the import and the matching `META-INF/spring.factories` key.

The modules the auto-configuration classes moved to include `org.springframework.boot:spring-boot-webmvc` (Web MVC), `org.springframework.boot:spring-boot-servlet` (Servlet), `org.springframework.boot:spring-boot-mongodb` (MongoDB) and `org.springframework.boot:spring-boot-integration` (Spring Integration). An application that depends on `spring-integration-core` alone no longer gets Spring Boot's `integrationGlobalProperties` bean or its `spring.integration.*` properties: add `spring-boot-starter-integration`, or `spring-boot-integration` alongside the Spring Integration modules.

Other common Spring changes:

- Rename `HttpStatus.MOVED_TEMPORARILY` to `HttpStatus.FOUND`.
- Compare a servlet response's integer status with `HttpStatus.UNPROCESSABLE_ENTITY.value()` rather than the `HttpStatus` object. Keep numeric wire assertions separate from typed client-response status assertions.
- Remove `HandlerAdapter.getLastModified` overrides.
- Replace use of removed Spring theme infrastructure such as `ThemeSource`, `Theme`, `SimpleTheme`, and `SessionThemeResolver`.
- Replace `SecurityProperties.DEFAULT_FILTER_ORDER` with `-100` only when a direct constant replacement is needed, and prefer fluent `HttpSecurity` configuration long term.
- Update embedded server imports in application and test code to the new Spring Boot 4 packages:
  - `org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory` to `org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory`
  - `org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory` to `org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory`
  - `org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext` to `org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext`
  - `org.springframework.boot.web.context.WebServerApplicationContext` to `org.springframework.boot.web.server.context.WebServerApplicationContext`
  - `org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext` to `org.springframework.boot.web.context.servlet.AnnotationConfigServletWebApplicationContext`
- Add `spring-boot-tomcat` explicitly if application code directly references Tomcat classes.
- Prefer JSpecify nullability annotations (`org.jspecify.annotations.Nullable`, `org.jspecify.annotations.NonNull`) for new Spring-facing code. Spring Framework 7 deprecated `org.springframework.lang.Nullable` and `org.springframework.lang.NonNull`; they still work.
- `HttpMessageConverters` is deprecated. If the application contributed `HttpMessageConverter` beans through that class, switch to the `ClientHttpMessageConvertersCustomizer` and `ServerHttpMessageConvertersCustomizer` interfaces.
- Spring Boot 4.1 removes what Spring Boot 4.0 deprecated. If the application was tested against an earlier Grails 8 snapshot or Spring Boot 4.0.x, remove any use of APIs, methods or configuration properties that were only deprecated there.
- Replace the removed one-argument `ContentCachingRequestWrapper` constructor with the request and an explicit cache limit. A limit of `0` preserves the former unlimited behavior; choose a finite limit only with deliberate handling of truncated cached bodies.
- Spring Messaging 7 adds default-destination overloads such as `convertAndSend(Object payload, Map headers)`. A Groovy call `convertAndSend(topic, mapPayload)` can select this instead of the destination/payload overload, producing `No 'defaultDestination' configured` or silently sending the topic string as payload to an unrelated configured default. Cast the map argument explicitly: `convertAndSend(topic, (Object) mapPayload)`. Verify the destination and payload with a real `SimpMessagingTemplate` and message channel, both with and without a default destination; mocks alone can hide the overload mismatch.

## Groovy 5 Source Compatibility

### Syntax, Conversions, and Groovy Truth

- Remove a trailing comma after the last enum constant when fields or methods follow it. A newline is sufficient; adding a semicolon is unnecessary in Groovy. Check nested enums and constants followed by comments too.
- Replace `value instanceof List<MyType>` with `value instanceof List` (or an unbounded wildcard). The runtime check never verified element types. If static compilation needs the generic type afterwards, keep it in an explicit cast or typed local variable.
- Audit `value as boolean` when `value` can be null: Groovy 5 can throw `IllegalArgumentException: null to boolean`. Use `value != null` for presence checks or `!!value` for Groovy truth, preserving empty collection/string and zero semantics where relevant.
- Make map-key and filesystem assertions explicit. Use `map.get(key)` / `containsKey(key)` when testing entries such as `metaClass`, rather than bean-property introspection. Groovy 5 File/Path truth can test filesystem existence; use `path != null` when asserting that a path was supplied, and an explicit existence check when that is the contract.
- Use a collection copy constructor such as `new LinkedHashSet<Element>(source)` when a shallow copy is intended and a runtime collection's `clone()` is inaccessible. Do not rely on reflective access to a protected implementation method.

### Class Names, Properties, and DSL Scope

- Investigate repeated getter frames in a `StackOverflowError` before changing the query. An acronym getter such as `getURLValue(LocalDate asOf = LocalDate.now())` also exposes a zero-argument getter and the case-sensitive property `URLValue`. In the checked Groovy 5 stack, `URLValue.lookup()` inside that getter resolved the property and re-entered the getter instead of selecting the class; the same plain-Groovy example worked on the Groovy 4 stack. Qualify the class (`example.URLValue.lookup()`) and comment why the qualification prevents recursion. This is not specific to GORM or Hibernate proxies.
- Reading a static constant or a reusable validator closure inside `static constraints`, such as `matches: CODE_PATTERN` or `validator: codeValidator`, does not need the class name under Groovy 5, so do not qualify such reads mechanically. Qualify one, as `Book.CODE_PATTERN`, only where that particular read has been seen to fail during datastore initialization because the name resolved against the closure's delegate instead of the declaring class. Assigning to a static field from such a closure is the case that needs the class name, under static compilation.
- Keep closures declared locally inside `static constraints` as local references; qualifying those as class members introduces a missing-property error. Apply the same ownership check to static helpers called from binding/validation closures: qualify the declaring class when lookup is hitting the wrong owner or delegate.
- `ConfigObject.properties` is a read-only bean property in Groovy 5. For datasource or Quartz configuration whose actual key is `properties`, construct a map and use `put('properties', values)` or `putAll([properties: values])`; do not assign `delegate.properties` in a configuration closure.

The following generic example was compared using the Grails 7.2.4 / Groovy 4 and Grails 8 snapshot / Groovy 5.1.3 stacks. It has no GORM domain, database, or proxy:

```groovy
package example

import java.time.LocalDate

class URLValue {
    static URLValue lookup() { new URLValue() }
}

class ReferenceHolder {
    URLValue getURLValue(LocalDate asOf = LocalDate.now()) {
        // Qualify the class to avoid resolving the URLValue property and recursively calling this getter.
        example.URLValue.lookup()
    }
}
```

| Receiver expression inside the getter | Groovy 4 stack | Groovy 5.1.3 stack |
|--------------------------------------|----------------|-------------------|
| `URLValue.lookup()` | Returns a value | Recursive getter / `StackOverflowError` |
| `example.URLValue.lookup()` | Returns a value | Returns a value |

The return type is a type reference; the receiver of the method call is where the ambiguity occurs. For a domain query, the same diagnosis applies to `URLValue.createCriteria()`. Qualify only the ambiguous receiver, preserve the query, and verify against the actual compiler version before treating the workaround as permanent.

### Closure Logger Access

- Separate access to a class's own `@Slf4j` logger from logger inheritance. A returned closure from a Spring-enhanced `@Configuration` class failed with `MissingPropertyException: log` naming the generated CGLIB subclass, despite the declaring class having `@Slf4j`. Qualifying `ClientConfiguration.log` inside that closure selects the same logger generated on `ClientConfiguration`; it does not introduce a superclass logger. Verify the actual proxied bean and callback; a directly constructed configuration instance may not reproduce the failure.
- For genuinely inherited logger use, the default private `@Slf4j` field is a separate visibility concern. If subclasses intentionally share it, configure protected visibility on the base logger with `@Slf4j(visibilityId = 'logger')` and `@VisibilityOptions(id = 'logger', value = Visibility.PROTECTED)`; if a base-class closure should use the base logger, qualify that declaring class. Preserve intentional logger categories rather than adding duplicate logging annotations mechanically. Remove unused logging annotations from interfaces, where their generated private field is invalid.

```groovy
import groovy.util.logging.Slf4j
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Slf4j
@Configuration
class ClientConfiguration {
    @Bean
    Runnable clientCallback() {
        return () -> {
            // Use this configuration class's logger rather than looking up log on the CGLIB subclass.
            ClientConfiguration.log.debug('Configuring client')
        }
    }
}
```

These are targeted receiver/visibility fixes, not a general requirement to replace `log` everywhere in Groovy 5. The CGLIB case was verified in the application stack but has not been isolated to a particular compiler commit. Do not attribute it to the overload-dispatch change below without a separate reproducer.

### Runtime Overload Dispatch

- Groovy 5's [nestmate/direct-call change, GROOVY-10687](https://github.com/apache/groovy/commit/642a7532d71a7bce3eba411c7fa865c57195ccca) removed reflective dispatch used for some non-public calls inside closures. In the checked Groovy 4 stack, a protected overloaded method in a closure could be reselected using the runtime argument type, even under static compilation; Groovy 5 invokes the statically selected overload directly. This can surface through transactional services.
- A static caller that only knows `BaseMessage` cannot select an overload accepting a particular subclass. `<T extends BaseMessage>` still exposes the base type to static selection and did not fix the compared example. The compiler is not generally preferring the least-specific runtime type; Groovy 4's second, reflective dispatch had concealed the distinction.

This comparison used both plain `@CompileStatic` classes and transactional services, without Hibernate proxies:

| Compared path | Groovy 4 stack | Groovy 5.1.3 stack |
|---------------|----------------|-------------------|
| Ordinary static call with a base-typed parameter to public overloads | `Object` overload | `Object` overload |
| Static wrapper, then closure calling protected overloads | Runtime child overload | Statically selected `Object` overload |
| Static caller into a transactional overloaded method | Runtime child body after reflective redispatch | Selected `Object` body |
| Same transactional call with `<T extends BaseMessage>` | Runtime child body after reflective redispatch | Selected `Object` body |
| Explicitly dynamic caller into public overloads | Runtime child overload | Runtime child overload |

If the application's contract is runtime overload selection, mark only that dispatch boundary dynamic:

```groovy
import groovy.transform.CompileDynamic

@CompileDynamic
String dispatch(BaseMessage message) {
    handler.process(message)
}
```

Keep the typed overload implementations statically compiled and retain `process(Object unsupported)` as the unsupported-type guard. Do not replace the guard with an `instanceof` router before identifying the changed dispatch. Compare concrete instances with proxies separately: runtime dispatch does not itself unwrap a base-class Hibernate proxy. Verify every supported subtype and the unsupported-type path through public entrypoints.

### Static Compilation and Version-Specific Compiler Workarounds

- Groovy 5 can infer `Object` where earlier versions accepted a more specific type. Prefer typed locals across `try`/`catch` and `Map.get(null)` over ambiguous null-key subscript expressions; do not disable static checking broadly.
- If a nested ternary selecting class literals produces a cast of a `Class` object to one of the represented domain types, inspect the generated code. Explicit `Class<?>` locals with `if`/`else` assignments fixed the checked application case. Treat this as a targeted compiler workaround, not a reason to replace every ternary.
- In the checked Groovy 5.1.3 / Grails 8 snapshot, a typed local named `first` in transactional code contaminated the generated `setTargetDatastore` method's inferred type, producing a cast of the datastore to an unrelated application class. Confirm the generated bytecode and use a descriptive local name as a narrowly scoped workaround; do not change datastore wiring to satisfy that cast. Recheck the framework/compiler fix before generalizing this snapshot defect.
- [GROOVY-12437](https://issues.apache.org/jira/browse/GROOVY-12437) is a confirmed separate-compilation defect affecting ternary expressions selecting inherited generic map properties under `@CompileStatic`: compiling the superclass and subclass together succeeds, but compiling the subclass against superclass bytecode reports `unable to resolve class E` at line `-1`. The [standalone reproducer](https://github.com/jdaugherty/groovy5-incrementa-compilation-issue) passes on Groovy 4.0.33 and fails on 5.0.8, 5.1.2, and 5.1.3, including JDK 21 and 25 for 5.1.3. Replace the affected ternary with `if`/`else` assignments explicitly cast to the concrete map type, and reference the issue beside that code. This localized workaround allows `GroovyCompile.options.incremental = true`. Verify with a clean `--no-build-cache --rerun-tasks` build, followed by a source edit and an incremental `--no-build-cache --info` build without `--rerun-tasks`; also compile the subclass alone against superclass bytecode to isolate the reported defect. Disabling incremental compilation is a fallback if affected code cannot be localized. Check the issue's fix version before removing the source workaround. Do not call it a Gradle defect solely because an incremental build exposes it.

## Configuration Changes

Review application configuration after booting with the Spring Boot properties migrator.

- Rename Spring Boot MongoDB auto-configuration properties from `spring.data.mongodb.*` to `spring.mongodb.*`.
- Do not change Grails GORM MongoDB `mongodb.*` properties for this Spring Boot rename.
- Jackson configuration under `spring.jackson.*` was reorganized. Watch for migrator output such as `spring.jackson.read.*` moving to `spring.jackson.json.read.*`, `spring.jackson.write.*` moving to `spring.jackson.json.write.*`, and `spring.jackson.parser.*` moving to `spring.jackson.json.read.*` where equivalents exist. The migrator detects most of these, not all.
- DevTools live reload is disabled by default. Set `spring.devtools.livereload.enabled: true` under the development environment if needed.
- The Spring Boot `BuildInfo` task writes `META-INF/build-info.properties` by default. If the build, packaging or tests read `build-info.properties` from the root of the generated resources directory, read `META-INF/build-info.properties` instead or configure the task's `filename` property.
- Liveness and readiness probes are enabled by default on the health endpoint, and the `liveness` and `readiness` health groups are exposed automatically. Disable through `management.endpoint.health.probes.enabled: false` only if required.
- Logback's default charset for log files is now `UTF-8`. Console output uses `Console#charset()` if available, otherwise `UTF-8`. Switch log scraping tooling that assumed the platform-default charset to UTF-8.
- Spring Data JPA repository bootstrap modes were tightened. GORM is unaffected; an application that also uses Spring Data JPA directly should review any `spring.data.jpa.repositories.bootstrap-mode` setting.
- Derby support is deprecated in Spring Boot. Grails does not use Derby by default; an application that added Derby through Spring Boot auto-configuration should plan a move to H2, HSQLDB or another supported database.
- **`Resource[]` values expand location patterns.** A `classpath*:` or other location pattern converted to `Resource[]` now resolves to every matching resource, or to an empty array when nothing matches, as in a plain Spring application. Previously the value was converted one element at a time as a single `Resource`, so `classpath*:META-INF/spring.integration.properties` became one resource with the pattern as its path, and loading it failed when it was opened. This applies to `Resource[]` properties of beans declared in `resources.groovy`, `doWithSpring`, XML or by a library (a single pattern, a comma-delimited String, or a list or array of locations), to `@Value` injection into a `Resource[]` field, and to `config.getProperty(name, Resource[])`, including list-valued configuration entries. Placeholders in the pattern are resolved from the environment. A `Resource` value still resolves a single location.
  - A list or array of locations is collected into a set: a list that names the same location twice now gives one resource, while a comma-delimited String keeps both.
  - A `null` element in a list or array now fails the conversion instead of becoming a `null` entry, so a bean property given such a list fails at startup.
- **Configuration lists are exposed as indexed properties.** A non-empty list loaded from `application.yml` or `application.groovy` reaches Spring's `Environment` under indexed names such as `app.names[0]`, which lets Spring Boot bind each element to its declared type and resolve placeholders inside scalar lists. The list itself is no longer a property of the `Environment`: `environment.getProperty('app.names', String[])` returns `null` and `environment.containsProperty('app.names')` returns `false`. Search for `environment.getProperty(<name>, List)`, `getProperty(<name>, String[])` and `containsProperty(<name>)` on list settings.
  - Bind the list with `@ConfigurationProperties`, or with `Binder.get(environment).bind('app.names', Bindable.listOf(String)).orElse([])`, using `Binder` and `Bindable` from `org.springframework.boot.context.properties.bind`. `Binder` accepts a name only in Spring Boot's canonical form (lowercase, dot-separated, kebab-case); for a name built from a value such as the environment name, pass `ConfigurationPropertyName.adapt(name, '.' as char)` from `org.springframework.boot.context.properties.source` instead of the string.
  - Whole-list access through `grailsApplication.config.getProperty('app.names', List)` remains supported.
  - A `${...}` placeholder that names a list of plain values, such as `@Value('${app.names}')`, still resolves to the elements joined by commas, as in Grails 7. A placeholder that names a list of objects has nothing to resolve to: the injection point receives the placeholder text, or its default when it has one. Bind such a list instead.
  - Grails' own list settings, `grails.plugin.fields.exclusions.input`, `grails.plugin.fields.exclusions.display`, `grails.plugin.fields.exclusions.list`, `grails.gsp.tldScanPattern` and `spring.gsp.templateRoots`, accept either form.
- If `grails.mail.overrideAddress` is set, it now also replaces a `from` set in `sendMail`. To keep the application's sender while still redirecting every recipient, use `grails.mail.overrideToAddress` in its place.
- Replace every `grails.controllers.upload.*` property with its `spring.servlet.multipart.*` equivalent: `maxFileSize`, `maxRequestSize`, `location`, `fileSizeThreshold`, `enabled`, `resolveLazily` and `strictServletCompliance`. Grails 8 fails at startup, with migration instructions, while any property remains under `grails.controllers.upload`. That check is a migration aid for Grails 8 only and is removed in Grails 9, after which leftover properties are silently ignored, so finish the rename on Grails 8.
  - With no limits configured, Spring Boot's defaults apply: 1MB per file and 10MB per request. Set `spring.servlet.multipart.maxFileSize` and `spring.servlet.multipart.maxRequestSize` explicitly to preserve existing uploads, and check the servlet container's overall request limit as well.
  - The multipart location no longer defaults to `System.getProperty("java.io.tmpdir")`. When `spring.servlet.multipart.location` is not set, uploads use the servlet container's temporary directory, which matters when that storage is constrained or the application expects uploaded files in `java.io.tmpdir`.
- Grails no longer registers a `messageSource` bean. Spring Boot's `MessageSourceAutoConfiguration` supplies a standard `ResourceBundleMessageSource`, and Grails contributes the list of base names, recorded at build time. Most applications need no change; check the following.
  - `spring.messages.*` was ignored before and is now honored in full: `encoding`, `cache-duration`, `fallback-to-system-locale`, `always-use-message-format`, `use-code-as-default-message` and `common-messages`. Search the configuration for `spring.messages` entries that never took effect.
  - `grails.i18n.cache.seconds` is deprecated in favor of `spring.messages.cache-duration`. It is still translated into that setting, with a deprecation warning, and as before applies only when reload is enabled; an explicit `spring.messages.cache-duration` wins. `grails.i18n.filecache.seconds` has no replacement and is ignored.
  - `grails.i18n.availableLocales.includePlugins` is replaced by `grails.i18n.include-plugin-bundles`, which governs both message resolution and the locales a language selector offers.
  - Plugin message bundles must use the plugin's namespace: a plugin named `common` ships `common.properties` or `common-<purpose>.properties`, with matching locale variants. Rename generic plugin bundles such as `messages.properties`; the plugin build fails otherwise (`generateI18nDescriptor` rejects colliding names). A namespaced bundle also works under Grails 7, so one source branch serves both. Application bundles are unaffected.
  - Every bundle needs a file without a locale suffix. An application shipping only `messages_de.properties` fails the build.
  - A base name ending in a valid locale identifier is ambiguous: `api_fr.properties` reads as base name `api` in French, and `api_errors.properties` beside `api.properties` reads as a mistyped locale variant. Both fail the build; declare the base names in `build.gradle` with `grails { i18n { basenames = ['api', 'api_errors'] } }`.
  - `org.grails.spring.context.support.PluginAwareResourceBundleMessageSource` and the Grails fork of `ReloadableResourceBundleMessageSource` beside it are removed. Code that injects `MessageSource` is unaffected. Code that cast the bean to either type, or called `getBundleCodes()` or another Grails-specific method such as `getMergedPluginProperties`, must inject `MessageSource` and use its interface, or declare its own `messageSource` bean, which Boot's auto-configuration backs off from. For a known code catalog, enumerate its namespaced `ResourceBundle` keys and resolve the text through the public `MessageSource` API so application overrides remain effective. Test catalog/search and validation-error paths as well as individual message lookups.
  - The locales offered by `g:localeSelect available="true"` come from the recorded bundles and follow the plugin set message resolution uses: a plugin that was evicted, excluded or failed to load no longer contributes a language entry. An application that declares its own `AvailableLocaleResolver` bean must supply the effective descriptors instead of a `ClassLoader`.
  - Editing a value in an existing bundle still reloads without a restart in development. Adding or removing a base name now needs a restart.
- If Liquibase `includeAll` encounters compiled script/closure `.class` files beside Groovy changelogs, restrict discovery with `endsWithFilter: '.groovy'`. Verify existing source changelogs are still discovered; do not alter already-applied changesets to suppress resource warnings.
- Preserve explicit metadata keys with `Metadata.current.getProperty('info.app.name', String, null)` and `getProperty('info.app.version', String, null)` rather than subscripting `Metadata`. The convenience getters `getApplicationName()` and `getApplicationVersion()` read those same keys; `applicationName` and `applicationVersion` are their Groovy bean-property spellings, not different configuration keys. The name getter supplies `grailsApplication` as its default, and the version getter supplies `null`.
- Audit external and gitignored configuration loaded through `grails.config.locations`, including development-only blocks and home-directory overrides. A `ConfigSlurper` stack trace naming a generated `Script_...` class can originate there; test-environment startup does not exercise those branches. Replace remaining `Metadata.current['info.app.name']` and `grailsApplication.metadata['info.app.version']` lookups with their typed accessors, and keep private local configuration out of commits.
- Move `spring.http.client.connect-timeout` and `read-timeout` to `spring.http.clients.*`, and `spring.http.client.factory` to `spring.http.clients.imperative.factory` in Boot 4.1. Preserve the chosen HTTP implementation and existing timeout values.
- Replace `management.endpoints.enabled-by-default` with `management.endpoints.access.default` (`none` or `unrestricted` as appropriate), and per-endpoint `enabled` flags with `access` levels. Check web exposure independently, because accessibility and exposure are separate settings.
- Replace `server.maxHttpHeaderSize` / `server.max-http-header-size` with `server.max-http-request-header-size` and an explicit data size, such as `128KB`. Reconcile duplicate old/new declarations so the effective request-header limit is retained.

## Micronaut Integration

Micronaut-enabled Grails 8 applications have stricter requirements than ordinary Grails 8 applications.

- Run Micronaut-enabled applications on JDK 25 or later.
- Apply a Micronaut-compatible Grails BOM as `enforcedPlatform`, not plain `platform`.
- Use `grails-micronaut-bom` for the default Micronaut setup.
- Use `grails-hibernate5-micronaut-bom` for Micronaut with Hibernate 5.
- Use `grails-hibernate7-micronaut-bom` for Micronaut with Hibernate 7.
- Remove explicit `BootArchive.loaderImplementation = LoaderImplementation.CLASSIC`; Spring Boot 4 removed the CLASSIC loader.
- Do not disable `grails { micronautAutoSetup = false }` unless the build intentionally owns all Micronaut annotation processors and BOM validation.

Example:

```groovy
dependencies {
    implementation enforcedPlatform("org.apache.grails:grails-micronaut-bom:$grailsVersion")
    implementation 'org.apache.grails:grails-micronaut'
}
```

## Jackson and JSON Rendering

Grails 8 follows Spring Boot 4 and Jackson 3.

- Jackson annotations remain under `com.fasterxml.jackson.annotation.*`.
- Jackson databind moves from `com.fasterxml.jackson.databind.*` to `tools.jackson.databind.*`.
- The auto-configured mapper is `tools.jackson.databind.json.JsonMapper`, not Jackson 2 `ObjectMapper`.
- Prefer `JsonMapper.builder().build()` for direct mapper construction.
- Jackson 3 exceptions are unchecked `JacksonException` types.
- Default dates are ISO-8601 strings instead of numeric timestamps.
- To keep Jackson 2 defaults temporarily, set `spring.jackson.use-jackson2-defaults: true`. Spring Boot 4 still manages the Jackson 2 BOM; this is a migration aid only.
- Update injection points of the auto-configured mapper from `com.fasterxml.jackson.databind.ObjectMapper` to `tools.jackson.databind.json.JsonMapper`. The mapper is immutable: its configuration is locked after `build()`. The `readTree(String)`, `writeValueAsString(...)` and `readValue(...)` method names are unchanged.
- Jackson 3 auto-detects all Jackson modules on the classpath by default. To stop an unwanted module being picked up, the upgrade guide gives the switch as `spring.jackson.find-and-modules: false`; confirm the property name against the Spring Boot application properties reference of the target version before relying on it.

For custom mapper configurations:

- Declare bean return types as `JsonMapper`, retaining bean names/qualifiers and the intended primary bean. An `ObjectMapper` return type may not suppress Boot's format-specific default. Verify the real application context, not only test mapper replacements. Custom builders can use `configureForJackson2()` to preserve published defaults while running Jackson 3; Boot properties do not configure manually constructed mappers.
- Move custom serializers/deserializers to `ValueSerializer`, `ValueDeserializer`, and `SerializationContext`; modifiers use `ValueSerializerModifier` / `ValueDeserializerModifier` with `BeanDescription.Supplier`. Configure mix-ins on the builder before building the immutable mapper, including `MixInResolver.hasMixIns()` and `snapshot()` for custom resolvers. Jackson 3 includes java.time support; retain only required date-format overrides. Migrate databind annotations such as `JsonSerialize`, while retaining shared Jackson annotations.
- Preserve wire behavior explicitly: null inclusion, enum representation, plain decimals, unknown properties, compact/pretty output, date precision, local wall-clock versus offset timestamps, and `+00:00` versus `Z`. Exercise form authentication, JSON, multipart and binary HTTP paths. In Spring 7, `RestClient.Builder.configureMessageConverters` starts with an empty builder: call `registerDefaults()` before editing its converter list, then use `JacksonJsonHttpMessageConverter`; otherwise even String form bodies can lose their converter.
- Distinguish migrating application JSON from eliminating Jackson 2 jars. Trace non-constraint dependency edges, excluding shared `jackson-annotations`, and check actual replacement releases. Libraries compiled against Jackson 2 cannot use Jackson 3 through exclusions or version substitution. Also search legacy wrapper classes such as Batch's `Jackson2ExecutionContextStringSerializer`: changing persisted execution-context JSON requires compatibility tests against existing rows, not just a new-serializer round trip.

Spring Boot helper renames include:

| Jackson 2 helper | Jackson 3 helper |
|------------------|------------------|
| `Jackson2ObjectMapperBuilderCustomizer` | `JsonMapperBuilderCustomizer` |
| `JsonObjectSerializer` | `ObjectValueSerializer` |
| `JsonValueDeserializer` | `ObjectValueDeserializer` |
| `@JsonComponent` | `@JacksonComponent` |
| `@JsonMixin` | `@JacksonMixin` |

JSON views now use `groovy.json.JsonGenerator`. If custom JSON view converters used Grails-specific generator classes, migrate them to `groovy.json.JsonGenerator.Converter` and rename the service loader file to `src/main/resources/META-INF/services/groovy.json.JsonGenerator$Converter`.

Enum serialization changed:

- `SimpleEnumMarshaller` is the default for JSON and XML enum serialization: an enum is written as a simple string value such as `"SUBMIT"` instead of the verbose legacy format with type metadata.
- Remove explicit configuration that opted into simple enum formatting: `grails.converters.json.enum.format: simple` and `grails.converters.xml.enum.format: simple`.
- The legacy `org.grails.web.converters.marshaller.json.EnumMarshaller` and `org.grails.web.converters.marshaller.xml.EnumMarshaller` classes are removed, and no Grails 8 configuration switch restores the verbose enum-object shape. If an external API contract still requires that shape, register a custom object marshaller explicitly.
- Rendering a single enum with `render(MyEnum.VALUE as JSON)` now throws `ConverterException`; render an object, map, or explicit string instead.

## OpenAPI Documentation

For an application using Swagger/JAX-RS scanning solely to document Grails controllers, adopt `implementation 'org.apache.grails:grails-openapi'` and retain its standard `@Operation`, `@ApiResponse`, `@Parameter`, and `@Schema` annotations. See the target version's [OpenAPI guide](https://grails.apache.org/docs/snapshot/guide/REST.html#openApi), checking the branch as described above.

- The module supplies the `generateOpenApi` Gradle task through its auto-provisioned CLI companion. Use that task; do not recreate it or write a custom document generator. Remove the old Swagger Gradle plugin, its buildscript dependency, JAX-RS scanner and documentation-only JAX-RS annotations.
- Configure `grails.openapi.base-document` to retain the existing title, servers, security schemes, tag descriptions and viewer extensions. Use `annotated-only` and `paths-to-match` to retain the intended public API selection.
- Paths now come from Grails URL mappings, not `@Path`. Reconcile base-document server prefixes so `/api/v1` is not duplicated, and rename `@Parameter(in = PATH)` declarations to match actual mapping variables. Verify non-GET operations against the mappings.
- Groovy `metaClass` and validateable `errors` properties are already excluded; remove a custom model converter whose only purpose was hiding those properties.
- Generation starts an application context and needs its datasource and services. Configure the supplied task and CLI classpath for a disposable build environment when packaging must work without an external database. CLI-only dependencies belong in `grailsCli`, not the deployed runtime.
- Generate into `build/openapi`, then include the output when packaging or running the application. Do not put generated documentation into `sourceSets.main.output` or make `processResources` depend on generation: the command itself needs `classes`, creating a dependency cycle.
- For a static Redoc page, continue serving the generated YAML through the application's existing endpoint. Add springdoc only if runtime `/v3/api-docs` or Swagger UI is wanted. Test the generated contract, schema references, and the served document, not just task success.

## Hibernate and GORM

Do not assume a Grails 8 upgrade requires Hibernate 7.

- Grails 8 supports Hibernate 5 by default.
- Opt in to Hibernate 7 only when the application is ready for Hibernate ORM 7 changes.
- If application code directly imports Spring ORM Hibernate classes, replace `org.springframework.orm.hibernate5` with `org.grails.orm.hibernate.support.hibernate5` for Hibernate 5 or `org.grails.orm.hibernate.support.hibernate7` for Hibernate 7.

Hibernate 7 opt-in example:

```groovy
dependencies {
    implementation enforcedPlatform("org.apache.grails:grails-hibernate7-bom:$grailsVersion")
    implementation 'org.apache.grails:grails-hibernate7'
}
```

If moving from Hibernate 5 to Hibernate 7, audit direct Hibernate API use:

| Hibernate 5 call | Hibernate 7 replacement |
|------------------|-------------------------|
| `session.save(entity)` | `session.persist(entity)` |
| `session.update(entity)` | `session.merge(entity)` |
| `session.saveOrUpdate(entity)` | `session.persist(entity)` or `session.merge(entity)` based on entity state |
| `session.delete(entity)` | `session.remove(entity)` |
| `session.load(Class, id)` | `session.getReference(Class, id)` |
| `session.get(Class, id)` | `session.find(Class, id)` |

Also check for:

- Removed Hibernate annotations (`org.hibernate.annotations.*`) on Java or Groovy classes that use Hibernate annotations directly. Domain classes that use the `mapping { }` DSL are not affected.
  - `@Where`: replace with `@SQLRestriction`.
  - `@WhereJoinTable`: replace with `@SQLJoinTableRestriction`.
  - `@Proxy`: remove; proxy configuration is no longer supported.
  - `@LazyCollection`: remove and use `@ManyToMany(fetch = FetchType.LAZY)` or `EAGER` directly.
  - `@Persister`: remove; custom persisters are no longer supported.
  - `@SelectBeforeUpdate`: remove; the behavior is configurable through `@DynamicUpdate`.
  - `@Loader`: remove; use `@SQLSelect` on the entity itself.
- Removed `CascadeType.SAVE_UPDATE`: replace `cascade = CascadeType.SAVE_UPDATE` with `cascade = CascadeType.ALL` or `cascade = [CascadeType.PERSIST, CascadeType.MERGE]` as appropriate. Persisting a transient entity that has detached associations now throws `EntityExistsException` instead of silently merging.
- The automatic `cascade = CascadeType.PERSIST` on `@Id` and `@MapsId` associations was removed. Where it was relied on, add an explicit `cascade = CascadeType.PERSIST` to the `@ManyToOne` or `@OneToOne` carrying `@MapsId`.
- Detached `refresh()` or `lock()` calls, which now throw `IllegalArgumentException`. Re-attach the entity first with `session.merge()`, or reload it, when the session has been closed or the entity evicted.
- Native SQL query results (through `executeQuery`, `withCriteria`, or a raw `Session.createNativeQuery`) now return `LocalDate`, `LocalTime` and `LocalDateTime` instead of `java.sql.Date`, `java.sql.Time` and `java.sql.Timestamp`. Update casts of such results, or set `hibernate.query.native.prefer_jdbc_datetime_types: true` to restore the `java.sql` types during a phased migration.
- `StatelessSession` participating in second-level cache by default. Call `session.setCacheMode(CacheMode.IGNORE)` inside `withStatelessSession` for intentional cache bypass.
- DDL differences with `dbCreate` of `create-drop` or `update`, or a schema generated with `schema-export`. Validate the schema diff of an existing database before enabling `dbCreate = 'update'` or running Liquibase migrations.
  - All databases: `char` / `Character` fields map to `varchar(1)` instead of `char(1)`.
  - Oracle: `float` / `double` fields map to `binary_float` / `binary_double`. Set `hibernate.dialect.oracle.use_binary_floats=false` to restore the old mapping.
  - Oracle: `Timestamp` column precision is 9 digits. SQL Server: 7 digits. Existing columns need a migration.
  - MySQL / MariaDB: array-mapped columns are stored as JSON instead of `VARBINARY`. Set `hibernate.type.preferred_array_jdbc_type=VARBINARY` to restore the old behavior.
- Hibernate 6 intermediate changes:
  - An HQL query with a `JOIN` but no explicit `SELECT` clause returns the root entity type (`List<Person>`) instead of `List<Object[]>`. Add an explicit `SELECT` clause to keep the tuple result.
  - Ordinal HQL parameters are 1-based instead of 0-based. Only raw HQL strings with ordinal parameters need updating; dynamic finders and the criteria API are not affected.
  - The legacy Hibernate `Criteria` / `DetachedCriteria` API is removed. GORM's `createCriteria()` and `withCriteria()` DSL are not affected.
  - String-based boolean type names in a mapping, such as `type: 'yes_no'`, move to the corresponding converter class (`YesNoConverter`).
  - `jakarta.persistence` remains required.

GORM behavior changes:

- Unconstrained persistent domain properties are nullable by default in Grails 8. Previously every domain property without an explicit constraint had an implicit `nullable: false`, so `save()` failed validation when it was `null`.
- The change also reaches the schema: columns of unconstrained properties are nullable by default, so schema creation and schema migration can produce nullable columns where Grails 7 produced `NOT NULL` columns. Review the existing schema and declare `nullable: false` on properties that must stay required before running schema migration or diff tools.
- Declare `nullable: false` on required domain properties, or restore the previous application-wide default with the new `grails.gorm.default.nullable: false`. The existing wildcard form works too: `grails.gorm.default.constraints = { '*'(nullable: false) }` in `application.groovy`.
- `Validateable` command-object properties remain non-nullable by default, except collections and maps, which remain nullable by default.
- The exception is a property a command object imports with `importFrom` from a domain class that declares no `nullable` constraint for it: it gets the domain class's nullable default, so it is now nullable unless the required-by-default behavior is restored. Search command objects for `importFrom`.
- Domains mocked with `DataTest` or `DomainUnitTest` use the same settings as the running application, read from `application.yml` or `application.groovy`. A specification can also set them by overriding `doWithConfig`: `Closure doWithConfig() {{ config -> config.grails.gorm.default.nullable = false }}`.
- Check command objects that import domain constraints too: making an existing domain field explicitly required can also restore the intended requiredness of an imported constraint. Verify both domain and command validation rather than changing the application's global nullability setting to satisfy one fixture.
- GORM dynamic methods such as `save()`, `delete()`, `get()`, `load()`, and `merge()` are not affected by Hibernate `Session` API removals.
- Migrate legacy `namedQueries` definitions, and calls such as `Publication.recentPublications.list()`, to `where` / `DetachedCriteria` queries. `namedQueries` was already deprecated, and Grails 8 [removed the classes that ran a named query](https://github.com/apache/grails-core/commit/2110c45f367b9955da734afcc5dfd56d133941f7) (`NamedCriteriaProxy`, `NamedQueriesBuilder`). A domain class that still declares `namedQueries` compiles and still gets a static method per named query, so a clean compile is not proof the query runs. Retain every predicate and verify excluded, unrelated, and absent rows.
- The `sort` and `order` arguments of `list()`, dynamic finders, where queries, criteria queries and `listOrderBy*` are validated on both Hibernate versions: `sort` must be a dotted property path that resolves through the mapping (a dotted alias such as `c.name` is still passed through), and `order` must be `asc` or `desc`. Anything else throws `IllegalArgumentException` (`Invalid sort property` / `Invalid sort direction`) instead of being ignored or failing inside Hibernate. On Hibernate 7, `list()` also rejects a `fetch` key that is not a persistent property.
- **Compile-time GORM query safety check.** Passing `find`, `findAll`, `executeQuery`, `executeUpdate`, or a Neo4j Cypher equivalent a `String`-typed variable that was built from an interpolated `GString` is now a compile error, as in `String query = "from Book as b where b.title = ${params.title}"` followed by `Book.executeQuery(query)`. Passing the `GString` directly is safe, because GORM binds the interpolated value as a query parameter; assigning it to a `String` first embeds the value as raw query text. The check runs wherever `grails-datamapping-core` is on the compile classpath, with no opt-in.
  - If the interpolated expressions are values, keep the query a `GString` all the way to the call, or use named or positional parameters.
  - If they are HQL fragments, such as restrictions or an `order by` clause chosen at runtime, do not switch to a `GString`: GORM would bind the fragment as a parameter value and the query would no longer work.
  - Interpolating or appending constant text is not flagged: string literals, `static final` constants, ternaries over them, and locals that only ever hold such text. The usual dynamic-restriction idioms, including `query += " and ..."`, compile unchanged.
  - For a reviewed call site that is safe, add `@SuppressWarnings("GormUnsafeQueryString")` to the declaration of the variable that holds the query text, or to the enclosing method, and document why, rather than restructuring the code.
  - The whole check can be disabled by setting the `protectSqlInjectionAttacks` system property to `false` (default `true`). That is a build-wide, last-resort switch for a false positive with no workaround, not a substitute for `@SuppressWarnings`.
  - The same check emits compile-time warnings, which do not fail the build, for a `String`-typed field assigned an interpolated `GString` and later read through `this.field` in a query call, and for query text built with `+` concatenation from a non-constant value. Review both.
- Check single-result criteria explicitly. In the tested pre-release Grails 8 build, `withCriteria(uniqueResult: true)` returned a `PagedResultList`; Grails 7 dispatched it to `get`. Use `createCriteria().get { ... }` for entity/scalar results, preserving `cache` and valid fetch joins in the DSL. Test both absent and present results, especially row-count projections where a nonempty list containing zero is truthy. Treat this as a version-specific regression, not a universal documented removal.
- Nested transaction propagation was corrected in [Grails commit bd268672976](https://github.com/apache/grails-core/commit/bd268672976fff60853021dd659cd6d55482e631): `REQUIRED` joins an active transaction, while `REQUIRES_NEW` suspends it and uses a separate session, then restores the outer session. This also affects SimpleMap-backed domain/service unit tests. A thread-bound test session does not mean every service invocation shares one transaction or first-level cache; check the service annotations and explicit `withNewTransaction` calls before attributing changed fixture visibility to a regression.
- **A transaction started inside another joins it on GORM for MongoDB and on the simple map datastore**, which `DataTest` unit tests run on, as Hibernate and Neo4j already did. Search for transactional service methods called from other transactional methods and for `withTransaction` inside `withTransaction`. On MongoDB the inner transaction used to commit on its own on the same session, and the outer commit then did nothing. Now everything commits once, when the outer transaction does, and a joined transaction that fails rolls the whole transaction back.
  - Simple map datastore: a nested transaction's writes are flushed at the outer commit instead of when it returns. Its sessions also share one identifier count now, so an insert in `withNewSession` inside a transaction no longer overwrites another session's unflushed insert.
  - Neo4j: a joined transaction that fails now only marks the transaction rollback-only, where it rolled back on the spot. Writes the outer code makes after catching the failure roll back with it, where they used to be committed as they ran.
  - `PROPAGATION_REQUIRES_NEW` suspends the surrounding transaction and runs in a session of its own, on Neo4j too. The new session starts empty: load an entity of the surrounding transaction again inside it, and settings made on the surrounding session, such as the database or collection chosen with `withDatabase` or `withCollection`, do not carry over.
  - `PROPAGATION_NOT_SUPPORTED` suspends the surrounding transaction, `PROPAGATION_MANDATORY` inside a transaction works where it always threw, and `PROPAGATION_NEVER` and `PROPAGATION_NESTED` inside a transaction throw.
  - A read-write transaction started inside a read-only one joins it and is read-only, as on Hibernate. A write it leaves queued is not saved by the read-only commit, which logs a warning naming the session. Call such a method from read-write code, or give it `PROPAGATION_REQUIRES_NEW`. A write it flushes with `save(flush: true)` is saved, but outside any transaction, so a later failure cannot roll it back.
  - Transactional code run from a transaction's `afterCommit` or `afterCompletion` callbacks, such as an `@TransactionalEventListener`, begins a transaction of its own.
  - A read-only transaction puts the session's flush mode back when it completes, on Neo4j too. It used to leave the session in `COMMIT` mode, so a session bound for the whole request was not flushed when the request ended.
  - Committing a transaction that is no longer active throws `IllegalTransactionStateException`, where the commit used to be skipped silently.
  - Without multi-document transactions, GORM for MongoDB does not flush before a query while a transaction is in progress, so a write a joined transaction leaves queued is not visible to a later query in the same transaction. Use `save(flush: true)` where a later query must see the write. This includes integration tests annotated with `@Rollback`, whose service calls now join the test's transaction.
  - With multi-document transactions enabled on MongoDB, GORM flushes before a query inside the server-side transaction, a read-only transaction no longer starts a server-side transaction, and beginning a transaction on a session whose server-side transaction is still active throws `IllegalTransactionStateException` instead of discarding it.
- **A read-only transaction no longer flushes the session on GORM for MongoDB.** `@ReadOnly` and `@Transactional(readOnly = true)` commit without flushing on every GORM datastore; Hibernate, Neo4j and the simple map datastore already behaved this way. On MongoDB, a write saved without `flush: true` before read-only code ran in the same session used to be persisted as a side effect of the read. It is now left queued, and is written by whatever flushes the session next (a read-write commit, `save(flush: true)`, the flush at the end of the request), or discarded when the session closes if nothing does. Flush such writes with `save(flush: true)`, or perform them in a read-write transaction. To find them, look for the warning `org.grails.datastore.mapping.transactions.DatastoreTransactionManager` logs when a read-only transaction commits while its session holds pending inserts, updates or deletes.
- GORM for MongoDB index and criteria changes:
  - Indexes are built when the datastore starts, not when it is created. Nothing changes in an application, where Spring starts the datastore before `BootStrap` runs. Code that creates a `MongoDatastore` itself, also with its own `MongoClient`, and expects the indexes once the constructor returns has to call `start()`; the first GORM query or save on a datastore nothing has started also starts it.
  - A declared `unique` index that cannot be built because documents already share a value no longer ends the index build with a `DuplicateKeyException`, which with the default synchronous build stopped the application from starting. It is logged at `ERROR`, the remaining indexes are built, and the application starts without that index. Check the startup log, remove the duplicates and build the index again.
  - Each connection builds only the indexes of the domain classes mapped to it, including those mapped to `ConnectionSource.ALL`. A named connection used to create the indexes of classes mapped only to another connection as well, in its own database; indexes already created there are left in place.
  - `findWhere`, `findAllWhere`, criteria queries, the `where` DSL and dynamic finders throw `InvalidDataAccessResourceUsageException` when a `Map` passed as a criterion value contains a key starting with `$` at any level, on any property type. Maps without such keys are still sent as literal subdocument comparisons, and geospatial criteria are exempt. Code that deliberately passed an operator document through a criteria value should use the native `find(Bson)` API.
- Verify `ident()` and reload/proxy behavior for composite keys and renamed single-property identifiers. In the checked pre-release build, `ident()` read only the ordinary `id` property, returning null for a persisted composite-key domain and for a key mapped with `id name: 'code'`; Grails 7 used the mapped property or constructed a serializable identifier from the composite key fields. Consequently `load(entity.ident())` returned null before any proxy was created. Regression tests reproduced both cases on Hibernate 5 and Hibernate 7. Treat this as a framework regression to isolate and fix, rather than excluding composite-key domains from coverage or interpreting the null as a proxy-initialization change.
- Initialization does not change a Hibernate base-class proxy into its concrete subtype. Before subclass/trait-only access, unwrap through the supported Hibernate API and retain the resulting instance; a cast alone is not unwrapping. A collection `find` still returns the original element even if its predicate unwrapped it; use `findResult` when the intended result is the unwrapped matching object. Equivalent warmed-call-site failures were reproduced on the old stack too, so distinguish a pre-existing proxy assumption from a new compiler regression.
- Unidirectional `hasMany` join-table foreign-key column names under Hibernate 7 are derived from the physical table name of the associated domain class (e.g. `catalog_book_id` instead of `book_id`), which includes an explicit `table` mapping and any change a custom physical naming strategy makes. Search for a `hasMany` with no `belongsTo` and no reciprocal `hasMany` on the other side whose element class maps its table to another name. The owner's column in the same join table (`shelf_id`) is unchanged, and a bidirectional many-to-many association is not affected. Configure `joinTable: [name: 'shelf_books', key: 'shelf_id', column: 'book_id']` explicitly, with the names the database already uses, to preserve an existing Grails 7 schema; declaring all three names keeps the naming strategy from changing the mapping.
- The element column of a basic, non-enum collection, such as `static hasMany = [items: String]`, is now resolved through the naming strategy's column rules instead of its table rules for the property-name prefix. This matters only where a custom `PhysicalNamingStrategy` implements `toPhysicalColumnName` and `toPhysicalTableName` differently. An enum collection is unaffected.

### Validation Error Reset and Snapshot Regressions

- For Hibernate-backed domains, compare explicit `validate()`, validation on `save()`, and dirty-instance flush separately. The verified Grails 7 behavior starts validation with fresh errors while retaining data-binding failures; manually added global `reject(...)` errors and ordinary `rejectValue(...)` errors are reset. Constraints and `beforeValidate` can then add current errors. `deepValidate: false` changes association validation, not that reset contract. Consult the target version's [validate reference](https://grails.apache.org/docs/snapshot/ref/Domain%20Classes/validate.html), checking its version as above.
- A checked Grails 8 pre-release retained global errors and consequently rejected saves/flushes that passed on Grails 7. Both Hibernate implementations needed the compatibility correction. Do not turn global errors into artificial field errors, remove constraints, or weaken assertions merely to make that snapshot pass. Verify the target framework contains the fix. If an application rule must survive repeated validation, check it at the appropriate operation boundary or reproduce it through the intended validation rule; do not assume the mutable errors object is durable state.

## Web Layer and Content Negotiation

### Binding Include Lists and Nested Association IDs

- Preserve the distinction between an omitted/null include list and `include: []`: the latter binds nothing. Passing an empty list to a lower-level binding API is not a spelling of the default behavior. Keep deliberate include/exclude restrictions, `bindable: false`, and deny-by-default configuration intact; consult the target version's [bindData reference](https://grails.apache.org/docs/snapshot/ref/Controllers/bindData.html).
- With explicit includes, include the actual nested path required by the operation, such as `lineItem.product.id`. Put a lookup code on its associated entity's ID field, not the containing object's unrelated numeric ID. Test the real parameter structure, including any values inserted by the controller, and retain a negative test for protected properties.
- In compatibility mode, dynamically constructing a command subclass should not restrict its binding to an enhanced superclass's fields. A checked pre-release did so when only the superclass was declared as an action parameter; [the framework correction](https://github.com/apache/grails-core/pull/16491) restores subclass binding while preserving explicit restrictions and `bindable: false`. Reproduce through `bindData` / the public binding API on both versions before adding application-wide allowlists to compensate. Do not infer the same defaults for secure mode.
- `bindData(target, source, [include: [...], clearMissing: true])` clears included properties that are absent from the binding source. The behavior is opt-in and requires an `include` list; calls without `clearMissing: true` keep omitted fields unchanged.
- **A date with an offset is bound to the instant it names.** When controllers, command objects and `bindData` bind a request, a date and time in ISO 8601 form, with a `T` between date and time, is read first, before the formats listed in `grails.databinding.dateFormats`. A server running in UTC sees no change for the first two cases below. Search for clients or tests that compensate for the old results.
  - `2024-05-01T10:00:00Z` was read as a time in the zone of the server, so it was bound off by that zone's offset.
  - `2024-05-01T10:00:00+02:00` had its offset left unread and was bound as a time in the zone of the server.
  - A fraction of a second of fewer than three digits, such as the `.5` of `10:00:00.5Z`, was read as 5 milliseconds rather than 500.
  - An `OffsetDateTime` or `ZonedDateTime` could not be bound from `2024-05-01T10:00:00Z` or `2024-05-01T10:00:00+02:00`, and a `LocalDateTime` or `LocalTime` could not be bound from a value with a fraction of a second. Each now binds.
  - The formats in `grails.databinding.dateFormats` are then tried in order, and a format that reads all of the value is used before an earlier one that reads only its start. A value no format reads all of is bound as before, by the first format that reads its start: `2024-05-01 10:00` is still bound as midnight by `yyyy-MM-dd`. Add a format that reads the whole value, such as `yyyy-MM-dd HH:mm`, to keep the time.
  - The date scalars of GraphQL, which read `grails.databinding.dateFormats` too, are not changed.

### Link Generation

- **Link generation is namespace-aware.** A link, form action, pagination link, sortable column link, redirect, chain or include that targets a controller without an explicit `namespace` now has the namespace resolved, nearest scope first: the current request's namespace if it defines a controller with that name, then the non-namespaced controller, then the namespace of the only controller with that name. Search the GSPs and actions of namespaced controllers for `controller:` targets with no `namespace`.
  - A name defined only in namespaces other than the default and the current one is ambiguous: Grails logs a warning, and the link must specify `namespace`.
  - A name no registered controller has stays in the namespace the link is made from, where a link previously dropped the namespace. A page rendered by a controller in the `admin` namespace that links with `controller: 'other', action: 'list'` now generates `/admin/other/list` when no `OtherController` is registered, and so does a unit test that sets `webRequest.controllerNamespace` itself. A `ControllerUnitTest`, which leaves the request namespace unset, still generates `/other/list`.
  - A redirect naming no namespace is resolved exactly as a link is. Previously it was forced into the namespace of the controller issuing it, so a redirect to a controller defined only in another namespace produced a URL for a controller that does not exist.
  - Pass `namespace` to target another namespace, and `namespace: null` from Groovy code or `namespace=""` in a GSP tag to target the non-namespaced controller explicitly.
- **A resource link targets the most specific controller serving its domain class.** A link, form or redirect built from a domain instance, such as `<g:link resource="${book}">`, `redirect book`, or the `self` link of a HAL, Atom or JSON:API response, previously always targeted the controller named after the domain class, even when that controller did not exist. It now targets the nearest controller that serves the domain class and defines the target action. A controller serves a domain class when it is named after it, extends `RestfulController` parameterised on it, such as `PeopleController extends RestfulController<Person>`, or is scaffolded with `@Scaffold(Person)`. The order is the controller handling the request, then one in the request's namespace, then the default namespace, then any namespace, with the controller named after the domain class settling a tie within a namespace.
  - An application with one controller per domain class is unaffected. Search for domain classes served by two controllers: a link rendered by one of them now stays in that controller. Pass the `controller` attribute where a link should leave the current controller.
  - `generate-controller` and `generate-async-controller` now generate `redirect action: "show", id: book.id` rather than `redirect book` after a form save or update. Change a controller generated earlier and since renamed the same way, so that it still redirects to itself.

### Content Negotiation and Rendered Output

Grails 8 supplies MIME type defaults from the framework.

- New applications no longer need to declare the full `grails.mime.types` block.
- An existing `grails.mime.types` block still replaces the defaults.
- Set `grails.mime.mergeDefaults: true` when adding custom MIME types while keeping built-in defaults.

The HTTP `Accept` header is honored for all clients by default, including browsers.

- Browser page loads still usually negotiate HTML because modern browsers rank `text/html` highest.
- Browser `fetch()` or `XMLHttpRequest` calls requesting JSON now receive JSON without relying on `X-Requested-With`.
- `respond` actions without an HTML view may now error for browser requests because browsers negotiate HTML. Add a GSP view, use `render`, or scope formats with `responseFormats` or `respond(..., formats: ...)`.
- API clients that send a specific `Accept`, such as `application/json`, are unaffected, and `withFormat { html { } json { } }` continues to serve HTML to browsers and JSON to clients that ask for it.
- `grails.mime.disable.accept.header.userAgents` now defaults to unset, so no user agents are filtered. To restore the Grails 7 behavior of ignoring the `Accept` header for browsers, set it explicitly to the previous default, the list `['Gecko', 'WebKit', 'Presto', 'Trident']`.

The HTML codec uses XML-safe escaping when `grails.views.gsp.htmlcodec` is not set, as applications generated with `htmlcodec: xml` already did.

- Applications that set `htmlcodec: xml` are unaffected; the setting can stay or be removed.
- Without the setting, non-ASCII characters are no longer written as named entities (`é` instead of `&eacute;`), and `@`, the backslash and the backtick are escaped. Update tests that compare escaped markup exactly.
- Set `grails.views.gsp.htmlcodec: html4` only where pages are served in a character set that relies on the named entities, such as ISO-8859-1.

An interceptor's `model` returns the map that an action passed to `render(template: ..., model: ...)` instead of `null`.

- The template is already rendered when `after()` runs. Changing the map has no effect on the response, and changing an immutable map throws `UnsupportedOperationException`.
- Review interceptors that check `model != null`, or use `model?.`, before changing the model. That code now also runs for template renders.
- Objects passed with `bean` or `collection` are not part of `model`.

### Multipart Requests Preserve the Outermost Wrapper

- Grails 8 intentionally keeps the outermost servlet request exposed to controllers, taglibs, and GSPs, preserving security, hidden-method, and application filter wrappers. It no longer substitutes the resolved multipart request. Migrate `request instanceof MultipartHttpServletRequest` / `StandardMultipartHttpServletRequest` checks and casts; changing only the concrete class to the interface still relies on the removed substitution.
- Use the supported upload accessors directly: `request.getFile(name)`, `getFiles(name)`, `getFileNames()`, `getFileMap()`, `getMultiFileMap()`, and `getMultipartContentType(name)`. File binding through `params` remains supported. Grails locates the resolved multipart request behind the wrappers or through the dispatcher-published attribute. When separate validation paths need a format check, inspect the multipart content type rather than the request's runtime class; upload accessors throw `IllegalStateException` on a request without resolved multipart data.
- Handle multipart validation before a generic `ContentCachingRequestWrapper` / JSON-body branch. An upload can now retain that outer wrapper, and an empty cached JSON/body buffer does not establish that its multipart file map is empty. Validate upload presence and part contents through the upload API, preserving the application's missing-part, empty-file, and non-multipart error responses.
- Verify real HTTP uploads through the application's filters, covering valid files, absent/wrongly named parts, empty files, and non-multipart requests. Check upload-limit handling with the target servlet container as described in the upgrade guide's multipart section; configure limits with `spring.servlet.multipart.*`.
- **An oversized upload now reaches the application's error handling.** An upload larger than `spring.servlet.multipart.maxFileSize` or `spring.servlet.multipart.maxRequestSize` (1MB per file and 10MB per request by default) used to abort the request inside the filter chain, leaving the container's own error page. It now surfaces during dispatch as `org.springframework.web.multipart.MultipartException` / `MaxUploadSizeExceededException`, is available to `HandlerExceptionResolver` beans, and the response is a `413` rendered through the application's error dispatch.
  - Handle it with a `"413"` response code URL mapping, such as `"413"(controller: 'errors', action: 'tooLarge')`. The mapped action runs on the container's error dispatch: `request.dispatcherType` is `ERROR`, `params` is empty, and `request.getFile(..)` is unavailable. The upload is gone; the action only renders the response.
  - The parameters of such a request are empty everywhere, which is observable in a filter that inspects `params` ahead of the dispatch. The request never reaches the originally requested controller.
  - Tomcat and Jetty behave as described. Undertow refuses the request at the HTTP layer while it reads the request entity, so no application code runs: the response is a bare `413` with an empty body, and a `"413"` mapping is not consulted. Raising `spring.servlet.multipart.maxRequestSize` raises the point at which Undertow refuses.
- **An error handler that fails is no longer forwarded to from inside itself.** A `"500"` or exception-specific URL mapping that names a controller is reached by a forward. An error handler that failed for a reason belonging to the request, such as an unparseable multipart body or a missing collaborator, used to fail again inside its own forward until the dispatch stack overflowed. The repeat failure is now logged and the exception is returned to the `DispatcherServlet`, so the response is a single container-level error. A later error on the same request, once the forward has returned, is forwarded to the error handler as before.

## Asset Pipeline Wildcard Paths

Grails 8 uses asset-pipeline 5.2, where a `%` or `*` component of an asset path, in a `require` directive, an `<asset:...>` tag, or a Sass import, stands for exactly one directory wherever the asset is found.

- Grails 7 let one `%` stand for several directories when the asset came from a jar, as every webjar does, or from the manifest of a packaged application. A path such as `webjars/%/dist/jquery.js` resolved in Grails 7 and resolves to nothing in Grails 8.
- Search the application's asset manifests, GSPs, and Sass imports for `%` and `*` in asset paths. Write one `%` per directory, as `create-app` generates (`webjars/jquery/%/dist/jquery.js`), or `%%` (or `**`) for zero or more directories (`webjars/%%/dist/jquery.js`).
- In a CSS `*= require` block, use `%` and `%%`, never `*` or `**`, because `*/` ends the comment.
- When several versions match, the highest wins, including for paths with one `%` per directory. Hidden directories never match, and a wildcard inside a file name, such as `jquery-%.js`, does not resolve.
- A path that no longer resolves does not fail the build: `assetCompile` logs `Unable to Locate Asset: <path>` and leaves the file out, and an `<asset:...>` tag renders the path unchanged, so the browser cannot load it. Check the `assetCompile` output for that warning after upgrading.
- The `includes` and `excludes` patterns of the `assets` block in `build.gradle` are not affected.
- Exercise a development CSS/Sass request as well as packaging. Sass-Dart may download a platform-specific Javet native library on first use. If initialization fails with a missing artifact, inspect the download URL and ensure the configured `javetBaseUrl` repository proxies the current platform/version, rather than only hosting older private releases.
- Migrate application-owned Sass `@import` to explicit `@use` / `@forward` dependencies. Configure a shared theme before forwarding Bootstrap or other configurable libraries; module isolation changes variable visibility and cross-module `@extend` behavior. Compare generated theme values and representative selectors, not just compilation, and avoid introducing a second unthemed Bootstrap entrypoint. Dependency-internal deprecation warnings need an upstream library migration rather than blindly rewriting vendored sources.

### jQuery 4 and Bundled JavaScript

- The jQuery webjar the Grails BOM manages (`org.webjars.npm:jquery`) moves from 3.7.1 to 4.0.0, so an application that references the webjar without an explicit version picks up 4.0.0 once it adopts the Grails 8 BOM. jQuery 4 drops Internet Explorer and other legacy browsers and removes long-deprecated utilities such as `jQuery.trim`, `jQuery.type`, `jQuery.isArray`, `jQuery.isFunction`, `jQuery.proxy` and `jQuery.isWindow`. Migrate the call sites, or, to stay on the previous version until the application is ready, pin `jquery.version=3.7.1` in `gradle.properties`.
- Verify the selected artifact in the browser-test/development/packaging configurations and inspect the delivered browser version. In the checked build, the browser-test classpath selected jQuery 4 from the Grails BOM while the standalone asset-development configuration still selected a declared 3.x version. Align version declarations deliberately rather than assuming a property pins every classpath.
- Replace removed utilities in application scripts and bundled plugins: `$.isArray` with `Array.isArray`, `$.isFunction` with a function-type check, `$.parseJSON` with `JSON.parse`, and `$.type` with checks appropriate to the supported input types. Audit actual callers for boxed values or cross-window objects before assuming every replacement is equivalent.
- Preserve `$.trim`'s handling of absent values: use `String(value ?? '').trim()` for nullable or numeric input, and native `.trim()` for known strings. `value || ''` incorrectly drops numeric zero. A replacement for `$.isNumeric` must deliberately handle finite numbers/numeric strings while rejecting null, booleans, blanks, and infinities; `Number.isFinite(Number(value))` alone accepts several of those invalid inputs.
- Check vendored Select2 and other initialization dependencies for removed helpers such as `$.camelCase`. An exception during their initialization can prevent the whole form/table setup. Verify actual browser interaction, not only JavaScript syntax, server responses, or the presence of generated HTML.

## TagLibs and Tests

Grails 8 recommends method-based TagLib handlers while keeping closure-based tags supported.

- Conventional method signatures `def tag(Map attrs)`, `def tag(Closure body)`, and `def tag(Map attrs, Closure body)` are discovered automatically.
- Zero-argument method tags and method tags with named parameters must use `@grails.gsp.Tag`.
- Annotate public helper methods with `@grails.gsp.NotATag` when they must not be exposed as tags.
- A `Map` parameter named `attrs` receives the full attributes map.
- A `Closure` parameter named `body` receives the tag body. A `Closure` parameter with any other name is treated as a normal named attribute and must be supplied by name, just as a `Map` parameter not named `attrs` is; in earlier iterations of method-based handlers a `Closure` parameter received the body whatever its name. Search method tags for a `Closure` parameter not named `body`, such as `def before(Closure renderer)`, and rename it.
- In Grails 8, `grails { preserveParameterNames = true }` is the default. It makes Groovy compilation retain method and constructor parameter names (`groovyOptions.parameters`); set `grails { preserveParameterNames = false }` in `build.gradle` for the previous compiler behavior. Java parameter names come from the Spring Boot Gradle plugin, which adds `-parameters` to every `JavaCompile` task.
- When parameter names are not preserved, the first `Map` parameter is bound to `attrs` and the first `Closure` parameter to `body` regardless of their names, as for closure-based tags, and a `@Tag` method with named parameters fails at runtime with `MissingMethodException`, with no compile-time warning. If TagLib classes are compiled outside the Grails Gradle plugin, or without the Spring Boot plugin's compiler configuration, add `options.compilerArgs.add('-parameters')` to `JavaCompile` tasks and `groovyOptions.parameters = true` to `GroovyCompile` tasks.
- The compile-time deprecation warning for closure-based tag fields can be silenced with the system property `grails.taglib.warnDeprecatedClosures=false`. It covers the `def` form as well as the explicitly typed `Closure` form; the tags still work and are called the same way.
- The tag dispatchers registered for a taglib's own namespace now override existing methods of the same name, so that a function-style call such as `tagLib.myTag(attrs)` returns the captured tag output rather than writing to `out`. A TagLib method that shares its name with a default-namespace tag in another TagLib is therefore shadowed by the dispatcher: rename the helper method or move it behind a different namespace. Nothing reports this at runtime by default. To audit, enable `DEBUG` logging for `org.grails.taglib.TagLibraryMetaUtils` in `logback.xml` or `logback-spring.xml` and, after startup, look for `Registering tag dispatcher <namespace>:<tagName> over existing method <Class>.<method>(<paramTypes>)` entries whose class is one of the application's TagLibs.
- Tag calls are resolved against a description each tag library contributes when it is compiled, and a resolved call is compiled into a direct invocation. The tag is still selected by name when the call runs, so overriding tag libraries and registration order behave as before, and a call into a namespace no compiled tag library declares, such as a plugin built against an earlier version of Grails, dispatches as it did. These settings of the `grails { compileStatic { ... } }` block in `build.gradle` control it:
  - `strictTags = true` makes a tag no compiled tag library declares a compilation error. By default such a tag is left to resolve at runtime and nothing is reported. Turn it on once to find misspelled tags. It checks only a call that names its namespace and one written as markup; a namespaced expression in a page is checked only when the page declares `compileStatic`.
  - `dynamicTagNamespaces = [...]` names namespaces whose tag libraries are registered while the application runs. Calls into them are never checked or rewritten and dispatch exactly as before.
  - `unqualifiedTagCalls = true` compiles calls written without a namespace; by default they dispatch exactly as before. With it on, a bare name reaches a tag only when nothing nearer answers to it. A method added while the application runs, through a plugin's `doWithDynamicMethods`, is not visible at compile time, so a call that used to reach such a method and shares its name with a tag reaches the tag instead: declare the method on the class, name the namespace in `dynamicTagNamespaces`, or call the tag with its namespace.
- Tags are no longer installed onto the metaclass of every tag library, controller and page. Calling a tag is unaffected, but `tagLib.metaClass.respondsTo(tagLib, 'someTag')` now answers `false`: call the tag, or consult the tag library lookup. `NamespacedTagDispatcher.initializeMetaClass()`, `NamespacedTagDispatcher.registerTagMetaMethods(ExpandoMetaClass)` and `TemplateNamespacedTagDispatcher.registerTagMetaMethods(ExpandoMetaClass)` are removed, `GroovyPagesMetaUtils.registerMethodMissingForGSP(...)` is retained but does nothing, and the `TagLibraryMetaUtils` methods `enhanceTagLibMetaClass`, `registerTagMetaMethods`, `registerMethodMissingForTags`, `registerNamespaceMetaProperties`, `registerPropertyMissingForTag` and `addTagLibMethodToMetaClass` are deprecated.
- An unqualified call from a controller to a tag declared as a method now returns what the tag wrote to `out`, where it used to return the method's own return value. Search controllers for bare calls to method-declared tags whose result is used. A tag called with its namespace, and any tag called from a GSP, already captured.
- When converting closure tags to methods, retain `Map attrs` and `Closure body` parameters, return-object declarations, and encoding behavior. Audit test code that assigns closures to tag properties; use method-aware mocks with cleanup instead. Helper classes stored under `grails-app/taglib` are not necessarily tag libraries, so do not mechanically convert every closure in that directory.
- Remove obsolete integration-test assignments such as `sitemeshTagLib.applyLayout = { ... }` when the method-based tag API no longer exposes that closure property. Exercise the real layout/rendering path in integration tests; replacing the tag namespace can conceal the behavior the upgrade needs to verify.
- Capture sibling tag output through the namespace, for example `tf.with(attrs, body)` rather than directly calling `with(attrs, body)`. Direct method dispatch can write into the outer output and return an `OutputProxyWriter`, placing inputs outside a form or rendering the writer's identity. Use real runtime GSP rendering to verify markup nesting and returned text; taglib unit-test metaclasses can hide the defect.
- For SiteMesh 3 migration, replace `grails-layout` with `grails-sitemesh3`, and use `grails.sitemesh.default.layout`. Replace custom inheritance from `RenderGrailsLayoutTagLib` and direct `GSPGrailsLayoutPage` access with public `g:applyLayout`, `g:pageProperty`, and `g:ifPageProperty` tag delegation. Verify nested layouts, captured page properties, titles, and email layouts.

Test changes:

- Remove custom `purgeTagLibMetaClass` properties or getters from TagLib specs.
- TagLib metadata cleanup happens automatically through the web test infrastructure.
- Mocked tag libraries are cleared after each feature method. If a spec implements `GrailsWebUnitTest` and calls `mockTagLib` directly, move the call from `setupSpec()` to `setup()`.
- Prefer native `mockTagLib(MyTagLib)` registration over replacing a controller's namespace property or constructing a custom namespace dispatcher. Compiled tag calls resolve through the registered taglib bean and can bypass controller metaclass stubs. Despite its name, `mockTagLib(Class)` registers a real, autowired bean through `defineBeans` when absent, registers its tags, and returns it; it does not create a Spock mock or spy.
- When a method tag needs a Spock spy, register that exact spy as the taglib bean under the class's fully qualified name before calling native `mockTagLib(Class)`, which reuses the bean. A bean definition with `instanceSupplier = { -> tagSpy }` can supply the instance; retain autowiring when the taglib needs injected collaborators. Wrapping the returned real bean with `Spy(...)` alone does not replace the instance used by tag dispatch. The checked Grails version has no instance-taking `mockTagLib` overload; do not present an application helper overload as a framework API.
- Match the actual method signature when stubbing tags, and write output tags to `tagSpy.out`; returning a string alone does not reproduce captured writer output. Stub object-returning tags according to their declared return contract. Use Spock's `@ConfineMetaClassChanges` for every class whose metaclass a test changes, including global Groovy mocks; automatic Grails tag metadata cleanup does not replace cleanup of unrelated test mutations.
- Distinguish rendered HTML from raw URLs in tag assertions: an `href` can correctly contain `&amp;` where the destination URL contains `&`. Update the expected markup while preserving the destination and query values, rather than disabling attribute escaping to match an old string fixture.
- Stub a void method with `>> { }` rather than `>> null` when suppressing real behavior on a spy. The latter produced a null-to-void coercion failure under the checked Groovy 5 / Spock combination.
- For SimpleMap unit fixtures consumed in an independent transaction, flush pending fixture writes before invoking the service, including reference entities that an assigned identifier alone does not persist. After it returns, the original session can still contain stale instances: use `refresh()` for updated entities or reload after clearing the session / in a fresh session for persisted-state/deletion assertions. Compare persistent identifiers when the domain uses instance equality. Apply this at verified transaction boundaries, rather than adding blanket flush/refresh calls. SimpleMap flushing makes its writes available to another session; it is not proof of database transaction isolation. In database-backed integration tests, `flush()` is not a commit, so independently transactional work needs fixtures committed outside the suspended transaction.
- Check fixture-builder APIs before adding flush options. In the checked build-test-data 6.x version, `findOrBuild` treats its map as finder/domain values: use `findOrBuild(...).save(flush: true)`, rather than adding a `flush` property to that map. Its argument handling differs from `build(flush: true, ...)`.
- Generate fixture usernames and other natural keys with a counter or UUID-derived suffix that respects field length and normalization. A millisecond timestamp is not unique during rapid fixture creation. Verify several fixtures created back-to-back and their distinct persisted identities rather than treating the resulting authentication/uniqueness failures as framework defects.
- Clear the existing `flash.now` map when resetting it in tests rather than assigning a replacement map to its read-only property. Retire test shims when the supported framework behavior now supplies that state.
- Verify domain unit tests honor `grails.gorm.default.nullable` as well as runtime validation. A pre-release testing-support defect ignored the configured required-by-default setting, so build-test-data skipped required associations and later reported missing constraints on their entities. Check a minimal domain validation test before adding more mocks; use a framework build with the constraint-evaluator configuration fix.
- `@SpringBootTest` no longer auto-configures `MockMvc`, `WebClient`, or `TestRestTemplate`. Add `@AutoConfigureMockMvc`, `@AutoConfigureWebClient`, or `@AutoConfigureTestRestTemplate` as needed.
- Replace removed `@MockBean` and `@SpyBean` with `@MockitoBean` and `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito`. The new annotations work only as fields of a test class; they cannot be declared in `@Configuration` classes, so move such declarations into the tests.
- Remove explicit `MockitoTestExecutionListener` registration.
- Update `TestRestTemplate` imports to `org.springframework.boot.resttestclient`. `RestTestClient`, with `@AutoConfigureRestTestClient`, is available as a fluent alternative.
- Several test helpers left the production classpath and are published as Gradle test fixtures. Search test imports for them and add only the fixtures the project uses, to the configuration that compiles those tests. Never add them to `implementation`.
  - `grails.plugin.json.view.test.JsonViewTest` and the related JSON view test helpers: `testImplementation testFixtures('org.apache.grails:grails-views-gson')`
  - `org.grails.gorm.graphql.testing.GraphQLSchemaSpec` and `org.grails.gorm.graphql.testing.MockDataFetchingEnvironment`: `testImplementation testFixtures('org.apache.grails.data:grails-data-graphql-core')`
  - `grails.util.GrailsWebMockUtil`: `testImplementation testFixtures('org.apache.grails.web:grails-web-common')`
  - The mock resource and plugin helpers of `grails-core`, including `MockResourceLoader`, `MockStringResourceLoader`, `MockFileResource`, `MockPluginDiscovery` and `MockGrailsPluginManager`: `testImplementation testFixtures('org.apache.grails:grails-core')`
- Add `org.junit.platform:junit-platform-launcher` to custom test runtime configurations when the testing plugins do not already supply it; Gradle 9 no longer supplies an implicit launcher.
- Helpers including `grails.util.GrailsWebMockUtil` now live in test fixtures. Tests using it need `testFixtures('org.apache.grails.web:grails-web-common')`. If production code used it to run a controller on a background thread, prefer shared service calls, or real authenticated HTTP requests when controller binding, filters, interceptors and rendering are required. Merely constructing a `GrailsWebRequest` around Spring mocks still ships test utilities and bypasses the real request lifecycle. Remove the production `spring-test` dependency and verify the resolved runtime classpaths, keeping fixtures in test or CLI-only configurations.
- When replacing synthetic controller calls with HTTP, test the wire contract through the application's HTTP-client integration harness: caller identity, repeated form values, exact binary bytes, content headers, error statuses and token cleanup. Preserve export row counts separately from file length so empty-result suppression still works. Check application-specific custom date converters against serialized execution windows; ISO local date-times preserve seconds/fractions, while date-only criteria must remain bindable as dates.
- Grails 8's built-in temporal binding accepts ISO `OffsetDateTime` values. Update tests that assumed those always bind to null; keep separate assertions for custom screen formats and zone-bearing input.
- Testcontainers 2 renames module coordinates, for example `org.testcontainers:postgresql` to `org.testcontainers:testcontainers-postgresql` and `org.testcontainers:spock` to `org.testcontainers:testcontainers-spock`.
- Check actual test counts and reports when a build uses `Test.ignoreFailures = true`; a successful Gradle exit does not establish passing tests.
- `Interceptor.throwable` is a `Throwable`, not necessarily an `Exception`. A pre-release error-handling defect cast the servlet error attribute to `Exception`, causing a second `GroovyCastException` for initialization or assertion errors before `afterView` could run. Use a framework build with the Throwable-handling fix and inspect the earlier exception to diagnose the original application failure; fixing error reporting does not fix its cause.

### Mock Response Writers, Streams, and Reset

- Spring 7 mock responses enforce writer/output-stream exclusivity. Read rendered output through `response.contentAsString` / `contentAsByteArray` instead of calling `getOutputStream()` merely to inspect a response that used the writer.
- A checked Grails pre-release selected the writer eagerly during `response.reset()`, making a subsequent CSV/binary download fail even when its query and generated bytes were correct. [The framework correction](https://github.com/apache/grails-core/pull/16490) leaves output selection lazy. Verify writer-to-reset-to-stream and stream-to-reset-to-writer with the real bound Grails web request. Remove temporary alternate-response or empty-export workarounds once the fixed framework is used.

## Plugin Compatibility

Check plugin compatibility before treating a Grails 8 runtime failure as an application bug.

- Some plugins may not yet support Spring Boot 4 or Spring Framework 7.
- Current Grails 8 includes Spring Security compatibility support and SiteMesh 3 support; do not assume early milestone compatibility warnings still apply. Existing `grails-layout` applications can retain that layout plugin.
- Audit custom Spring Security integrations separately. `RoleHierarchyImpl` is now final and constructed with `fromHierarchy`; implement `RoleHierarchy` and delegate if the application reloads hierarchy data. Legacy `org.springframework.security.access.event` classes are removed. Modern `AuthorizationEvent` exposes authentication through a supplier; verify that the configured authorization chain actually publishes the events the application needs.
- A custom role-hierarchy bean may still receive `hierarchy` assignments from the Grails Spring Security plugin during startup. Preserve that setter when changing from inheritance to delegation, and test that startup assignment does not suppress lazy database initialization or later resets.
- `DaoAuthenticationProvider` takes `UserDetailsService` in its constructor; replace the removed setter in application Spring DSL and test fixtures, then set the password encoder separately.
- Remove obsolete `portResolver` assignments from custom `LoginUrlAuthenticationEntryPoint` / `AjaxAwareAuthenticationEntryPoint` bean definitions. Their Spring Security 7 API no longer exposes that setter; retain supported settings such as `portMapper` and `redirectStrategy`. Check the specific receiving class rather than deleting every `portResolver` reference: other security components still use it. Exercise development/deployed-only bean definitions, since a test profile may skip the wiring entirely.
- Spring Security 7's `DefaultSavedRequest` no longer takes a `PortResolver`; construct it with the request and verify the resulting saved-request/redirect flow.
- Check application-owned test plugins for descriptor-name collisions with framework plugins. An application `GebGrailsPlugin` also has the logical name `geb`, even in another package, and can conflict with Grails' Geb plugin so test bootstrap never runs. Give the application plugin a distinct class/logical name, clean its generated descriptor/classes, and run a real login/bootstrap smoke test.
- Applications using Spring Batch directly also cross the Batch 6 boundary: job, step, parameter, listener, and repository classes have package/API changes. Review the Spring Batch migration guide, metadata schema, and custom repository/launcher code; compiling the Grails services alone does not verify batch processing.
- Batch 6 uses `JobExecution.getJobInstanceId()`; update mocks as well as callers. JDBC execution lookup can throw `EmptyResultDataAccessException` for an absent row where older code expected `null`; preserve the application's lookup contract and test missing and persisted executions.
- Migrate legacy Batch chunks deliberately: `.chunk(size, transactionManager)` retains the deprecated tasklet engine; `.chunk(size).transactionManager(transactionManager)` selects `ChunkOrientedStep`. In the new engine, the chunk task executor covers processing rather than the whole read/process/write transaction. If a synchronous executor establishes accounting/security context, wrap the entire transaction callback (including transaction creation and commit/rollback) with that context; do not assume a processor executor preserves the old boundary or apply this blindly to asynchronous partition executors.
- Batch 6 retry limits count retries rather than total attempts; verify the intended total attempts. Test real chunk execution, rollback, skip limits, checkpoints, scoped beans and context visibility. A final method on a step-scoped class cannot be delegated by a CGLIB proxy and may execute on uninjected proxy fields; remove `final` only where proxy dispatch is needed and test multiple actual step scopes.
- Preserve JDBC Batch metadata intentionally: Boot's ordinary Batch starter now defaults to in-memory operation; use `spring-boot-starter-batch-jdbc` or an explicit `JdbcDefaultBatchConfiguration` when retaining a database repository and its sequences.
- Select the plugin line built for the target framework. For example, `grails-spring-websocket` 3.0.x targets Grails 8 / Boot 4 / Jackson 3; 2.7.x targets Grails 7. Verify the actual broker's JSON converter and routed payloads after upgrading, not just dependency resolution.
- `grails.gorm.annotation.AutoTimestamp`, deprecated for removal in Grails 7.1, no longer exists in Grails 8; `@CreatedDate` and `@LastModifiedDate` of the same package replace it. A plugin that references the annotation can fail even if the application does not use it. Upgrade the plugin or fix its optional annotation lookup; adding application mocks does not fix the class-linkage error. Plugins doing configuration work in `doWithSpring` must use the supplied `grailsApplication` and avoid prematurely instantiating Spring Security services during bean registration.
- Check the Grails issue tracker and each plugin release notes before upgrading plugin-dependent applications.

### Known Plugin Incompatibilities

A Grails 7 plugin is not automatically incompatible with Grails 8, but resolving, appearing in the load order and letting the application start does not establish compatibility. Exercise the plugin's documented public behavior: web requests, outbound calls, serialization, commands, and any domain traits or annotations it exposes. When a released Grails 7 plugin fails, first check whether its maintainer has published a Grails 8 version. Do not add framework-internal compatibility classes to an application to work around a failure. Use the first distinctive failure to choose the next step:

- **`MissingPropertyException` or `MissingMethodException` from `Metadata`**, often as an HTTP 500 on a plugin endpoint and only when that code path runs: the plugin reads metadata through `Metadata.current.'some.key'` or `Metadata.current.get('some.key')`. It must be rebuilt against the typed API, `Metadata.current.getProperty('info.app.name', String, null)`, or `Metadata.current.navigate('some', 'nested', 'key')` for a nested value that needs no conversion. There is no compatibility shim (#16122).
- **`MalformedParameterizedTypeException` mentioning `<Trait>$Trait$FieldHelper`**, thrown by JavaBeans, Spring or Jackson introspection: the application implements a generic trait with fields that a Grails 7 plugin compiled with Groovy 4. The plugin's trait must be recompiled with Groovy 5. Non-generic traits are not affected (#16123).
- **`MissingPropertyException` for an unexpected internal property after using a plugin annotation**: a plugin AST transformation that targets a private trait method silently lost that method from the application class. The plugin author must target a member emitted on the implementing class and rebuild with Groovy 5 (#16126).
- **`NoClassDefFoundError: org/apache/groovy/runtime/ObjectUtil`** when constructing an `@Immutable` class of a plugin: Groovy 4's transformation generated the missing call. Changing application source does not fix a released jar; the plugin must be rebuilt with Groovy 5 (#16128).
- **`NoClassDefFoundError: com/fasterxml/jackson/databind/exc/InvalidDefinitionException`** at startup, usually while creating `OrderedFormContentFilter`: a plugin brings Jackson 2 Smile, CBOR or YAML dataformat artifacts while Jackson 2 databind is absent. Add `runtimeOnly 'com.fasterxml.jackson.core:jackson-databind'`, without a version when the active BOM manages it, then retest the plugin's public behavior. Jackson 2 core, annotations and the XML dataformat alone do not cause this failure (#16124).
- **`Could not find org.grails:grails-core:.`**, or another `org.grails:` coordinate with an empty version: the plugin imports a pre-Apache `org.grails` BOM and declares versionless `org.grails:*` dependencies, which Grails 8 does not publish. Use a plugin version built against `org.apache.grails`. A narrowly targeted dependency substitution rule is a temporary diagnostic or migration aid, not proof of compatibility: no complete substitution map exists, and module names and binary compatibility are not always one-to-one. A plugin author replaces `platform('org.grails:grails-bom:...')` with `platform("org.apache.grails:grails-bom:$grailsVersion")` (#16125).
- **`IncompatibleClassChangeError` involving `HttpHeaders`**, often during an outbound HTTP request: Spring Framework 7's `HttpHeaders` no longer implements `MultiValueMap`, and the plugin was compiled against Spring 6. Changing the application's dependencies cannot repair the bytecode; upgrade to a plugin release compiled for Grails 8. A plugin author removes any explicit `MultiValueMap` cast, as in `new HttpEntity(body, headers)`, and recompiles against Spring Framework 7 (#16127).
- **An unknown command of a released Grails 7 plugin that implements `grails.dev.commands.ApplicationCommand`**: keep the default `cliAutoProvision = true`, set `grails { legacyCommandSupport = true }`, and run the command with `./gradlew runCommand "-Pargs=<command> <arguments>"` or through the shell. Legacy commands create no named Gradle tasks. Script, YAML and profile commands do not need the bridge.
- **A plugin that reads `Holders.grailsApplication` from `doWithSpring` and finds nothing**: `doWithSpring` now runs before Spring Boot auto-configuration, when the global holder is not yet published. This is not a compatibility bridge: the plugin must use its injected `grailsApplication`, configuration or environment.

### Plugin Beans Register Before Spring Boot Auto-Configuration

Plugin bean definitions, from `doWithSpring` and from the new `beanRegistrar()` hook, are registered before Spring Boot expands its auto-configurations, where Grails 7 registered them after. Most applications and plugins need no action. Check these cases:

- A Boot auto-configuration bean guarded by `@ConditionalOnMissingBean` now backs off in favor of a plugin bean of the same name or type. Remove plugin code that overrode or removed Boot's default after the fact.
- `Holders.grailsApplication` is published only after the plugin `doWithSpring` and `beanRegistrar()` callbacks complete. Search plugin `doWithSpring` closures for `Holders` and use the plugin's `grailsApplication` property instead. `grailsApplication.config` is fully loaded, plugin-contributed configuration included, before any bean-definition code runs.
- Artefact discovery happens earlier; the set of discovered artefacts is unchanged.
- The application class is instantiated a second time during early artefact discovery, so a constructor of the `Application` class with side effects runs twice per boot. Keep it free of side effects.
- A `doWithSpring` closure that checks for a bean definition, such as `dispatcherServlet` as a sign of a web application, now runs before Boot has registered its beans and gets `false` where it got `true`. Test a signal that does not depend on registration order, such as whether the application context is a web application context.
- The `annotationHandlerMapping` and `annotationHandlerAdapter` beans are removed. `@Controller` beans are handled by Spring Boot's standard MVC infrastructure. Search for references to those two bean names.
- `doWithSpring()` on `GrailsApplicationLifeCycle` and `grails.plugins.Plugin` is deprecated and continues to work. The recommended hook is `beanRegistrar()`, which returns an `org.springframework.beans.factory.BeanRegistrar`; a closure coerced with `as BeanRegistrar` is enough. Both hooks can be used on one plugin during migration; where both register a bean under the same name, the registrar's definition wins.
- **A `@Configuration` class registered as a bean in `resources.groovy`, `resources.xml`, the `doWithSpring` of the `Application` class, or the application's `beanRegistrar()` is no longer processed.** It is created as a plain bean, and its `@Bean` methods, `@Import` and `@Enable...` annotations are ignored. No error or warning is reported: the beans are simply missing. Search those places for classes annotated with `@Configuration`. Import the class from the `Application` class with `@Import(MyConfiguration)`, annotate the `Application` class with `@ComponentScan`, or declare the beans themselves in `resources.groovy` or in a `beans` block on the `Application` class. `@Configuration` classes contributed by a plugin's `doWithSpring` or `beanRegistrar()` are not affected.
- `spring.main.allow-bean-definition-overriding` defaults to `true` in Grails, and under that default an application bean with the same name as a plugin bean still replaces it. An application that sets it to `false` now gets a `BeanDefinitionOverrideException` for an application bean that shadows a plugin bean, where the shadowing used to be silent.
- In a `beans` block, `bean(Outer.Helper)` with no name registers a bean named `helper`, the type's decapitalized simple name. 8.0.0-M6 and 8.0.0-RC1 named it `outer$Helper`; name it explicitly, `bean('outer$Helper', Outer.Helper)`, if anything looks it up by the old name.
- Unit tests follow the same change:
  - `doWithSpring()` on the `GrailsUnitTest` trait, and so on every testing trait, is deprecated. A test declares its beans in a `beans` block, overrides `beanRegistrar()`, or declares a static nested `@Configuration` class, which its context registers ahead of the framework's auto-configurations.
  - A `beans` block compiles into a nested class named `BeansConfiguration`; rename a nested class of that name the test already declares.
  - A test that already has a static nested `@Configuration` class meant for something else, such as one built for an `ApplicationContextRunner`, now has it registered in its own context too; override `getConfigurationClasses()` to leave it out.
  - A test that includes a plugin through `getIncludePlugins()` also gets the beans of the plugin's `beans` block, and the plugin's `doWithSpring` and `beanRegistrar` beans are registered before the test's configuration is read, so a `@ConditionalOnMissingBean` bean backs off from them as in an application.
  - A test's `doWithSpring` bean now replaces an included plugin's bean of the same name, where the plugin's used to win.
  - What a test's `doWithConfig` sets now reaches the Spring `Environment` before the context's configuration is read, so a property condition such as `@ConditionalOnProperty` sees it; it used to reach only `grailsApplication.config` and `${}` placeholders.

### Custom CSRF / Synchronizer-Token Holders

- Audit subclasses of `SynchronizerTokensHolder`: `withForm` now validates and consumes tokens through `isValidAndResetToken(url, token)`. Overriding only `resetToken` no longer preserves an application's reusable AJAX/session token. The characteristic failure is that the first AJAX POST succeeds, then later requests fail CSRF validation because the token URL has disappeared from the holder.
- Override the atomic method for the application's designated reusable-token URL, validating the submitted token against the token belonging to the current session without consuming it. Delegate ordinary form URLs to `super.isValidAndResetToken` so duplicate submissions are still rejected atomically; do not bypass CSRF validation for AJAX requests.
- Preserve the holder's lifecycle as well as validation. A reusable token kept separately from the bounded per-form token map must survive that map becoming empty or evicting older URLs; adapt `isValid` and `isEmpty` consistently when those methods are used. Include recovery of a persisted holder whose map entry was already consumed by the old implementation.
- Verify successive and concurrent requests using the same session token, rejection of missing/malformed/foreign tokens, single-use form tokens, and session serialization. A test of only the first successful POST misses this regression. For behavior-only changes to a serialized holder, preserve a verified compatible serialization UID; invalidate genuinely incompatible pre-upgrade sessions rather than assuming an old UID makes their serialized state compatible.

## Hidden HTTP Method Override and Security Rules

Grails 8 no longer registers the servlet filter that rewrote a `POST` into a `PUT`, `PATCH` or `DELETE` from a `_method` parameter or an `X-HTTP-Method-Override` header. That filter ran ahead of the Spring Security filter chain, and reading `_method` on a `multipart/form-data` POST made the container parse the whole body, writing its temporary files, before the request was routed or authenticated. The `_method` parameter is still honoured, but it is now resolved inside the dispatcher, after multipart handling and after the filter chain. `<g:form method="DELETE">`, the scaffolded views and existing GSP templates need no changes. Several consequences are silent, so check each one:

- **Servlet filters and Spring Security see the real request method.** A `POST` carrying `_method=DELETE` now reaches Spring Security and every servlet filter as a `POST`. A security rule matching `DELETE /books/**` no longer fires for a browser form submit, and because the URL is the same, `update` and `delete` cannot be told apart by path either. Search the security configuration and the application's servlet filters for rules keyed on `PUT`, `PATCH` or `DELETE` and review each one.
- **`POST` to a member URL now reaches `update`.** A `resources` mapping now generates `POST /books/$id` alongside `PUT /books/$id`. This route did not exist before, so a `POST` the application previously rejected at the member URL now reaches `update`. A rule that permits `POST` on the collection (`POST /books` to create) also permits `POST /books/1` wherever it is written as a prefix. Search `UrlMappings` for `resources:` mappings and review every authorization rule that grants `POST` on those paths. The route reaches `update` only: it does not answer for a `PATCH` or `DELETE` form, it is not generated for a singular `resource` mapping, and it does not affect a URL the application mapped to `PUT` itself.
- **`X-HTTP-Method-Override` is no longer honoured**, and a `_method` value other than `PUT`, `PATCH` or `DELETE` is ignored. A proxy or SDK that rewrites `DELETE` into `POST` plus the header now has its `POST /books/1` answered by `update` with a `200`, while the caller believes it issued a `DELETE`. Search clients, proxies and tests for `X-HTTP-Method-Override`. Enable the filter for an application any such client talks to.
- **`request.method` in a controller reports `POST`** for a form submit with `_method`. The servlet request reports the method the request arrived as. URL mapping resolution (including a method-keyed `controller`/`action` name), `allowedMethods`, link generation when no `method` attribute is given, `RenderContext.getHttpMethod()`, command object initialization and whether a request body is bound all use the overridden method. Search controllers, interceptors and filters for `request.method` and `request.getMethod()` compared with `PUT`, `PATCH` or `DELETE`; use `allowedMethods`, separate actions, or an explicit read of the parameter instead.
- **An application's own `HiddenHttpMethodFilter` bean does not select filter mode.** The mode is read only from `grails.web.hiddenmethod.filter.enabled` and `spring.mvc.hiddenmethod.filter.enabled`. An application that registers its own filter bean without setting either property gets that filter and the dispatcher behaviour above, including the generated `POST` member route. Set the property alongside the bean.

To keep the Grails 7 behaviour, set `grails.web.hiddenmethod.filter.enabled` (default `false`):

```yaml
grails:
    web:
        hiddenmethod:
            filter:
                enabled: true
```

- This restores the filter exactly as in Grails 7, including the header and the unrestricted method names, and suppresses the `POST` member route, so the mappings a `resources` block generates are unchanged.
- `spring.mvc.hiddenmethod.filter.enabled: true` selects the same mode but registers Spring's filter, which reads only the `_method` parameter and honours only `PUT`, `PATCH` and `DELETE`. Either property is enough: a filter is on the chain, the dispatcher stops resolving `_method`, and the `POST` member route is not generated.
- For an application that declares `@EnableWebMvc`, Grails contributes its own filter when Spring Boot's is asked for, so a filter is still on the chain.

## Spring Boot MVC Auto-Configuration Without @EnableWebMvc

Grails 7 added Spring's `@EnableWebMvc` to the `Application` class at compile time, which switched off Spring Boot's `WebMvcAutoConfiguration`. Grails 8 no longer adds it, so `WebMvcAutoConfiguration` is active for Grails servlet web applications. Most applications need no change, and the bean-definition override warnings at startup for `localeResolver` and `internalAutoProxyCreator` go away. Check the following:

- **`spring.mvc.*` and `spring.web.*` properties now apply.** Properties such as `spring.web.locale`, `spring.mvc.format.date` / `time` / `date-time` and `spring.web.resources.*` were inert in Grails 7 and now take effect. Search the application's configuration, including external configuration, for `spring.mvc.` and `spring.web.` and remove any that were copied in without intent.
- **Boot's static resource handler is off by default.** Grails sets `spring.web.resources.add-mappings` to `false` by default, so Boot's catch-all handler for `classpath:/META-INF/resources/`, `/resources/`, `/static/` and `/public/` does not shadow URL mappings. Set `spring.web.resources.add-mappings: true` only if the application needs that handler.
- **Boot's welcome page mapping is removed**, so `UrlMappings` owns `/` and a static `index.html` is not served for the root path. Set `grails.web.removeWelcomePageMapping: false` to keep Boot's welcome page.
- **Boot's `defaultViewResolver` is removed** for every Grails servlet web application. Without the removal, an application with no JSP views could fail with `Circular view path [index]: would dispatch back to the current handler URL`. An application that relies on JSP or `InternalResource` views sets `grails.web.removeDefaultViewResolverBean: false`. Search for `spring.gsp.removeDefaultViewResolverBean`: it is still honoured but deprecated, logs a deprecation warning at startup, and `grails.web.removeDefaultViewResolverBean` takes precedence. Rename it. `spring.gsp.replaceViewResolverBean` still controls the GSP view resolver as before.
- **Form bodies of `PUT`, `PATCH` and `DELETE` are parsed by a `FormContentFilter`.** Fields of an `application/x-www-form-urlencoded` body are visible through `request.getParameter(...)` and in `params` for all three methods. Grails 7 parsed only `PUT` and `PATCH` bodies; `DELETE` is new. `spring.mvc.formcontent.filter.enabled` is `true` by default. An application that reads those bodies itself sets it to `false`, which disables the parsing for all three methods:

```yaml
spring:
    mvc:
        formcontent:
            filter:
                enabled: false
```

- **View resolution for non-GSP applications** now goes through Boot's `ContentNegotiatingViewResolver` as the primary `viewResolver`. GSP applications keep the GSP view resolver.
- **`mvcConversionService` is Boot's `ApplicationConversionService`**, which honours `spring.mvc.format.*`, and the MVC HTTP message converters pick up Boot's customizations, including the application's Jackson mapper. This mainly affects MVC-layer data binding and `@RestController`-style endpoints.
- **`GrailsWebRequest` stays bound for the whole request.** Grails registers its request-binding filter as a `RequestContextFilter` bean so Boot's filter backs off. No configuration is needed. An application's own `GrailsWebRequestFilter` bean, or its own `grailsWebRequestFilter` filter-registration bean, still overrides the Grails ones.
- **Locale resolution is unchanged**, including `?lang=` switching. The resolver strategy is configurable through `grails.i18n.localeResolver`: `session` (default), `cookie`, `acceptHeader` or `fixed`. An application that declares `@EnableWebMvc` itself is unaffected: the configured Grails resolver still wins.

## URL Mapping Names and Request Processing

A mapping that captures its controller, action or namespace from the URL, such as the default `"/$controller/$action?/$id?"`, now takes those names from the URI match only. In Grails 7 a token the URI did not supply was filled from a request parameter of the same name, from the query string or a form body.

- `/article?action=gallery` reached `ArticleController.gallery()` in Grails 7 and reaches the default action of `ArticleController` in Grails 8. The same applies to `controller` and `namespace`. `params.action` still holds `gallery`; only the routing changed.
- Search links, forms, JavaScript and tests for `?action=`, `?controller=`, `?namespace=` and for hidden form fields with those names that were used to choose the route. Put the name in the URI, or ask for the parameter explicitly with a closure, which keeps reading request state as before:

```groovy
static mappings = {
    "/$controller" {
        action = { params.goHere }
    }
}
```

- `grails.web.mapping.UrlMappingInfo` gains `isNameResolutionRequestDependent()`, a default method that reports `true`. An implementation outside the framework keeps its behaviour without changes.

Other request-path changes:

- **API versioning headers are sent for Grails-mapped requests.** The `Deprecation`, `Sunset` and `Link` headers configured through `spring.mvc.apiversion.*` were never sent for Grails-mapped requests and now are. Search configuration for `spring.mvc.apiversion`; unset the corresponding setting if the application relied on the headers being absent.
- **The `LocaleContext` is restored at the end of a request**, not cleared with `LocaleContextHolder.setLocale(null)`. A `LocaleContext` set earlier in the filter chain by non-Grails code now survives the Grails request.
- **`GrailsApplicationAttributes` is created once per servlet context** and reused, not once per request. Search for classes that implement `GrailsApplicationAttributes`: an implementation must hold no request-scoped state and must be safe to use from several request threads.
- **`GrailsWebRequest.setMultipartRequest(..)` is deprecated.** It still works. Replace it with the request attribute:

```groovy
// Before
webRequest.setMultipartRequest(resolved)

// After
request.setAttribute(WebUtils.MULTIPART_HTTP_SERVLET_REQUEST_ATTRIBUTE, resolved)
webRequest.multipartRequestResolved()
```

- **`GrailsWebRequest.getCurrentRequest()` returns the same object as `getRequest()`.** Calling it is still fine. Code that used it to reach uploaded files uses the `request.getFile(..)` methods. Search tests for `Mock(GrailsWebRequest)` with a stubbed `getCurrentRequest()`: framework code now calls `getRequest()`, which is `final` and cannot be stubbed. Use a real web request over a `MockHttpServletRequest`:

```groovy
// Before
def webRequest = Mock(GrailsWebRequest)
webRequest.getCurrentRequest() >> request

// After
def webRequest = new GrailsWebRequest(request, response, servletContext)
```

## Component Scanning and Overridable Framework Beans

- **`grails.spring.bean.packages` is now scanned wherever the process starts.** In Grails 7 the scan found nothing unless the working directory held a `grails-app` directory or an `Application.groovy`, so an application started from a systemd unit with its own `WorkingDirectory`, a container `WORKDIR` or an application server silently registered none of the `@Component`, `@Service`, `@Repository` and `@Controller` classes in the listed packages. Those deployments now get the beans.
  - Search configuration for `grails.spring.bean.packages` and list the annotated classes in each package.
  - A class that duplicates a bean defined elsewhere under the same name now fails startup.
  - Beans that were absent now take part in autowiring by type, so an injection point that resolved to one bean can become ambiguous.
  - To keep the previous set of beans, remove the packages from `grails.spring.bean.packages`, or remove the stray classes from those packages.
- **More framework beans back off for an application bean.** `localeResolver`, `localeChangeInterceptor`, `messageSource`, `grailsCorsFilter`, `urlMappingsErrorPageCustomizer`, `urlMappingsInfoHandlerAdapter`, `exceptionHandler`, `grailsCacheManager` and `customCacheKeyGenerator` are now guarded with `@ConditionalOnMissingBean`. No action is required. A bean of the same name or type from a `@Bean` method in an application configuration class, or from a plugin's `doWithSpring` or `beanRegistrar()`, replaces the framework bean without a bean-definition override, so it no longer warns at startup or fails when `spring.main.allow-bean-definition-overriding` is `false`. Beans in the application's `resources.groovy` or `resources.xml` register later and still take effect through bean-definition overriding, as before.

## GORM Registry and Multi-Tenancy Calls

GORM now keeps per-entity, per-connection state in a registry, `org.grails.datastore.gorm.GormRegistry`, and builds its API objects on first use. `Book.list()`, dynamic finders and `Book.withNewSession { }` behave as before. Two multi-tenancy calls and the `GormEnhancer` extension points changed.

- **`Tenants.withId(SomeDatastore, tenantId) { ... }` still compiles but binds the wrong datastore.** The overload of `grails.gorm.multitenancy.Tenants.withId(Class, Serializable, Closure)` that took a datastore class is removed; only the domain-class overload remains, and both have the same erased signature. A call that passes a `Datastore` subclass now looks the class up as a domain class, finds no entity, and falls through to the `DEFAULT` datastore. On a single-datastore application nothing changes. On a multi-datastore application the tenant is bound against the wrong datastore with no exception and no log, which can show up later as a read or write in the wrong database. Search for `Tenants.withId(` and check the first argument of every three-argument call. Look the datastore up and use the datastore-instance overload, or pass a domain class if that was meant (`Tenants.withId(SomeDomainClass, tenantId) { ... }`):

```groovy
MultiTenantCapableDatastore datastore = Tenants.datastoreLocator.getDatastore(SomeDatastore) as MultiTenantCapableDatastore
Tenants.withId(datastore, tenantId) { ... }
```

- **`CurrentTenantHolder.get()` with no argument throws `TenantException`** when different datastores hold different tenants on the calling thread. It used to return an arbitrary one of them. Search for `CurrentTenantHolder.get()` and pass the datastore the code operates on: `CurrentTenantHolder.get(datastore)`.
- **`GormEnhancer` subclasses** concern authors of GORM implementations and plugins that extend `org.grails.datastore.gorm.GormEnhancer`. Search for `extends GormEnhancer`.
  - The public constructors, `enhance()`, `enhance(PersistentEntity)` and `dynamicEnhance` are unchanged. The `transactionManager` field is deprecated and immutable after construction: read it, do not assign it.
  - Removed: the `finders` field and `getFinders()` (use `createDynamicFinders(Datastore)` or read the finders from the API object in use); `removeConstraints()` (now `GormRegistry.removeConstraints()`, called from `GormRegistry.removeDatastore(Datastore)` when the enhancer's `close()` removes the datastore; there is nothing to call directly); the `onlyExtendedMethods` parameter of `addStaticMethods` and `addInstanceMethods`, which now take only the entity.
  - Removed helpers: `findTenantId`, `apiQualifiers`, `findDatastoreByType`, `findSingleDatastore`, `findSingleTransactionManager`, `findTransactionManager(Class, String)`, `appliesToDatastore`, `getInstanceMethodApiProviders`, `registerStaticMethod`, `registerInstanceMethod`, `doesRealMethodExist` and `isRealMethod`. Code that overrode one of them to customise dispatch overrides `registerApiFactories()` or supplies a `GormApiFactory`.
  - `registerApiFactories()` is a new hook, called once before any entity is registered. Code that registered API factories from `registerConstraints(Datastore)` against an earlier snapshot moves it there:

```groovy
class MyGormEnhancer extends GormEnhancer {

    @Override
    protected void registerApiFactories() {
        GormRegistry.instance.registerApiFactory(MyDatastore, new MyGormApiFactory())
    }
}
```

## Table-per-Hierarchy Discriminator Columns

This note belongs to the Hibernate 5 to Hibernate 7 migration and concerns only tables created by 8.0.0-RC1 or 8.0.0-RC2.

- A table-per-hierarchy tree with no discriminator mapping uses the Grails 7 defaults: a column named `class` that stores the fully qualified name of each class. Tables created by Grails 7 need no change.
- 8.0.0-RC1 and 8.0.0-RC2 named the column `DTYPE` and stored the simple name of each subclass (for example `com.example.Vehicle`, `Car`, `SportsCar`). Such a table now fails `dbCreate: 'validate'` with `missing column [class]`, and its subclass rows no longer match.
- To keep that layout without migrating the data, map the column on the root class and the simple name on each subclass:

```groovy
class Vehicle {
    static mapping = {
        discriminator column: 'DTYPE'    // the root value stays the fully qualified class name
    }
}

class Car extends Vehicle {
    static mapping = {
        discriminator 'Car'
    }
}

class SportsCar extends Car {
    static mapping = {
        discriminator 'SportsCar'
    }
}
```

## Groovy 5 Static Fields, ConfigObject Probes, and Map Accessors

- **Assigning a static field from a static closure needs the class name.** Under `@CompileStatic` or `@GrailsCompileStatic`, an unqualified assignment to a static field inside a static closure, such as the `constraints`, `mapping` or `namedQueries` block, no longer resolves to the enclosing class's field. Search statically compiled classes for assignments inside those blocks and qualify them. Reading a static field is unaffected.

```groovy
@GrailsCompileStatic
class Book implements Validateable {
    static boolean constraintsClosureCalled = false

    static constraints = {
        Book.constraintsClosureCalled = true
    }
}
```

- **Subscripting a `ConfigObject` for a missing key inserts an entry.** `configObject[key]` on a `groovy.util.ConfigObject` now creates and inserts an empty nested `ConfigObject` for a key that does not exist, where it used to read back as a falsy value. Search for code that walks a raw `ConfigObject` with `[]` and expects `null` for a missing key. Test for the key first:

```groovy
def value = configObject.containsKey(key) ? configObject.get(key) : null
```

- **A getter on a `Map` class is resolved before a map entry.** In Groovy 5 a public JavaBean accessor declared on a class that implements `Map` wins over an entry of the same name, and assigning to that name throws `groovy.lang.ReadOnlyPropertyException`. Grails 8 removed the accessors that collided on `params`, so every parameter name, including `request` and `identifier`, is an ordinary map entry in dotted and subscript form, as in Grails 7.
  - Search for `params.getRequest()` and replace it with `params.request()`.
  - Search for `params.getIdentifier()` and replace it with `params.id`; it only ever read the `id` parameter.
  - In a GSP tag, `attrs['gspTagSyntaxCall'] = value` now stores an attribute of that name, as `attrs.put('gspTagSyntaxCall', value)` does. In Grails 7 it invoked a setter. `attrs.gspTagSyntaxCall = value` still invokes the setter.
  - Search for classes that extend `grails.util.TypeConvertingMap`. Do not declare a no-argument `getX()` or `isX()` method on one: the entry `x` becomes unreachable, and statically compiled callers bind directly to the accessor, so it cannot be corrected at runtime.

## CAS Single Sign-Out and the Spring Security UI Plugin

These concern applications that use the Spring Security CAS plugin or the `spring-security-ui` plugin.

- **CAS single sign-out is off by default.** `grails.plugin.springsecurity.cas.useSingleSignout` now defaults to `false`; it defaulted to `true`. With the old default every CAS application registered the CAS client's `SingleSignOutFilter`.
  - Enabling it disables session fixation prevention, because CAS maps the service ticket to the HTTP session id. That is a security trade-off the application must choose deliberately.
  - Single sign-out did not work before Grails 8: the filter was registered, but the session was still replaced on login and CAS logout requests matched nothing. An upgrading application is therefore unlikely to lose working behaviour. In Grails 8 the setting has its documented effect.
  - To enable it, set `grails.plugin.springsecurity.cas.useSingleSignout = true` in `application.groovy`. The plugin logs a warning at startup that session fixation prevention has been disabled.
- **`spring-security-ui` is rewritten on Bootstrap 5** and ships no styling of its own. It no longer ships or depends on jQuery UI, jquery-form, jGrowl, DataTables, bgiframe or the jdMenu/positionBy plugins, and every plugin stylesheet and image is removed. jQuery (4.x) and Bootstrap are expected from the host application, as the `create-app` layout provides them.
  - Plugin pages render through the application's own layout, set with `grails.plugin.springsecurity.ui.gsp.parentLayout` (default `main`). The security menu and login state are contributed through `nav` and `navActions` content blocks; a custom layout that wants them in its navbar renders `<g:pageProperty name="page.nav"/>` and `<g:pageProperty name="page.navActions"/>`.
  - Search for views copied into the application with `s2ui-override`. They reference removed stylesheets and the old table-based markup of the `s2ui` field-row tags, which now emit Bootstrap form groups. Regenerate or update them.
  - Search CSS for `s2ui_` and `jd_menu` classes. Nothing matches them any more; restyle against Bootstrap classes.
  - If the application used the plugin's transitive webjars (jQuery UI, jGrowl, DataTables, jquery-form), declare those dependencies itself.
  - Search for `grails.plugin.springsecurity.ui.Constants`, `spring-security-ui-register.js` and `spring-security-ui-*` stylesheet assets. They are removed.
  - `LogoutController` now declares `allowedMethods` that restrict its action to POST while `logout.postOnly` is active, which is the default.

## Quartz Scheduling Errors and Job Cleanup

These concern applications that use the Quartz plugin. No job class has to change.

- **A trigger that can never fire no longer stops startup.** A cron expression whose last occurrence is in the past, or an end time that has passed, used to fail startup with `org.quartz.SchedulerException: Based on configured schedule, the given trigger will never fire.` The plugin now logs the trigger as an error and leaves it unscheduled; the job is still registered and can be triggered and rescheduled at runtime. Check the startup log for that error after upgrading. To fail startup as before, set `quartz.failOnNeverFiringTriggers: true`.
- **An application removes only the jobs it registered itself.** At startup the plugin removes jobs in the job store that are no longer enabled job artefacts. It used to consider every job in the store; it now considers only jobs that carry the application's name, taken from `info.app.name` or from the application metadata when that is unset. For an application with `quartz.jdbcStore` enabled:
  - Two different applications that share Quartz tables each need their own `info.app.name`. Instances of one application share the name. An application that does not name itself registers its jobs under a default name shared by every unnamed application, and the plugin warns about that at startup.
  - A job written to the store by Grails 7 or earlier carries no application name and is never removed automatically. A job class deleted while on the older version leaves a row behind; remove it with `jobManagerService.removeJob(group, name)` or from the Quartz tables.
- **Scheduling methods no longer throw `NullPointerException`.** The static `schedule`, `reschedule`, `unschedule`, `triggerNow` and `removeJob` methods of a job class now throw `IllegalArgumentException` for a null argument, and `IllegalStateException` when the job is not registered with a scheduler (the plugin is disabled, the job has `jobEnabled = false`, or the class is not a job artefact). Search for `catch (NullPointerException` around those calls and catch the new exceptions.

## GSP Source Checksums and the gspCompile Configuration

- **A build that declares dependencies on `gspCompile` fails** with `Could not find method gspCompile()`. The `org.apache.grails.gradle.grails-gsp` plugin no longer registers that configuration. Search build files for `gspCompile` and move the declarations to `compileOnly`:

```groovy
dependencies {
    // Grails 7
    // gspCompile 'com.example:some-library:1.0'

    // Grails 8
    compileOnly 'com.example:some-library:1.0'
}
```

- **GSPs record a checksum of their source, not its modification time**, for pages compiled at build time and at runtime. Runtime reloading still works: a page is reloaded when its source no longer matches the recorded checksum.
  - A page whose modification time moves without a content change is no longer recompiled. Search scripts and deployment steps for `touch` used to force a GSP to recompile; edit the page instead.
  - `grails.gsp.reload.granularity` is removed and setting it has no effect. Remove it from configuration. `grails.gsp.reload.interval` is unchanged.
  - Removed API: `GroovyPageMetaInfo.getLastModified()`, `setLastModified(long)` and `applyLastModifiedFromResource(Resource)`, and `GroovyPageParser.getLastModified()` and `setLastModified(long)`. Use `GroovyPageMetaInfo.getSourceChecksum()`. Generated page classes no longer declare a `LAST_MODIFIED` constant.
  - A GSP precompiled by Grails 7 inside a binary plugin carries no checksum. It is served normally and is never reload-checked, as before.

## Stack Trace Filtering and the Startup Banner

- **`grails.logging.stackTraceFiltererClass` and `grails.exceptionresolver.logFullStackTraceOnFilter` now apply everywhere the framework filters a stack trace**, including GSP view-render exceptions, not only to requests that go through the exception resolver. Search configuration for both keys and expect them to take effect on more log output. An application that silenced the `StackTrace` logger in its Logback configuration only to suppress render-time noise can set `logFullStackTraceOnFilter: false` instead.
  - The filterer is resolved once at startup and is JVM-global: in a JVM that hosts more than one application context, the last one booted supplies the filterer for all of them.
  - A context not started through `SpringApplication`, such as the CLI, plain `main()` usage and Grails unit tests, keeps a plain `DefaultStackTraceFilterer` with no configuration applied, as before.
- **The startup banner shows the servlet container and, where the application has it, the Spring Security version** without being asked. Both were opt-in through `grails.banner.versions.include`. Existing `spring-security`, `tomcat`, `jetty` and `undertow` entries under `include` keep working. A version option that names no known key is now reported as a warning after the banner. To restore the previous banner:

```yaml
grails:
    banner:
        versions:
            exclude:
                - container
                - spring-security
```

## Removed and Renamed Extension APIs

Most applications do not call these APIs. Search the application, its plugins and its build logic for each name; a hit needs the listed replacement.

Auto-configuration classes named in `@EnableAutoConfiguration(exclude = ...)`, `spring.autoconfigure.exclude` or `@AutoConfigureAfter(name = ...)` are the most likely hits in an application. Spring Boot reports an invalid exclude only for a class still on the classpath, so an entry with an old name is silently ignored and the beans it was meant to suppress are registered anyway.

| Old name | New name |
|----------|----------|
| `org.grails.plugins.core.CoreAutoConfiguration` | `org.grails.plugins.CoreAutoConfiguration` |
| `grails.plugin.cache.GrailsCacheAutoConfiguration` | `grails.plugin.cache.CacheAutoConfiguration` |
| `org.grails.plugins.databinding.DataBindingConfiguration` | `org.grails.plugins.databinding.DataBindingAutoConfiguration` |
| `GrailsDomainClassAutoConfiguration` | `org.grails.plugins.domain.DomainClassAutoConfiguration` |

Other application-level APIs:

| Changed or removed API | Replacement |
|------------------------|-------------|
| `grails.web.JSONBuilder` (`grails-converters`) | `groovy.json.JsonBuilder` or `groovy.json.StreamingJsonBuilder` |
| `grails.util.Mixin`, `MixinTargetAware`, `MixinTransformation` (`grails-core`) | Groovy traits, extension methods, or explicit composition |
| `grails.validation.ConstraintsEvaluator` (`grails-core`) | `org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator` |
| `ConstraintEvaluatorAdapter` and `GrailsDomainClassAutoConfiguration.constraintsEvaluator(...)` (`grails-domain-class`) | The `validateableConstraintsEvaluator(...)` bean path and the datastore constraint evaluator APIs |
| `BeanPropertyAccessor.getBeanClass()` and its setter, `PropertyPathAccessor.getBeanClass()`, `FormFieldsTagLib.input(...)` and direct `render*` helper methods (`grails-fields`) | The `beanType` property of the accessors; custom field renderers go through the `f:field`, `f:widget` and `f:displayWidget` tags and templates |
| `grails.plugins.PluginFilter`, `IncludingPluginFilter`, `ExcludingPluginFilter`, `NoOpPluginFilter` and related filter types (`grails-core`) | The types of the same names in `org.apache.grails.core.plugins.filters` |
| `ControllersAutoConfiguration.dispatcherServletRegistration(...)` return type (`grails-controllers`) | It now returns Spring Boot 4's `org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean`; update imports and overrides that used the Spring Boot 3 type |

APIs for plugin authors, custom bootstrapping and build logic:

| Changed or removed API | Replacement |
|------------------------|-------------|
| `GrailsApplicationPostProcessor` and `DefaultGrailsPluginManager` constructors (`grails-core`) | Pass the plugin-discovery dependency when constructing either directly. `ProfilingGrailsApplicationPostProcessor` keeps its constructor shape, but the application context must expose the `grailsPluginDiscovery` bean. Required since the Grails 7.1 plugin-loading changes. |
| `BinaryGrailsPluginDescriptor`, `CorePluginFinder`, `BinaryGrailsPlugin.getBinaryDescriptor()` (`grails-core`) | The current plugin-discovery APIs and `BinaryGrailsPlugin.getPluginDescriptor()` |
| `org.grails.plugins.codecs.CodecsGrailsPlugin` no longer overrides `doWithSpring()` (`grails-codecs`) | Do not depend on that override to contribute codec beans; a plugin registers its own beans from its own `doWithSpring()` |
| `GrailsApplicationContext.onRefresh()` (`grails-spring`) | Supported Spring lifecycle extension points |
| `ApplicationContextCommandTask`, `ApplicationContextScriptTask`, `ProfileCompilerTask` and `GrailsRunTask` are abstract; `GrailsGradlePlugin.createBuildPropertiesTask(...)` no longer returns a task; several `IntegrationTestGradlePlugin` constants are removed (`grails-gradle-plugins`) | Register these task types through Gradle task registration; use Gradle source sets, configurations and the current test-phase APIs |
| `AetherGrapeEngine`, `AetherGrapeEngineFactory` (`grails-shell-cli`) | `MavenResolverGrapeEngine`, `MavenResolverGrapeEngineFactory` |
| JLine 2 `complete(...)` signatures (`grails-shell-cli`) | The JLine 3 `org.jline.reader.Completer` contract, `complete(LineReader, ParsedLine, List<Candidate>)` |

## Plugin Authors: CLI Test Source Sets

This concerns only plugins that apply `org.apache.grails.gradle.grails-plugin-cli` and publish a companion `-cli` artifact.

- `src/test` no longer sees the `cli` source set or its dependencies. Move a spec that exercises a command or another CLI class to `src/test-cli` (the `testCli` task, wired into `check`), or to `src/integration-test-cli` (the `integrationTestCli` task) if it boots an application. The plugin creates both source sets; there is nothing to declare.
- Both CLI test source sets can read the module's `main` and `test` output, so shared fixtures stay where they are. `src/integration-test-cli` also reuses `src/integration-test/resources` when that folder exists.

## Gradle Build Script Changes

Search `build.gradle`, `buildSrc`, and convention plugins for the names below. Each of these fails the build, so they are found on the first run.

- `importJavaTime` is removed from the `grails { }` extension of the `org.apache.grails.gradle.grails-app` plugin. A build that sets it fails with `Could not set unknown property 'importJavaTime'`. Delete the line; there is no replacement, because Groovy 5 imports `java.time` by default. `importGrailsCommonAnnotations` and `starImports` are unchanged and both still default to off.
- Assigning `groovyOptions.configurationScript` from `compileGroovy.doFirst { ... }` fails on Gradle 9.7 and later, because Gradle has already finalized the compiler options. Set `groovyOptions.configurationScriptFile` while the task is configured. Grails prepares its combined compiler script before compilation.
- When another task generates the compiler script, declare the script as an output file of that task and pass the task's output provider. No separate `dependsOn` is needed:

```groovy
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Writing a short compiler script is inexpensive')
abstract class GenerateCompilerScript extends DefaultTask {
    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void generate() {
        def script = outputFile.get().asFile
        script.parentFile.mkdirs()
        script.text = "withConfig(configuration) { imports { star 'java.nio.file' } }"
    }
}

def generateUserConfigScript = tasks.register('generateUserConfigScript', GenerateCompilerScript) {
    outputFile.set(layout.buildDirectory.file('user-config.groovy'))
}

tasks.named('compileGroovy', GroovyCompile) {
    groovyOptions.configurationScriptFile.set(generateUserConfigScript.flatMap { it.outputFile })
}
```

- An existing build can keep the plain file assignment with an explicit `dependsOn` on the producing task, as long as that task declares the exact script file as an output, through an `@OutputFile` property or `outputs.file(...)`:

```groovy
tasks.named('compileGroovy', GroovyCompile) {
    dependsOn generateUserConfigScript
    groovyOptions.configurationScript = file('build/user-config.groovy')
}
```

- A producer that declares only a containing output directory, writes the file without declaring it, or is reachable only through another task's `dependsOn` is not recognised. Such a build fails from a clean checkout with `Input file does not exist` on the `configurationScript` property of the task `generateCompileGroovyGrailsCompilerConfig`. Expose the script as an output file and use the provider wiring.
- A checked-in compiler script can still use a plain file assignment without a producer.

### Scaffolded Views Are Compiled at Build Time

The pages of every controller annotated `@Scaffold`, the application's or a plugin's, are now expanded by the `generateScaffoldedViews` task into `build/generated/scaffolded-views` and compiled by `compileGroovyPages` together with `grails-app/views`. A request gets the same page as in Grails 7, where the page was expanded and compiled on the first request.

- Search for scaffolding templates of the application's own, in `src/main/templates/scaffolding` or packaged under `META-INF/templates/scaffolding`. A template that cannot be expanded, or whose page does not compile, now fails the build. In Grails 7 it failed on the first request for the view.
- A page expanded from a template that a dependency supplies does not fail the build when it cannot be compiled. The build output names it, and it is expanded when it is first rendered, as in Grails 7.
- A view the application declares still takes precedence over a scaffolded page.
- `compileGroovyPages` takes the pages as its `generatedViews`, beside its `source`, so a build that sets `source` itself still compiles them.
- Only when upgrading from 8.0.0-RC1: remove settings of the `templateClasspath` and `applicationViews` properties of `generateScaffoldedViews`, which fail with an unknown property, and references to the `stageGroovyPages` task and to `GroovyPagePlugin.scaffoldsAnyController`, which are gone.

## Converter Marshalling of Non-Public Classes

Groovy 5 no longer marks anonymous inner classes as public. Grails 8 still marshals a bean whose class is not public, through compatibility handling, and logs a warning once for each such class.

- Search for anonymous classes passed to `as JSON`, `as XML`, `render` or `respond`, such as `new UserDetails() { ... }` inside a controller action.
- The warning starts with `Class [com.example.DemoController$1] is not public` and says the handling may be withdrawn in a future major release. Classes from the JDK, Groovy and Spring are never reported.
- Declare the class as a named public class. For a class the application does not own, register an `ObjectMarshaller` for it.
- To silence the warning without changing code, set the log level of `org.apache.grails.common.reflect.ReflectionUtils` to `ERROR`:

```yaml
logging:
    level:
        org.apache.grails.common.reflect.ReflectionUtils: ERROR
```

- A `public` instance field declared on a non-public plain Java class is now included in the JSON or XML body. It was silently omitted before, although it was always included for a Groovy bean. Compare payloads of Java objects that have public fields. To keep a field out, exclude it or register an `ObjectMarshaller` for the class:

```groovy
def json = someObject as JSON
json.excludes = ['nickname']
render json
```

- A public field that cannot be read at all, because its class is in a named module that does not open its package, is skipped and the rest of the object converts. A getter in that situation still fails the conversion, as before.

## Date and Time Values in JSON, XML, and Data Binding

`grails.converters.JSON` (`render ... as JSON`, and `respond` for a JSON request) and JSON views, including `g.render`, now write date and time values as Spring Boot's default Jackson `JsonMapper` does. Nothing fails; the output is different, so clients and tests that read these values have to be checked.

Search for domain classes, command objects and maps that are rendered and hold `java.sql.Time`, `java.time` types other than the common date-time types, `TimeZone`, the `javax.xml.datatype` types, or date-typed map keys. Search for `grails.views.json.generator.timeZone` and `grails.views.json.generator.dateFormat`, for `as XML` on objects with a `Date`, and for bound properties of type `java.time.Month`.

A `Date`, `Calendar` or `java.sql.Timestamp` value renders as in Grails 7, such as `"2026-09-25T03:00:00.000Z"`. An application that upgrades from Grails 7 needs no change for these; 8.0.0-RC1 rendered them differently for a while.

Values that render differently than in Grails 7:

| Value | Grails 7 | Grails 8 |
|-------|----------|----------|
| `java.sql.Time` | `"2025-10-08T07:48:46.407Z"`, the UTC instant | `"01:48:46"`, from `Time#toString()` in the JVM default time zone |
| A `Date`, `Calendar`, `java.sql` date type or `ZonedDateTime` used as a map key | The key's `toString()`, such as `"Wed Oct 08 01:48:46 MDT 2025"` | A `Date`, `java.sql.Time` or `Calendar` key as a `Date` value is written, such as `"2025-10-08T07:48:46.407Z"`; a `ZonedDateTime` key as `"2026-09-25T00:00:00-03:00"` |
| A `Date` or `Calendar` before 1 AD or after 9999 (`grails.converters.JSON`) | `"12345-01-01T00:00:00.000Z"`, `"0044-03-15T00:00:00.000Z"` (44 BC) | `"+12345-01-01T00:00:00.000Z"`, `"-0043-03-15T00:00:00.000Z"`, ISO 8601 expanded years |
| A `Date` inside a collection that `g.render` writes element by element, such as a `List<Object>` | `"2025-10-08T07:48:46+0000"` | `"2025-10-08T07:48:46.407Z"`, as any other `Date` value |
| A `Date` or `Calendar` in JSON views with `grails.views.json.generator.timeZone` other than `GMT` | The offset in hours only, such as `"2025-10-08T03:48:46.407-04"` | The offset in hours and minutes, such as `"2025-10-08T03:48:46.407-04:00"`; the default date format is now `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` |
| A `Date` rendered by `grails.converters.XML` | `2025-10-08 01:48:46.407 MDT` | `2025-10-08T01:48:46.407-06:00`, with the offset of the JVM default time zone |

Types that Grails 7 rendered as an object of their properties, as `{}`, or not at all, and that now render as values:

| Type | Grails 8 |
|------|----------|
| `java.time.LocalTime` (`grails.converters.JSON`) | `"01:48:46.407254"`, as JSON views already rendered it |
| `java.time.OffsetTime` (`grails.converters.JSON`) | `"03:00:00-03:00"`, as JSON views already rendered it |
| `java.time.Period` (`grails.converters.JSON`) | `"P1Y2M3D"`, as JSON views already rendered it |
| `java.time.Year` | `2026`, a JSON number |
| `java.time.YearMonth`, `java.time.MonthDay` | `"2026-09"`, `"--09-25"` |
| `java.time.Duration` | `"PT1H30M"` |
| `java.time.ZoneId`, `java.time.ZoneOffset` | `"America/Sao_Paulo"`, `"-03:00"` |
| `java.util.TimeZone` | `"America/Sao_Paulo"`, its ID |
| `javax.xml.datatype.XMLGregorianCalendar` | As a `Calendar` is, such as `"2025-10-08T07:48:46.407Z"` |
| `javax.xml.datatype.Duration` | `"P1DT2H"` |

Further points:

- Data binding now binds a `java.time.Month` from its number as well as its name: `9`, `"9"` and `"SEPTEMBER"` bind to `SEPTEMBER`. Grails 7 bound a number by ordinal, so `9` bound to `OCTOBER`. A client that sends month numbers gets a different month without an error. Check every client that submits a `Month`.
- In JSON views, `grails.views.json.generator.dateFormat` no longer applies to `java.sql.Time` values, which are written as `"01:48:46"`. It now applies to `Date` and `Calendar` map keys, in the configured `timeZone` and `locale`.
- Enums still render by `name()`, so a `Month` renders as `"SEPTEMBER"`, where Spring Boot writes `9`. To render the number, register `JSON.registerObjectMarshaller(Month) { Month month -> month.value }` and a JSON views converter that returns `month.value`.
- The HAL renderer, `grails.rest.render.hal.HalJsonRenderer` for `application/hal+json`, is not changed.

To keep the Grails 7 rendering of a type, register an object marshaller for it with `grails.converters.JSON`, for example in `BootStrap.init`, and a `groovy.json.JsonGenerator.Converter` for it in JSON views:

```groovy
DateTimeFormatter utc = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
JSON.registerObjectMarshaller(java.sql.Time) { java.sql.Time time ->
    utc.format(Instant.ofEpochMilli(time.time))
}
```

- A JSON views converter that returns `new Date(time.time)` for a `java.sql.Time` writes it with the configured `dateFormat`, as Grails 7 did.
- Map keys are not rendered by marshallers or converters. To keep the `toString()` of a date key, convert the keys to strings before rendering, for example with `map.collectEntries { key, value -> [(key.toString()): value] }`. A marshaller registered for `Date` or `Calendar`, and `grails.converters.json.date` set to `javascript`, change values but not map keys.
- The default JSON marshallers have implicit negative priorities that follow their order, and the marshallers added in 8.0 shift those. Search for `registerObjectMarshaller` calls with an explicit negative priority: such a marshaller may now come before or after different defaults. A marshaller registered with the default priority of 0, or higher, still takes precedence over all defaults.

## Request and Response Behavior Changes

### XML Request Bodies With a DOCTYPE Are Refused

XML that Grails parses for an application is now read with external entity expansion, external DTD retrieval and DTD grammar loading disabled. In addition, an `application/xml`, `text/xml` or `application/hal+xml` request body, or a string passed to `XML.parse`, that declares a `DOCTYPE` is refused, whether or not the declaration references anything external.

- Search for clients, integrations and test fixtures that send XML with a `<!DOCTYPE` declaration, and for `XML.parse` calls on documents the application does not produce itself.
- Binding such a body fails with the `invalidRequestBody` error code. An endpoint that accepted the document in Grails 7 now rejects it.
- There is no setting that relaxes this. The client has to omit the declaration.
- Descriptors read from the classpath, such as JSP tag library descriptors and `web.xml`, and the `response.xml` accessor in controller unit tests may still declare a `DOCTYPE`; the DTD it names is never retrieved.

### Default Security Response Headers

Grails 8 registers a servlet filter that adds these headers to the responses of every servlet web application, without any change in the application:

```
X-Content-Type-Options: nosniff
X-Frame-Options: SAMEORIGIN
Referrer-Policy: strict-origin-when-cross-origin
X-XSS-Protection: 0
```

- The filter writes a header when the response commits, and only if nothing else has set it by then. Values set by controllers, interceptors, custom filters, or Spring Security header writers at Spring Boot's default `spring.security.filter.order` are kept.
- Check pages that other sites embed in a frame: `X-Frame-Options: SAMEORIGIN` now applies to them unless the application sets its own value. Check tests and clients that assert on the exact set of response headers.
- Search the reverse proxy configuration for the same headers, such as nginx `add_header`. When the proxy already sends them, set `grails.security.headers.defaults: auto`, so that requests arriving through the proxy (detected from forwarded request headers, `server.forward-headers-strategy`, or an active cloud platform) receive only the headers the application configures explicitly, or strip the application's copies at the proxy.
- The filter is registered at `GrailsFilters.FIRST`. If the application orders the Spring Security chain ahead of that, the Spring Security header writers other than the `X-Frame-Options` writer find the Grails value already present and leave it. Disable the corresponding `grails.security.headers.<header>` in that case.
- A response served by a filter ordered ahead of `GrailsFilters.FIRST`, such as Spring Boot's forwarded-header filter or, in a WAR deployment, its `ErrorPageFilter`, receives no default headers.
- To disable or change the defaults, configure `grails.security.headers.*`. The HTTP Security Headers section of the guide's Security chapter lists the settings.

### File Renders Are Sent as Attachments

`render(file: ...)` now sets `Content-Disposition: attachment` unless the application opts out. A file that the browser displayed in Grails 7, such as a PDF or an image, is now downloaded.

- Search controllers for `render(file:` and `render file:`. For each one decide whether the browser should display the file or download it.
- Pass `inline: true` to keep the file inline, or set the `Content-Disposition` header before calling `render`. Grails leaves an existing header alone.
- When a `fileName` is supplied, or derived from a `File`, unsafe characters in the name are escaped before the header is written. Check tests that assert on the header value.

```groovy
render(file: new File(absolutePath), fileName: 'report.pdf')
render(file: new File(absolutePath), inline: true)
```

### Interceptor URI Matching Uses the Dispatched Path

Interceptors that match on `uri`, with `match(uri: ...)` or `excludes(uri: ...)`, now match the path that URL mappings route on: percent escapes are decoded, matrix parameters (`;name=value`) are removed, and the context path is stripped. Before, the raw request URI was matched, so `/%61dmin/users` or `/admin;x=1/users` could reach a controller without triggering an interceptor declared for `/admin/**`. Interceptors matched by controller, action or namespace are unaffected.

Search interceptors for `uri:` and review each one:

- An interceptor that combines `match(uri:)` with any `excludes(...)` never ran when the application was deployed under a context path other than the root, such as `server.servlet.context-path=/app`. It now behaves as configured, so it starts executing in such deployments. Only that shape was affected: `matchAll()` with exclusions, name-based matchers such as `match(controller:)`, `match(uri:)` without an exclusion, and patterns that match both forms, such as `/**`, already ran.
- A pattern is matched against the path within the application only. `match(uri: '/*/*')` no longer matches `/app/save` under the context path `/app`. A pattern that begins with the context path, such as `match(uri: '/app/admin/**')`, is still accepted.
- During a `<g:include>` or a `RequestDispatcher` include, `uri` matchers see the included path, not the URI of the outer request.
- A custom `grails.interceptors.Matcher` implementation receives the decoded, application-relative path without matrix parameters as the `uri` argument of `doesMatch`. Override the new default method `doesMatch(String uri, UrlMappingInfo info, String method, String contextPath)` when the matcher needs the context path.
- The Spring Security plugin applies the same canonical path when it matches `ipRestrictions` patterns and when the `AntPathRequestMatcher` compatibility class selects a filter chain. An encoded or matrix-parameter variant of a restricted path now gets the same restriction as the plain path.

## Application Context, Layout, and Configuration Lists

### The Plugin Lifecycle Runs for a Grails Application Only

The Grails plugin lifecycle runs only for an application that `GrailsApp` launched, or whose context sources include a Grails application class such as `grails-app/init/Application.groovy`. This is what Grails 7 did; earlier Grails 8 milestones ran it for any Spring Boot application with `grails-core` on the classpath. A Grails application is unaffected.

- A Spring Boot application that is not a Grails application and relied on the `doWithSpring` or `beanRegistrar` beans of a Grails plugin no longer gets those beans. A plugin that also publishes an auto-configuration, as the Grails modules do and as `@GrailsBeans` generates for a plugin's `beans` block, still contributes through Spring Boot's auto-configuration.
- Search tests for `@SpringBootTest(classes =`. When the named classes do not include the application class, the plugin lifecycle does not run in that test. Include `Application` only if the test needs the Grails plugin runtime.
- By default `@SpringBootTest` does not invoke the application's main method, so in an application with Hibernate it can fail on bean overriding or circular references even when the application class is selected. Use `useMainMethod = SpringBootTest.UseMainMethod.ALWAYS`, or set both `spring.main.allow-bean-definition-overriding=true` and `spring.main.allow-circular-references=true` on the test.
- A bare `@SpringBootTest` discovers the `Application` class by itself. `@Integration` tests and the `grails-testing-support` unit test traits are unaffected.

```groovy
// A context using only this configuration, without the Grails plugin lifecycle
@SpringBootTest(classes = SomeConfig)
class ConfigurationSpec extends Specification { }

// Include the application and start through its main method to apply GrailsApp's defaults
@SpringBootTest(classes = [Application, SomeConfig], useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class ApplicationSpec extends Specification { }
```

### `sitemesh.decorator.default` Decides the Default Layout When No Grails Key Is Set

With SiteMesh 3, the default layout is resolved from `grails.sitemesh.default.layout`, then the legacy `grails.views.layout.default`, and now `sitemesh.decorator.default` as a last fallback. With none of them set, the implicit `application` layout is used.

- Search the configuration for `sitemesh.decorator.default`. An application that sets it and neither Grails key was decorated with the `application` layout and is now decorated with the layout the property names.
- Set `grails.sitemesh.default.layout` to be explicit, or remove `sitemesh.decorator.default` to keep the `application` layout. An application that sets either Grails key is unaffected.

### A Configuration List of Objects Is Presented Element by Element

A list of objects in `application.yml` or `application.groovy` is now presented to Spring under the indexed names Spring Boot binds a list from, such as `springdoc.group-configs[0].group`. A `@ConfigurationProperties` class that declares a `List` of objects is bound with instances of that type. Before, the list was also presented under its own name, Spring Boot bound it as a list of maps, and code reading the declared type failed with a `ClassCastException`.

```yaml
springdoc:
    group-configs:
        - group: sales
          paths-to-match: /api/v1/**
```

- Search for `Environment.getProperty(...)`, `Environment.containsProperty(...)`, `@Value('${...}')` and other `${...}` placeholders that name a list of objects. `getProperty` now returns `null` for the list's own name and `containsProperty` returns `false`, as in a Spring Boot application, and a placeholder that names the list has nothing to resolve to.
- Read such a list through `grailsApplication.config`, which is unchanged, or bind it with `@ConfigurationProperties`.
- The same applies to a list holding lists of objects, presented under names such as `rows[0][0].a`, and to a list mixing plain values and objects, such as `mixed: [a, {b: 1}]`, presented as `mixed[0]` and `mixed[1].b`. Within such a list, an empty object, an empty list or a `null` value is presented as an empty string under its own name, such as `empty[0]`.
- The `config-report` command lists such a list as one row for each value.
- GORM used outside a Grails application, given a `ConfigurableEnvironment`, no longer finds the list under its own name. In a Grails application a datastore is given `grailsApplication.config` and sees the list as before.

## Promises and Async Requests

The default `PromiseFactory` now uses the JDK `CompletableFuture`. Existing Groovy `task`, `tasks`, `then` and `onError` entry points remain supported. Explicit GPars and RxJava providers remain available.

- Search for casts of the promise factory to `ExecutorPromiseFactory` or `ExecutorService`. The default factory is neither any more. Inject `PromiseFactory` and use its promise operations.
- In an application, promises and the default event bus now share Spring Boot's `applicationTaskExecutor`. The execution policy changes from an expanding cached pool to Boot's configured executor, normally eight core threads with an unbounded queue. Review blocking workloads, and configure `spring.task.execution.pool` and the shutdown settings, or enable `spring.threads.virtual.enabled`. Do not wait synchronously for nested tasks in the same saturated pool. Standalone promise factories keep a cached pool.
- Eager web promises and returned promises now honor `spring.mvc.async.request-timeout`. When it is unset the servlet container's default applies, normally 30 seconds on Tomcat; eager web promises had no timeout before. A timeout returns HTTP 503 and can use a `503` URL mapping. Set the timeout explicitly for long-running responses.
- With the default and the virtual-thread factories, single and aggregate error callbacks receive the original failure, while direct and chained `get()` calls throw the `ExecutionException` wrapper of the `Future` contract. Check `catch` blocks around `get()`.
- Search for `PromiseFactoryBean`, `AsyncController.startAsync()`, the FutureTask-based promise implementations and the Grails-specific Servlet async wrappers. They are deprecated for removal. Replace custom factory wiring with the async plugin and Boot executor configuration, and replace manual Servlet dispatch and completion with a returned `Promise`.
- With `grails.async.promiseFactory=virtual-thread`, aggregate `onComplete` now resolves to the callback's result, and aggregate `onError` observes the original failure and leaves the returned promise failed. Before, it recovered to an empty list.

## GORM API Changes in Grails 8

### `count()` Returns `Long`

`count()` and the `count` property of a domain class return `Long` instead of `Integer`. The change covers `GormStaticOperations.count()` and `getCount()`, the members generated on every domain class, and `RestfulController.countResources()`. The old `Integer` result was narrowed with `intValue()`, which silently truncated a count above `Integer.MAX_VALUE`.

Dynamic Groovy is unaffected: `Book.count() == 5`, `int n = Book.count()` and arithmetic on the result keep working. Search for these cases, which need a change:

- Statically compiled code that declares the result as `Integer`, and methods that return it as `Integer`. Declare `Long`.
- Overrides of `countResources()` in subclasses of `RestfulController`. Change the return type to `Long`.
- Java callers that assign the result to `Integer` or `int`. `long total = Book.count()` needs no change; use `Book.count().intValue()` only when the count is known to be small.
- GSP model directives and JSON view `model` blocks that declare the count as `Integer`. Declare `Long`, or `Number` to accept either width.
- Implementations of `HalViewHelper`: `paginate()` and `links()` now declare `total` as `Long`, and `paginate()` declares `offset` as `Long`. Callers are unaffected.
- GraphQL schemas: a count field such as `personCount: Int` is now `personCount: Long`. Queries and the JSON value are unchanged, but generated clients and schema snapshots must be regenerated.

```groovy
@CompileStatic
class ReportService {
    // Grails 7: Integer bookCount() { Book.count() }
    Long bookCount() { Book.count() }
}

class BookController extends RestfulController<Book> {
    // Grails 7: protected Integer countResources() { ... }
    protected Long countResources() { ... }
}
```

```groovy
// grails-app/views/book/index.gson
model {
    Iterable<Book> bookList
    Long bookCount     // or Number, to accept either width
}
```

### Criteria Closures Are Typed `DELEGATE_FIRST`

GORM's criteria closure methods (`where`, `whereLazy`, `build`, `buildLazy`, `projections`, `and`, `or`, `not`, `find`, `get`, `list`, `count`, `asBoolean`, and the closure subquery forms of `in`, `inList`, `notIn`, `eqAll`, `gtAll`, `ltAll`, `geAll`, `leAll`, `gtSome`, `geSome`, `ltSome` and `leSome`) now declare `strategy = Closure.DELEGATE_FIRST` in their `@DelegatesTo` annotations. This is the strategy GORM already applied at runtime. Only statically compiled code is affected.

- Forwarding a `Closure` parameter to one of these methods no longer compiles. The error is `Closure parameter with resolve strategy OWNER_FIRST passed to method with resolve strategy DELEGATE_FIRST`. Declare the same strategy on the parameter, or cast the argument:

```groovy
static DetachedCriteria<Book> withExtra(
        @DelegatesTo(value = DetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure extra) {
    new DetachedCriteria<Book>(Book).where(extra)
}

// or
new DetachedCriteria<Book>(Book).where((Closure) extra)
```

- A name that exists both on the enclosing class and on the criteria now resolves to the criteria, as dynamic code already did. This compiles and changes the query. Search `@CompileStatic` and `@GrailsCompileStatic` classes for criteria closures that read a property or call a method of the enclosing class whose name the criteria also has, such as `orders`:

```groovy
@CompileStatic
class BookQueries {
    List<Order> getOrders() { ... }

    DetachedCriteria<Book> query() {
        new DetachedCriteria<Book>(Book).where {
            inList 'name', orders    // now reads the criteria's orders, not getOrders()
        }
    }
}
```

- Statically compiled criteria closures nested inside another criteria closure now add their restrictions to the nested criteria, and projection blocks keep their projections. Before, they were applied to the enclosing criteria. Queries written that way can return different rows; recheck them.

### `mutex` Reloads the Instance Under the Lock

Where the datastore supports it, GORM for Hibernate 5 and 7 included, `entity.mutex { }` now acquires its exclusive lock without a version check and reloads the row under it. It waits for a competing writer and runs the closure on the committed state. Before, a commit to the row between the load and the `mutex` call made it fail with an optimistic locking failure.

Search for `.mutex` and check each call:

- Unflushed changes to the instance are discarded by the reload, as they are for `refresh`. Apply changes inside the closure:

```groovy
book.title = 'new title'
book.mutex { book.save() }        // the pending title is discarded by the reload

book.mutex {                      // apply the change inside instead
    book.title = 'new title'
    book.save()
}
```

- An active transaction is required. Without one the call throws `jakarta.persistence.TransactionRequiredException`.
- The instance must be attached to the current session. A detached one throws `IllegalArgumentException`; call `attach()` first.
- Code that relied on `mutex` failing fast on a stale version should check the version itself, or use `entity.lock()`, which is unchanged.

### Dynamic Finder Classes for Datastore Authors

This concerns only a GORM datastore implementation or plugin outside the Grails repository. Applications are unaffected: dynamic finders such as `Book.findByTitle('The Stand')` behave as before.

- `AbstractFinder`, `AbstractFindByFinder`, `FindByFinder`, `FindByBooleanFinder`, `FindAllByFinder`, `FindAllByBooleanFinder`, `FindOrCreateByFinder`, `FindOrSaveByFinder` and `CountByFinder` in `org.grails.datastore.gorm.finders` are removed. Replace `new FindByFinder(...)` and its siblings, and subclasses of these classes, with the static factory methods of `SingleResultFinder` (`findBy`, `findByBoolean`, `findOrCreateBy`, `findOrSaveBy`), `ListResultFinder` (`findAllBy`, `findAllByBoolean`) and `CountFinder` (`countBy`), which take a datastore resolver or a `Datastore`, and a `MappingContext`.
- `DynamicFinder` is now a concrete class that only parses a finder method name and builds the criteria. It no longer implements `FinderMethod` and has no `invoke` or `doInvokeInternal`. Code that only holds finders as `FinderMethod` compiles unchanged.

## Named Connections and Tenant Filtering in GORM

These changes concern applications with more than one datasource or with multi-tenancy. An application with a single datasource and no multi-tenancy is unaffected.

### Calls on a Class Inside a Named Connection's Block Use That Connection

Inside a session or transaction opened through a named connection, such as `Book.secondary.withTransaction { }` or `Book.secondary.withNewSession { }`, calls on `Book` itself, such as `Book.get(id)` or `book.save()`, now use that connection. In Grails 7 they used the default connection.

```groovy
Book.secondary.withTransaction {
    def book = Book.get(42)     // Grails 7: read from the default database; Grails 8: from secondary
    book.save()                 // Grails 7: default connection; Grails 8: secondary
}
```

This changes where data is read and written without an error. In Grails 7 the save failed with `No Session found for current thread` when only the named connection's session was open, but with a default-connection session open further out, as the open-session-in-view interceptor or an outer `@Transactional` provides, it wrote to the default database.

- Search for `.withTransaction`, `.withNewTransaction`, `.withSession` and `.withNewSession` called on a named connection (`Book.<connection>.with...`), and for `@Transactional(connection = ...)`. Inside each block, look for calls on the same domain class that do not name a connection.
- A copy between connections such as the following used to write the copies to the default database and now writes them back into `legacy`:

```groovy
Book.legacy.withTransaction {
    Book.legacy.list().each { new Book(name: it.name).save() }    // meant for the default database
}
```

- To reach the default connection from inside such a block, name it:

```groovy
Book.secondary.withTransaction {
    Book.'default'.withSession { Book.count() }    // counts the default database
}
```

- Other domain classes are not affected, and an operation that names a connection keeps it.
- The same holds inside a method annotated `@Transactional(connection = 'secondary')`, for every domain class mapped to `secondary`. A class that is not mapped to it keeps its own connection, and a multi-tenant class is left to its tenant.
- A method annotated `@Transactional` without a connection, called from inside such a block, does not reset the routing. Its transaction is the default connection's, but its calls on a class the block routes still reach the block's connection and join the block's transaction. This also holds under `propagation = REQUIRES_NEW`, whose separate transaction then covers none of those writes. Name the connection, as in `@Transactional(connection = 'secondary')`, where the method's own transaction has to cover its writes.

### Instance Operations Through a Named-Connection Static API

`Book.secondary.save(book)`, `Book.secondary.delete(book)` and the other instance operations reached through a named-connection static API now use that connection: `save`, `insert`, `merge`, `delete`, `attach`, `isAttached`, `discard`, `ident`, `lock`, `mutex`, `refresh` and `instanceOf`. Before, they resolved the default connection and failed with `No Session found for current thread` when only the named connection's session was open.

- Search for these calls on a named connection, such as `Book.secondary.save(book)`, and check that the named connection is the database the call is meant for.
- On GORM for Hibernate 5, a named-connection static API now reports its real connection name as its qualifier. It reported `default` before. Check code that reads the qualifier.
- The instance form `book.secondary.save()` already used the named connection and is unchanged.

### `lock(id)` Applies the Multi-Tenancy Filter

`DomainClass.lock(id)` on a `MultiTenant` entity now resolves within the bound tenant, as `get(id)` does, and returns `null` for a row that belongs to another tenant. Before, it locked and returned the row of any tenant. Entities that are not multi-tenant are unaffected.

- Search for static `lock(` calls on multi-tenant domain classes. Add a `null` check where code assumed a result.
- Code that relied on `lock(id)` reaching across tenants has to bind the owning tenant:

```groovy
Tenants.withId('tenant1') {
    Book.lock(id)
}
```

## GORM for Hibernate 7: Sequences, Versions, and Events

These changes apply only to applications on GORM for Hibernate 7. Applications on Hibernate 5 are unaffected.

### Sequence Names Follow the Table

On Hibernate 7 an unnamed sequence is named after the entity's table with the `_SEQ` suffix, such as `book_SEQ` for the table `book`. Grails 7 on Hibernate 5 took every such id from the single sequence `hibernate_sequence`. Two mappings are affected:

- `id generator: 'sequence'` without `params: [sequence: '...']`.
- The default id mapping, with no `id` entry in `mapping` at all, on a database whose dialect draws native ids from a sequence, such as PostgreSQL and Oracle. This is the mapping most domain classes use, so on such a database every domain class is affected.

Databases that generate native ids with IDENTITY or auto-increment columns, such as H2, MySQL and MariaDB, are unaffected. So are mappings that already name a sequence.

When the application upgrades against an existing schema, the result depends on `dbCreate`:

| `dbCreate` | Result |
|------------|--------|
| `create`, `create-drop` | The sequences are created with the tables. Nothing to do. |
| `validate` | Startup fails with `Schema validation: missing sequence [book_SEQ]`. |
| `none` | The first save fails, because the sequence does not exist. |
| `update` | Hibernate creates `book_SEQ` starting at 1, which collides with the ids already in the table. |

The `update` case gives no error at startup: the application starts, and the new sequence then hands out ids that are already in the table. Before starting an application with `dbCreate: update` on Hibernate 7 against an existing PostgreSQL or Oracle database, search the domain classes for `generator: 'sequence'` and for classes without an `id` mapping, and choose one of these:

- Keep drawing ids from `hibernate_sequence` for every unnamed sequence. This is the drop-in choice for a database that already has that sequence:

```yaml
hibernate:
    id:
        db_structure_naming_strategy: legacy
```

- Keep the old name for a single class:

```groovy
static mapping = {
    id generator: 'sequence', params: [sequence: 'hibernate_sequence']
}
```

- Add the new sequences to the schema, each starting above the highest id already in its table. The schema snapshot of the database migration plugin includes them.

### A New Entity Starts at Version 0

An entity whose identifier is generated before the insert, for example one mapped with `id generator: 'increment'` or `'assigned'`, or any member of a `tablePerConcreteClass` hierarchy, is now written with a single `INSERT` and starts at `version` 0, as on Hibernate 5. Before, an `UPDATE` followed the `INSERT` and the entity started at `version` 1.

- Search tests and code that compare the `version` of a new entity with 1, or that count statements or update events for a new entity. Expect the value Hibernate 5 gives:

```groovy
def book = new Book(title: 'Grails').save(flush: true)   // id generator: 'increment'
assert book.version == 0                                 // was 1 before Grails 8.0
```

### Persistence Listeners Receive Persist and Merge Events

An `AbstractPersistenceEventListener` on Hibernate 7 now receives a `PersistEvent` (`eventType` `Persist`) for every explicit or flush-time cascaded persist, and a `MergeEvent` (`eventType` `Merge`) for every merge. Neither can be cancelled. Hibernate 5 publishes `SaveOrUpdateEvent` when an entity is saved and no event for a merge.

- Search for subclasses of `AbstractPersistenceEventListener` that switch over `event.eventType` and throw in the default branch. Handle the new values:

```groovy
@Override
protected void onPersistenceEvent(AbstractPersistenceEvent event) {
    switch (event.eventType) {
        case Persist:                      // new on Hibernate 7
        case Merge:                        // new on Hibernate 7
        case PreInsert:
            // ...
            break
        default:
            throw new IllegalStateException("Unhandled ${event.eventType}")
    }
}
```

# Grails 8.0.1

Apply this part when the application is on Grails 7 or on Grails 8.0.0.

## GORM for Hibernate 7: Custom User Types

- Custom `UserType` implementations must be rewritten for the Hibernate 7 contract (generic in the Java type, `getSqlType()`, position-based `nullSafeGet()` / `nullSafeSet()`). A type that spans several columns implements `CompositeUserType`; the columns of its `type` mapping are assigned to the embeddable's attributes in the alphabetical order of the attribute names, which is also the index `getPropertyValue()` and `instantiate()` use, where Hibernate 5 followed `getPropertyNames()`. Check the column-to-attribute assignment of every multi-column type when porting it.

## GORM for Hibernate 7: Unidirectional hasMany Associations That Share a Join Table

- Two unidirectional `hasMany` associations to the same class share one join table by default (a key column each, one element column), and the table is now written. A set gets a unique key per association over its key column and the element; on Oracle and Db2 for Linux, UNIX and Windows it is one unique key over both key columns and the element. Two lists or two maps sharing a join table are written too (#16700): each keeps its rows unique by its own key and index columns, since a position or a map key is taken once while an element may repeat, and the table has no primary key, because the one Hibernate makes over one list's key and index columns rejects every row of the other list. A schema created by an earlier Grails 8 release has that primary key and `NOT NULL` owner-key and index columns, which `dbCreate: 'update'` does not change: drop the primary key and make the owner-key and index columns nullable with a migration. Each collection on a shared join table isolates its rows during reads, element removals, and bulk deletes: for lists and maps sharing the same foreign-key and element columns, queries and deletions are restricted by each list's distinct index column so neither collection interferes with the other. A bag has no key; its elements may repeat. A schema from an earlier Grails 8 release has only the first association's unique key: `dbCreate: 'update'` adds the second, and on Oracle and Db2 add a migration that replaces it with the wider key rather than expecting `update` to do it.
- Removing an element from a unidirectional `hasMany` set or bag join table uses GORM's collection persister to delete only the specified row by owner key and element, matching Hibernate 5 behavior. An application that configures `hibernate.persister.resolver` keeps its own resolver and loses this behavior (causing `removeFrom` to delete every row of the owner).
- GORM sets `hibernate.query.sqm.translator` to `RestrictedCollectionSqmTranslatorFactory` by default, which translates `is empty` / `is not empty` on an association that shares a join table and a key column as a size test, so the test does not see the other association's rows (#16748). An application that configures `hibernate.query.sqm.translator` itself keeps its translator and the Hibernate 7 behaviour; flag that setting, and `hibernate.persister.resolver` above, rather than treating a wrong `is empty` result as a query bug.

## GORM for Hibernate 7: Collection Criteria Next to Association Blocks

- Collection criteria such as `isNotEmpty`, `isEmpty`, or `sizeEq` resolve properly to the parent collection property even when placed adjacent to an association block of the same name. Applications that reordered criteria or replaced `isEmpty('pets')` with a size projection to work around earlier 8.0 `ClassCastException` issues can retain standard criteria syntax.

## Map Entries Named `properties` or `metaClass`

- **A map entry named `properties` or `metaClass` is not reachable as a property or by subscript, on any `Map`.** In Groovy 4 `map.properties` and `map['properties']` read the map entry. In Groovy 5 both return the Groovy meta-properties of the map object, in dynamic and in statically compiled code alike, and `map['properties'] = value` throws `groovy.lang.ReadOnlyPropertyException`. `metaClass` reads the same way; assigning to it through a subscript sets the metaclass of the map, as it did in Groovy 4, and a value that is not a `MetaClass` throws `groovy.lang.GroovyCastException`. `class` and `empty` are exempt and still address the entry. Search for maps holding data the application does not control, such as the `properties` member of a GeoJSON `Feature`, and read these keys with `get('properties')`. Grails stores and loads them through the `Map` API, so they round-trip through a `Map` property or an embedded map of a GORM for MongoDB entity, and through data binding of a `Map` property, whether the value arrives as a nested map or as an indexed key such as `attributes[properties]`.
  - With GORM for Hibernate, a loaded `Map` property is Hibernate's `PersistentMap`, whose accessors are not redirected: `attrs.key`, `attrs['value']`, `attrs.owner`, `attrs.role`, `attrs.size`, `attrs.session` and `attrs.dirty`, among others, return the state of the collection rather than the entry. Read such an entry with `get`.
  - With GORM for MongoDB, a loaded `Map` property is a dirty-checking wrapper with accessors named `target`, `parent`, `property`, `originalSize` and `assigned`. The wrapper resolves those five names to the map entry in dotted and subscript form, as in Grails 7. Dotted access from statically compiled code still reaches the accessor; read such an entry with `get` there.

## Gradle Configuration Cache

- Projects created with Grails 8.0 or later enable the Gradle configuration cache (`org.gradle.configuration-cache=true` in `gradle.properties`). A project can add the same property once it uses the Grails 8.0 Gradle plugins and BOM; when upgrading from an earlier Grails 8 milestone or release candidate, update the plugins and BOM first. Gradle then fails the build when a task action (`doFirst`, `doLast`, `onlyIf`) uses `project`, other tasks or build-script properties while it runs: read those values while configuring instead. Mark tasks of third-party plugins without support `notCompatibleWithConfigurationCache(...)`, or leave the property off. See the guide's "Using the Gradle Configuration Cache" section.

## Grails Data Plugins Leave the Buildscript Classpath

- Builds generated by earlier versions of Grails Forge put the Grails Data plugin on the Gradle `buildscript` classpath, as `classpath "org.apache.grails:grails-data-hibernate5"`, and in `buildSrc` when the build used one. Remove that `classpath` entry and the matching `implementation` line in `buildSrc/build.gradle`; keep `classpath "org.apache.grails:grails-gradle-plugins"` and the application's own `implementation "org.apache.grails:grails-data-hibernate5"`.
- Left in place, the plugin's global compile-time transformations run in the Groovy that Gradle embeds, and the build can fail at configuration time with an error such as `java.lang.NoSuchMethodError: 'java.lang.Number org.codehaus.groovy.runtime.typehandling.NumberMath.remainder(java.lang.Number, java.lang.Number)'` raised from `GlobalDetachedCriteriaASTTransformation`.
- The same applies to `grails-data-hibernate7` and to any other Grails module that registers a global transformation.

## DISCRIMINATOR Multi-Tenancy

- **With `DISCRIMINATOR` multi-tenancy, only `Tenants.withoutId` and `@WithoutTenant` run without a tenant.** A tenant id of `default` is now a tenant id like any other, whether a `TenantResolver` returns it or it is passed to `Tenants.withId`: queries return the instances of the tenant `default`, and a saved instance gets the tenant id `default`. Search for `TenantResolver` implementations that return `default`, for `SubDomainTenantResolver` used on `localhost` during development, for `new FixedTenantResolver()`, and for `Tenants.withId('default')`. The `DATABASE` and `SCHEMA` modes are otherwise unchanged.
  - Code that relied on a resolver returning `default` to work with every tenant has to run inside `Tenants.withoutId` or in a method annotated with `@WithoutTenant`.
  - With GORM for MongoDB, GORM for Neo4j and GORM's in-memory datastore, queries inside `Tenants.withoutId` still return only the instances of the tenant `default`; there `Tenants.withoutId` only lets a saved instance keep the tenant id that is set on it. Inside `Tenants.withoutId` these datastores still use `0` for a numeric tenant id.
  - With a numeric tenant id, a resolver that returns `default` now fails, as it does with GORM for Hibernate.
  - A `TenantResolver` that returns `null` now causes a `TenantNotFoundException`, in every multi-tenancy mode.
  - Inside `Tenants.withoutId`, `Tenants.currentId()` returns `default`, and `Tenants.withCurrent` and `@CurrentTenant` keep running without a tenant. Use `Tenants.isWithoutId(datastore)` to tell whether code runs without a tenant id. `Tenants.isWithoutId()` does so for the default datastore, and throws an `IllegalStateException` when GORM isn't initialized or more than one GORM implementation is configured.
  - GORM for Hibernate now also restricts a collection of a multi-tenant entity to the current tenant when it is loaded lazily or fetched by join, including a many-to-many collection. A collection of a multi-tenant entity mapped with `fetch: 'join'` is loaded eagerly with a select of its own instead. Loading such a collection lazily without a current tenant, outside `Tenants.withoutId`, throws `TenantNotFoundException`.
  - With GORM for Hibernate, `load` and `proxy` of a multi-tenant entity run a query, as `get` does, and return `null` when no instance is found, instead of a proxy that fails when it is used. Add `null` checks.
  - Before a query on an entity that isn't multi-tenant but has a collection of a multi-tenant entity, directly or through its associations, GORM for Hibernate now calls the `TenantResolver` and activates the filter for the current tenant. When the resolver throws a `TenantNotFoundException` or returns `null`, the query runs as before; any other exception from the resolver fails the query. A resolver must not run a query on a multi-tenant entity, because that query calls the resolver again.
- **With `DISCRIMINATOR` multi-tenancy, lookups by id are restricted to the current tenant on MongoDB, Neo4j and the in-memory datastore.** GORM for MongoDB, GORM for Neo4j and GORM's in-memory datastore used by unit tests now restrict `get`, `read`, `exists`, `getAll`, `load` and `proxy` to the current tenant, as queries and dynamic finders already were. Previously these methods returned an instance that belongs to another tenant. GORM for Hibernate already restricted `get`, `read`, `exists` and `getAll`. Search for lookups by id on multi-tenant domain classes, in unit tests too, that run without a tenant or for another tenant's instance.
  - The id of another tenant's instance is treated like an id that does not exist: `get` and `read` return `null`, `exists` returns `false`, `getAll` returns `null` in its place, and the proxy returned by `load` or `proxy` throws when it is initialized.
  - Without a current tenant, these methods throw `TenantNotFoundException`, as a query does. `getAll` without ids still returns an empty list.
  - To keep the previous result, run the lookup inside `Tenants.withoutId { }`. With GORM for MongoDB and GORM's in-memory datastore, lookups by id are not restricted to a tenant there. A tenant id of `default` does not lift the restriction.
  - A lookup by id still finds an instance that the session holds for the current tenant, including one that has been saved but not flushed, and it does not flush the session.

## Data Binding White Lists

- **Data binding lists are generated wherever a class is compiled.** Every class that implements `grails.validation.Validateable` or declares `constraints` or `transients`, and every class that extends one, now gets its generated lists of bindable properties when it is compiled with `grails-web-databinding` on the compile classpath. Previously only domain classes and the command objects compiled with their controller got them. Search for command objects in plugins or shared modules, `Validateable` objects bound as nested properties, command objects declared inside another class, and `@Entity` classes outside `grails-app/domain`.
  - Their dynamically typed properties, declared with `def` or as `Object`, are no longer bound by default in the permissive mode. Declare a type for the property or mark it `bindable: true`.
  - `bindData` no longer binds the `id`, `version`, `dateCreated` and `lastUpdated` of an entity compiled outside `grails-app/domain`.
  - `bindData` with an `exclude` list and no `include` list keeps the generated list in effect and removes the excluded properties from it.
  - A property of type `org.springframework.validation.Errors` is never bound from request data.
  - These classes carry new `public static final` fields. Declare a `serialVersionUID` on a `Serializable` class that has none and is kept in a persisted HTTP session or a distributed cache, or instances serialized before the upgrade do not deserialize.
  - A compiler warning that the lists of a subclass are derived from the parent's bytecode, or that a command object is bound without restriction, means the plugin or module holding the parent or the command object must be recompiled with Grails 8.0.1 or later.

## Exception Mappings and the HTTP Server Observation

- **A `"500"` mapping that names an exception matches the thrown exception before its root cause.** Search `UrlMappings` for `"500"` mappings with an `exception`. Where the thrown exception and its root cause each match a mapping, the mapping for the thrown exception now answers: with mappings for `SQLException` and `RuntimeException`, a `DataAccessException` caused by a `SQLException` now goes to the `RuntimeException` mapping. To keep the previous routing, also map `DataAccessException` to the first handler, declared after the `RuntimeException` mapping, since the mapping declared last answers. In the error handler, the `cause` of `request.exception` is now the exception the action threw.
- **An exception an action throws is recorded on the HTTP server observation.** `http.server.requests` now tags a failed request with the exception the action threw and the route of the action, also when an error view or an error controller answers. Check alerts that count a non-`none` `error` tag, which will see more server errors, and dashboards keyed on the `uri` of an error controller or on `UNKNOWN` and `REDIRECTION`, since those requests are now reported under the route of the action. After a forward to an error controller, the `jakarta.servlet.error.exception` request attribute holds the exception the action threw for a filter that reads it once the chain has returned.

## Running on Java 25: Metaspace and Class Pointer Flags

Metaspace is unlimited by default on Java 21 and Java 25 unless `-XX:MaxMetaspaceSize` is set. `-XX:MetaspaceSize` is only the initial threshold that triggers garbage collection, not a limit. A Grails application loads many classes, and the heap limit (`-Xmx` or `-XX:MaxRAMPercentage`) does not cover metaspace, so a container sized only for the heap can be killed by the container runtime when metaspace grows.

- Search startup scripts, Dockerfiles, service units and deployment manifests for `-XX:MaxMetaspaceSize` and `UseCompressedClassPointers`.
- When running on Java 25, set an explicit ceiling so that the JVM raises `OutOfMemoryError: Metaspace` before the container runtime kills the process:

```bash
java -Xmx512m -XX:MaxMetaspaceSize=256m -Dgrails.env=prod -jar application.jar
java -XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=256m -Dgrails.env=prod -jar application.jar
```

- Choose the value from a measurement: run the application under representative load, read metaspace usage with `jcmd <pid> VM.metaspace`, and add a safety margin.
- `-XX:+/-UseCompressedClassPointers` is deprecated in Java 25 and obsolete in JDK 27, where it is accepted with a warning and ignored. Remove `-XX:+UseCompressedClassPointers`; it is the default. Before removing `-XX:-UseCompressedClassPointers`, check the class-space use that `jcmd <pid> VM.metaspace` reports, because dropping it changes the memory layout, and from JDK 27 compressed class pointers are always on.

## `rlike` on Hibernate 5 and Hibernate 7

- **`rlike` falls back to `like` on the same databases with Hibernate 5 and Hibernate 7.** Search criteria and where queries for `rlike`. With Hibernate 5, a database that has no regular expression operator that Grails uses, such as SQL Server, Sybase, CockroachDB and Db2 for z/OS, no longer fails with `rlike is not supported with the configured dialect`: `rlike` falls back to `like`, as it does with Hibernate 7, so `%` and `_` are the wildcards there and not regular expression characters. Such a query now matches different rows instead of failing. Whether case matters follows the database: Oracle, H2 and Db2 match case-sensitively, MySQL and MariaDB by the collation of the column, and PostgreSQL case-sensitively (`~`) with Hibernate 7 and case-insensitively (`~*`) with Hibernate 5.

## Spring Security REST Plugin: OAuth Callback and Encrypted JWTs

- **The OAuth `callback` parameter of the Spring Security REST plugin needs allowed origins.** Search for requests to `/oauth/authenticate/<provider>` that pass a `callback` parameter. The plugin redirects to that URL, with the token appended, only when the URL's origin (scheme, host and port) is listed in the new setting `grails.plugin.springsecurity.rest.oauth.allowedCallbackOrigins` and the URL has a path, a query or a fragment after the host. The setting has no default: when it is missing, every `callback` URL is ignored and the plugin redirects to the `frontendCallbackUrl`, as it does without the parameter. List the origins of the frontend, for example `grails.plugin.springsecurity.rest.oauth.allowedCallbackOrigins = ['https://my.frontend-app.com']`. Since the plugin became part of Grails, the `callback` parameter failed with a `MissingMethodException`; it works again within these limits.
- **Encrypted JWTs of the Spring Security REST plugin are signed.** With `grails.plugin.springsecurity.rest.token.storage.jwt.useEncryptedJwt` enabled, the plugin signs the claims with the RSA private key, using RS256, before it encrypts them with the RSA public key, and accepts an encrypted JWT only when the JWT inside carries a valid signature made with the private key. Search the configuration for `useEncryptedJwt`, and the code for `RSAKeyProvider` implementations, `JwtService.parse`, `accessTokenJwt`, `EncryptedJWT` and `RSA1_5`.
  - Access and refresh tokens issued before the upgrade are no longer accepted, so users have to log in again. Upgrade instances that share the keys together: an instance that was not upgraded rejects the tokens of an upgraded one, and the other way round.
  - An application that generates or validates encrypted JWTs needs both keys, also when a custom `RSAKeyProvider` provides them. The private key must be at least 2048 bits long; a shorter key fails with an `IllegalArgumentException` when a token is generated. Use the key pair for these tokens only.
  - For an encrypted JWT, `JwtService.parse` and the `accessTokenJwt` property of an `AccessToken` now return a `grails.plugin.springsecurity.rest.token.generation.jwt.NestedJwt` instead of a `com.nimbusds.jwt.EncryptedJWT`. Its claims are those of the signed JWT inside, and its header is that of the encrypted JWT.
  - With encrypted JWTs enabled, a signed JWT is rejected as an invalid token, and with signed JWTs, an encrypted JWT is. Previously such a token failed with a `NullPointerException`.
  - An encrypted JWT whose content encryption key is encrypted with `RSA1_5` is rejected unless `grails.plugin.springsecurity.rest.token.generation.jwt.jweAlgorithm` is `RSA1_5`. A `jwtService` bean that the application defines itself accepts `RSA1_5` as before, unless the application sets the new `jweAlgorithm` property of `JwtService` too.

## GSP Tag Attributes

- **More GSP tags HTML-encode the attribute values they write**, as `<g:link>` and `<g:textField>` already did: the URL in `action` and `formaction` of `<g:form>`, `<g:uploadForm>` and `<g:formActionSubmit>`, and the hidden fields that a `RequestDataValueProcessor` adds to a form; `name`, `value`, `form` and the `id` taken from the name of `<g:checkBox>`, on the checkbox and its hidden field; `value`, the `action` in the button's name and `src` of `<g:actionSubmitImage>`; `name`, `id` and `selectDateClass` of `<g:datePicker>`; `name` of `<g:radio>` and `<g:radioGroup>`, and the `id` taken from it; the `noSelection` key of `<g:select>`; `type` of `<g:field>`; the values of `attrs` of `<g:withTag>` that are not closures; `uri` of `<g:external>` and `src` of `<g:javascript>`; `class` of `<g:paginate>` on the current step; the `title` of `<g:sortableColumn>`, which it writes as the text of the column's link (a text from `titleKey` is written as the message source returns it); and the `*Class` attributes of `<g:renderException>`. The browser decodes the attribute, so a value without a character reference reaches it unchanged. A value encoded with `encodeAsHTML()` before it reaches the tag, a value captured from a GSP body with `<g:set>`, and the output of `fieldValue()` are written once, also when a `RequestDataValueProcessor` returns the value unchanged. Search the GSP views, layouts and tag libraries for these tags and check:
  - A character reference written in the page, as in `name="a&amp;b"`, is now encoded as well. Write the character itself.
  - `<g:form>` and `<g:formActionSubmit>` build their URL from `uri`, `base`, `controller` and their other link attributes and only then encode it, so a value encoded beforehand is encoded twice there, as in the `href` of `<g:link>`. Pass those values unencoded. A `url` attribute that the tag writes as it is given is written once.
  - When a `RequestDataValueProcessor` changes a value, the tag encodes what the processor returns, so a value encoded beforehand is encoded twice, as `<g:textField>` already did.
  - A `title` of `<g:sortableColumn>` that holds markup must be marked with `raw()`.
  - A value marked with `raw()`, and the text and `default` of `message()`, which comes back marked raw, are still written as given by the tags that wrote them so before. Do not pass request data through `raw()` or as the `default` of `message()`.
  - Tests that compare rendered markup: an `&` in the URL of a form is now written as `&amp;`, as `<g:link>` writes it in an `href`.
- **Values that were encoded twice are encoded once.** `<g:radio>` encoded its `value` twice, so a value with `&`, `<`, `>`, `"` or `'` was submitted with character references in it, such as `R&amp;D` for `R&D`; remove any code that decoded the submitted value to undo this. A value encoded beforehand is no longer encoded twice in the `value` of `<g:textField>`, `<g:hiddenField>` and the other field tags when a `RequestDataValueProcessor` returns it unchanged, nor in an option of `<g:select>`. `<g:paginate>` and `<g:sortableColumn>` write a `class` that was encoded beforehand once next to the classes they add, such as `step` and `sortable`.

## Placeholders That Name a Configuration List

- **A placeholder that names a configuration list no longer fails to resolve, and a list of plain values resolves to its elements.** Search for `@Value('${...}')` and other `${...}` placeholders that name a list in `application.yml` or `application.groovy`. A placeholder that names a list of objects is now left as written, so the injection point receives the placeholder text, or its default when it has one; in 8.0.0 a placeholder without a default failed to resolve. A list of plain values only, such as `names: [a, b]`, is now presented by its indexed names too, and a placeholder that names it resolves to its elements joined by commas. Read a list of objects through `grailsApplication.config` or bind it.

## GORM for Hibernate 7: setting for a sort by an association

A sort by a property of an association, such as `Book.list(sort: 'author.name')`, joins the association with an inner join, so it leaves out the instances whose association is `null`. That is unchanged by default. As of 8.0.1, GORM for Hibernate 7 reads the `grails.gorm.leftJoinAssociationSort` setting, default `false`, as GORM for Hibernate 5 does. Set to `true`, the association is joined with a left join and the sort keeps those instances.

A block-local sort uses a join type requested for its full path at the query root: `join('team.club', JoinType.LEFT)` applies to `team { order('club.name') }` or `team { order('club.id') }`, before or after the block. LEFT retains the missing club and INNER excludes it, independently of the association-sort setting.

A sort by a collection property works as with Hibernate 5: the collection is joined with an inner join, with or without the setting, so instances whose collection is empty are left out. `list` and a criteria query return an instance once for each element, as with Hibernate 5. A where query, a detached criteria and a dynamic finder return it once from the rows they read (they returned it once for each element). `max`, `offset`, `count` and `totalCount` work on the rows of the elements, so a page can hold fewer instances than its total counts. An association after the collection on the sort path, as in `books.publisher.name`, is joined with an inner join too, with or without the setting (with Hibernate 5 the setting keeps a book without a publisher). A query that fetches a collection (`fetch: [tags: 'join']`, `join('tags')` or a join type requested for it, also through a to-one association such as `fetch: ['author.books': 'join']`) returns each instance once, and `count` and `totalCount` count the instances; a bag that another collection join would multiply is loaded after the query, so the rows of the other collection are paged and counted. Use a property of the queried class or a to-one association to page without repeated instances.

- Nothing needs to change to keep the previous results.
- Search for `sort:` arguments, `.sort(` calls and `order(` calls whose property contains a `.`, such as `sort: 'author.name'`. If the application expects every instance from such a query, set `grails.gorm.leftJoinAssociationSort: true` in `application.yml`.
- With the setting off, a sorted where-query count and the totalCount of sorted criteria, detached-criteria and static list pages retain the sort's inner joins and now exclude roots without the association, as the list does. A sort through a collection counts a row for each element of the collection, as with Hibernate 5, since max and offset page through those rows. Remove the sort from a separate count query to count roots without that restriction. Counts omit ORDER BY but keep its joins. findWhere and findAllWhere now apply their sort argument, which Hibernate 7 ignored, and the setting applies to them as with Hibernate 5.
- A sort by an association that a where query, a detached criteria, a dynamic finder or a criteria query fetches with `'join'` or `'eager'`, or with `join('author')`, now uses the left join of the fetch, with or without the setting, so it returns the instances without the association, as a static list with such a fetch already did (with Hibernate 5 only the static list keeps them unless the setting is enabled); request `JoinType.INNER` for the fetch, or with the setting off leave the fetch out, to leave them out as before.
- An `order` inside an association block of a criteria query, such as `author { order('name') }`, now sorts the query by that property of the association, as with Hibernate 5. It was ignored. Remove the `order` to get the previous order. Such an `order` now fails with an `IllegalArgumentException` when it names no property of the association, and at the database when the query has a `distinct`, `groupProperty` or aggregate projection that does not select the property of the order; remove it there, since it had no effect.
- A join type given to an association block inside `and`, `or` or `not`, as in `or { author(1) { ... } }` for a left join, was ignored and is now applied. To keep the inner join, leave the join type out.
- The setting is enabled by default as of Grails 8.1 and removed in Grails 9.

## GORM for Hibernate 7: a join type requested for an association is always applied

With GORM for Hibernate 7, a where query, a detached criteria, a dynamic finder or a criteria query can request the type of the join of an association, with the `fetch` argument, as in `Book.where { ... }.list(fetch: [author: JoinType.INNER])`, with `join('author', JoinType.INNER)`, or, on a data service method that queries by its parameters, such as `Book find(String title)`, with `@Join(value = 'author', type = JoinType.INNER)`. The requested join type was applied to the list of a query that requests it for an association of the queried class, and otherwise ignored. As of Grails 8.0.1, a requested join type other than `JoinType.LEFT` is applied whether or not anything else in the query uses the association, as GORM for Hibernate 5 applies it.

- Search for `JoinType.` in `fetch:` arguments, in `join(` calls and in `@Join` annotations on data service methods that query by their parameters, and for `join(` and `select(` calls with one argument in criteria queries. These queries now apply what they request. Data service methods named like dynamic finders, such as `findByTitle`, `findAllByTitleLike` and `countByTitleLike`, still ignore the join type of `@Join`; their generated fetch argument requests only a fetch mode. This limitation does not apply to a domain-class dynamic finder given an explicit join type in its `fetch` argument.
- With `JoinType.INNER`, the instances whose association is `null`, or whose collection is empty, are no longer counted by `count` or by the `totalCount` of a paged list, as they are already left out of the list. This also applies to a collection of a basic type, such as `static hasMany = [tags: String]`. To return and count these instances, request `JoinType.LEFT` or `'join'`.
- `JoinType.RIGHT` leaves out the same instances as `JoinType.INNER` and still fetches the association. For an association to a single instance, a right join also has a row for each associated instance that none of the queried instances refers to, such as an author without a book, unless the criteria of the query exclude it: `list` contains `null` for such a row, and `count` counts each of them. For a collection, a right join returns the same instances as an inner join.
- A query that is used as a subquery applies the join type that it requests as well, so a subquery with `join('author', JoinType.INNER)` no longer selects the instances without an author.
- `updateAll` and `deleteAll` apply the join type that the where query or the detached criteria requests, so they update or delete the instances that `list` returns: `Book.where { title ==~ 'Draft%' }.join('author', JoinType.INNER).deleteAll()` no longer deletes the drafts without an author. To update or delete those as well, as before, do not request a join type for the association, or request `JoinType.LEFT`. On MySQL, and on MariaDB before version 10.3, `updateAll` and `deleteAll` throw an `UnsupportedOperationException` when the join type is requested for a path that leads back to the queried class, such as the `parent` of a category, or for a collection of a basic type, because these databases do not allow an update or a delete to select from the table that it modifies. Restrict such a query with criteria instead, as in `Category.where { parent != null }.deleteAll()`. A `deleteAll` that applies a join type through a join table or a collection of a basic type selects the identifiers of the instances first, and fails with an `UnsupportedOperationException` for an entity with a composite identifier; it deleted every matching instance before, ignoring the join type. Remove the join type from such a delete to get the previous result.
- A join type requested for a path through more than one association, such as `fetch: ['author.publisher': JoinType.INNER]`, was ignored. Every association on the path is now joined and fetched, with the requested join type for the last one, so `JoinType.INNER` leaves out the instances without an author as well as those whose author has no publisher. When the path ends in a collection or passes through one, such as `books` in `fetch: ['books.publisher': JoinType.INNER]`, the query is restricted to the instances for which the join returns a row, and returns and counts each of them once. The collection and the other associations on the path are fetched with outer joins, so the collection holds all of its elements, also the books without a publisher: a join type decides which instances a query returns, and never leaves elements out of a fetched collection. A `JoinType.LEFT` requested for such a path fetches the associations on the path. A join type requested for a path through an embedded component, such as `fetch: ['details.author': JoinType.INNER]`, is still left alone, as with Hibernate 5.
- `list`, `findWhere`, `findAllWhere`, and `find` and `findAll` with an example, now apply a join type in their `fetch` argument, as in `Book.list(fetch: [author: JoinType.INNER])`. They ignored it and returned the books without an author.
- The `fetch` argument of the `list` of a criteria query, as in `Book.createCriteria().list(fetch: [author: JoinType.INNER]) { ... }`, was ignored, for a fetch mode as well as for a join type. It is now applied.
- In a criteria query, `join('author')` without a join type is a fetch mode, as it is in a where query: it fetches the association with a left join and returns every instance. It joined the association with an inner join and left out the instances without an author. To leave them out, as before, request `join('author', JoinType.INNER)`. `select('author')` is a fetch mode as well, and the query returns its instances: it returned the authors instead. The same holds for `select('author')` of a where query or a detached criteria, for `'select'` and `'lazy'` in the `fetch` argument of a where query, a detached criteria or a dynamic finder, and for a path through more than one association, such as `select('author.publisher')`, which returned the publishers. For a property that is not an association, `select` still selects the property.
- A fetch mode that is requested for an association after a join type replaces the join type, so `Book.where { ... }.join('author', JoinType.INNER).list(fetch: [author: 'join'])` returns every book. A join that criteria on the association make keeps the join type that was requested, and so does an alias that `createAlias` makes without a join type of its own: after `join('author', JoinType.LEFT)`, `createAlias('author', 'a')` now joins with a left join and keeps the books without an author (it joined inner unless the join type was requested after the alias). Pass the join type, as in `createAlias('author', 'a', 0)`, to keep the inner join.
- An explicit type for a to-one prefix wins over the default LEFT join needed by a nested fetch in either declaration order. `fetch: ['author.publisher': JoinType.LEFT, author: JoinType.INNER]` excludes books without an author, including in counts, paged totals and projections, but keeps books whose author has no publisher. Previously the nested fetch could hide the parent's explicit INNER request. Request LEFT for the parent or omit its explicit type to retain those books. A later untyped `join` or `select` still replaces the requested fetch type.
- A join type requested for a property that is not an association is ignored. It failed with a `SemanticException`.
- A join inside an association block uses that block's path. A default association block no longer overwrites an explicit join type requested before it; supplying the join before or after the block gives the same result.
- Bulk operations apply requested joins inside subqueries by selecting matching identifiers first and modifying them in batches. Deletes through join tables use the same tenant-filtered selection, retaining owners with only another tenant's elements.
- Static list fetches nested collections with their owners. A single requested bag that would be multiplied by another collection join loads separately before the result is returned, potentially adding a query per root and collection. Two requested bag fetches retain their existing restriction.
- Named-connection calls retain their connection when applying requested joins, including `list`, `findWhere`, `findAllWhere` and paged totals. A list with nested fetch modes or a bag fetch beside a sort keeps its connection too; these calls could read the default connection before.
- A root-level requested LEFT join for a nested path also governs default nested association blocks, including inside a junction, whether supplied before, after or through fetch. A default block also preserves a requested type after an untyped join or select. Explicitly typed blocks retain their restrictions.
- Nested to-one RIGHT joins list and count the same rows, including unmatched null rows. When a to-one RIGHT join also fetches a collection, that collection loads separately before the result is returned to preserve one null per unmatched row. This can add a query per returned instance and collection; the two-bag restriction remains.
- When migrating from Hibernate 5, review collection-query duplicate counts, plain property projections, missing-association projections and RIGHT joins: Hibernate 7 returns each entity once from a criteria query that fetches a collection (a criteria query that only sorts by a collection still repeats it per element, with or without leftJoinAssociationSort) but does not deduplicate plain projected values, can omit missing-association projections, and retains unmatched RIGHT-join rows in static list. Use explicit distinct projections, root/to-one sorts for pagination and INNER joins where those are the intended results.
- To keep the previous result of a query, remove the join type that it requests, or request `JoinType.LEFT`.

## Cache Plugin Beside Another Cache Manager

- **A second `CacheManager` beside the cache plugin's `grailsCacheManager` no longer stops a cached application from starting.** Search for `CacheManager` beans the application or its plugins define, and for `cacheUsers` in the Spring Security plugin's configuration, which defines one named `cacheManager`. Where Spring could not choose between them, as when neither is marked primary or has a `@Priority`, Grails 8.0.0 failed to create `grailsCacheAdminService` and every class with a cached method; the cache annotations now use `grailsCacheManager` there, also for classes compiled against 8.0.0. Two cache managers both marked primary, or sharing the highest `@Priority`, still fail to start, as Spring rejects them before it looks further; mark only one primary, or give one the higher priority. A cache manager whose `@Priority` is only on the object its factory method or `FactoryBean` returns, with the declared type a plain `CacheManager`, is not seen as preferred beside `grailsCacheManager`, which the annotations then use; mark it primary to keep it. Wherever Spring can choose, as for a cache manager marked primary or with a `@Priority`, it chooses as before, and nothing changes with `grails.cache.enabled: false`. The cache tags use `grailsCacheManager` by name, so with an application cache manager marked primary the annotations and the tags cache in different cache managers, as in 8.0.0.
- **A method whose cache the cache manager does not have runs uncached.** A cache manager answers null for a cache name it has no cache of, as a `JCacheCacheManager` does for a cache it was not configured with, and in Grails 8.0.0 every call of such a `@Cacheable`, `@CachePut` or `@CacheEvict` method failed with a `NullPointerException`. Classes compiled against 8.0.1 run uncached there, and a warning names the class and the cache once. Configure the cache in that cache manager to cache with it.

## GORM for Hibernate 5: a sort uses a requested left join

A where query, a detached criteria or a dynamic finder that requests a left join of an association, with `join('owner', JoinType.LEFT)` or `fetch: [owner: JoinType.LEFT]`, and sorts by a property of that association, as with `sort: 'owner.lastName'`, sorted with an inner join and left out the instances without the association. As of 8.0.1, and 7.0.18 on the 7.0.x line, the sort uses the requested left join, as GORM for Hibernate 7 already did, so those instances are returned, and the `totalCount` of a paged list counts them.

- Search for `JoinType.LEFT` in calls to `join` and in `fetch` arguments of queries that also sort by a property of the same association.
- To leave out the instances without the association, as before, request `JoinType.INNER`, or add a criterion such as `owner != null`.

## GORM for Hibernate 5: `list` sorts by every property of a sort map

`list` with a `Map` of sort properties, such as `Book.list(sort: [title: 'asc', 'author.name': 'desc'])`, sorted by the last entry of the map only. As of 8.0.1, and 7.0.18 on the 7.0.x line, it sorts by every entry, in the order of the map, as GORM for Hibernate 7 already did. The same applies to a default sort that the mapping declares for more than one property, as in `sort author: 'asc', title: 'desc'`.

- Search for `sort: [` in calls to `list`, and for a `sort` with more than one property in a `static mapping` block. The results of these calls are now sorted by every property, so the rows selected by `max` and `offset` can change.
- To sort by one property, as before, pass that property alone.
- An association is joined once for all the entries that sort by it. `find` and `findAll` with an example, `findWhere` and `findAllWhere` failed with `duplicate association path` for such a sort and now work.
- Sorting by two properties of the same collection returns fewer duplicate rows. A book with three chapters occurs three times for `Book.list(sort: ['chapters.title': 'asc', 'chapters.id': 'asc'])`, rather than nine times. Update assertions that relied on the repeated joins. No compatibility setting restores them.

## GORM for Hibernate 5: setting for a sort by an association

A sort by a property of an association, such as `Book.list(sort: 'author.name')`, joins the association with an inner join, so it leaves out the instances whose association is `null`, and `count` and the `totalCount` of a paged list do not count them. That is unchanged by default. 8.0.1, and 7.0.18 on the 7.0.x line, add the `grails.gorm.leftJoinAssociationSort` setting, default `false`. Set to `true`, the association is joined with a left join and the sort keeps those instances.

- Nothing needs to change to keep the previous results.
- Search for `sort:` arguments and `.sort(` calls whose property contains a `.`, such as `sort: 'author.name'`. If the application expects every instance from such a query, set `grails.gorm.leftJoinAssociationSort: true` in `application.yml`.
- With the setting, the instances without the association come first or last, depending on how the database sorts nulls. To leave them out of one query, add a criterion that the association is not `null`.
- A sort by a property of a collection, such as `sort: 'books.title'`, joins the collection with an inner join with or without the setting.
- Explicitly setting it to `false` logs a startup warning naming Grails 9, which removes the setting. The planned default in Grails 8.1 is `true`; check the target release's compatibility settings before upgrading.

## GORM for Hibernate 5: a join type requested for an association is always applied

With GORM for Hibernate 5, a where query, a detached criteria, a dynamic finder or a criteria query can request the type of the join of an association, with the `fetch` argument, as in `Book.where { ... }.list(fetch: [author: JoinType.INNER])`, with `join('author', JoinType.INNER)`, or, on a data service method that queries by its parameters, such as `Book find(String title)`, with `@Join(value = 'author', type = JoinType.INNER)`. In a where query, a detached criteria and a dynamic finder, the requested join type was only used when the query also had criteria on the association, and a criteria query never used it. The association was otherwise fetched with an outer join, whatever join type was requested, so the query also returned the instances whose association is `null`. As of Grails 8.0.1, and 7.0.18 on the 7.0.x line, a requested join type other than `JoinType.LEFT` is applied whether or not anything else in the query uses the association.

- Search for `JoinType.` in `fetch:` arguments, in `join(` calls and in `@Join` annotations on data service methods that query by their parameters. Methods named as dynamic finders, such as `findByTitle` or `findAllByTitleLike`, still treat `@Join` as a fetch mode and ignore its join type.
- With `JoinType.INNER`, the instances whose association is `null`, or whose collection is empty, are no longer returned by `list`, `get` and `find`, and no longer counted by `count` or by the `totalCount` of a paged list. The association is still fetched. This also applies to a collection of a basic type, such as `static hasMany = [tags: String]`, for which the requested join type was ignored. To return these instances as before, request `JoinType.LEFT` or `'join'`.
- `JoinType.RIGHT` is applied as well. For a to-one association, `list` returning entities keeps one `null` for each associated instance that no queried instance refers to, unless the criteria exclude that row; `count` and `totalCount` count each. A property projection still returns repeated values, including `null`, once when the query joins, so its result size can differ from the count. For a collection, a right join returns the same instances as an inner join, including when criteria use the collection.
- A query that is used as a subquery applies the join type that it requests as well, so a subquery with `join('author', JoinType.INNER)` no longer selects the instances without an author.
- `updateAll` and `deleteAll` apply the join type that the where query or the detached criteria requests too, so they leave out the instances that the join type leaves out of `list`: `Book.where { title ==~ 'Draft%' }.join('author', JoinType.INNER).deleteAll()` no longer deletes the drafts without an author. To update or delete those as well, as before, do not request a join type for the association, or request `JoinType.LEFT`. A sort is not applied to an update or a delete: while `grails.gorm.leftJoinAssociationSort` is off, a sort by a property of an association leaves the instances whose association is `null` out of `list`, and `updateAll` and `deleteAll` still change them. MySQL, and MariaDB before version 10.3, do not allow an update or a delete to select from the table that it modifies. With a MySQL dialect, or a MariaDB dialect older than `MariaDB103Dialect`, `updateAll` and `deleteAll` therefore select the identifiers of the instances first, and then update or delete the instances with those identifiers, when the join type is requested for a path that leads back to the queried class, such as the `parent` of a category, or that starts with a collection of a basic type.
- A join type requested for a path through more than one association, such as `fetch: ['author.publisher': JoinType.INNER]`, was ignored unless the query had criteria on the path. Every association on the path is now joined and fetched, with the requested join type for the last one, so `JoinType.INNER` leaves out the instances without an author as well as those whose author has no publisher. When the path passes through a collection, such as `books` in `fetch: ['books.publisher': JoinType.INNER]`, the query is restricted to the instances for which the join returns a row, and counts each of them once. The collection and the other associations on the path are fetched as well, with outer joins, so the collection holds all of its elements, also the books without a publisher: a join type decides which instances a query returns, and never leaves elements out of a fetched collection. A where query and a dynamic finder return each author once, and a criteria query returns an author once for each of the books that it fetches. A `JoinType.LEFT` requested for such a path, as in `fetch: ['books.publisher': JoinType.LEFT]`, now fetches the associations on the path as well: it fetched none of them.
- Bulk updates and deletes also honor requested join types inside subqueries, including subqueries in junctions. They select matching root identifiers first, with the join restrictions and tenant filters, then modify those identifiers in batches. Existing unsupported association criteria in bulk operations remain unsupported.
- A query with a property projection applies the requested join type too.
- A nested `JoinType.LEFT` request also keeps intermediate joins used only for sorting outer in where queries, detached criteria and dynamic finders. A sort through `books.publisher` with a LEFT request for that path keeps authors without books and books without a publisher, in both sort-setting modes. Explicit intermediate join types, replacement fetch modes and association criteria retain precedence. Paged collection sorts load complete collections separately.
- A paged query loads collections on a requested join path separately when accessed, so the limited SQL rows cannot initialize an incomplete collection. The join type still restricts the root instances and their count.
- For a collection joined only by that request, pagination now applies to root instances rather than joined collection rows. A page can contain more distinct instances than before, up to `max`. The fetch mode `'join'`, including `join('books')` without a type, still pages joined rows and can load an incomplete collection, as before. Do not treat `JoinType.LEFT` and `'join'` as interchangeable when preserving a page: they keep the same unrestricted root selection but can return different pages.
- Check `select('author')` or `join('author')` after a requested left join, in a criteria query, a where query or a detached criteria: the left join is kept for the criteria, which can now return a book without an author. A fetch mode that replaces an inner join also applies to property projections, which can now include `null`.
- A join type that the `fetch` argument requests is now also used for an association that the where query or the detached criteria itself has criteria or a projection on. Such an association was joined before the argument was read: with an inner join for criteria and with a left join for a projection, whatever the argument requested. `Book.where { author.name == 'Stephen King' || title == 'Anonymous' }.list(fetch: [author: JoinType.LEFT])` now also returns the book without an author that matches the title, as it already did when the join type was requested with `join('author', JoinType.LEFT)`.
- A join type for a nested association is requested by its path. `join('author.publisher', JoinType.LEFT)` is now used for the join that criteria on `author.publisher` make, which was an inner join whatever was requested. A join type requested for an association of the queried class, such as `publisher`, is no longer used for a nested association of the same name.
- In a criteria query the requested join type also applies to a block or alias, whether `join` appears before or after it. A requested LEFT join preserves a missing association when an alternative `or` criterion matches the root, and when a sort uses the association. A paged query loads its collections separately to keep their elements complete.
- The requested type replaces even an explicit type on `createAlias`: `createAlias('author', 'a', CriteriaSpecification.LEFT_JOIN)` followed by `join('author', JoinType.INNER)` now leaves out books without an author, where the alias's left join kept them.
- A paged criteria query with `join('books', JoinType.LEFT)` now pages root instances when the collection is joined only for fetching, so a page that previously repeated one author can now contain two authors. Plain `join('books')` and `fetch: [books: 'join']` still page joined rows and can repeat a root on the page, as before.
- An unpaged criteria query whose `fetch` argument now fetches a collection can return an instance once per element, where the join type previously fetched nothing and did not repeat rows. Check assertions that compare those rows with a root-instance `totalCount`.
- To keep the previous result of a query, remove the join type that it requests, or request `JoinType.LEFT`.
- Check queries that store a sort with `sort()` before passing a `fetch` argument to `list` or `find`. `Book.where { ... }.sort('author.name').list(fetch: [author: JoinType.LEFT])` now also returns books without an author. Request `JoinType.INNER` to keep leaving them out; nested paths follow the same rule.

# After the Upgrade

## Verification Checklist

Run verification through public application behavior, not only compilation.

- `./gradlew clean check` for the application, or the closest module-specific equivalent when the full suite is too expensive.
- Start the application with the target JDK and no Spring Boot properties migrator warnings.
- Exercise login, main pages, JSON endpoints, browser `fetch()` flows, file uploads, custom `withFormat` or `respond` actions, TagLib-rendered views, and persistence paths.
- Run database migration diffs before enabling Hibernate 7 schema update behavior in any environment with existing data.
- Re-run affected integration tests after changing Spring Boot test annotations, mocked beans, content negotiation, or persistence behavior.
- Remove temporary migration toggles once equivalent permanent changes are in place.
- Review every security rule keyed on `PUT`, `PATCH` or `DELETE`, and every rule that grants `POST` on a `resources` path. Submit a `<g:form method="DELETE">` and a plain `POST` to a member URL as an unauthorized user and confirm both are refused.
- Send a request with `X-HTTP-Method-Override` from each non-browser client that used it, and confirm the action that answers. Set `grails.web.hiddenmethod.filter.enabled: true` where a client depends on the header.
- Search controllers and filters for `request.method` compared with `PUT`, `PATCH` or `DELETE`, and exercise those paths with a browser form submit.
- Request a URL with `?action=`, `?controller=` or `?namespace=` wherever the application used a parameter to pick the route, and confirm the action that answers.
- Request `/`, a static file the application served from `static/` or `public/`, and an unmapped URL, and confirm the result with `spring.web.resources.add-mappings` at its new default of `false`.
- Search configuration for `spring.mvc.*` and `spring.web.*` properties, which now take effect, and for the deprecated `spring.gsp.removeDefaultViewResolverBean`.
- Search for `Tenants.withId(` calls that pass a datastore class and for `CurrentTenantHolder.get()` with no argument; on a multi-datastore application, verify the tenant is bound on the intended datastore.
- If `grails.spring.bean.packages` is set, start the packaged application from a directory other than the project root and confirm it starts with no duplicate bean names or ambiguous injection points.
- For a CAS application, decide whether single sign-out is wanted and set `grails.plugin.springsecurity.cas.useSingleSignout` explicitly; verify login and logout.
- For a Quartz application, check the startup log for triggers reported as never firing, give each application on a shared job store its own `info.app.name`, and remove rows of job classes deleted before the upgrade.
- For an application that customized `spring-security-ui`, open every overridden view and the pages styled with `s2ui_*` or `jd_menu` classes.
- Check `@EnableAutoConfiguration(exclude = ...)` and `spring.autoconfigure.exclude` entries against the renamed Grails auto-configuration classes, and confirm the excluded beans are absent.
- Search build files for `gspCompile` and configuration for `grails.gsp.reload.granularity`.
- For a schema created by 8.0.0-RC1 or 8.0.0-RC2 with table-per-hierarchy domain classes, start with `dbCreate: 'validate'` and load subclass instances.
- For a plugin with a companion `-cli` artifact, run `testCli` and `integrationTestCli` after moving the CLI specs.
- Build from a clean checkout to confirm that no build script sets `importJavaTime`, assigns `groovyOptions.configurationScript` in a `doFirst`, or refers to removed scaffolding task properties, and that the application's own scaffolding templates compile.
- Start the application and check the log for `is not public` warnings from `org.apache.grails.common.reflect.ReflectionUtils`; declare each reported class as a named public class or register an `ObjectMarshaller`.
- Compare JSON and XML responses that contain `java.sql.Time`, `java.time` values, time zones or date-typed map keys with the Grails 7 output, and confirm that every client reads the new form.
- Submit a `java.time.Month` as a number through each endpoint that binds one and confirm the bound month.
- Send an XML request body with a `DOCTYPE` declaration to each XML endpoint that outside clients call, and confirm those clients do not send one.
- Inspect the response headers of a page, an API response and an error response for `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` and `X-XSS-Protection`, behind the production reverse proxy too, and confirm that no header is duplicated and that pages meant to be framed by other sites still are.
- Request every `render(file: ...)` action in a browser and confirm that files meant to be displayed inline are still displayed.
- Exercise interceptors that match on `uri` with plain, percent-encoded and matrix-parameter paths, and under the deployed context path.
- With promises or async controllers, run a request that takes longer than the async request timeout and confirm the response, and exercise blocking tasks under load against the configured `applicationTaskExecutor`.
- Compile and test statically compiled code and Java code that uses `count()`, and regenerate GraphQL clients and schema snapshots that include a count field.
- With several datasources, verify for every block opened on a named connection which database each read and write inside it reaches.
- With multi-tenancy, verify `lock(id)` with an id that belongs to another tenant.
- Exercise every `mutex` call inside a transaction with an attached instance, and confirm that changes are applied inside the closure.
- On Hibernate 7 with PostgreSQL or Oracle, start against a copy of the production schema and insert a row for a domain class with the default id mapping and for one with `id generator: 'sequence'`, before any environment runs with `dbCreate: update`.
- On Hibernate 7, check the `version` of new entities with pre-insert identifiers and run every custom persistence event listener through a save and a merge.
- On Java 25, start the application with the deployed JVM flags and confirm that `-XX:MaxMetaspaceSize` is set and that no deprecation warning for `UseCompressedClassPointers` is printed.

## Common Fix Patterns

```groovy
// Required domain property after Grails 8 nullable-default change
class Book {
    String title

    static constraints = {
        title nullable: false
    }
}
```

```yaml
# Restore legacy required-by-default domain validation temporarily
grails:
    gorm:
        default:
            nullable: false
```

```yaml
# Add a custom MIME type while keeping Grails 8 defaults
grails:
    mime:
        mergeDefaults: true
        types:
            custom: application/vnd.example+json
```

```groovy
// Spring Boot 4 test bean override
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class BookServiceSpec extends Specification {
    @MockitoBean BookRepository bookRepository
}
```

## Pitfalls to Avoid

- Do not upgrade to Hibernate 7 just because the application is upgrading to Grails 8.
- Do not leave the Spring Boot properties migrator or Jackson 2 compatibility toggles in the final build without a clear follow-up task.
- Do not rely on compilation alone. Grails 8 changes default validation and content negotiation behavior that may only show up through real requests.
- Do not keep an old full `grails.mime.types` block if the intent is to extend the new defaults. Use `grails.mime.mergeDefaults`.
- Do not keep `purgeTagLibMetaClass` in tests. It is removed.
- Do not use `javax.*` imports. Grails 8 continues the Jakarta baseline.
- Do not override a managed version under a Grails-specific property name. Use the upstream (Spring Boot) property name; an override no BOM declares changes nothing and reports nothing.
- Do not ignore plugin compatibility. Spring Boot 4 and Spring Framework 7 removals often surface first through plugins.
- Do not assume a security rule on `PUT`, `PATCH` or `DELETE` still protects a browser form. Spring Security now sees the `POST` the form sent.
- Do not register a `HiddenHttpMethodFilter` bean without setting `grails.web.hiddenmethod.filter.enabled` or `spring.mvc.hiddenmethod.filter.enabled`. The bean alone does not select filter mode.
- Do not leave a `Tenants.withId(SomeDatastore, ...)` call because it compiles. On a multi-datastore application it silently binds the tenant on the `DEFAULT` datastore.
- Do not enable `grails.plugin.springsecurity.cas.useSingleSignout` to match the old default. It disables session fixation prevention and did not work before Grails 8.
- Do not leave an auto-configuration exclude under an old class name. Spring Boot ignores it without a message and the beans register anyway.
- Do not set `spring.web.resources.add-mappings: true` or `grails.web.removeWelcomePageMapping: false` unless the application needs Boot's static resources or welcome page; they can shadow URL mappings.
- Do not rely on a request parameter named `action`, `controller` or `namespace` to choose the route. Use the URI or an explicit closure in the mapping.
- Do not use `touch` to force a GSP to recompile. Only a content change reloads a page.
- Do not start an application on Hibernate 7 with `dbCreate: update` against an existing PostgreSQL or Oracle schema before deciding how unnamed sequences are handled. Hibernate creates new `_SEQ` sequences that start at 1.
- Do not assume a call on a domain class inside `Book.secondary.withTransaction { }` still reaches the default database. It reaches `secondary` now, without an error.
- Do not treat a successful compile as proof that `count()` callers are correct. Dynamic code keeps working, while statically compiled code, Java callers, typed view models and GraphQL schemas need a change.
- Do not look for a setting that accepts XML request bodies with a `DOCTYPE`. There is none; the client has to omit the declaration.
- Do not add the default security headers at a reverse proxy and in the application at the same time. Set `grails.security.headers.defaults: auto` or strip one copy.
- Do not leave `render(file: ...)` calls unreviewed. Pass `inline: true` where the browser should display the file.
- Do not rely on tests alone for date and time rendering. Compare real responses, because map keys, `java.sql.Time` and `java.time` types change form without a failure.
- Do not send or accept `Month` numbers by ordinal. `9` now binds to `SEPTEMBER`.
- Do not set a property on an entity and then call `mutex` to save it. The reload discards the unflushed change.
- Do not silence the non-public class warning as the only fix. The compatibility handling may be withdrawn in a future major release.
- Do not cast the `PromiseFactory` to `ExecutorService` or `ExecutorPromiseFactory`.
- Do not read a configuration list of objects with `Environment.getProperty` or a `${...}` placeholder. Use `grailsApplication.config` or `@ConfigurationProperties`.
