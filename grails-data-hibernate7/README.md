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
This project implements [GORM](https://gorm.grails.org) for the Hibernate 7.

With the removal of Criterion API in Hibernate 7, we wanted to continue to support the DetachedCriteia in GORM as much as possible. We also wanted to encapsulate the JPA Criteria Building in one class so the following was done:
* DetachedCriteria holds almost all the state of the Query being built. It hold the target class for the query. It does not hold a session.
* HibernateQuery has a session and holds the DetachedCriteria and is a thin wrapper for it. Calling list or singleResult will internally create the Query and execute it. 
* HibernateCriteriaBuilder is a thin wrapper around HibernateQuery. Its main function is to use closures to populate the Hibernate Query and execute it at the end of the closure.
* Only the grails-datastore-gorm-hibernate7 module is being developed at the time.

For testing the following was done:
* Used testcontainers for specific tests instead of h2 to verify features not supported by h2.
* A more opinionated and fluent HibernateGormDatastoreSpec is used for the specifications.

## Module Structure

| Module | Description |
|---|---|
| `grails-data-hibernate7-core` | Domain binding pipeline, GORM/Hibernate mapping, `HibernateDatastore` |
| `grails-data-hibernate7-spring-orm` | Shared Spring ORM / Hibernate integration support used by the core, boot-plugin, and Grails plugin modules |
| `grails-data-hibernate7-boot-plugin` | Spring Boot autoconfiguration (`HibernateGormAutoConfiguration`) and Grails CLI SPI (`GormCompilerAutoConfiguration`) |

## Autoconfiguration

### `HibernateGormAutoConfiguration` (Spring Boot)

Bootstraps `HibernateDatastore`, `SessionFactory`, and `PlatformTransactionManager` from any available `DataSource` bean.
Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### `GormCompilerAutoConfiguration` (Grails CLI)

A Grails CLI SPI hook (`org.grails.cli.compiler.CompilerAutoConfiguration`) that detects `@Entity` classes in Grails scripts and automatically adds the `grails-data-hibernate7-core` dependency and `grails.gorm.*` imports to the compilation context.
Registered via `META-INF/services/org.grails.cli.compiler.CompilerAutoConfiguration`.

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






