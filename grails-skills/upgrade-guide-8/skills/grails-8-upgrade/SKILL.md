---
name: grails-8-upgrade
description: Guide for upgrading Grails applications from Grails 7.x to Grails 8, covering Java 21, Groovy 5 name resolution and static compilation, Spring Boot 4.1, Spring Framework 7, dependency management, Micronaut, Jackson 3, Hibernate 7, TagLibs, testing, content negotiation, asset pipeline wildcard paths, and validation behavior changes
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide upgrades of Grails applications from Grails 7.x to Grails 8.
- Turn the Grails 8 upgrade guide into a practical migration checklist.
- Identify Groovy 5, Spring Boot 4.1, Spring Framework 7, Jackson 3, Gradle platform, Micronaut, Hibernate, TagLib, testing, validation, and content negotiation changes that can break an application.
- Keep migration work focused on public application behavior, not internal Grails framework implementation details.

## When to Use Me

Activate this skill when:

- Updating an application to Grails 8.
- Reviewing a Grails 8 upgrade branch or pull request.
- Fixing tests, build failures, runtime failures, or behavior changes after moving from Grails 7.x to Grails 8.
- Deciding whether an upgrade should remain on Hibernate 5 or opt in to Hibernate 7.
- Updating application build files, configuration, custom Spring Boot integration, TagLibs, JSON rendering, or content negotiation behavior for Grails 8.

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

## Upgrade Baseline

Start every Grails 8 upgrade by checking these platform requirements:

- Use JDK 21 or later to build and run ordinary Grails 8 applications.
- Use JDK 25 or later if the application uses `grails-micronaut`, `micronaut-http-client`, or other Micronaut features.
- Update the Gradle wrapper to the Grails 8 managed line, currently Gradle 9.8.0. The Grails 8 Gradle plugins require Gradle 9.7 or later.
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
- The Grails Gradle Plugin auto-applies the selected Grails BOM to declarable configurations.
- Existing version overrides in `gradle.properties` and `ext['property.version']` still work through the bundled `org.apache.grails.gradle.bom-property-overrides` plugin.
- Grails BOMs now follow the upstream BOM property names: an override of a version Spring Boot also manages uses Spring Boot's property name, and for a module Spring Boot manages through an imported BOM, the property Spring Boot imports that BOM with. Rename Grails-specific overrides, because a property no BOM declares is silently ignored:

| Grails 7 or earlier Grails 8 property | Grails 8 property |
|---------------------------------------|-------------------|
| `jackson.version`, `jackson2.version` | `jackson-2-bom.version` (Jackson 2, `com.fasterxml.jackson.*`) |
| `jackson3.version` | `jackson-bom.version` (Jackson 3, `tools.jackson.*`, the default) |
| `jackson-bom.version` set to a 2.x version (Spring Boot 3's name for Jackson 2) | `jackson-2-bom.version`; in Grails 8 `jackson-bom.version` sets Jackson 3, so a 2.x value makes dependency resolution fail |
| `neo4j-driver.version` (`grails-neo4j-bom`) | `neo4j-java-driver.version` |

- Replace `grails { springDependencyManagement = false }` with `grails { bom = null }` for new builds that intentionally opt out.
- If Spring DM was used to pin arbitrary dependencies, replace it with direct dependencies or Gradle `resolutionStrategy.force` where a transitive version must be forced.
- If strict BOM behavior is required, explicitly use `enforcedPlatform("org.apache.grails:grails-bom:$grailsVersion")`.
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

For WAR deployment to an external servlet container, replace `providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat'` with `providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat-runtime'`.

Spring Boot 4 dropped `spring-boot-starter-undertow`; Grails 8 provides Undertow through the Grails Undertow plugin instead. For an application that runs on Undertow, make the build match what Grails Forge generates for `--servlet=undertow`:

- Replace `spring-boot-starter-undertow` with `implementation 'org.apache.grails:grails-undertow'`, without a version; the Grails BOM manages it.
- Remove direct `io.undertow:undertow-servlet` and `io.undertow:undertow-websockets-jsr` dependencies from the old stack. Servlet 6.1 support uses Undertow core 2.4.x with servlet/websocket artifacts in `io.undertow.ee`; let the Grails Undertow plugin and BOM supply the compatible set.
- Do not declare `spring-boot-starter-tomcat` or `spring-boot-tomcat`; those are what Forge adds for Tomcat applications instead.
- Keep the `server.undertow.*` properties; they continue to work. `server.undertow.max-http-post-size` now defaults to 2MB, Undertow's hardened default; set it to `-1` only if the application relied on the previous unlimited request size.

Spring Retry is no longer managed by Spring Boot. If the application directly uses its `RetryTemplate`, `@Retryable`, `@EnableRetry`, or `@Recover`, declare `org.springframework.retry:spring-retry` directly. The Grails 8 BOM manages its version; otherwise supply an explicit version or migrate to Spring Framework's retry API. A transitive dependency is not a declaration of the application's own use.

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

Other common Spring changes:

- Rename `HttpStatus.MOVED_TEMPORARILY` to `HttpStatus.FOUND`.
- Compare a servlet response's integer status with `HttpStatus.UNPROCESSABLE_ENTITY.value()` rather than the `HttpStatus` object. Keep numeric wire assertions separate from typed client-response status assertions.
- Remove `HandlerAdapter.getLastModified` overrides.
- Replace use of removed Spring theme infrastructure such as `ThemeSource`, `Theme`, `SimpleTheme`, and `SessionThemeResolver`.
- Replace `SecurityProperties.DEFAULT_FILTER_ORDER` with `-100` only when a direct constant replacement is needed, and prefer fluent `HttpSecurity` configuration long term.
- Update embedded server imports such as `TomcatServletWebServerFactory` to the new Spring Boot 4 packages.
- Add `spring-boot-tomcat` explicitly if application code directly references Tomcat classes.
- Prefer JSpecify nullability annotations for new Spring-facing code.
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
- Qualify domain constants and reusable validator closures inside `static constraints`, for example `matches: Book.CODE_PATTERN` or `validator: Book.codeValidator`. Under Groovy 5, unqualified names can resolve against the mapping DSL delegate instead of the declaring class and fail during datastore initialization.
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
- Jackson configuration under `spring.jackson.*` was reorganized. Watch for migrator output such as `spring.jackson.read.*` moving to `spring.jackson.json.read.*` and `spring.jackson.write.*` moving to `spring.jackson.json.write.*`.
- DevTools live reload is disabled by default. Set `spring.devtools.livereload.enabled: true` under the development environment if needed.
- Spring Boot now writes build info to `META-INF/build-info.properties` by default.
- Liveness and readiness probes are enabled by default on the health endpoint. Disable through `management.endpoint.health.probes.enabled: false` only if required.
- If `grails.mail.overrideAddress` is set, it now also replaces a `from` set in `sendMail`. To keep the application's sender while still redirecting every recipient, use `grails.mail.overrideToAddress` in its place.
- Replace every `grails.controllers.upload.*` property with its `spring.servlet.multipart.*` equivalent. Grails 8 rejects the old namespace at startup. Set explicit per-file and per-request limits to preserve existing uploads, and check the servlet container's overall request limit as well. An unspecified multipart location now uses the container's temporary directory.
- Plugin message bundles must use the plugin's namespace: a plugin named `common` ships `common.properties` or `common-<purpose>.properties`, with matching locale variants. Rename generic plugin bundles such as `messages.properties`; `generateI18nDescriptor` rejects colliding names. Application bundles still use the application message-source conventions.
- Boot now owns the `ResourceBundleMessageSource`; application calls to old Grails-specific methods such as `getMergedPluginProperties` fail. For a known code catalog, enumerate its namespaced `ResourceBundle` keys and resolve the text through the public `MessageSource` API so application overrides remain effective. Test catalog/search and validation-error paths as well as individual message lookups.
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
- To keep Jackson 2 defaults temporarily, set `spring.jackson.use-jackson2-defaults: true`.

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

- `SimpleEnumMarshaller` is the default for JSON and XML enum serialization.
- Remove explicit configuration that opted into simple enum formatting.
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

- Removed annotations such as `@Where`, `@WhereJoinTable`, `@Proxy`, `@LazyCollection`, `@Persister`, `@SelectBeforeUpdate`, and `@Loader`.
- Removed `CascadeType.SAVE_UPDATE`; use `CascadeType.ALL`, `CascadeType.PERSIST`, or `CascadeType.MERGE` as appropriate.
- Detached `refresh()` or `lock()` calls, which now throw `IllegalArgumentException`.
- Native SQL query results now returning `java.time` date/time types instead of `java.sql` date/time types.
- `StatelessSession` participating in second-level cache by default. Set `CacheMode.IGNORE` for intentional cache bypass.
- DDL differences for character columns, Oracle floating and timestamp columns, SQL Server timestamp precision, and MySQL or MariaDB array columns.
- Hibernate 6 intermediate changes: HQL join result typing, 1-based ordinal parameters, legacy Criteria API removal, boolean type mapping converters, and continued `jakarta.persistence` usage.

GORM behavior changes:

- Unconstrained persistent domain properties are nullable by default in Grails 8.
- Declare `nullable: false` on required domain properties, or set `grails.gorm.default.nullable: false` to restore the previous application-wide default.
- Command object fields are unaffected and remain required by default.
- Check command objects that import domain constraints too: making an existing domain field explicitly required can also restore the intended requiredness of an imported constraint. Verify both domain and command validation rather than changing the application's global nullability setting to satisfy one fixture.
- GORM dynamic methods such as `save()`, `delete()`, `get()`, `load()`, and `merge()` are not affected by Hibernate `Session` API removals.
- Migrate affected legacy `namedQueries` definitions and `createNamedQuery` calls to supported `where` / `DetachedCriteria` queries. Grails 8 [removed deprecated query infrastructure](https://github.com/apache/grails-core/commit/2110c45f367b9955da734afcc5dfd56d133941f7); an old generated method name is not proof the underlying API remains supported. Retain every predicate and verify excluded, unrelated, and absent rows.
- The `sort` and `order` arguments of `list()`, dynamic finders, where queries, criteria queries and `listOrderBy*` are validated on both Hibernate versions: `sort` must be a dotted property path that resolves through the mapping (a dotted alias such as `c.name` is still passed through), and `order` must be `asc` or `desc`. Anything else throws `IllegalArgumentException` (`Invalid sort property` / `Invalid sort direction`) instead of being ignored or failing inside Hibernate. On Hibernate 7, `list()` also rejects a `fetch` key that is not a persistent property.
- A GString coerced to `String` before `executeQuery` or another checked GORM query method can fail compilation with `GormUnsafeQueryString`. Bind data values as parameters. When interpolation is solely for reviewed, fixed query fragments and values already use named parameters, document that distinction and suppress the check on that specific method rather than converting query syntax into bound values.
- Check single-result criteria explicitly. In the tested pre-release Grails 8 build, `withCriteria(uniqueResult: true)` returned a `PagedResultList`; Grails 7 dispatched it to `get`. Use `createCriteria().get { ... }` for entity/scalar results, preserving `cache` and valid fetch joins in the DSL. Test both absent and present results, especially row-count projections where a nonempty list containing zero is truthy. Treat this as a version-specific regression, not a universal documented removal.
- Nested transaction propagation was corrected in [Grails commit bd268672976](https://github.com/apache/grails-core/commit/bd268672976fff60853021dd659cd6d55482e631): `REQUIRED` joins an active transaction, while `REQUIRES_NEW` suspends it and uses a separate session, then restores the outer session. This also affects SimpleMap-backed domain/service unit tests. A thread-bound test session does not mean every service invocation shares one transaction or first-level cache; check the service annotations and explicit `withNewTransaction` calls before attributing changed fixture visibility to a regression.
- Verify `ident()` and reload/proxy behavior for composite keys and renamed single-property identifiers. In the checked pre-release build, `ident()` read only the ordinary `id` property, returning null for a persisted composite-key domain and for a key mapped with `id name: 'code'`; Grails 7 used the mapped property or constructed a serializable identifier from the composite key fields. Consequently `load(entity.ident())` returned null before any proxy was created. Regression tests reproduced both cases on Hibernate 5 and Hibernate 7. Treat this as a framework regression to isolate and fix, rather than excluding composite-key domains from coverage or interpreting the null as a proxy-initialization change.
- Initialization does not change a Hibernate base-class proxy into its concrete subtype. Before subclass/trait-only access, unwrap through the supported Hibernate API and retain the resulting instance; a cast alone is not unwrapping. A collection `find` still returns the original element even if its predicate unwrapped it; use `findResult` when the intended result is the unwrapped matching object. Equivalent warmed-call-site failures were reproduced on the old stack too, so distinguish a pre-existing proxy assumption from a new compiler regression.

### Validation Error Reset and Snapshot Regressions

- For Hibernate-backed domains, compare explicit `validate()`, validation on `save()`, and dirty-instance flush separately. The verified Grails 7 behavior starts validation with fresh errors while retaining data-binding failures; manually added global `reject(...)` errors and ordinary `rejectValue(...)` errors are reset. Constraints and `beforeValidate` can then add current errors. `deepValidate: false` changes association validation, not that reset contract. Consult the target version's [validate reference](https://grails.apache.org/docs/snapshot/ref/Domain%20Classes/validate.html), checking its version as above.
- A checked Grails 8 pre-release retained global errors and consequently rejected saves/flushes that passed on Grails 7. Both Hibernate implementations needed the compatibility correction. Do not turn global errors into artificial field errors, remove constraints, or weaken assertions merely to make that snapshot pass. Verify the target framework contains the fix. If an application rule must survive repeated validation, check it at the appropriate operation boundary or reproduce it through the intended validation rule; do not assume the mutable errors object is durable state.

## Web Layer and Content Negotiation

### Binding Include Lists and Nested Association IDs

- Preserve the distinction between an omitted/null include list and `include: []`: the latter binds nothing. Passing an empty list to a lower-level binding API is not a spelling of the default behavior. Keep deliberate include/exclude restrictions, `bindable: false`, and deny-by-default configuration intact; consult the target version's [bindData reference](https://grails.apache.org/docs/snapshot/ref/Controllers/bindData.html).
- With explicit includes, include the actual nested path required by the operation, such as `lineItem.product.id`. Put a lookup code on its associated entity's ID field, not the containing object's unrelated numeric ID. Test the real parameter structure, including any values inserted by the controller, and retain a negative test for protected properties.
- In compatibility mode, dynamically constructing a command subclass should not restrict its binding to an enhanced superclass's fields. A checked pre-release did so when only the superclass was declared as an action parameter; [the framework correction](https://github.com/apache/grails-core/pull/16491) restores subclass binding while preserving explicit restrictions and `bindable: false`. Reproduce through `bindData` / the public binding API on both versions before adding application-wide allowlists to compensate. Do not infer the same defaults for secure mode.

### Content Negotiation and Rendered Output

Grails 8 supplies MIME type defaults from the framework.

- New applications no longer need to declare the full `grails.mime.types` block.
- An existing `grails.mime.types` block still replaces the defaults.
- Set `grails.mime.mergeDefaults: true` when adding custom MIME types while keeping built-in defaults.

The HTTP `Accept` header is honored for all clients by default, including browsers.

- Browser page loads still usually negotiate HTML because modern browsers rank `text/html` highest.
- Browser `fetch()` or `XMLHttpRequest` calls requesting JSON now receive JSON without relying on `X-Requested-With`.
- `respond` actions without an HTML view may now error for browser requests because browsers negotiate HTML. Add a GSP view, use `render`, or scope formats with `responseFormats` or `respond(..., formats: ...)`.
- To restore the old browser-ignore behavior, set `grails.mime.disable.accept.header.userAgents` explicitly.

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
- A `Closure` parameter named `body` receives the tag body.
- In Grails 8, `grails { preserveParameterNames = true }` is the default for Groovy compilation.
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
- Replace removed `@MockBean` and `@SpyBean` with `@MockitoBean` and `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito`.
- Remove explicit `MockitoTestExecutionListener` registration.
- Update `TestRestTemplate` imports to `org.springframework.boot.resttestclient`.
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
- A plugin referencing the removed `grails.gorm.annotation.AutoTimestamp` can fail even if the application does not use that annotation. Upgrade the plugin or fix its optional annotation lookup; adding application mocks does not fix the class-linkage error. Plugins doing configuration work in `doWithSpring` must use the supplied `grailsApplication` and avoid prematurely instantiating Spring Security services during bean registration.
- Check the Grails issue tracker and each plugin release notes before upgrading plugin-dependent applications.

### Custom CSRF / Synchronizer-Token Holders

- Audit subclasses of `SynchronizerTokensHolder`: `withForm` now validates and consumes tokens through `isValidAndResetToken(url, token)`. Overriding only `resetToken` no longer preserves an application's reusable AJAX/session token. The characteristic failure is that the first AJAX POST succeeds, then later requests fail CSRF validation because the token URL has disappeared from the holder.
- Override the atomic method for the application's designated reusable-token URL, validating the submitted token against the token belonging to the current session without consuming it. Delegate ordinary form URLs to `super.isValidAndResetToken` so duplicate submissions are still rejected atomically; do not bypass CSRF validation for AJAX requests.
- Preserve the holder's lifecycle as well as validation. A reusable token kept separately from the bounded per-form token map must survive that map becoming empty or evicting older URLs; adapt `isValid` and `isEmpty` consistently when those methods are used. Include recovery of a persisted holder whose map entry was already consumed by the old implementation.
- Verify successive and concurrent requests using the same session token, rejection of missing/malformed/foreign tokens, single-use form tokens, and session serialization. A test of only the first successful POST misses this regression. For behavior-only changes to a serialized holder, preserve a verified compatible serialization UID; invalidate genuinely incompatible pre-upgrade sessions rather than assuming an old UID makes their serialized state compatible.

## Verification Checklist

Run verification through public application behavior, not only compilation.

- `./gradlew clean check` for the application, or the closest module-specific equivalent when the full suite is too expensive.
- Start the application with the target JDK and no Spring Boot properties migrator warnings.
- Exercise login, main pages, JSON endpoints, browser `fetch()` flows, file uploads, custom `withFormat` or `respond` actions, TagLib-rendered views, and persistence paths.
- Run database migration diffs before enabling Hibernate 7 schema update behavior in any environment with existing data.
- Re-run affected integration tests after changing Spring Boot test annotations, mocked beans, content negotiation, or persistence behavior.
- Remove temporary migration toggles once equivalent permanent changes are in place.

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
