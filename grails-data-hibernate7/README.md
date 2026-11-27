<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# GORM for Hibernate 7

This project implements [GORM](https://gorm.grails.org) for Hibernate 7.

With the removal of the Criteria API in Hibernate 7, we wanted to continue to support the DetachedCriteria in GORM as much as possible. We also wanted to encapsulate the JPA Criteria Building in one class so the following was done:

* DetachedCriteria holds almost all the state of the Query being built. It holds the target class for the query. It does not hold a session.
* HibernateQuery has a session and holds the DetachedCriteria and is a thin wrapper for it. Calling list or singleResult will internally create the Query and execute it. 
* HibernateCriteriaBuilder is a thin wrapper around HibernateQuery. Its main function is to use closures to populate the Hibernate Query and execute it at the end of the closure.
* The core Hibernate 7 implementation is split across the modules listed below.

For testing the following was done:

* Used testcontainers for specific tests instead of h2 to verify features not supported by h2.
* A more opinionated and fluent HibernateGormDatastoreSpec is used for the specifications.

## Module Structure

| Module | Description |
|---|---|
| `grails-data-hibernate7-core` | Domain binding pipeline, GORM/Hibernate mapping, `HibernateDatastore` |
| `grails-data-hibernate7-spring-orm` | Shared Spring ORM / Hibernate integration support used by the core, Spring Boot, and Grails plugin modules |
| `grails-data-hibernate7-spring-boot` | Spring Boot autoconfiguration (`HibernateGormAutoConfiguration`) and Grails CLI SPI (`GormCompilerAutoConfiguration`) |

## Autoconfiguration

### `HibernateGormAutoConfiguration` (Spring Boot)

Bootstraps `HibernateDatastore`, `SessionFactory`, and `PlatformTransactionManager` from any available `DataSource` bean.
Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### `GormCompilerAutoConfiguration` (Grails CLI)

A Grails CLI SPI hook (`org.grails.cli.compiler.CompilerAutoConfiguration`) that detects `@Entity` classes in Grails scripts and automatically adds the `grails-data-hibernate7-core` dependency and `grails.gorm.*` imports to the compilation context.
Registered via `META-INF/services/org.grails.cli.compiler.CompilerAutoConfiguration`.

## Running the suite with the classic domain binder (contributors)

Native domain binding is the default (`hibernate.generatedDomainClasses` defaults to `true`), so the core suite boots every datastore the
TCK manager builds natively. `./gradlew -Pgrails.test.classicDomainBinding=true :grails-data-hibernate7-core:test` boots them through the
deprecated classic binder instead (the Gradle property sets the JVM property `grails.hibernate.classicDomainBinding`, which the settings
default and the TCK manager read; a spec that sets `hibernate.generatedDomainClasses` itself is not affected). The specs listed in
`core/classic-only-specs.txt` assert facts of the classic boot model and run only in that mode; the default run excludes them. Both modes
are wired in `gradle/hibernate7-test-config.gradle`.

## Differential specs and the frozen classic oracle (contributors)

Native domain binding (`hibernate.generatedDomainClasses`, `GrailsDomainGenerator`) is checked against what the classic domain binder
(`GrailsDomainBinder`) produces for every `@Entity` in the TCK and in the Hibernate 7 tests, by two specs in
`core/src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/jpa`:

* `GrailsDomainGeneratorDifferentialSpec` compares the facets the generator decides, and what Hibernate's annotation binder reads back from
  the generated classes, with the facts the classic binder bound (per entity and property); it writes `build/differential-report.txt`.
* `GeneratedDomainClassesDdlDifferentialSpec` boots every group of associated domain classes in the generated mode on H2 and compares the
  schema (tables, columns, keys, indexes, checks, sequences, and H2's `SCRIPT NODATA`) with the schema the classic binder derived; it
  writes `build/ddl-report.txt`.

The classic side of both comparisons is **recorded data**, not a live classic binder: `core/src/test/resources/classic-oracle/`
(`generator-differential.txt`, `ddl-differential.txt`). `ClassicOracle` documents the format (one record per line, sorted keys, nothing that
varies from run to run) and has three modes:

| Mode | How | What happens |
|---|---|---|
| FROZEN (default) | `./gradlew :grails-data-hibernate7-core:test --tests '*DifferentialSpec'` | The classic binder is not booted. The recorded files are the oracle. A scanned fixture with no recorded group fails with a message that says to refreeze. |
| VERIFY | add `-Pgrails.test.verifyClassicOracle=true` | The classic binder is booted too and what it produces must equal the recorded files exactly (the freeze is faithful and current); the comparison then runs as in FROZEN. |
| REFREEZE | add `-Pgrails.test.refreezeClassicOracle=true` | The classic binder is booted and the files are rewritten. Review and commit the diff: a changed record is a change in what the classic binder does, or a new or changed fixture. |

Run VERIFY after changing a fixture, classic binder code or Hibernate, and REFREEZE only to record that change. The `KNOWN`
list of the DDL spec states why each remaining difference between native and classic binding is kept; it is not part of the recording.
`ClassicOracleSpec` and the "changed recorded fact" features of the two differential specs prove that the serialisation is stable, that
the files cover every scanned fixture, and that changing a recorded fact makes the comparison fail.

The classic binder is internal code that is being retired. When it is deleted, the VERIFY and REFREEZE modes go with it, the recorded files
become plain expectations, and a new fixture gets its records written by hand (copy the records of a similar fixture into the group of the
new one, in the format `ClassicOracle` reads).

## Using GORM Without Grails

### Strategy: Write Domain Classes in Groovy

The recommended integration strategy for any JVM project (Java, Kotlin, Scala) is to write domain/entity classes in Groovy and use `HibernateCriteriaBuilder` for queries. This works because:

- **GORM AST transforms run at Groovy compile time.** `GormEntityTransformation` weaves all dynamic finders, `where {}`, `list()`, `get()`, `save()`, etc. into the compiled `.class` files as real JVM bytecode methods.
- **The resulting `.class` files are standard JVM bytecode.** Java and Kotlin callers consume them like any other class — `Book.findByTitle("GORM")` is just a static method call.
- **`HibernateCriteriaBuilder` provides a powerful query DSL** via Groovy closures. Kotlin callers can use SAM conversions; Java callers can use `DetachedCriteria` directly.

A typical mixed-language Gradle project layout:

```
myapp/
  domain/          ← Groovy subproject (compiled with grails-data-hibernate7-core on classpath)
    src/main/groovy/
      Book.groovy  ← @grails.gorm.annotation.Entity — AST injects all GORM methods at compile time
  service/         ← Java or Kotlin subproject, depends on :domain
    src/main/java/
      BookService.java  ← calls Book.list(), Book.findByTitle(), new Book(title:"X").save()
```

### Feature Availability by Language

| Feature | Groovy | Kotlin / Java |
|---|---|---|
| Spring Boot autoconfiguration | ✅ | ✅ |
| `HibernateDatastore` CRUD API | ✅ | ✅ |
| Dynamic finders (`findBy*`) on Groovy entities | ✅ | ✅ (compiled-in bytecode) |
| `where {}` criteria DSL | ✅ | via `DetachedCriteria` API |
| `HibernateCriteriaBuilder` closures | ✅ | Kotlin SAM / Java `Closure` |
| Defining new entities in Kotlin/Java | ❌ (no AST) | ❌ (no AST) |

The only limitation is that entity *definitions* must be Groovy to benefit from the GORM trait injection. Code that *calls* GORM entities can be in any JVM language.

### Publishing as a Standalone Library

The `grails-data-hibernate7-core` module has minimal coupling to the Grails framework. To publish it for use outside Grails, the main remaining tasks are:

1. Add BOM coordinates, Javadoc/sources JARs, and POM metadata for Maven Central publication
2. Write integration tests validating end-to-end use from a plain Spring Boot app

