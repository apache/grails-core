---
name: grails-upgrade
description: "Upgrades a Grails application to Grails 7.2 (releases 7.0.0 to 7.0.18, 7.1.0 to 7.1.8 and 7.2.0 to 7.2.5). Base version: Grails 6; use it for an application on Grails 6, Grails 7.0, Grails 7.1, or an earlier Grails 7.2 release. An application on Grails 5 or earlier first upgrades to the last Grails 6 release. Covers Java 17, Groovy 4, Spring Boot 3, Spring Framework 6, Jakarta EE packages, Apache coordinates, the Grails BOM, Gradle plugin changes, Micronaut, Spring Security, Quartz, layouts, the SiteMesh 3 integration, JSON rendering, data binding, interceptors, multi-tenancy, URL mappings, scaffolding, audit annotations and exception logging."
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide upgrades of Grails applications from Grails 6 to Grails 7.
- Guide upgrades between releases of the Grails 7 line, with one part per release that changed something an application can notice.
- Turn the "Upgrading from Grails 6 to Grails 7.0" chapter of the Grails user guide into ordered steps for an agent working in an application's source tree.
- Cover the "Upgrading from Grails 7.0 to Grails 7.1" and "Upgrading from Grails 7.1 to Grails 7.2" chapters of the guide in the parts of their releases.
- Keep migration work focused on public application behavior, not internal Grails framework implementation details.

## When to Use Me

Activate this skill when:

- Updating an application from Grails 6 to Grails 7.
- Updating an application from Grails 7.0 to Grails 7.1 or 7.2, or from Grails 7.1 to Grails 7.2.
- Updating an application from one Grails 7 release to a newer one.
- Reviewing a Grails 7 upgrade branch or pull request.
- Fixing build failures, test failures, runtime failures, or behavior changes after either kind of upgrade.

## Which Parts Apply

The base version of this skill is Grails 6. After this section the skill has one part per release, in ascending order, each under a heading such as "Grails 7.0.0", and a last part, "After the Upgrade". A change sits in the part of the first release that carried it, as in the upgrade guide. A change that adds a configuration setting names the setting and its default.

1. Read the application's current Grails version from `grailsVersion` in `gradle.properties`.
2. Establish the target version. This skill covers targets up to Grails 7.2.5.
3. Apply the part of every release after the application's current version, up to and including the target version, in ascending order.
4. An application on Grails 6 starts at "Grails 7.0.0" and applies every part from there up to and including the target version. An application on Grails 5 or earlier first upgrades to the last Grails 6 release.
5. A Grails 7.1 or 7.2 release carries changes of the older release lines without a part of its own for them. When the current or the target version is such a release, select the parts as described under "Releases that carry an older line" below.
6. Finish with "After the Upgrade".

These releases have no upgrade notes of their own and therefore no part: 7.0.1, 7.0.3, 7.0.4, 7.0.5, 7.0.7, 7.0.8, 7.0.9, 7.0.10, 7.0.12 and 7.0.15; 7.1.1 and 7.1.3 to 7.1.8; 7.2.1 to 7.2.5.

If an application that is already on Grails 7 shows a behavior described under "Grails 7.0.0", treat that part as applying to it too.

The upgrade guide of the target version is the source of truth. Where this skill writes `<groovyVersion>` or `<gradleVersion>`, take the value from its chapter "Upgrading from Grails 6 to Grails 7.0". The chapters for the later Grails 7 releases name no Groovy or Gradle version.

### Releases that carry an older line

A Grails 7.1 release carries the changes of the Grails 7.0 releases, without a part of its own for them. The guide states which:

| Grails 7.1 release | Carries the changes of |
|--------------------|------------------------|
| 7.1.0 | every Grails 7.0 release up to 7.0.10 |
| 7.1.1 | 7.0.11 |
| 7.1.2 | 7.0.12 |
| 7.1.3 | 7.0.13 |
| 7.1.4 | 7.0.14 |
| 7.1.5 | 7.0.15 |
| 7.1.6 | 7.0.16 |
| 7.1.7 | 7.0.17 |
| 7.1.8 | 7.0.18 |

A Grails 7.2 release carries the changes of the Grails 7.1 releases in the same way, and with them the changes of the Grails 7.0 releases those carry:

| Grails 7.2 release | Carries the changes of | And with it, of |
|--------------------|------------------------|-----------------|
| 7.2.0 | every Grails 7.1 release up to 7.1.3 | every Grails 7.0 release up to 7.0.13 |
| 7.2.1 | 7.1.4 | 7.0.14 |
| 7.2.2 | 7.1.5 | 7.0.15 |
| 7.2.3 | 7.1.6 | 7.0.16 |
| 7.2.4 | 7.1.7 | 7.0.17 |
| 7.2.5 | 7.1.8 | 7.0.18 |

So a 7.2 release stands at a release of the 7.1 line and at a release of the 7.0 line as well: the ones it carries. A 7.1 release stands at itself and at the 7.0 release it carries. A 7.0 release stands at itself. Apply:

- the part of every 7.0 release after the 7.0 release the current version stands at, up to and including the 7.0 release the target version stands at;
- the part of every 7.1 release after the 7.1 release the current version stands at, up to and including the 7.1 release the target version stands at. An application on Grails 7.0 starts at "Grails 7.1.0";
- the part of every 7.2 release after the current version, up to and including the target version. An application on Grails 7.0 or 7.1 that moves to Grails 7.2 starts at "Grails 7.2.0".

Examples:

- From 7.1.3 to 7.1.8: 7.1.3 carries 7.0.13 and 7.1.8 carries 7.0.18, so apply "Grails 7.0.14", "Grails 7.0.16", "Grails 7.0.17" and "Grails 7.0.18". Grails 7.0.15 and 7.1.4 to 7.1.8 have no notes of their own.
- From 7.2.0 to 7.2.5: 7.2.0 carries 7.1.3 and with it 7.0.13, and 7.2.5 carries 7.1.8 and with it 7.0.18, so apply "Grails 7.0.14", "Grails 7.0.16", "Grails 7.0.17" and "Grails 7.0.18". Grails 7.1.4 to 7.1.8 and 7.2.1 to 7.2.5 have no notes of their own.
- From 7.1.1 to 7.2.0: 7.1.1 carries 7.0.11, and 7.2.0 carries 7.1.3 and with it 7.0.13, so apply "Grails 7.0.13", "Grails 7.1.2" and "Grails 7.2.0".
- From 7.0.12 to 7.1.2: 7.1.2 carries 7.0.12, so no 7.0 part applies. Apply "Grails 7.1.0" and "Grails 7.1.2".

# Grails 7.0.0

## Upgrading from Grails 6 to Grails 7

Apply the steps in order. Build file and coordinate changes come first so that later compile errors point at application code.

### 1. Platform baseline

- Java 17 is the baseline. Configure the build, CI and the runtime to use Java 17 before anything else.
- Groovy 4 is the baseline. See "Groovy 4 changes" below.
- Grails 6 used Spring Boot 2.7.x. Grails 7 uses Spring Boot 3. Review the Spring Boot release notes for 3.0, 3.1, 3.2, 3.3, 3.4 and 3.5 at `https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.<minor>-Release-Notes`.
- Grails 7 is built on Spring Framework 6. If the application uses Spring-specific features, review https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x.
- Spring switched from `javax` to `jakarta` packages. See "javax to jakarta".

### 2. Groovy 4 changes

Review the Groovy 4 release notes (https://groovy-lang.org/releasenotes/groovy-4.0.html). Search the application for code affected by these changes:

- GROOVY-10621: a primitive `boolean` property no longer generates both `isProperty` and `getProperty`. Only `isProperty()` is generated. Grails sometimes generates these properties for domain objects. Switch callers of `getX()` on primitive booleans to `isX()`.
- GROOVY-5169 and GROOVY-10449: fields with a `public` modifier are now returned by `MetaClassImpl#getProperties()`. They were not in Groovy 3.
- Closures using the `DELEGATE_FIRST` strategy resolve differently. Groovy 3 order: delegate's `invokeMethod`, owner's `invokeMethod`, delegate's `invokeMissingMethod`, owner's `invokeMissingMethod`. Groovy 4 order: delegate's `invokeMethod`, delegate's `invokeMissingMethod`, owner's `invokeMethod`, owner's `invokeMissingMethod`.
- GROOVY-11568: closures defined in a parent class that reference its private properties or methods may no longer have access to them in subclasses.
- Some older libraries bring in an older version of Groovy but are still compatible with Groovy 4. GPars is one example. Force the dependency upgrade in `build.gradle`:

```groovy
configurations.configureEach {
    resolutionStrategy.eachDependency { DependencyResolveDetails details ->
        if (details.requested.group == 'org.codehaus.groovy') {
            details.useTarget(group: 'org.apache.groovy', name: details.requested.name, version: '<groovyVersion>')
        }
    }
}
```

- Groovy 4 uses invokedynamic by default instead of callsite optimizations. This can cause performance regressions compared to Grails 6. As of Grails 7.1 the Grails Gradle plugins disable invokedynamic for all `GroovyCompile` tasks themselves. Do not add a `tasks.withType(GroovyCompile)` block that sets `indy = false` to `build.gradle`, and remove one that is there. To compile with invokedynamic, set `indy = true` on the `grails` extension. See "Grails 7.1.0".

### 3. Set the unified project version

Grails 7 moved to a mono repository. All projects of a Grails release share one version.

- Set `grailsVersion` in `gradle.properties` to the target version.
- Remove older properties such as `gormVersion` and `grailsGradlePluginVersion`. Use `grailsVersion` only.
- Rename `groovyVersion` to `groovy.version` in `gradle.properties` if the application defines it to override the Groovy version and uses the Spring Dependency Management plugin. The new name matches the Spring BOM property names.

### 4. Grails BOM

Grails 6 had no single Bill of Materials; a `micronaut-bom` and a `spring-bom` existed side by side. Grails 7 publishes `org.apache.grails:grails-bom`, which inherits from the `spring-bom`. The `micronaut-bom` is not included by default.

- The BOM is applied automatically, through the Spring Dependency Management plugin, when a Grails Gradle plugin is applied. Remove dependency versions that the BOM now manages.
- For a project that does not use a Grails Gradle plugin, apply the BOM as a Gradle platform:

```groovy
dependencies {
    implementation platform("org.apache.grails:grails-bom:$grailsVersion")
}
```

- Override a BOM version by setting the associated Gradle property. The properties are listed in the "Grails BOM" reference of the target version's documentation. Variant BOMs exist for Hibernate 5 and for Micronaut, each with its own reference page.
- The Spring Dependency Management plugin is still used by default. It can be disabled with the `springDependencyManagement` property of the `grails` extension.

BOM property names changed during the Grails 7 line. See "Grails 7.0.6".

### 5. Coordinate changes

Grails moved to the Apache Software Foundation. The group ID of Grails artifacts is now `org.apache.grails`. This applies to all Grails packages maintained by the Grails team, including core plugins and core dependencies.

- The mapping from old to new coordinates is in `RENAME.md`: https://github.com/apache/grails-core/blob/7.0.x/RENAME.md.
- A script in `etc/bin` of the grails-core repository (https://github.com/apache/grails-core/tree/7.0.x/etc/bin) scans Gradle files and updates dependencies and exclusions to the new coordinates. It also updates the Spring Security and Quartz artifacts:

```shell
./rename_gradle_artifacts.sh -l my/project/location
```

- Search every Gradle file for `org.grails` and `org.grails.plugins` after running the script, and check each remaining match against `RENAME.md`.

Also check for:

- `org.apache.grails:grails-i18n` and `org.grails:grails-plugin-i18n`. Remove both. The plugin is now `org.apache.grails.i18n:grails-i18n` and is provided transitively.
- The asset pipeline. It has a new home (https://github.com/wondrify/asset-pipeline). Version 5.0.12 and later is compatible with Grails 7.x. The coordinates are `cloud.wondrify:asset-pipeline-gradle` and `cloud.wondrify:asset-pipeline-grails`. The Gradle plugin ID is `cloud.wondrify.asset-pipeline`:

```groovy
plugins {
    id "cloud.wondrify.asset-pipeline"
}
```

or

```groovy
apply plugin: "cloud.wondrify.asset-pipeline"
```

### 6. Gradle

Update the wrapper to the Gradle version the upgrade guide of the target version recommends, or higher:

```console
./gradlew wrapper --gradle-version <gradleVersion>
```

Then check `gradle/wrapper/gradle-wrapper.properties`. The `distributionUrl` must point to that version:

```
distributionUrl=https\://services.gradle.org/distributions/gradle-<gradleVersion>-bin.zip
```

The Grails build is now parallel and lazy, and most tasks are cacheable. Task inputs changed to support lazy configuration, so custom configuration of tasks that Grails distributes may need updates.

Gradle plugin changes to check for:

- `FindMainTask` now fails when no `Application` class is found and the project has a Grails Gradle plugin applied. It is also cacheable: restarting with `bootRun` does not force a recompile when nothing changed.
- Configuration tasks were added to customize the `compileGroovy` tasks. They pass metadata to the Groovy compiler so that AST transformations know whether code is a Grails plugin or a Grails application. If these tasks do not run or are cached incorrectly, URL mappings may resolve incorrectly. Report such a case to the Grails project.
- The `grails-doc` Gradle plugin was removed. Grails no longer provides documentation generation. Remove the plugin from the build.
- A project cannot be both a plugin and an application. Search `build.gradle` for a project that applies both the `grails-plugin` and the `grails-web` Gradle plugin. Grails 7 raises a validation error when a Grails application Gradle plugin and a Grails plugin Gradle plugin are configured for the same Gradle project. Split the project.
- Jar artifacts produced by Grails plugins no longer have the suffix `-plain`. Search build scripts and deployment scripts for `-plain`.
- Test dependencies are no longer on the production classpath. If production code needs one, add it to the `implementation` configuration.
- `Test` and `JavaExec` tasks no longer get default JVM arguments. To restore the old behavior:

```groovy
tasks.withType(Test).configureEach {
   jvmArgs('-XX:+TieredCompilation', '-XX:TieredStopAtLevel=1', '-XX:CICompilerCount=3')
}
tasks.withType(JavaExec).configureEach {
   jvmArgs('-XX:+TieredCompilation', '-XX:TieredStopAtLevel=1', '-XX:CICompilerCount=3')
}
```

- The Database Migration plugin finds changelogs in `grails-app/migrations` without build configuration. The `org.apache.grails.gradle.grails-plugin` Gradle plugin adds the directory. Remove this block from `build.gradle`:

```groovy
sourceSets {
    main {
        resources {
            srcDir 'grails-app/migrations'
        }
    }
}
```

The `exploded` setup for multi-project reloading is simplified. Earlier versions required the property `grails.run.active`, the property `exploded` set to `true` on the plugin project before the `grails-plugin` Gradle plugin was applied, and `exploded` set on the `grails` extension of the application. Replace that setup with:

1. In the plugin project, apply the Gradle plugin `org.apache.grails.gradle.grails-exploded`.
2. In the application project, define plugins inside the `plugins` block of the `grails` extension, not in the `dependencies` block:

```groovy
grails {
   plugins {
        implementation project(":my-plugin")
   }
}
```

Class files and resource files are used instead of the jar only if the plugin applies `org.apache.grails.gradle.grails-exploded` and is a subproject of the same Gradle build.

Reproducible builds: Grails applications can now be built reproducibly.

- Set the environment variable `SOURCE_DATE_EPOCH` to a fixed date. Grails itself uses the last commit: `SOURCE_DATE_EPOCH=$(git log -1 --pretty=%ct)`.
- Generated build metadata properties files, including `META-INF/grails.build.info`, `META-INF/grails.factories` and `gsp/views.properties`, no longer contain the timestamp comment written by Java's `Properties.store`. No `SOURCE_DATE_EPOCH` comment replaces it. Descriptive comments and property values are preserved. Check tooling that reads the timestamp comment.

### 7. Compare with a generated application

Generate a new Grails 7 application with https://start.grails.org/ and compare it with the application. Apply the differences to the Gradle files and the configuration.

- Several Gradle configurations that were previously required are now defaults of the Grails Gradle plugins. Remove them from the application where the generated application does not have them.
- Generated configuration files changed during the Grails 7 line. See "Grails 7.0.11" before removing properties from `application.yml`.
- The generated `Bootstrap` class no longer includes `servletContext`. If `init` needs it, inject it:

```groovy
ServletContext servletContext
```

- Generated applications use Spring Boot Developer Tools for development reloading, as in Grails 4 and 5 and in shell-generated Grails 6 applications.
- Both the legacy `grails-shell-cli` and `grails-forge-cli` are included with Grails 7. See the "Downloading & Installing" section of the guide for how to use the CLI commands.

Then build:

```console
./gradlew build
```

### 8. javax to jakarta

- Search the application for `javax.` imports and references covered by the Jakarta EE move, and change them to `jakarta.`. Consult the Spring upgrade guides for the impact.
- For older dependencies that still use `javax.`, a Gradle migration plugin can apply transforms and dependency substitutions until the dependency is updated or replaced. To configure the `gradle-jakartaee-migration-plugin` in `build.gradle`:

```groovy
plugins {
    id 'com.netflix.nebula.jakartaee-migration'  version '1.0.0'
}

jakartaeeMigration {
    // Exclude Grails dependencies since AST and jakartaeeMigration are not compatible
    excludeTransform('org.apache.grails:**')
    excludeTransform('org.apache.grails.**:**')

    // Exclude logback-core to avoid extra logback debug messages
    excludeTransform('ch.qos.logback:logback-core')

    // enable automatic migration from EE 8 (javax) or earlier to EE 10 (jakarta) or later
    migrate()
}
```

### 9. Plugins

All Grails plugins written for earlier versions need an update to work with Grails 7.

- List the application's plugins and find a Grails 7 release of each.
- Plugins in the Grails Plugins GitHub organization (https://github.com/grails-plugins/) are updated by the Grails core team as time permits and on request. Ask through a ticket at https://github.com/apache/grails-core/issues.

### 10. Removed libraries, classes and APIs

Search the application for each name below.

- `grails-web-fileupload` and its only class `ContentLengthAwareCommonsMultipartResolver` are removed, because Spring 6 removed `CommonsMultipartResolver`. Remove the dependency and any bean that uses the class. Use the multipart support of the servlet container, as the Spring Boot documentation describes (https://docs.spring.io/spring-boot/how-to/spring-mvc.html#howto.spring-mvc.multipart-file-uploads).

Removed deprecations:

| Removed | Replacement |
|---------|-------------|
| `org.grails.spring.beans.factory.OptimizedAutowireCapableBeanFactory` | none given |
| `GrailsPlugin#checkForChangesExpected` | none given |
| `GrailsClassUtils#isGetter(String, Class[])` | `isGetter(String, Class, Class[])` |
| `GrailsClassUtils#getPropertyForGetter(String)` | `getPropertyForGetter(String, Class)` |
| `AbstractGrailsClass#getPropertyDescriptors` | `getMetaProperties` |
| `org.grails.core.metaclass.BaseApiProvider` | traits |
| Several static variables on `ClosureEventTriggeringInterceptor` | the variables on `AbstractPersistentEvent` |
| `grails.testing.gorm.DataTest#dataStore` | `DataTest#datastore` |

Removed classes and their replacements:

| Removed class | Replacement |
|---------------|-------------|
| `grails.core.GrailsTagLibClass` | `grails.core.gsp.GrailsTagLibClass` |
| `org.grails.core.artefact.TagLibArtefactHandler` | `org.grails.core.artefact.gsp.TagLibArtefactHandler` |
| `org.grails.core.DefaultGrailsTagLibClass` | `org.grails.core.gsp.DefaultGrailsTagLibClass` |
| `org.grails.plugins.CodecsGrailsPlugin` | `org.grails.plugins.codecs.CodecsGrailsPlugin` |
| `grails.artefact.AsyncController` | `grails.async.web.AsyncController` |
| `grails.beans.util.LazyBeanMap` | `grails.beans.util.LazyMetaPropertyMap` |
| `org.grails.plugins.databinding.DataBindingGrailsPlugin` | `DataBindingConfiguration` |

Renamed API:

- `GrailsClassUtils#isMatchBetweenPrimativeAndWrapperTypes` is now `GrailsClassUtils#isMatchBetweenPrimitiveAndWrapperTypes`.

### 11. Micronaut

In Grails 4 to 6, Micronaut was the parent context of the Grails application. In Grails 7, Micronaut is set up through the `micronaut-spring-starter` by the `grails-micronaut` plugin. An application that does not use Micronaut needs no change here and keeps using `grails-bom`.

For an application that uses Micronaut:

- Apply `grails-micronaut-bom` as `enforcedPlatform` and add the `grails-micronaut` library:

```groovy
dependencies {
    // enforcedPlatform on grails-micronaut-bom is required when using grails-micronaut
    implementation enforcedPlatform("org.apache.grails:grails-micronaut-bom:$grailsVersion")

    implementation 'org.apache.grails:grails-micronaut'
}
```

- Only Micronaut 4.9.2 or higher is supported.
- Remove the `micronautPlatformVersion` Gradle property. `grails-micronaut-bom` pins the Micronaut platform version.
- Groovy classes annotated with `@Singleton`, `@Factory`, `@ConfigurationProperties` and similar are processed automatically. The Grails Gradle Plugin configures `micronaut-inject-groovy` on `compileOnlyApi`.
- Search for Java source files with Micronaut annotations such as `@Singleton` or `@Factory`. For those, add the annotation processor by hand. It is not configured automatically because it is incompatible with Groovy incremental compilation. For projects that mix Java and Groovy Micronaut beans, consider separate source sets or modules:

```groovy
dependencies {
    annotationProcessor enforcedPlatform("org.apache.grails:grails-micronaut-bom:$grailsVersion")
    annotationProcessor 'io.micronaut:micronaut-inject-java'
    annotationProcessor 'jakarta.annotation:jakarta.annotation-api'
}
```

- The Grails Gradle Plugin sets the `bootJar` and `bootWar` tasks to the `CLASSIC` loader implementation when `grails-micronaut` is detected. `java -jar` execution needs it. Remove an explicit `loaderImplementation` setting from `build.gradle`.
- Spring Boot DevTools does not work with the Micronaut integration (https://github.com/micronaut-projects/micronaut-spring/issues/769).

As of Grails 7.0.11 the build fails when `grails-micronaut-bom` is not applied as `enforcedPlatform`. See "Grails 7.0.11".

### 12. Hibernate, Ehcache and H2

`org.hibernate:hibernate-ehcache` is no longer provided by the `org.apache.grails:grails-hibernate5` plugin.

Search `application.yml` and `application.groovy` for `org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory`. It is deprecated in Hibernate 5.6 and is not compatible with `org.hibernate:hibernate-core-jakarta`, which Grails 7 uses. Setting `hibernate.cache.region.factory_class` to it adds the non-jakarta `hibernate-core` to the classpath, which causes `NoClassDefFoundError` and `ClassNotFoundException`.

Preferred fix: change the region factory to `jcache` and add the Ehcache dependency.

```yaml
hibernate:
    allow_update_outside_transaction: true
    cache:
        queries: false
        use_second_level_cache: true
        use_query_cache: false
        region:
            factory_class: 'jcache'
```

The `cache` block belongs under `hibernate`, since the setting is `hibernate.cache.region.factory_class`. Change `factory_class` where the application already has it and keep its other cache settings.

```groovy
    implementation 'org.ehcache:ehcache', {
        capabilities {
            requireCapability('org.ehcache:ehcache-jakarta')
        }
    }
```

Alternative: declare `hibernate-ehcache` explicitly. It brings in a conflicting `javax` version of `org.hibernate:hibernate-core`, so exclude that:

```groovy
dependencies {
    implementation 'org.hibernate:hibernate-ehcache:5.6.15.Final', {
        // exclude javax variant of hibernate-core
        exclude group: 'org.hibernate', module: 'hibernate-core'
    }
    runtimeOnly 'org.jboss.spec.javax.transaction:jboss-transaction-api_1.3_spec:2.0.0.Final', {
        // required for hibernate-ehcache to work with javax variant of hibernate-core excluded
    }
}
```

H2 is stricter about reserved keywords. If the application uses H2, check domain class, property, table and column names when H2 reports errors. Examples of the new restrictions are in https://github.com/grails/grails-data-hibernate5/pull/910/commits/c8de45df204966ccc228b46b94beeb2142ae0f59.

### 13. Layouts

Grails 7 has two layout engines: SiteMesh 2.6.x in the `grails-layout` plugin and SiteMesh 3.x in the `grails-sitemesh3` plugin. `grails-layout` is what traditionally shipped with Grails. `grails-sitemesh3` is functional but has some known issues.

To use `grails-layout`, add the dependency:

```groovy
implementation "org.apache.grails:grails-layout"
```

Then search the application for the old names:

| Kind | Old | New |
|------|-----|-----|
| Package | `grails.web.sitemesh` | `org.apache.grails.web.layout` |
| Package | `org.grails.web.sitemesh` | `org.apache.grails.views.gsp.layout` |
| Class | `org.grails.web.servlet.view.GrailsLayoutViewResolver` | `org.apache.grails.web.layout.EmbeddedGrailsLayoutViewResolver` |
| Class | `org.grails.web.servlet.view.SitemeshLayoutViewResolver` | `org.apache.grails.web.layout.GrailsLayoutViewResolver` |
| Class | `org.grails.web.sitemesh.GrailsLayoutView` | `org.apache.grails.web.layout.EmbeddedGrailsLayoutView` |
| Class | `org.grails.web.sitemesh.SitemeshLayoutView` | `org.apache.grails.web.layout.GrailsLayoutView` |
| Class | `org.grails.web.sitemesh.GSPSitemeshPage` | `org.apache.grails.web.layout.GSPGrailsLayoutPage` |
| Property | `grails.sitemesh.default.layout` | `grails.views.layout.default` |
| Property | `grails.sitemesh.enable.nongsp` | `grails.views.layout.enable.nongsp` |
| Constant | `org.grails.web.sitemesh.GrailsLayoutView.GSP_SITEMESH_PAGE` | `org.apache.grails.web.layout.EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE` |
| Tag namespace | `sitemesh` | `grailsLayout` |

The property renames in this table apply to `grails-layout` only. With `grails-sitemesh3` the default layout is configured with `grails.sitemesh.default.layout`. Do not rename it to `grails.views.layout.default` there. As of 7.2.0 the SiteMesh 3 integration no longer uses a servlet filter and ignores several older properties; see "Grails 7.2.0".

### 14. Multipart limits in Tomcat

Tomcat 10.1.42 introduced limits for the part count and the part header size of `multipart/form-data` requests.

| Property | Default |
|----------|---------|
| `server.tomcat.max-part-count` | 10 |
| `server.tomcat.max-part-header-size` | 512B |

Search the application for forms and API clients that post more than 10 parts in one request. Raise the limits in the configuration where needed.

### 15. Date and time output

Java 20 and later format dates differently, following Unicode CLDR 42:

- The space before the period (AM or PM) is a narrow non-breaking space (` `) instead of a standard space.
- With the `LONG` or `FULL` time style together with a date style, the separator between date and time is `, ` instead of ` at `. Example: `January 5, 1941, 8:00:00 AM UTC` instead of `January 5, 1941 at 8:00:00 AM UTC`.

Search tests and parsing code for formatted date strings that contain ` AM`, ` PM` or ` at `.

JSON rendering of date and time types changed. It is now the same in the standard JSON converters (`render ... as JSON`, the `grails-converters` module) and in JSON views (`.gson` files, the `grails-views-gson` module), and matches Spring Boot's Jackson serialization:

| Type | Before | After |
|------|--------|-------|
| `java.util.Calendar` | object with all properties: `{"time": 1759869218602, "timeZone": {...}, "firstDayOfWeek": 1, ...}` | `"2025-10-07T21:14:31Z"` |
| `java.time.Instant` | standard converters: `1759869218602`; JSON views: `{"epochSecond": 1759869218, "nano": 602000000}` | `"2025-10-07T21:14:31.602Z"` |
| `java.time.LocalDate` | `{"year": 2025, "month": "OCTOBER", "dayOfMonth": 8, ...}` | `"2025-10-08"` |
| `java.time.LocalDateTime` | JSON views: `{"year": 2025, "month": "OCTOBER", "dayOfMonth": 7, ...}` | `"2025-10-07T21:14:31"` |
| `java.time.OffsetDateTime` | `{"offset": {...}, "year": 2025, "month": "OCTOBER", ...}` | `"2025-10-08T00:48:46.407254-07:00"` |
| `java.time.ZonedDateTime` | `"2025-10-08T00:48:46.407254-07:00[America/Los_Angeles]"` | `"2025-10-08T00:48:46.407254-07:00"` |
| `java.util.Date` | `"2025-10-07T21:14:31Z"` | `"2025-10-07T21:14:31.407Z"` |

Rules of the new output:

- `Date`, `Calendar` and `Instant` render in ISO-8601 with the `Z` suffix (UTC).
- `LocalDate` renders as `YYYY-MM-DD`. `LocalDateTime` renders without the `Z` suffix. Neither carries timezone information.
- `OffsetDateTime` and `ZonedDateTime` render with their timezone offset, such as `-07:00`. The zone ID, such as `[America/Los_Angeles]`, is no longer in the output.
- `Date` and `Calendar` have millisecond precision (`.SSS`). `Instant`, `OffsetDateTime` and `ZonedDateTime` have nanosecond precision, up to 9 decimal places with trailing zeros dropped.

Search the application for domain classes, command objects and JSON views that render these types, and for tests and API clients that read them. Clients that parsed epoch milliseconds or object structures must parse the ISO-8601 string, for example with `Instant.parse()`, `LocalDate.parse()`, `LocalDateTime.parse()`, `OffsetDateTime.parse()` or `ZonedDateTime.parse()`.

Enum rendering in JSON and XML has an opt-in simple format. See "Grails 7.0.2".

### 16. GORM for MongoDB

`Instant` persistence switched from epoch milliseconds (BSON int64) to BSON DateTime, the same BSON type as `java.util.Date`. `LocalDateTime` is still converted to BSON DateTime using the configured zone.

- Search the domain classes for properties of type `Instant`.
- Convert the stored values before upgrading. For a collection `Example` with a property `created`:

```javascript
db.Example.updateMany(
  { created: { $type: "long" } },
  [ { $set: { created: { $toDate: "$created" } } } ]
);
```

### 17. Functional tests with Geb

The Grails Geb plugin now provides test fixtures for containerized browser testing. This is the recommended way to write functional tests in Grails 7, and it requires a container runtime.

- To adopt it, extend `ContainerGebSpec` instead of `GebSpec`. A container with a browser that can reach the application under test is started automatically when the integration tests run. It is provided by Testcontainers. No further configuration is needed.
- Supported container runtimes include Docker Desktop, OrbStack (macOS only), Rancher Desktop, Podman Desktop, and Colima (macOS and Linux).
- The previous method with WebDriver binaries remains supported for backward compatibility. Matching driver and browser versions can be difficult.

Also update tests for the changes listed in other steps: `DataTest#dataStore` is now `DataTest#datastore`, test dependencies are no longer on the production classpath, `Test` tasks have no default JVM arguments, and H2 is stricter about reserved keywords.

# Grails 7.0.2

## Enum JSON and XML serialization

Enum JSON and XML serialization can use a simple format.

- New settings: `grails.converters.json.enum.format` and `grails.converters.xml.enum.format`. Set them to `simple` to opt in. The default remains the legacy format.
- The legacy marshallers `org.grails.web.converters.marshaller.json.EnumMarshaller` and `org.grails.web.converters.marshaller.xml.EnumMarshaller` are deprecated for removal since 7.0.2. Search the application for references to them.
- Deprecation timeline: in 7.0.2 the legacy `EnumMarshaller` is deprecated and remains the default, and `SimpleEnumMarshaller` is available through the settings. In Grails 8.0 `SimpleEnumMarshaller` becomes the default and the legacy `EnumMarshaller` classes are removed. Clients that read the legacy structure have to change before an upgrade to Grails 8.0.

Legacy output, still the default:

```json
{
  "stage": {
    "enumType": "com.example.ChallengeStage",
    "name": "SUBMIT"
  }
}
```

```xml
<stage enumType="com.example.ChallengeStage">SUBMIT</stage>
```

Simple output, produced by `SimpleEnumMarshaller`:

```json
{
  "stage": "SUBMIT"
}
```

```xml
<stage>SUBMIT</stage>
```

The legacy format is asymmetric: a client posts `"stage":"SUBMIT"` and receives the object structure. The simple format returns what was posted. To opt in:

```yaml
grails:
  converters:
    json:
      enum:
        format: simple
    xml:
      enum:
        format: simple
```

Leave the settings out to keep the previous output. Check API clients and tests that read enum values before opting in.

# Grails 7.0.6

## BOM property changes

BOM version properties restored.

- The initial 7.0.0 release did not populate the version properties of the Grails BOM correctly. The 7.0.4 release restored them, with inconsistent and duplicated names.
- Later Grails 7 versions renamed and consolidated the properties. The guide lists the renames under Grails 7.0.6. They follow below.
- Search `gradle.properties` and the build files for properties ending in `.version` that override a BOM version, and check each name against the BOM reference of the target version.

BOM property names changed during the Grails 7 line. When the application overrides BOM versions, search `gradle.properties` and the build files for the old names below.

Dotted duplicates of third-party properties were removed. Only the hyphenated form is kept:

| Removed | Kept |
|---------|------|
| `asset.pipeline.gradle.version` | `asset-pipeline-gradle.version` |
| `byte.buddy.version` | `byte-buddy.version` |
| `commons.text.version` | `commons-text.version` |
| `directory.watcher.version` | `directory-watcher.version` |
| `grails.publish.version` | `grails-publish-plugin.version` |
| `javaparser.core.version` | `javaparser-core.version` |
| `bootstrap.icons.version` | `bootstrap-icons.version` |
| `commons.codec.version` | `commons-codec.version` |
| `geb.spock.version` | `geb-spock.version` |
| `asset.pipeline.bom.version` | `asset-pipeline-bom.version` |

The guide also lists these pairs as duplicates with slightly different names and equal values: `spock.version` / `spock.bom.version`, `jackson.version` / `jackson.bom.version`, `groovy.version` / `groovy.bom.version`, `selenium.version` / `selenium.bom.version`. All dotted variants were removed. Check the BOM reference for the name that remains.

Grails-specific properties use hyphens instead of dots. For example, `grails.async.version` became `grails-async.version` and `grails.data.hibernate5.version` became `grails-data-hibernate5.version`.

Profile properties gained the `grails-profile` prefix:

| Old property | New property |
|--------------|--------------|
| `base.version` | `grails-profile-base.version` |
| `plugin.version` | `grails-profile-plugin.version` |
| `profile.version` | `grails-profile-profile.version` |
| `rest.api.version` | `grails-profile-rest-api.version` |
| `rest.api.plugin.version` | `grails-profile-rest-api-plugin.version` |
| `web.version` | `grails-profile-web.version` |
| `web.plugin.version` | `grails-profile-web-plugin.version` |

Groups of related properties were consolidated or renamed:

| Old properties | Property to use |
|----------------|-----------------|
| `liquibase.version`, `liquibase.cdi.version`, `liquibase.core.version`, `liquibase5.hibernate.version`, `liquibase.hibernate5.version` | `liquibase-hibernate5.version` |
| `bson.version`, `mongodb.driver.core.version`, `mongodb.driver.sync.version`, `bson.record.codec.version` | `mongodb.version` |
| `spring.boot.starter.sitemesh.version` | `starter-sitemesh.version` |
| `ant.junit.version` | `ant.version` |
| `spring.boot.cli.version`, `spring.boot.gradle.plugin.version`, `spring.boot.dependencies.version` | `spring-boot.version` |
| `asciidoctor.gradle.jvm.version` | `asciidoctor-gradle-jvm.version` |

The `spring-boot-dependencies` BOM import now uses `${spring-boot.version}`. Other imported BOMs also use a single property name each.

The RxJava property values were wrong and are fixed:

| Property | Group | Was | Is |
|----------|-------|-----|----|
| `rxjava.version` | `io.reactivex` (RxJava 1.x) | 3.1.11 | 1.3.8 |
| `rxjava2.version` | `io.reactivex.rxjava2` (RxJava 2.x) | 3.1.11 | 2.2.21 |
| `rxjava3.version` | `io.reactivex.rxjava3` (RxJava 3.x) | 3.1.11 | 3.1.11 |

The rework also removed properties of non-published projects and fixed invalid versions for properties used by asciidoctor.

- All published Grails projects keep separate properties, so their versions can be overridden individually.

# Grails 7.0.11

## Generated configuration files

Generated `application.yml` files no longer contain several properties.

From 7.0.11 onwards, `grails-shell-cli`, `grails-forge-cli` and the Grails Forge website (https://start.grails.org) leave these properties out of a generated `application.yml`:

```yaml
grails:
  controllers:
    defaultScope: singleton # already framework default
  converters:
    encoding: UTF-8 # already framework default
  mime:
    disable:
      accept:
        header:
          userAgents: # already framework default
            - Gecko
            - WebKit
            - Presto
            - Trident
  urlmapping:
    cache:
      maxsize: 1000 # Attention! framework default: 5000
  views:
    default:
      codec: html # redundant because GSP expressions already default to html encoding
    gsp:
      codecs:
        expression: html # already framework default
        taglib: none # already framework default
        staticparts: none # already framework default
```

- An existing application is not changed by this. The entries matter when comparing the application with a freshly generated one.
- Most entries restate framework defaults. Removing them does not change behavior.
- `grails.urlmapping.cache.maxsize` is the exception. Older generated applications set it to `1000`; the framework default is `5000`. Keep the property to keep the older cache size.
- Search `application.yml` for these keys. Keep any that the application sets to a custom value.

## Micronaut needs grails-micronaut-bom as an enforced platform

For an application that uses Micronaut, as set up in step 11 of "Grails 7.0.0":

- The Grails Gradle Plugin fails at configuration time if `grails-micronaut` is detected and `grails-micronaut-bom` is not applied as `enforcedPlatform`. The reason: the Micronaut platform manages its own versions of libraries such as `javaparser-core`, and without an enforced BOM Gradle's highest-version-wins resolution lets them override `grails-bom` versions, which leads to binary incompatibilities at runtime.
- Do not apply `enforcedPlatform` to a project without Micronaut. It would force `grails-bom` versions over Spring's managed defaults.
- The Micronaut auto-setup, which performs this validation and other Micronaut-specific configuration, is on by default. It can be disabled when a project deliberately deviates from the recommended setup. This is not recommended:

```groovy
grails {
    micronautAutoSetup = false // not recommended
}
```

# Grails 7.0.13

## The stop-app command

The interactive CLI `stop-app` command no longer stops an application through JMX or the Spring Boot Actuator `/actuator/shutdown` endpoint. `run-app` records the application's process id in `build/run-app.pid`, and `stop-app` reads that file and stops the process.

- Search scripts for `stop-app --host` and `stop-app --port`. The `--host` and `--port` options are removed. Drop them.
- `stop-app` only stops an application that was started with `run-app` for the same project. It can be invoked from interactive mode or from a separate terminal.
- On Linux and macOS the application is shut down gracefully, the equivalent of `SIGTERM`, so JVM shutdown hooks and Spring's orderly shutdown run. On Windows the application is terminated on a best-effort basis and a graceful shutdown is not guaranteed.
- Every `bootRun` invocation, including a direct `./gradlew bootRun`, writes `build/run-app.pid` while the application runs and removes it when the application stops.
- `stop-app` always looks for the PID file under the conventional `build` directory. A customized Gradle build directory (`layout.buildDirectory`) is not supported by `stop-app`.

# Grails 7.0.14

## Spring Security plugins

The Spring Security plugins (Core, ACL, CAS, LDAP, OAuth2, REST and UI) are part of Grails core as of Grails 7. This section covers an application coming from plugin versions 5.x or 6.x with Spring Security 5.8.x, moving to Spring Security 6.x.

Update the dependencies in `build.gradle`. Remove the version; the Grails BOM manages it.

| Old dependency | New dependency |
|----------------|----------------|
| `org.grails.plugins:spring-security-core` | `org.apache.grails:grails-spring-security` |
| `org.grails.plugins:spring-security-acl` | `org.apache.grails:grails-spring-security-acl` |
| `org.grails.plugins:spring-security-cas` | `org.apache.grails:grails-spring-security-cas` |
| `org.grails.plugins:spring-security-ldap` | `org.apache.grails:grails-spring-security-ldap` |
| `org.grails.plugins:spring-security-oauth2` | `org.apache.grails:grails-spring-security-oauth2` |
| `org.grails.plugins:spring-security-rest` | `org.apache.grails:grails-spring-security-rest` |
| `org.grails.plugins:spring-security-rest-gorm` | `org.apache.grails:grails-spring-security-rest-datamapping` |
| `org.grails.plugins:spring-security-rest-grailscache` | `org.apache.grails:grails-spring-security-rest-grails-cache` |
| `org.grails.plugins:spring-security-rest-memcached` | `org.apache.grails:grails-spring-security-rest-memcached` |
| `org.grails.plugins:spring-security-rest-redis` | `org.apache.grails:grails-spring-security-rest-redis` |
| `org.grails.plugins:spring-security-ui` | `org.apache.grails:grails-spring-security-ui` |

```groovy
// Before (Grails 6)
implementation 'org.grails.plugins:spring-security-core:6.1.1'

// After (Grails 7)
implementation 'org.apache.grails:grails-spring-security'
```

Note that `spring-security-core` lost its `-core` suffix.

Also check for:

- `javax.servlet` in security code. Spring Security 6 uses `jakarta.servlet`. See the Spring Security migration guide: https://docs.spring.io/spring-security/reference/migration/index.html.
- Classes that extend `WebSecurityConfigurerAdapter`. It is removed in Spring Security 6. Migrate them to the component-based approach. The plugin handles the core filter chain configuration itself, but custom Spring Security beans may need updating.
- Reliance on default behaviors that changed in Spring Security 6, such as CSRF protection and session management.

The `grails.plugin.springsecurity.*` configuration namespace is unchanged. Existing security configuration in `application.groovy` or `application.yml` should work without modification after the dependency update.

# Grails 7.0.16

## Quartz plugin

The Quartz plugin is part of Grails core as of Grails 7.

- Change the coordinate from `org.grails.plugins:quartz` to `org.apache.grails:grails-quartz` and remove the version. The Grails BOM manages it:

```groovy
dependencies {
    // Grails 6
    // implementation 'org.grails.plugins:quartz:2.0.13'

    // Grails 7
    implementation 'org.apache.grails:grails-quartz'
}
```

- Search `build.gradle` for `org.springframework:spring-context-support`. If it was added only as a workaround for `java.lang.NoClassDefFoundError: org/springframework/scheduling/quartz/AdaptableJobFactory`, remove it. The plugin now declares the dependency, and it comes in transitively. Plugin version 4.0.x did not declare it.
- Search the job classes for the configuration properties `concurrent`, `sessionRequired`, `description` and `group` declared with `def`. They must be `static`. This changed in plugin version 2.0.13, so it affects applications coming from an older release:

```groovy
class MyJob {

    // Grails 6 and earlier
    // def concurrent = false

    static concurrent = false

    static triggers = {
        simple repeatInterval: 5000L
    }

    void execute() {
        // ...
    }
}
```

# Grails 7.0.17

## GORM for MongoDB: Map criterion values with `$` keys

`Map` criterion values that contain `$` keys are rejected. `findWhere`, `findAllWhere`, criteria queries, the `where` DSL and dynamic finders throw `InvalidDataAccessResourceUsageException` when a `Map` passed as a criterion value contains a key starting with `$` at any level, on any property type including enums. MongoDB interprets such a value document as an operator expression, not as a value.

- Maps without such keys are still sent as literal subdocument comparisons. Exact matches on `Map` properties, dotted paths and array elements are unchanged. Geospatial criteria are exempt.
- Search for code that passes an operator document through a criteria value on purpose. Change it to the native `find(Bson)` API.

# Grails 7.0.18

## Interceptor URI matching

Interceptors that match on `uri`, with `match(uri: ...)` or `excludes(uri: ...)`, now match the path that URL mappings route on: percent escapes are decoded, matrix parameters (`;name=value`) are removed, and the context path is stripped before the pattern is applied. Previously the raw request URI was matched, so an encoded (`/%61dmin/users`) or matrix-parameter (`/admin;x=1/users`) request could reach a controller without triggering an interceptor declared for `/admin/**`. Interceptors matched by controller, action or namespace are unaffected.

Search the interceptors for `uri:` and review each match for these changes:

- An interceptor that combines `match(uri:)` with an exclusion (`excludes(uri:)`, `excludes(controller:)`, `excludes(action:)` or a closure) now runs under a non-root context path such as `server.servlet.context-path=/app`. Earlier, that shape never matched there, because the exclusion check required the pattern to match both the context-prefixed and the context-relative request URI, which a pattern such as `/api/**` cannot do. `matchAll()` with exclusions, `match(controller:)` and other name-based matchers, `match(uri:)` without an exclusion, and patterns that match both forms such as `/**` were not affected. Expect such an interceptor to start executing.
- Patterns are matched against the path within the application only. `match(uri: '/*/*')` no longer matches `/app/save` under the context path `/app`. Patterns that begin with the context path, such as `match(uri: '/app/admin/**')`, are still accepted for backwards compatibility.
- During a `<g:include>` or a `RequestDispatcher` include, `uri` matchers see the included path, not the URI of the outer request. This is consistent with `match(controller:)` matchers, which already matched the included controller.
- Custom `grails.interceptors.Matcher` implementations receive the canonical path. The `uri` argument of `doesMatch` is the decoded, application-relative path without matrix parameters.

The Spring Security plugin applies the same canonicalization when it matches `ipRestrictions` patterns. An encoded or matrix-parameter variant of a restricted path is subject to the same restriction as the plain path.

Details of the canonical form:

- Path parameters are removed from each segment before percent-decoding, so an encoded semicolon (`%3B`) is a literal character and not a parameter delimiter.
- Percent-encoded unreserved characters such as `%61` are decoded. The path is decoded exactly once.
- The context path is compared case-insensitively.

## Multi-tenancy with DISCRIMINATOR mode

Only `Tenants.withoutId` and `@WithoutTenant` run without a tenant. A tenant id of `DEFAULT` is a tenant id like any other, whether a `TenantResolver` returns it or it is passed to `Tenants.withId`. Queries return the instances of the tenant `DEFAULT`, and a saved instance gets the tenant id `DEFAULT`.

Search the application for `TenantResolver` implementations, `SubDomainTenantResolver`, `FixedTenantResolver`, `Tenants.` calls, and the `@CurrentTenant` and `@WithoutTenant` annotations. Then check:

- Code that relied on a resolver returning `DEFAULT` to work with every tenant must run inside `Tenants.withoutId` or in a method annotated with `@WithoutTenant`. Examples are `SubDomainTenantResolver` on `localhost` during development, and `new FixedTenantResolver()`.
- With GORM for MongoDB and GORM's in-memory datastore, queries inside `Tenants.withoutId` still return only the instances of the tenant `DEFAULT`. There, `Tenants.withoutId` only lets a saved instance keep the tenant id that is set on it.
- With a tenant id property that is not a `String`, such as a `Long`, a tenant id of `DEFAULT` now fails. A save throws a `TenantException`, and a query with GORM for Hibernate throws an `IllegalArgumentException`. Have the resolver return a tenant id of the property's type.
- Inside `Tenants.withoutId`, `Tenants.currentId()` returns `DEFAULT`, and `Tenants.withCurrent` and `@CurrentTenant` keep running without a tenant. `Tenants.isWithoutId()` tells whether the current code runs without a tenant id.
- A `TenantResolver` that returns `null` now causes a `TenantNotFoundException`, as a resolver that finds no tenant does. This applies in every multi-tenancy mode.
- The `DATABASE` and `SCHEMA` modes are otherwise unchanged.

GORM for Hibernate, additional changes:

- A collection of a multi-tenant entity is restricted to the current tenant when it is loaded lazily or fetched by join, also when it is a many-to-many collection or a query fetches it by join through another association.
- `load` and `proxy` of a multi-tenant entity run a query, as `get` does. Like `get`, they return `null` when no instance is found, instead of a proxy that fails when it is used. Search for `load(` and `proxy(` calls on multi-tenant entities and handle `null`.
- A collection of a multi-tenant entity that is mapped with `fetch: 'join'` is loaded eagerly with a select of its own.
- Loading such a collection lazily without a current tenant, outside `Tenants.withoutId`, throws `TenantNotFoundException`.
- Before a query on an entity that is not multi-tenant but has a collection of a multi-tenant entity, directly or through its associations, GORM calls the `TenantResolver` and activates the filter for the current tenant, so that a collection the query fetches by join is restricted. When there is no current tenant, because the resolver throws a `TenantNotFoundException` or returns `null`, such a query runs as before. Any other exception from the resolver fails the query.
- A query that the resolver itself runs on an entity that is not multi-tenant does not call the resolver again. A query on a multi-tenant entity does, so a resolver must not run one.

GORM for MongoDB and GORM's in-memory datastore used by unit tests, lookups by id:

- `get`, `read`, `exists`, `getAll`, `load` and `proxy` are now restricted to the current tenant, as queries and dynamic finders already were. Previously they looked an instance up by its id alone and returned an instance of another tenant. GORM for Hibernate already restricted `get`, `read`, `exists` and `getAll`.
- The id of another tenant's instance is treated like an id that does not exist: `get` and `read` return `null`, `exists` returns `false`, `getAll` returns `null` in its place, and the proxy returned by `load` or `proxy` throws when it is initialized.
- Without a current tenant, these methods throw `TenantNotFoundException`, as a query does. `getAll` without ids still returns an empty list.
- Inside `Tenants.withoutId { }` a lookup by id is not restricted to a tenant, and neither is a proxy returned by `load` or `proxy` that is initialized there. A tenant id of `DEFAULT` restricts a lookup by id to that tenant.
- A lookup by id still finds an instance that the session holds for the current tenant, including one that has been saved but not flushed, and it does not flush the session.

Unit tests that look up instances across tenants need the same changes as application code.

## GORM for Hibernate: a sort uses a requested left join

A where query, a detached criteria or a dynamic finder that requests a left join of an association, with `join('owner', JoinType.LEFT)` or `fetch: [owner: JoinType.LEFT]`, and sorts by a property of that association, as with `sort: 'owner.lastName'`, sorted with an inner join and left out the instances without the association. As of 7.0.18 the sort uses the requested left join, so those instances are returned, and the `totalCount` of a paged list counts them.

- Search for `JoinType.LEFT` in calls to `join` and in `fetch` arguments of queries that also sort by a property of the same association.
- To leave out the instances without the association, as before, request `JoinType.INNER`, or add a criterion such as `owner != null`.

## Data binding white lists

Data binding white lists apply wherever a class is compiled.

Grails generates a list of bindable properties for a class at compile time, from its statically typed properties, its `bindable` constraints, its `transients` list and its `transient` fields. As of 7.0.18 the list is generated for every class that implements `grails.validation.Validateable` or declares `constraints` or `transients`, and for every class that extends one, wherever the class is compiled with the data binding library, `grails-web-databinding`, on the compilation's classpath, as it is in an application or a plugin. A library module that compiles against GORM alone generates no list and gets no warning. Previously only domain classes and the parameter types of controller actions compiled with their controller got the list, and a class without one was bound without restriction.

Search the application, its plugins and its shared modules for:

- Command objects compiled apart from the controller that binds them (in a plugin or a shared module), `Validateable` objects bound as a nested property, and command objects declared inside another class. They are now bound by their `bindable` constraints and their `transients`. Their dynamically typed properties, declared with `def` or as `Object`, are no longer bound by default. Declare a type for such a property, or mark it `bindable: true`.
- GORM entities compiled outside `grails-app/domain`, such as an `@Entity` class in `src/main/groovy` or in a shared module. They now get the list a domain class in `grails-app/domain` gets and are bound by the same rules. `bindData` no longer binds their `id`, `version`, `dateCreated` and `lastUpdated`. Previously such a class was bound without restriction, its `id` and `version` included.
- `bindData` calls with an `exclude` list and no `include` list, including the `bindData(target, source, List excludes)` overloads. The generated list now stays in effect and the excluded properties are removed from it. Previously the generated list was set aside, so that only the excluded properties were kept out. An empty `include` list still binds every property that is not excluded.
- Binding into a lazily loaded association, as `book.properties = params` binds nested `author` parameters into the book's author. The data is now bound by the list generated for the association's class. Previously an association that was still a proxy was bound without restriction.
- Properties of type `org.springframework.validation.Errors`, such as the `errors` property of a domain class or of a `Validateable` object. They are never bound from request data, whatever a generated list names.
- Classes that get the list and implement `Serializable` without declaring a `serialVersionUID`, such as a `Validateable` object kept in a persisted HTTP session or in a distributed cache. The list is carried as a new `public static final` field, so the class gets a new default `serialVersionUID`, and instances serialized before the upgrade do not deserialize after it. Declare a `serialVersionUID` on such a class.

Check the compiler output for two warnings:

- A class compiled against an already compiled parent takes the parent's bindable properties from the compiled parent. When the parent was compiled by an earlier version of Grails, or without the data binding library on its compile classpath, the compiler warns that the list of the subclass is derived from the parent's bytecode and asks for the parent to be recompiled. Recompile the plugin or module that holds the parent with Grails 7.0.18 or later.
- A controller compiled against a command object that was compiled without the list, by an earlier version of Grails or without the data binding library, warns that the command object is bound without restriction and should be recompiled.

The guide gives no setting that restores the previous behavior. Use typed properties, `bindable: true`, and explicit `include` lists.

## OAuth `callback` parameter of the Spring Security REST plugin

The OAuth `callback` parameter of the Spring Security REST plugin needs allowed origins.

- New setting: `grails.plugin.springsecurity.rest.oauth.allowedCallbackOrigins`, a list of origins. An origin is the scheme, host and port of a URL. When the setting is missing, every `callback` URL is ignored.
- The plugin redirects to the URL in the `callback` parameter of `/oauth/authenticate/<provider>`, with the token appended, only when the URL's origin is listed in the setting and the URL has a path, a query or a fragment after the host. For any other `callback` URL it redirects to the `frontendCallbackUrl`, as it does without the parameter.
- The URL is checked when the login starts and again when it completes. A new login drops the `callback` URL of an earlier login that was not completed.
- Since the plugin became part of Grails, the `callback` parameter failed with a `MissingMethodException`. It works again within these limits.

Search frontend code and API clients for requests to `/oauth/authenticate/` that pass a `callback` parameter. If the application uses the parameter, list the origins of its frontend:

```groovy
grails.plugin.springsecurity.rest.oauth.allowedCallbackOrigins = ['https://my.frontend-app.com']
```

## Encrypted JWTs of the Spring Security REST plugin

Encrypted JWTs of the Spring Security REST plugin are signed.

As of 7.0.18, with `grails.plugin.springsecurity.rest.token.storage.jwt.useEncryptedJwt` enabled, the plugin signs the claims with the RSA private key, using RS256, before it encrypts them with the RSA public key. It accepts an encrypted JWT only when the JWT inside carries a valid signature made with the private key.

Search the configuration for `useEncryptedJwt`. If it is enabled, check each point:

- Access and refresh tokens issued before the upgrade are no longer accepted, so users have to log in again. When several instances of an application share the keys, upgrade them together: an instance that was not upgraded rejects the tokens of an upgraded one, and the other way round.
- An application that generates or validates encrypted JWTs needs both keys, also when a custom `RSAKeyProvider` provides them. Search for `RSAKeyProvider` implementations and confirm that each provides the private key and the public key.
- The private key must be at least 2048 bits long. A shorter key fails with an `IllegalArgumentException` when a token is generated.
- Use the key pair for these tokens only. The plugin accepts any JWT that is signed with the private key and encrypted with the public key, whatever issued it.
- Search for `JwtService.parse`, `accessTokenJwt` and `com.nimbusds.jwt.EncryptedJWT`. For an encrypted JWT, `JwtService.parse` and the `accessTokenJwt` property of an `AccessToken` now return a `grails.plugin.springsecurity.rest.token.generation.jwt.NestedJwt` instead of a `com.nimbusds.jwt.EncryptedJWT`. Its claims are those of the signed JWT inside, its header is that of the encrypted JWT, and it serializes to the token.
- With encrypted JWTs enabled, a signed JWT is rejected as an invalid token, and with signed JWTs, an encrypted JWT is. Previously such a token failed with a `NullPointerException`.
- An encrypted JWT whose content encryption key is encrypted with `RSA1_5` is rejected unless `grails.plugin.springsecurity.rest.token.generation.jwt.jweAlgorithm` is `RSA1_5`. The plugin passes that setting to the new `jweAlgorithm` property of `JwtService`. A `jwtService` bean that an application defines itself accepts `RSA1_5` as before, unless the application sets that property too.

The guide gives no setting that restores encrypted JWTs without a signature.

## GORM for Hibernate: `list` sorts by every property of a sort map

`list` with a `Map` of sort properties, such as `Book.list(sort: [title: 'asc', 'author.name': 'desc'])`, sorted by the last entry of the map only. As of 7.0.18 it sorts by every entry, in the order of the map. The same applies to a default sort that the mapping declares for more than one property, as in `sort author: 'asc', title: 'desc'`.

- Search for `sort: [` in calls to `list`, and for a `sort` with more than one property in a `static mapping` block. The results of these calls are now sorted by every property, so the rows selected by `max` and `offset` can change.
- To sort by one property, as before, pass that property alone.
- An association is joined once for all the entries that sort by it. `find` and `findAll` with an example, `findWhere` and `findAllWhere` failed with `duplicate association path` for such a sort and now work.
- Sorting by two properties of the same collection returns fewer duplicate rows. A book with three chapters occurs three times for `Book.list(sort: ['chapters.title': 'asc', 'chapters.id': 'asc'])`, rather than nine times. Update assertions that relied on the repeated joins. No compatibility setting restores them.

## GSP tag attributes

More GSP tags HTML-encode the attribute values they write.

As of 7.0.18, these tags HTML-encode the values they write into attributes, as `<g:link>` and `<g:textField>` already did:

- `<g:form>`, `<g:uploadForm>` and `<g:formActionSubmit>`: the URL in `action` and `formaction`, and the hidden fields that a `RequestDataValueProcessor` adds to a form.
- `<g:checkBox>`: `name`, `value`, `form`, and the `id` taken from the name, on the checkbox and on its hidden field.
- `<g:actionSubmit>` and `<g:actionSubmitImage>`: `value`, the `action` in the button's name, and `src`.
- `<g:datePicker>`: `name`, `id` and `selectDateClass`.
- `<g:radio>` and `<g:radioGroup>`: `name`, and on `<g:radio>` the `id` taken from it.
- `<g:select>`: the key of `noSelection`.
- `<g:field>`: `type`.
- `<g:withTag>`: the values of `attrs` that are not closures.
- `<g:external>`: `uri`, and `<g:javascript>`: `src`.
- `<g:paginate>`: `class`, on the current step.
- `<g:sortableColumn>`: `title`, which it writes as the text of the column's link. A text from `titleKey` is written as the message source returns it.
- `<g:renderException>`: `detailsClass`, `stackClass`, `filenameClass`, `snippetClass`, `lineClass`, `lineNumberClass` and `lineErrorClass`.

The browser decodes the attribute, so a value without a character reference reaches it unchanged. A value encoded with `encodeAsHTML()` before it reaches the tag is written once, and so are a value captured from a GSP body with `<g:set>` and the output of `fieldValue()`, also when a `RequestDataValueProcessor` returns the value unchanged.

Search the GSP views, layouts and tag libraries for these tags. Then check:

- A character reference written in the page, as in `name="a&amp;b"`, is now encoded as well. Write the character itself.
- `<g:form>` and `<g:formActionSubmit>` build their URL from `uri`, `base`, `controller` and their other link attributes and only then encode it, so a value encoded beforehand is encoded twice there, as it already was in the `href` of `<g:link>`. Pass those values unencoded. A `url` attribute that the tag writes as it is given is written once.
- When a `RequestDataValueProcessor` changes a value, the tag encodes what the processor returns, so a value encoded beforehand is encoded twice, as `<g:textField>` already did.
- A `title` of `<g:sortableColumn>` that holds markup must be marked with `raw()`.
- A value marked with `raw()`, and the text and `default` of `message()`, which comes back marked raw, are still written as given by the tags that wrote them so before. Do not pass request data through `raw()` or as the `default` of `message()`.
- Tests that compare rendered markup: an `&` in the URL of a form is now written as `&amp;`, as `<g:link>` writes it in an `href`.

These bugs are fixed, which changes what the browser submits or what the page contains:

- `<g:radio>` encodes its `value` once. It encoded it twice, so a value with `&`, `<`, `>`, `"` or `'` was submitted with character references in it, such as `R&amp;D` for `R&D`. Remove any code that decoded the submitted value to undo this.
- A value encoded beforehand is no longer encoded twice in the `value` of `<g:textField>`, `<g:hiddenField>` and the other field tags when a `RequestDataValueProcessor` returns it unchanged, nor in an option of `<g:select>`.
- `<g:paginate>` and `<g:sortableColumn>` write a `class` that was encoded beforehand once next to the classes they add, such as `step` and `sortable`. They encoded it twice.

## GORM for Hibernate: setting for a sort by an association

A sort by a property of an association, such as `Book.list(sort: 'author.name')`, joins the association with an inner join, so it leaves out the instances whose association is `null`, and `count` and the `totalCount` of a paged list do not count them. That is unchanged by default. 7.0.18 adds the `grails.gorm.leftJoinAssociationSort` setting, default `false`. Set to `true`, the association is joined with a left join and the sort keeps those instances.

- Nothing needs to change to keep the previous results.
- Search for `sort:` arguments and `.sort(` calls whose property contains a `.`, such as `sort: 'author.name'`. If the application expects every instance from such a query, set `grails.gorm.leftJoinAssociationSort: true` in `application.yml`.
- With the setting, the instances without the association come first or last, depending on how the database sorts nulls. To leave them out of one query, add a criterion that the association is not `null`.
- A sort by a property of a collection, such as `sort: 'books.title'`, joins the collection with an inner join with or without the setting.
- Explicitly setting it to `false` logs a startup warning naming Grails 9, which removes the setting. The planned default in Grails 8.1 is `true`; check the target release's compatibility settings before upgrading.

## GORM for Hibernate: a join type requested for an association is always applied

With GORM for Hibernate, a where query, a detached criteria, a dynamic finder or a criteria query can request the type of the join of an association, with the `fetch` argument, as in `Book.where { ... }.list(fetch: [author: JoinType.INNER])`, with `join('author', JoinType.INNER)`, or, on a data service method that queries by its parameters, such as `Book find(String title)`, with `@Join(value = 'author', type = JoinType.INNER)`. In a where query, a detached criteria and a dynamic finder, the requested join type was only used when the query also had criteria on the association, and a criteria query never used it. The association was otherwise fetched with an outer join, whatever join type was requested, so the query also returned the instances whose association is `null`. As of Grails 7.0.18, a requested join type other than `JoinType.LEFT` is applied whether or not anything else in the query uses the association.

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

# Grails 7.1.0

The sections of this part come from the guide chapter "Upgrading from Grails 7.0 to Grails 7.1". The first five change what an application does by default, break compilation, or deprecate an API. The rest are additions that change nothing until the application uses them.

## Invokedynamic is disabled by the Grails Gradle plugins

The Grails Gradle plugins disable invokedynamic.

- The Grails Gradle plugins now disable invokedynamic for all `GroovyCompile` tasks.
- Search `build.gradle` for a `tasks.withType(GroovyCompile)` block that sets `indy = false`. It is no longer needed. Remove it:

```groovy
tasks.withType(GroovyCompile).configureEach {
    groovyOptions.optimizationOptions.indy = false
}
```

- New setting: `indy` on the `grails` extension in `build.gradle`. Set it to `true` to compile with invokedynamic again:

```groovy
grails {
    indy = true
}
```

## Plugin loading and plugin configuration

Plugin loading and plugin configuration are split.

- Annotations such as `@ConditionalOnProperty` can now be used with configuration stored in a plugin's `plugin.groovy` or `plugin.yml`. To support this, plugin loading and plugin configuration were split, and the plugin loading code was reworked.
- Search the application for classes that extend `GrailsApplicationPostProcessor` or `DefaultGrailsPluginManager`, and for code that calls their constructors. The constructors changed. A new class, `PluginDiscovery`, was extracted from the plugin manager and is now a dependency of both classes. The guide does not list the new constructor signatures. Take them from the API documentation of the target version.
- An application that relies on its own plugin loader may break. The guide gives no setting that restores the previous plugin loading.

## The `resource` field of `GormService`

The `resource` field of `GormService` is the domain class.

`grails.plugin.scaffolding.GormService` changed to fix a thread-safety issue:

- The type of the `resource` field changed from `GormAllOperations<T>` to `Class<T>`.
- A new field `gormStaticApi` of type `GormAllOperations<T>` provides the GORM operations. It is initialized lazily and thread-safely.
- The constructor no longer instantiates the resource class. It stores the class.

Search the application for classes that extend `GormService` and for code that reads its `resource` field:

- Change GORM calls on `resource` to `gormStaticApi`.
- Where the code needs the domain class, use `resource` itself instead of `resource.getClass()`.

```groovy
class MyService extends GormService<MyDomain> {
    void myMethod() {
        // Before: def result = resource.list()
        def result = gormStaticApi.list()

        // Before: Class<MyDomain> clazz = resource.getClass()
        Class<MyDomain> clazz = resource
    }
}
```

The guide gives no way to keep the previous field type.

## URL mapping wildcard validation

URL mapping variables captured by a wildcard are validated.

- Grails now validates the URL mapping variables `$controller`, `$action` and `$namespace` against the registered controller artefacts at request time. When a captured value does not match a registered artefact, the mapping is skipped and the next mapping is tried.
- Controller and action values that a mapping hardcodes are not affected.
- New setting: `grails.urlmapping.validateWildcards`. The validation is enabled by default.
- Search `UrlMappings` for mappings that use `$controller`, `$action` or `$namespace`, and check requests whose captured value names no registered controller or action. They now fall through to a later mapping.

The validation allows mappings such as:

```groovy
group "/topics", {
    "/$action"(controller: 'topic')              // tried first, only matches real actions
    "/$id"(controller: 'topic', action: 'show')  // fallback for values that are not actions
}
```

To keep the previous matching, disable the validation in `application.yml`:

```yaml
grails:
    urlmapping:
        validateWildcards: false
```

## `ContainerGebSpec` and the context path

`ContainerGebSpec` includes the context path in the base URL.

- `ContainerGebSpec` now appends the servlet context path of the server to the browser's base URL. The context path is looked up from the Spring `Environment` at test setup time. Previously the base URL held only the protocol, hostname and port, so with `server.servlet.context-path` set, page navigation missed the context path and ended in 404 errors.
- Search `application.yml` for `server.servlet.context-path`. If it is set, search the functional tests that extend `ContainerGebSpec` and their Geb pages for URLs.
- Page URLs stay relative to the context root, such as `static url = 'greeting'`. Do not include the context path in them. With the context path `/myapp`, `to(GreetingPage)` resolves to `http://host:port/myapp/greeting`.
- The guide gives no setting that restores the previous base URL.

## Audit annotations

`@AutoTimestamp` is deprecated in favor of audit annotations.

- New annotations in `grails.gorm.annotation`: `@CreatedDate` (creation timestamp, set on insert), `@LastModifiedDate` (modification timestamp, set on insert and update), `@CreatedBy` (current auditor, set on insert) and `@LastModifiedBy` (current auditor, set on insert and update). The date annotations work with `Date`, `LocalDateTime`, `Instant`, `OffsetDateTime` and `ZonedDateTime`.
- `@AutoTimestamp` is marked `@Deprecated(forRemoval = true)` and will be removed in Grails 8.0. It still works in Grails 7.1.

Search the domain classes for `@AutoTimestamp` and migrate:

1. Replace `@AutoTimestamp(AutoTimestamp.EventType.CREATED)` with `@CreatedDate`.
2. Replace `@AutoTimestamp`, or `@AutoTimestamp(EventType.UPDATED)`, with `@LastModifiedDate`.
3. Optionally add `@CreatedBy` and `@LastModifiedBy` fields for auditor tracking.
4. Implement and register an `AuditorAware` bean if the application uses auditor fields.
5. Remove `nullable: true` constraints from timestamp fields. They are no longer needed.
6. Keep `nullable: true` on auditor fields. Operations without a current auditor need it.

```groovy
// Deprecated
@AutoTimestamp(AutoTimestamp.EventType.CREATED)
LocalDateTime created

@AutoTimestamp
LocalDateTime modified

// Replacement
@CreatedDate
LocalDateTime created

@LastModifiedDate
LocalDateTime modified
```

For auditor fields:

- Implement `org.grails.datastore.gorm.timestamp.AuditorAware<T>`. Its method `Optional<T> getCurrentAuditor()` returns the current auditor, or `Optional.empty()` when there is none, for example for an anonymous user. Register the implementation as a Spring bean, for example with a `@Bean` method in `Application.groovy`.
- An auditor field can be a `String` for a username, a `Long` for a user id, or a custom type.
- Unlike timestamp fields, auditor fields can have constraints, such as `maxSize: 100`.

Two opt-in settings on the `grails` extension in `build.gradle` reduce imports:

- `importGrailsCommonAnnotations = true` imports `jakarta.validation.constraints.*`, `grails.gorm.annotation.*` and, when `grails-scaffolding` is on the classpath, `grails.plugin.scaffolding.annotation.*` automatically.
- `starImports = ['java.util.concurrent', 'groovy.transform', 'com.myapp.annotations']` adds star imports for the listed packages. It is combined with the imports of `importGrailsCommonAnnotations` and `importJavaTime` when those are enabled.

## Greedy extension marker in URL mappings

URL mappings have a greedy extension marker.

- Without the marker, a mapping with an optional extension, `"/$id(.$format)?"`, splits at the first dot: `/test.test.json` gives `id` = `test` and `format` = `test.json`. This is unchanged.
- The new `+` marker after the variable name splits at the last dot: `"/$id+(.$format)?"` gives `id` = `test.test` and `format` = `json`. For an optional parameter write `"/$id+?(.$format)?"`.
- A URL without a dot matches entirely as the parameter, with no format.
- The marker is opt-in for each mapping. Search `UrlMappings` for `(.$format)?` mappings whose ids contain dots, such as file names, qualified class names or version numbers, and add the marker where the last dot should separate the extension.

## Scaffolding namespace view defaults

Scaffolding can prefer namespace-specific scaffolded templates.

- New setting: `grails.scaffolding.enableNamespaceViewDefaults`. The default is `false`, which keeps the previous view resolution.
- When it is `true`, a namespace controller resolves a view in this order: the namespace-specific view (`grails-app/views/admin/event/index.gsp`), the namespace-specific scaffolded template (`src/main/templates/scaffolding/admin/index.gsp`), the non-namespaced view (`grails-app/views/event/index.gsp`), the non-namespaced scaffolded template (`src/main/templates/scaffolding/index.gsp`).
- Previously a scaffolded view was generated only when no view existed at all, so the non-namespaced view won over the namespace-specific scaffolded template.

## The `display` constraint and `DisplayType`

The `display` constraint accepts a `DisplayType`.

- `grails.gorm.validation.DisplayType` has the values `ALL` (display in all views, also for properties such as `dateCreated` and `lastUpdated` that scaffolding leaves out by default), `NONE` (never display), `INPUT_ONLY` (create and edit forms only) and `OUTPUT_ONLY` (show and index views only).
- Boolean values work as before. `display: false` is equivalent to `DisplayType.NONE`. No change is required.

## The `@Scaffold` annotation

`@Scaffold` is the preferred way to enable scaffolding.

- The legacy `static scaffold = Book` syntax is still supported. No change is required.
- `grails.plugin.scaffolding.annotation.Scaffold` works on controllers and on services: `@Scaffold(Book)`.
- `@Scaffold(RestfulServiceController<Book>)` on a controller delegates to the service. `@Scaffold(MyCustomService<Book>)` extends a custom class. `@Scaffold(domain = Book, readOnly = true)` scaffolds read-only.
- New CLI commands: `grails create-scaffold-controller Book`, `grails create-scaffold-service Book` and `grails generate-scaffold-all Book`. They support `--extends`, `--namespace` and `--service`.

## URL mapping group defaults

URL mapping groups accept defaults.

- `group` accepts default parameters that all child mappings inherit: `group "/api", namespace: 'api', controller: 'resource', { "/list"(action: 'list') }`.
- Nested groups merge defaults. An inner group inherits the defaults of the outer group and can extend or override them. A child mapping can override any default.
- Existing URL mappings are not affected.

## The `<g:flashMessages />` tag

A `<g:flashMessages />` tag renders flash messages.

- The tag renders `flash.message`, `flash.error` and `flash.warning` as Bootstrap 5 dismissible alerts, styled as success, danger and warning. The content is HTML-encoded. When a page and its layout both call the tag, only the first call produces output.
- The default layout (`main.gsp`) and all scaffolding templates now use the tag. This matters when comparing the application with a freshly generated one.
- Custom views that render flash messages by hand keep working. They can replace a block such as `<g:if test="${flash.message}"><div class="alert alert-primary" role="alert">${flash.message}</div></g:if>` with `<g:flashMessages />`.
- The tag has optional attributes for CSS classes, icons, the ARIA role and dismissibility. An application that does not use Bootstrap can override the defaults under `grails.views.gsp.flashMessages` in `application.yml`, with the keys `messageClass`, `messageIcon`, `errorClass`, `errorIcon`, `warningClass` and `warningIcon`.

# Grails 7.1.2

The sections of this part come from the guide chapter "Upgrading from Grails 7.0 to Grails 7.1".

## Exception log line with per-request context

The exception log line can include per-request context.

- `GrailsExceptionResolver` can append a parenthesised clause with the current auditor and the remote client address to its exception log headline:

```
RuntimeException occurred when processing request: [GET] /admin/users/42 (ip: 198.51.100.42, user: alice)
```

- New settings, both disabled by default: `grails.exceptionresolver.logAuditor` adds `user: <value>` when an `AuditorAware` bean is registered, and `grails.exceptionresolver.logRemoteAddr` adds `ip: <value>`. With both disabled the headline has the form it had before 7.1.
- The auditor is resolved through the same `AuditorAware` bean that populates `@CreatedBy` and `@LastModifiedBy` fields (see "Audit annotations" in "Grails 7.1.0").
- The address is `HttpServletRequest.getRemoteAddr()`. Behind a reverse proxy, set `server.forward-headers-strategy: native` so that it is the address of the real client.
- Before enabling either setting, search log parsers, alert rules and log-aggregation queries that match the exact headline. They have to tolerate the ` (ip: <value>, user: <value>)` suffix. User identifiers are typically personal data; enabling `logAuditor` writes them into exception logs.

```yaml
grails:
    exceptionresolver:
        logAuditor: true
        logRemoteAddr: true
```

## Stack trace filtering and the `StackTrace` logger

Stack trace filtering emits to the `StackTrace` logger under a setting.

- `DefaultStackTraceFilterer.filter(Throwable)` emits the unfiltered stack trace to the `StackTrace` logger as a side effect of cleaning the trace, as it did before 7.1. Code paths outside the exception resolver rely on this, such as a scheduled job that calls `GrailsUtil.sanitizeRootCause(ex)` before it logs through its own logger.
- New setting: `grails.exceptionresolver.logFullStackTraceOnFilter`. The default is `true`, which keeps the emission. Set it to `false` to disable the emission and rely on `logFullStackTrace` alone for the output of the exception resolver.
- The two settings interact. With both enabled, a request exception with N causes produces N+1 `StackTrace` records: one from the resolver and one for each throwable the filter visits.
- Search the logging configuration for the `StackTrace` logger and for tooling that counts or parses its records. The "Logging Full Stack Traces" section of the guide has a matrix of the four combinations.

# Grails 7.2.0

## SiteMesh 3 integration

The SiteMesh 3 integration no longer uses a servlet filter.

This entry comes from the guide chapter "Upgrading from Grails 7.1 to Grails 7.2". It applies only to an application that uses the SiteMesh 3 integration, the `grails-sitemesh3` plugin. The `grails-layout` plugin (SiteMesh 2) remains supported in Grails 7.2 and honors its existing configuration. If the application depends on `grails-layout`, leave its properties in place.

What changed:

- Decoration is performed by a Spring MVC `ViewResolver`, not by a servlet filter.
- The `SiteMeshFilter` servlet filter is no longer active. A placeholder bean is registered under the name `sitemesh`, so that SiteMesh's auto-configuration does not activate the filter-based integration.
- GSP pages capture the head, body and title at render time through the `grailsLayout` capture tag library. SiteMesh no longer parses the raw HTML response to extract them.

Required actions:

- For most applications no change is required. Layouts, `<g:layoutHead>`, `<g:layoutTitle>`, `<g:layoutBody>`, `<g:pageProperty>`, `<g:applyLayout>` and layout-by-convention behave as in the previous release.
- Search `application.yml` and the other configuration files for `grails.gsp.view.layoutViewResolver`, `sitemesh.filter.order` and `sitemesh.decorator.tagRuleBundles`. The SiteMesh 3 integration no longer consults them. Remove them, unless the application uses `grails-layout`.
- The guide gives no setting that restores the filter-based integration.

Default layout:

- With the SiteMesh 3 integration the default layout is still configured with `grails.sitemesh.default.layout`. The `grails.views.layout.default` property listed under "Layouts" in "Grails 7.0.0" belongs to `grails-layout`.

```yaml
grails:
    sitemesh:
        default:
            layout: application
```

Layout cache:

- In non-development environments, layout paths are cached by controller and action pair.
- New setting: `grails.sitemesh.layout.cache.interval`, the time to live of the cache in milliseconds. The default is `5000`. It is independent of the GSP reload interval.
- Search the configuration for `grails.gsp.reload.interval`. An application that relied on it for layout cache expiration, as snapshots before 7.2 did, sets the new key instead:

```yaml
grails:
    sitemesh:
        layout:
            cache:
                interval: 5000
```

Async controllers:

- Controller actions that return a `Callable` or use `@Async` are decorated correctly. Previously async dispatch could run the decoration on a different thread from the one that populated the SiteMesh request scope, which led to empty layouts or stale content.
- Search the controllers for actions that return `Callable` or use `@Async`, and check their pages after the upgrade.

Content blocks:

- Content blocks work as before. A page declares `<content tag="sidebar">...</content>` and the layout reads it with `<g:pageProperty name="page.sidebar" />`.
- The GSP preprocessor maps a content block to `<grailsLayout:captureContent tag="...">` at compile time, and the captured buffers are passed to the layout without another HTML parse.

# After the Upgrade

## Verification Checklist

Run verification through public application behavior, not only compilation.

- `gradle/wrapper/gradle-wrapper.properties` points to the intended Gradle version, and the build runs on Java 17 or later.
- `gradle.properties` sets `grailsVersion` and no longer sets `gormVersion`, `grailsGradlePluginVersion` or `groovyVersion`.
- No Gradle file contains a coordinate that `RENAME.md` lists as renamed, and no dependency that the Grails BOM manages carries an explicit version without a reason.
- `./gradlew build` succeeds, including the tests. A failure of `FindMainTask` means no `Application` class was found.
- The compiler output contains no data binding warning that asks for a parent class or a command object to be recompiled.
- No application source imports a `javax.` package that moved to `jakarta.`.
- The application starts without `NoClassDefFoundError` or `ClassNotFoundException`. For the Hibernate second-level cache and for Quartz, see step 12 of "Grails 7.0.0" and "Grails 7.0.16".
- URL mappings resolve as before. Incorrect resolution can mean that the compile configuration tasks did not run or were cached incorrectly.
- Exercise pages that use layouts, multipart uploads with many parts, and JSON and XML endpoints that render dates, times and enums. Compare the output with what API clients expect.
- Exercise requests that interceptors with `uri` matching should and should not intercept, with the deployed context path, including encoded and matrix-parameter variants of a protected path.
- With multi-tenancy, exercise code that runs without a tenant, lookups by id across tenants, and the tenant resolver in development.
- Exercise binding of command objects, nested `Validateable` objects and entities compiled outside `grails-app/domain`, with both allowed and protected properties.
- Run the functional tests with a container runtime available when they extend `ContainerGebSpec`.
- With the Spring Security REST plugin, exercise an OAuth login that passes a `callback` parameter, and with encrypted JWTs a login, a token refresh and a request with a token issued before the upgrade, which must be rejected.
- `build.gradle` has no `tasks.withType(GroovyCompile)` block that sets `indy = false`.
- No application class extends `GrailsApplicationPostProcessor` or `DefaultGrailsPluginManager` with a constructor call that no longer compiles, and no code calls GORM operations on the `resource` field of a `GormService`.
- Exercise URL mappings that capture `$controller`, `$action` or `$namespace`, with values that name a controller or action and with values that do not.
- With `server.servlet.context-path` set, the `ContainerGebSpec` tests navigate to pages under the context path.
- Log parsers and alert rules still match the exception log headline and the records of the `StackTrace` logger.
- With the SiteMesh 3 integration, exercise pages that use layouts, the default layout, content blocks, and actions that return a `Callable` or use `@Async`.
- With Micronaut, confirm that the build applies `grails-micronaut-bom` as `enforcedPlatform` and that `java -jar` starts the packaged application.
