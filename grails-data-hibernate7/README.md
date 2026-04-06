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
| `grails-data-hibernate7-boot-plugin` | Spring Boot autoconfiguration (`HibernateGormAutoConfiguration`) and Grails CLI SPI (`GormCompilerAutoConfiguration`) |

## Autoconfiguration

### `HibernateGormAutoConfiguration` (Spring Boot)

Bootstraps `HibernateDatastore`, `SessionFactory`, and `PlatformTransactionManager` from any available `DataSource` bean.
Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### `GormCompilerAutoConfiguration` (Grails CLI)

A Grails CLI SPI hook (`org.grails.cli.compiler.CompilerAutoConfiguration`) that detects `@Entity` classes in Grails scripts and automatically adds the `grails-data-hibernate7-core` dependency and `grails.gorm.*` imports to the compilation context.
Registered via `META-INF/services/org.grails.cli.compiler.CompilerAutoConfiguration`.

## Using GORM Without Grails

### Groovy + Spring Boot (low effort)

`HibernateGormAutoConfiguration` already provides full Spring Boot autoconfiguration outside of Grails. A plain Spring Boot application can use GORM by adding `grails-data-hibernate7-boot-plugin` as a dependency and providing a `DataSource` bean. The `@jakarta.persistence.Entity` Groovy AST transform (`GlobalJpaEntityTransform`) bridges JPA-annotated Groovy classes into full GORM entities with dynamic finders, criteria, and all GORM traits at compile time.

### Java / Kotlin (medium–high effort)

`HibernateDatastore` CRUD and query API is usable directly from Java and Kotlin today. However, GORM's dynamic finders (`findBy*`, `where {}`) and trait-based API are injected via Groovy AST transforms at compile time — they are not available to Java or Kotlin classes without additional tooling:

| Feature | Groovy | Kotlin | Java |
|---|---|---|---|
| Spring Boot autoconfiguration | ✅ | ✅ | ✅ |
| `HibernateDatastore` CRUD API | ✅ | ✅ | ✅ |
| Dynamic finders (`findBy*`) | ✅ (AST transform) | ❌ requires compiler plugin | ❌ requires annotation processor |
| `where {}` criteria DSL | ✅ | ❌ | ❌ |
| `@jakarta.persistence.Entity` auto-detection | ✅ (`GlobalJpaEntityTransform`) | ❌ | ❌ |

Kotlin support would require a Kotlin compiler plugin (analogous to `kotlin-allopen`) to inject GORM traits. Java support would require an annotation processor for codegen.

### Publishing as a Standalone Library

The `grails-data-hibernate7-core` module has minimal coupling to the Grails framework. To publish it for use outside Grails, the main remaining tasks are:

1. Populate the `AutoConfiguration.imports` file in `boot-plugin` with `HibernateGormAutoConfiguration`
2. Populate the CLI service file in `boot-plugin` with `GormCompilerAutoConfiguration`
3. Migrate `javax.sql.DataSource` → `jakarta.sql.DataSource` in `HibernateGormAutoConfiguration`
4. Add BOM coordinates, Javadoc/sources JARs, and POM metadata for Maven Central publication
5. Write integration tests validating end-to-end use from a plain Spring Boot app






