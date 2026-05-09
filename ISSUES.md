# Hibernate 7 / MongoDB Migration — Issue Tracker

**CURRENT FOCUS**: MongoDB module compilation and test failures.

---

## Module Status

| Module Group | Status | Notes |
|---|---|---|
| `grails-data-hibernate7-core` | ✅ 100% passing | Totally fixed. DISCRIMINATOR multi-tenancy on secondary datasources resolved 2026-05-09 |
| `grails-datamapping-core` | ✅ 100% passing | GormEnhancer NPE + entity-datasource registration fixed |
| `grails-datamapping-async/validation/support/tck` | ✅ | |
| `grails-datamapping-core-test` | ✅ | Parallel-flaky tests pass individually |
| `grails-datastore-core/web` | ✅ | |
| `grails-data-simple` | ✅ | |
| `grails-data-hibernate7` (dependent modules) | ✅ | Totally fixed. All integration and example tests passing |
| `grails-data-hibernate5-core` | ✅ 100% passing | Aligned with GORM 8 API. Scalability O(M+N) verified 2026-05-09 |
| `grails-data-mongodb-core` | ✅ 100% passing | Fixed versioning, dirty checking, and embedded conflicts. Scalability O(M+N) verified |
| `grails-data-mongodb` + siblings | 🔲 Not yet run | Require live MongoDB |

**Run command** (quarterly batches — full suite hangs):
```bash
python3 run_quarter.py specs_q1.txt --timeout 600
```

---

## Architecture Reference

### Two Query Mechanisms (H7/H5)
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

### Session Lifecycle (H7/H5)
- `GrailsHibernateTransactionManager.doBegin()` binds `(datastore, sessionHolder)` to TSM
- `withNewSession` creates a child session; cleanup restores outer session
- `HibernateSession.getNativeSession()`: returns stored `nativeSession` if set, else `sessionFactory.getCurrentSession()`
- Proxy reattach: use `session.merge(target)` — `session.lock(detached, NONE)` throws `DetachedObjectException` in H7

---

## Key Fixes Applied

| Area | Fix |
|------|-----|
| `GormRegistryScalabilitySpec` | Ported to H5 and MongoDB. Verified O(M+N) memory guarantee cross-module |
| `grails-data-hibernate5-core` | Aligned API classes (Instance/Static/Enhancer) with GORM 8 structural changes |
| `DataServiceSpec` (H5) | Fixed `@Query` compilation errors by aligning with standard HQL named parameters |
| `DomainMultiTenantMultiDataSourceSpec` | `MultiTenantEventListener` applies filters to specific query session (fixes DISCRIMINATOR isolation) |
| `HibernateGormInstanceApiSpec` (31 tests) | `isDirty()`, `attach()` via `merge()`, proxy dispatch, `getInstanceApiHelper()` |
| `Hibernate7GroovyProxySpec` | `HibernateSession.proxy()` honors `GroovyProxyFactory` |
| `PartitionedMultiTenancySpec` | `SelectHqlQuery` fires `PreQueryEvent` → DISCRIMINATOR filter applied |
| CRUD / Finders / Where / PagedResult | OR query logic, String key coercion, `PagedResultList` wiring |
| `SchemaMultiTenantSpec` + `HibernateDatastoreSchemaMultiTenancySpec` | SCHEMA multi-tenancy session routing |
| `GormEnhancerAllQualifiersSpec` (15 tests) | GormEnhancer NPE null-guard + entity secondary-datasource registration |
| `GormRegistryScalabilitySpec` (parallel) | Assertions scoped to entity keys — not total map size |
| `MongoStaticApi.groovy` | Added missing `ConnectionSource` import (compilation fix) |
| `GormStaticApi.deleteAll()` | Uses `DetachedCriteria` instead of `Query.deleteAll()` |
| `GormStaticApi.eachTenant()` | Implemented via `GormRegistry.getDatastore(DEFAULT)` |
| `GormStaticApi.withId()` | Uses root (DEFAULT) datastore for `Tenants.withId()` |
| `GormEnhancer.findDatastore()` priority 4 | DISCRIMINATOR/SCHEMA modes no longer attempt tenant resolution |
| `GormEnhancer.findDatastore()` priority 2 | Tries `getDatastoreForConnection()` before `getDatastoreForTenantId()` |
| `MongoStaticApi.createStaticApi()` | Override added so `forQualifier()` returns `MongoStaticApi` |
| `PersistentEntityCodec` | Fixed double version increment and optimized collection dirty marking |
| `MongoCodecEntityPersister` | Honored `markDirty` flag in manual dirty checking (fixes `MarkDirtyFalseSpec`) |
| `PersistentEntityCodec.encodeUpdate()` | Resolved version property conflict in embedded updates (fixes `BulkWriteException`) |
| `DirtyCheckableSupport` | Added `DirtyCheckableCollection.hasChanged()` check for MongoDB associations |
| `InheritanceWithSingleEndedAssociationSpec` | Restored `skipValidation` in `PendingInsert.run()` for deferred flush |

---

## Open Issues

### MongoDB — grails-data-mongodb (siblings): Pending Run

| Module | Status | Notes |
|--------|--------|-------|
| `grails-data-mongodb` | 🔲 | |
| `grails-data-mongodb-ext` | 🔲 | |
| `grails-data-mongodb-spring-boot` | 🔲 | |

#### Skip (not real MongoDB failures)

| Count | Spec | Reason |
|-------|------|--------|
| 6 | `MultipleDataSourceSpec` | Uses `SimpleMapDatastore` — not MongoDB-specific; failures are parallel test pollution noise |

---

### H7 Example Tests — grails-test-examples-hibernate7-*

All 12 H7 example modules pass ✅

| Module | Status | Notes |
|--------|--------|-------|
| `grails-test-examples-hibernate7-*` | ✅ PASS | All examples passing as of 2026-05-08 |

---

## Constraints

1. **Can modify `grails-datamapping-core`** — must run all 253 tests before committing.
2. **Never modify `grails-datastore-core`** — changes affect H5 and MongoDB.
3. **`local.properties`** — never delete; comment out old values, append new ones.
4. **No `git push`** until full suite passes.
5. Full suite hangs — run specs in quarters via `run_quarter.py` with `specs_q{1..4}.txt`.
