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

## grails-testing-support-cleanup-core

Provides the core database cleanup testing support for Grails integration tests, including the `@DatabaseCleanup` annotation and the `DatabaseCleaner` SPI.

### Supported Database Implementations

Database cleanup is automatically discovered and applied based on your datasource configuration. The following database implementations are available:

#### H2 Database (`grails-testing-support-cleanup-h2`)

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

#### PostgreSQL (`grails-testing-support-cleanup-postgresql`)

**Supported Driver**: PostgreSQL JDBC Driver

**Libraries**:
- `org.apache.groovy:groovy-sql` - SQL DSL for Groovy
- `org.postgresql:postgresql` - PostgreSQL JDBC Driver
- `org.testcontainers:postgresql:1.20.1` (test scope)
- `org.testcontainers:testcontainers:1.20.1` (test scope)

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
