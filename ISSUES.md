# Hibernate 7 / MongoDB Migration — Issue Tracker

**CURRENT FOCUS**: MongoDB module compilation and test failures.

---

## Module Status

| Module Group | Status | Notes |
|---|---|---|
| `grails-data-hibernate7-core` | ✅ 100% passing | SCHEMA multi-tenancy fixed 2026-05-05 |
| `grails-datamapping-core` | ✅ 100% passing | GormEnhancer NPE + entity-datasource registration fixed |
| `grails-datamapping-async/validation/support/tck` | ✅ | |
| `grails-datamapping-core-test` | ✅ | Parallel-flaky tests pass individually |
| `grails-datastore-core/web` | ✅ | |
| `grails-data-simple` | ✅ | |
| `grails-data-hibernate7` (dependent modules) | ✅ | Passed previously |
| `grails-data-mongodb-core` | 🔧 Compilation fix applied | Missing `ConnectionSource` import in `MongoStaticApi.groovy` |
| `grails-data-mongodb` + siblings | 🔲 Not yet run | Require live MongoDB |

**Run command** (quarterly batches — full suite hangs):
```bash
python3 run_quarter.py specs_q1.txt --timeout 600
```

---

## Architecture Reference

### Two Query Mechanisms (H7)
| Mechanism | Class | Used by |
|-----------|-------|---------|
| JPA Criteria | `HibernateQuery` | `withCriteria {}`, `countBy*`, `findBy*` |
| HQL | `SelectHqlQuery` / `HibernateHqlQuery` | `list(Map)`, `first()`, `last()`, `findAll()` |

Both must fire `PreQueryEvent` so `MultiTenantEventListener` can enable Hibernate filters.

### Multi-Tenancy Modes
| Mode | `isSharedConnection()` | Session behaviour |
|------|------------------------|-------------------|
| DATABASE | false | Opens new session on child datastore |
| SCHEMA | true | Calls closure directly — no new session |
| DISCRIMINATOR | true | Calls closure directly — filter via PreQueryEvent |

**Critical**: Do NOT modify `grails-datastore-core/MultiTenancySettings.groovy` — shared with H5 and MongoDB.

### Session Lifecycle (H7)
- `GrailsHibernateTransactionManager.doBegin()` binds `(datastore, sessionHolder)` to TSM
- `withNewSession` creates a child session; cleanup restores outer session
- `HibernateSession.getNativeSession()`: returns stored `nativeSession` if set, else `sessionFactory.getCurrentSession()`
- Proxy reattach: use `session.merge(target)` — `session.lock(detached, NONE)` throws `DetachedObjectException` in H7

---

## Key Fixes Applied

| Area | Fix |
|------|-----|
| `HibernateGormInstanceApiSpec` (31 tests) | `isDirty()`, `attach()` via `merge()`, proxy dispatch, `getInstanceApiHelper()` |
| `Hibernate7GroovyProxySpec` | `HibernateSession.proxy()` honors `GroovyProxyFactory` |
| `PartitionedMultiTenancySpec` | `SelectHqlQuery` fires `PreQueryEvent` → DISCRIMINATOR filter applied |
| CRUD / Finders / Where / PagedResult | OR query logic, String key coercion, `PagedResultList` wiring |
| `SchemaMultiTenantSpec` + `HibernateDatastoreSchemaMultiTenancySpec` | SCHEMA multi-tenancy session routing (see below) |
| `GormEnhancerAllQualifiersSpec` (15 tests) | GormEnhancer NPE null-guard + entity secondary-datasource registration |
| `GormRegistryScalabilitySpec` (parallel) | Assertions scoped to entity keys — not total map size |
| `MongoStaticApi.groovy` | Added missing `ConnectionSource` import (compilation fix) |

### SCHEMA Multi-Tenancy Fix Detail
`HibernateDatastore.getCurrentSession()` overridden with priority lookup:
1. Custom session resolver
2. GORM session holder (TSM key = datastore)
3. Spring TX `SessionFactory` holder (TSM key = `SessionFactory`) — wraps transactional session

Removed the `if (this instanceof ChildHibernateDatastore) return withNewSession(callable)` guard from `withSession()` — it opened a second independent session inside `withTransaction`.

---

## Open Issues

### MongoDB — Known Failing Specs (from Q3 run, need live MongoDB)

These specs need a live MongoDB to run. Expected failure areas:

| Area | Failing Specs |
|------|--------------|
| Dirty checking | `DirtyCheckingSpec` (5 tests) |
| Finders | `FindByExampleSpec` (2), `IsNullSpec` (2) |
| First/Last | `FirstAndLastMethodSpec` (3) |
| Paged result | `PagedResultSpec` (3) |
| Validation | `ValidationSpec` (1) |
| Where/Lazy | `WhereLazySpec` (2) |
| Arrays | `BasicArraySpec` (3) |
| Embedded | `EmbeddedCollectionAndInheritanceSpec`, `EmbeddedCollectionWithOneToOneSpec`, `EmbeddedUnsetSpec`, `EmbeddedWithNonEmbeddedCollectionsSpec` |
| GeoJSON | `GeoJSONTypePersistenceSpec` (6) |
| Multi-tenancy | `MongoConnectionSourcesSpec`, `MultiTenancySpec`, `MultipleDataSourceConnectionsSpec`, `SchemaBasedMultiTenancySpec`, `SingleTenancySpec`, `MongoStaticApiMultiTenancySpec` |
| Other | `DisjunctionQuerySpec`, `NullifyPropertySpec`, `NullsAreNotStoredSpec`, `SimpleHasManySpec`, `MultipleConnectionsSpec` |

---

## Architectural Debt

`MultiTenancySettings.MultiTenancyMode.isSharedConnection()` returns `true` for both SCHEMA and DISCRIMINATOR. The correct long-term fix: `Tenants.withId()` in core should differentiate SCHEMA (needs new session from child datastore) from DISCRIMINATOR (reuses session, applies filter). H7 works around this via `HibernateDatastore.getCurrentSession()` priority lookup. **Revisit if H5 or MongoDB implement SCHEMA multi-tenancy.**

---

## Constraints

1. **Can modify `grails-datamapping-core`** — must run all 253 tests before committing.
2. **Never modify `grails-datastore-core`** — changes affect H5 and MongoDB.
3. **`local.properties`** — never delete; comment out old values, append new ones.
4. **No `git push`** until full suite passes.
5. Full suite hangs — run specs in quarters via `run_quarter.py` with `specs_q{1..4}.txt`.

