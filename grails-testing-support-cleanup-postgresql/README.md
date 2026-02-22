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

## grails-testing-support-cleanup-postgresql

Provides the PostgreSQL database cleanup implementation for the `DatabaseCleaner` SPI, enabling automatic table truncation in PostgreSQL-backed integration tests.

### Required Libraries

To use PostgreSQL database cleanup in your integration tests, add the following dependencies:

```gradle
// PostgreSQL JDBC Driver
testImplementation 'org.postgresql:postgresql'

// Groovy SQL DSL (for PostgresDatabaseCleaner)
implementation 'org.apache.groovy:groovy-sql'

// Optional: For functional tests with Docker containers
testImplementation 'org.testcontainers:postgresql:1.20.1'
testImplementation 'org.testcontainers:testcontainers:1.20.1'
```

### How It Works

The PostgreSQL cleanup implementation:
1. Detects PostgreSQL databases via JDBC URL pattern: `jdbc:postgresql:*`
2. Determines the schema to clean (from `currentSchema` parameter or all non-system schemas)
3. Disables all triggers and constraints at session level: `SET session_replication_role = replica`
4. Truncates all tables in the target schema(s)
5. Re-enables triggers and constraints: `SET session_replication_role = DEFAULT`
6. Records table row counts before cleanup

This approach is more efficient than using `TRUNCATE TABLE ... CASCADE` because:
- Single session-level command disables all constraints at once
- No need to compute cascade order for foreign keys
- Handles complex relationships (self-referencing FKs, circular dependencies) seamlessly

### Schema Cleanup Behavior

- **If `currentSchema` is set in JDBC URL**: Only tables in the specified schema are cleaned
- **If `currentSchema` is not set**: All non-system schemas are cleaned (excluding `pg_catalog`, `information_schema`, `pg_toast`, `pg_temp_*`)

### Example JDBC URLs

```
jdbc:postgresql://localhost/testdb
jdbc:postgresql://localhost:5432/testdb?currentSchema=myschema
jdbc:postgresql://db.example.com/prod?user=postgres&password=secret&currentSchema=test
```

### Functional Tests

Functional tests requiring Docker can be run with:

```bash
./gradlew test --tests "PostgresDatabaseCleanerFunctionalSpec"
```

These tests use TestContainers to start a real PostgreSQL database and verify cleanup behavior with:
- Actual foreign key constraints
- Multiple schemas
- Complex relationship hierarchies (self-referencing, circular dependencies)

### Automatic Discovery

This implementation is automatically discovered and applied when PostgreSQL is used as the datasource. No manual registration is required.
