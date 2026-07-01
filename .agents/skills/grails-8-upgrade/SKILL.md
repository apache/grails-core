---
name: grails-8-upgrade
description: Guide for upgrading Grails applications from Grails 7.x to Grails 8, covering Java 21, Spring Boot 4.1, Spring Framework 7, dependency management, Micronaut, Jackson 3, Hibernate 7, TagLibs, testing, content negotiation, and validation behavior changes
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide upgrades of Grails applications from Grails 7.x to Grails 8.
- Turn the Grails 8 upgrade guide into a practical migration checklist.
- Identify Spring Boot 4.1, Spring Framework 7, Jackson 3, Gradle platform, Micronaut, Hibernate, TagLib, testing, validation, and content negotiation changes that can break an application.
- Keep migration work focused on public application behavior, not internal Grails framework implementation details.

## When to Use Me

Activate this skill when:

- Updating an application to Grails 8.
- Reviewing a Grails 8 upgrade branch or pull request.
- Fixing tests, build failures, runtime failures, or behavior changes after moving from Grails 7.x to Grails 8.
- Deciding whether an upgrade should remain on Hibernate 5 or opt in to Hibernate 7.
- Updating application build files, configuration, custom Spring Boot integration, TagLibs, JSON rendering, or content negotiation behavior for Grails 8.

## Primary Sources

Use these repository docs as the source of truth before changing an application:

| Path | Use for |
|------|---------|
| `grails-doc/src/en/guide/upgrading/upgrading80x.adoc` | Main Grails 7 to Grails 8 upgrade guide |
| `grails-doc/src/en/guide/introduction/whatsNew.adoc` | Grails 8 feature and platform overview |
| `grails-doc/src/en/guide/introduction/whatsNew/dependencyUpgrades.adoc` | Current platform dependency baseline |
| `grails-doc/src/en/ref/Dependency Versions.adoc` | BOM variants and dependency-version entry points |
| `grails-doc/src/en/guide/conf/micronaut.adoc` | Micronaut BOM usage, Hibernate-specific Micronaut BOMs, and JDK 25 requirements |
| `grails-doc/src/en/guide/theWebLayer/contentNegotiation.adoc` | MIME defaults and Accept header behavior |
| `grails-data-hibernate7/docs/src/docs/asciidoc/introduction/upgradeNotes.adoc` | Hibernate 7 GORM query and tenant-schema notes |
| `grails-data-hibernate7/dbmigration/README.md` | Database migration plugin version line for Grails 8 and Hibernate 7 |

When checking published docs, use explicit Grails 8 URLs such as `https://grails.apache.org/docs/8.0.0-M1/guide/upgrading.html` or `https://grails.apache.org/docs/snapshot/guide/upgrading.html`. Do not assume `https://grails.apache.org/docs/latest/guide/upgrading.html` points at Grails 8.

## Upgrade Baseline

Start every Grails 8 upgrade by checking these platform requirements:

- Use JDK 21 or later to build and run ordinary Grails 8 applications.
- Use JDK 25 or later if the application uses `grails-micronaut`, `micronaut-http-client`, or other Micronaut features.
- Update the Gradle wrapper to the Grails 8 managed line. Current 8.0.x snapshot docs use Gradle 9.6.0, while milestone docs may show an earlier Gradle 9.x version.
- Expect Spring Boot 4.1.x, Spring Framework 7.0.x, Spring Security 7.1.x, Spring Data 2026.0.x, Micrometer 1.17.x, Jackson 3.1.x, Tomcat 11.0.x, and Jakarta Servlet 6.1.
- Keep using `jakarta.*` APIs. Do not reintroduce `javax.*` packages.
- Add `runtimeOnly 'org.springframework.boot:spring-boot-properties-migrator'` temporarily during the migration, boot once, fix reported configuration properties, then remove it.

## Migration Workflow

Use this order so failures are isolated and reversible:

1. Move the build, CI, and deployment runtime to the required JDK.
2. Update the Grails version and regenerate or compare a fresh Grails 8 application for build-file conventions.
3. Boot with the Spring Boot properties migrator and fix configuration warnings.
4. Fix dependency management and starter changes before code changes.
5. Compile and fix direct Spring Boot, Spring Framework, Jackson, Hibernate, and testing API removals.
6. Run application tests and manually exercise public behavior that depends on rendering, validation, content negotiation, persistence, and custom Spring integration.
7. Remove temporary migration dependencies and compatibility toggles once the application is clean.

## Build and Dependency Management

Grails 8 no longer applies the `io.spring.dependency-management` plugin by default.

- Grails now uses Gradle native `platform()` dependency management with the Grails BOM.
- The Grails Gradle Plugin auto-applies the selected Grails BOM to declarable configurations.
- Existing version overrides in `gradle.properties` and `ext['property.version']` still work through the bundled `org.apache.grails.gradle.bom-property-overrides` plugin.
- Replace `grails { springDependencyManagement = false }` with `grails { bom = null }` for new builds that intentionally opt out.
- If Spring DM was used to pin arbitrary dependencies, replace it with direct dependencies or Gradle `resolutionStrategy.force` where a transitive version must be forced.
- If strict BOM behavior is required, explicitly use `enforcedPlatform("org.apache.grails:grails-bom:$grailsVersion")`.

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

Undertow is not available in Grails 8 until Undertow supports Servlet 6.1. Use Tomcat or Jetty.

Spring Retry is no longer managed by Spring Boot. If the application directly uses `@Retryable`, `@EnableRetry`, or `@Recover`, add `implementation 'org.springframework.retry:spring-retry'`.

## Spring Boot 4 and Spring Framework 7 Code Changes

Check for direct imports or references to Spring Boot auto-configuration classes. Spring Boot 4 split the old monolithic auto-configure module into domain modules, and many classes moved.

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
- Remove `HandlerAdapter.getLastModified` overrides.
- Replace use of removed Spring theme infrastructure such as `ThemeSource`, `Theme`, `SimpleTheme`, and `SessionThemeResolver`.
- Replace `SecurityProperties.DEFAULT_FILTER_ORDER` with `-100` only when a direct constant replacement is needed, and prefer fluent `HttpSecurity` configuration long term.
- Update embedded server imports such as `TomcatServletWebServerFactory` to the new Spring Boot 4 packages.
- Add `spring-boot-tomcat` explicitly if application code directly references Tomcat classes.
- Prefer JSpecify nullability annotations for new Spring-facing code.

## Configuration Changes

Review application configuration after booting with the Spring Boot properties migrator.

- Rename Spring Boot MongoDB auto-configuration properties from `spring.data.mongodb.*` to `spring.mongodb.*`.
- Do not change Grails GORM MongoDB `mongodb.*` properties for this Spring Boot rename.
- Jackson configuration under `spring.jackson.*` was reorganized. Watch for migrator output such as `spring.jackson.read.*` moving to `spring.jackson.json.read.*` and `spring.jackson.write.*` moving to `spring.jackson.json.write.*`.
- DevTools live reload is disabled by default. Set `spring.devtools.livereload.enabled: true` under the development environment if needed.
- Spring Boot now writes build info to `META-INF/build-info.properties` by default.
- Liveness and readiness probes are enabled by default on the health endpoint. Disable through `management.endpoint.health.probes.enabled: false` only if required.

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
- GORM dynamic methods such as `save()`, `delete()`, `get()`, `load()`, and `merge()` are not affected by Hibernate `Session` API removals.

## Web Layer and Content Negotiation

Grails 8 supplies MIME type defaults from the framework.

- New applications no longer need to declare the full `grails.mime.types` block.
- An existing `grails.mime.types` block still replaces the defaults.
- Set `grails.mime.mergeDefaults: true` when adding custom MIME types while keeping built-in defaults.

The HTTP `Accept` header is honored for all clients by default, including browsers.

- Browser page loads still usually negotiate HTML because modern browsers rank `text/html` highest.
- Browser `fetch()` or `XMLHttpRequest` calls requesting JSON now receive JSON without relying on `X-Requested-With`.
- `respond` actions without an HTML view may now error for browser requests because browsers negotiate HTML. Add a GSP view, use `render`, or scope formats with `responseFormats` or `respond(..., formats: ...)`.
- To restore the old browser-ignore behavior, set `grails.mime.disable.accept.header.userAgents` explicitly.

## TagLibs and Tests

Grails 8 recommends method-based TagLib handlers while keeping closure-based tags supported.

- Conventional method signatures `def tag(Map attrs)`, `def tag(Closure body)`, and `def tag(Map attrs, Closure body)` are discovered automatically.
- Zero-argument method tags and method tags with named parameters must use `@grails.gsp.Tag`.
- Annotate public helper methods with `@grails.gsp.NotATag` when they must not be exposed as tags.
- A `Map` parameter named `attrs` receives the full attributes map.
- A `Closure` parameter named `body` receives the tag body.
- In Grails 8, `grails { preserveParameterNames = true }` is the default for Groovy compilation.

Test changes:

- Remove custom `purgeTagLibMetaClass` properties or getters from TagLib specs.
- TagLib metadata cleanup happens automatically through the web test infrastructure.
- Mocked tag libraries are cleared after each feature method. If a spec implements `GrailsWebUnitTest` and calls `mockTagLib` directly, move the call from `setupSpec()` to `setup()`.
- `@SpringBootTest` no longer auto-configures `MockMvc`, `WebClient`, or `TestRestTemplate`. Add `@AutoConfigureMockMvc`, `@AutoConfigureWebClient`, or `@AutoConfigureTestRestTemplate` as needed.
- Replace removed `@MockBean` and `@SpyBean` with `@MockitoBean` and `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito`.
- Remove explicit `MockitoTestExecutionListener` registration.
- Update `TestRestTemplate` imports to `org.springframework.boot.resttestclient`.

## Plugin Compatibility

Check plugin compatibility before treating a Grails 8 runtime failure as an application bug.

- Some plugins may not yet support Spring Boot 4 or Spring Framework 7.
- The Grails 8 upgrade guide calls out `grails-spring-security` and SiteMesh 3 as known compatibility risks at the time of writing.
- Applications using `grails-sitemesh3` should stay on the `grails-layout` plugin until SiteMesh 3 compatibility is available.
- Check the Grails issue tracker and each plugin release notes before upgrading plugin-dependent applications.

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
- Do not ignore plugin compatibility. Spring Boot 4 and Spring Framework 7 removals often surface first through plugins.
