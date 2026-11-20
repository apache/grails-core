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

## grails-testing-support-dbcleanup-core

Provides the core database cleanup testing support for Grails integration tests, including the `@DatabaseCleanup` annotation and the `DatabaseCleaner` SPI.

### Usage

#### Basic Usage

Apply `@DatabaseCleanup` at the class level to clean all datasources after every test method:

```groovy
@DatabaseCleanup
class MyIntegrationSpec extends Specification { ... }
```

Apply at the method level to clean only after specific test methods:

```groovy
class MyIntegrationSpec extends Specification {
    @DatabaseCleanup
    void "test that modifies the database"() { ... }
}
```

#### Specifying Datasources

Clean only specific datasources (auto-discover cleaners):

```groovy
@DatabaseCleanup(['dataSource', 'dataSource_secondary'])
class MySpec extends Specification { ... }
```

Clean specific datasources with explicit cleaner types:

```groovy
@DatabaseCleanup(['dataSource:h2', 'dataSource_pg:postgresql'])
class MySpec extends Specification { ... }
```

#### Deferred Cleanup (cleanupAfterSpec)

By default, database cleanup runs after each test method. Use `cleanupAfterSpec = true` to defer cleanup until after the entire spec finishes. This is useful when test methods build on shared data or when per-test cleanup is too expensive:

```groovy
@DatabaseCleanup(cleanupAfterSpec = true)
class MySpec extends Specification {
    void "first test creates data"() { ... }
    void "second test uses data from first test"() { ... }
    // Database is cleaned once after both tests complete
}
```

**Note:** `cleanupAfterSpec` is only valid on class-level annotations. Using it on a method-level annotation will throw an `IllegalStateException`.

#### Custom ApplicationContext Resolver

```groovy
@DatabaseCleanup(resolver = MyCustomResolver)
class MySpec extends Specification { ... }
```

### Supported Database Implementations

Database cleanup is automatically discovered and applied based on your datasource configuration. The following database implementations are available:

#### H2 Database (`grails-testing-support-dbcleanup-h2`)

**Supported Driver**: H2 Database Engine

**Libraries**:
- `org.apache.groovy:groovy-sql` - SQL DSL for Groovy
- `com.h2database:h2` (test scope)

**Features**:
- Automatic table truncation for H2-backed tests
- Works with in-memory and file-based databases
- Uses `SET REFERENTIAL_INTEGRITY FALSE` for constraint handling

**JDBC URL Format**:
```
jdbc:h2:mem:testdb
jdbc:h2:file:/tmp/testdb
```

#### PostgreSQL (`grails-testing-support-dbcleanup-postgresql`)

**Supported Driver**: PostgreSQL JDBC Driver

**Libraries**:
- `org.apache.groovy:groovy-sql` - SQL DSL for Groovy
- `org.postgresql:postgresql` - PostgreSQL JDBC Driver
- `org.testcontainers:testcontainers-postgresql` (test scope)
- `org.testcontainers:testcontainers` (test scope)

**Features**:
- Automatic table truncation for PostgreSQL-backed tests
- Schema-aware cleanup (single schema or all non-system schemas)
- Uses `session_replication_role = replica` for efficient constraint handling
- Supports both local and containerized PostgreSQL instances

**JDBC URL Format**:
```
jdbc:postgresql://localhost/testdb
jdbc:postgresql://localhost:5432/testdb?currentSchema=myschema
jdbc:postgresql://db.example.com/prod?user=postgres&password=secret
```

**Schema Cleanup Behavior**:
- **If `currentSchema` is set in JDBC URL**: Only tables in the specified schema are cleaned
- **If `currentSchema` is not set**: All non-system schemas are cleaned (excluding `pg_catalog`, `information_schema`, `pg_toast`)

### Adding Support for Additional Databases

To add database cleanup support for a new database:

1. Create a new module: `grails-testing-support-cleanup-{database}`
2. Implement the `DatabaseCleaner` SPI interface
3. Register via ServiceLoader (place in `META-INF/services/org.apache.grails.testing.cleanup.core.DatabaseCleaner`)
4. Write unit tests (mocking datasource behavior)
5. Write functional tests (using TestContainers for real database instances)
