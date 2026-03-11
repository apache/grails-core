# Database Migration Issues (Hibernate 7)

This document tracks technical challenges, API incompatibilities, and known test failures discovered during the development of the `grails-data-hibernate7-dbmigration` module.

---

## ✅ RESOLVED

### Snapshot Generator Specs — GORM Entity Recognition
- **Affected Tests:** `ForeignKeySnapshotGeneratorSpec`, `PrimaryKeySnapshotGeneratorSpec`, `TableSnapshotGeneratorSpec`, `IndexSnapshotGeneratorSpec`, `UniqueConstraintSnapshotGeneratorSpec`, `SequenceSnapshotGeneratorSpec`
- **Root Cause:** `HibernateMappingContext.createPersistentEntity()` only creates persistent entities for classes where `GormEntity.class.isAssignableFrom(javaClass)`. The original test entities were plain Java/Groovy classes annotated with `@jakarta.persistence.Entity`, which are silently skipped by GORM — resulting in empty Hibernate metadata (no table mappings).
- **Fix:** Replaced all inner-static-class entities (Jakarta `@Entity`) and `com.example.ejb3.auction` Java imports with top-level GORM `@grails.gorm.annotation.Entity` classes defined in the same `.groovy` spec file. All 6 tests now pass.

### Envers Initialization
- **Issue:** `org.hibernate.HibernateException: Expecting EnversService to have been initialized prior to call to EnversIntegrator#integrate`.
- **Fix:** Explicitly disabled Envers in test config: `hibernate.integration.envers.enabled: false`.

---

### `TableGeneratorSnapshotGeneratorSpec` — Real GORM Entity via `HibernateSnapshotIntegrationSpec`
- **Issue:** `TableGenerator.getTableName()` in Hibernate 7 is not interceptable by Spock's `Stub()`/`GroovyStub()` — the method accesses `this.qualifiedTableName` which is only set after `configure()` + `registerExportables()`. `BasicValue.getGenerator()` was also removed in Hibernate 7, breaking the original approach.
- **Fix:** Extended `HibernateSnapshotIntegrationSpec`, added a top-level GORM `@Entity TableGeneratorEntity` with `id generator: 'table'`, and retrieved the real `GrailsTableGenerator` via `datastore.sessionFactory.getMappingMetamodel().getEntityDescriptor(TableGeneratorEntity.name).getGenerator()`. Assertions use `tableGenerator.getTableName()` / `tableGenerator.getSegmentColumnName()` / `tableGenerator.getValueColumnName()` so they remain correct regardless of default param values.

---

## ℹ️ BACKGROUND / OTHER NOTES

### `BasicValue.getGenerator()` Removal (Hibernate 7)
- `org.hibernate.mapping.BasicValue.getGenerator()` was removed in Hibernate 7.
- Any code that previously extracted a generator from mapping metadata this way must be rewritten using `createGenerator(Dialect, RootClass, ...)` or by iterating namespace exportables.

### `JdbcDatabaseSnapshot` Constructor
- Liquibase 4.x requires `JdbcDatabaseSnapshot(DatabaseObject[] examples, Database database)`. Tests use the correct constructor.

### Testcontainers / Docker Dependency
- Integration tests require Docker (PostgreSQL container). Tests use `@Requires({ isDockerAvailable() })` to skip when Docker is absent.
- Checks both `~/.docker/run/docker.sock` and `/var/run/docker.sock`.

### `GormDatabase` Service Loading
- Liquibase's `DatabaseFactory` tries to instantiate all registered `Database` implementations via no-arg constructor. `GormDatabase` requires a `HibernateDatastore`, so service-loader registration may cause issues in CLI contexts. Worked around in tests by manual instantiation.

