# Hibernate 7 Migration — Issue Tracker

**CURRENT FOCUS**: H7 dependent modules (`grails-data-hibernate7`, `grails-data-hibernate7-spring-boot`, etc.)

**`grails-data-hibernate7-core` is 100% passing** (all Q1–Q4 quarterly runs green, SCHEMA multi-tenancy fixed 2026-05-05).

**`grails-datamapping-core` is 100% passing** — GormEnhancer regressions fixed 2026-05-05 (NPE null-guard + entity-datasource registration). 15 failures resolved.

**datamapping sibling modules** — all green (2026-05-05):
- `grails-datamapping-async` ✅
- `grails-datamapping-validation` ✅
- `grails-datamapping-support` ✅
- `grails-datamapping-tck` ✅
- `grails-datamapping-core-test` ✅ (passes individually; `PagedResultSpec`/`FirstAndLastMethodSpec`/`MultipleDataSourceSpec` are pre-existing parallel-flaky, pass when run alone)
- `grails-datastore-core` ✅
- `grails-datastore-web` ✅
- `grails-data-simple` ✅

**Run command** (quarterly batches via `run_quarter.py` — full suite hangs):
```bash
python3 run_quarter.py specs_q1.txt --timeout 600
```
**Spec list files**: `specs_q1.txt` … `specs_q4.txt` (split from `all_h7_specs.txt`, 78–79 specs each, 313 total).

---

## Architecture Reference

### Two Query Mechanisms (H7)
| Mechanism | Class | Used by |
|-----------|-------|---------|
| JPA Criteria | `HibernateQuery` | `withCriteria {}`, `countBy*`, `findBy*` |
| HQL | `SelectHqlQuery` / `HibernateHqlQuery` | `HibernateGormStaticApi.list(Map)`, `first()`, `last()`, `findAll()` |

Both must fire `PreQueryEvent` so `MultiTenantEventListener` can enable Hibernate filters.
`HibernateQuery` already had this. `SelectHqlQuery` was **missing** it — now fixed.

### Multi-Tenancy Modes
| Mode | `isSharedConnection()` | Session behaviour in `Tenants.withId()` |
|------|------------------------|----------------------------------------|
| DATABASE | false | Opens new session on child datastore |
| SCHEMA | true | **Calls closure directly — no new session opened** |
| DISCRIMINATOR | true | Calls closure directly — Hibernate filter applied via PreQueryEvent |

**Critical**: `MultiTenancySettings.MultiTenancyMode.isSharedConnection()` returns `true` for BOTH SCHEMA and DISCRIMINATOR.
Do NOT modify `grails-datastore-core/MultiTenancySettings.groovy` — shared with H5 and MongoDB.

### Session Lifecycle
- `GrailsHibernateTransactionManager.doBegin()` binds `(datastore, sessionHolder)` to `TransactionSynchronizationManager`
- `withNewSession` creates a child session in the same thread; cleanup restores the outer session
- `HibernateSession.getNativeSession()`: returns stored `nativeSession` if set, else `sessionFactory.getCurrentSession()`

### Proxy Handling
- H7: `session.lock(detached, LockModeType.NONE)` throws `DetachedObjectException` → use `session.merge(target)` instead
- `GroovyProxyFactory` must be honored at **both** `HibernateSession.proxy()` and `HibernateGormStaticApi.proxy(id)`

---

## Fixed Issues

| Spec | Tests | Fix summary |
|------|-------|-------------|
| `HibernateGormInstanceApiSpec` | 31/31 ✅ | `isDirty()`, `attach()` via `merge()`, `methodMissing` for proxy dispatch, `getInstanceApiHelper()` |
| `Hibernate7GroovyProxySpec` | 1/1 ✅ | `HibernateSession.proxy()` honors `GroovyProxyFactory` from mapping context |
| `PartitionedMultiTenancySpec` | 9/9 ✅ | `SelectHqlQuery` fires `PreQueryEvent` → DISCRIMINATOR filter applied for `list()`, `first()`, `last()` |
| CrudOps / Finders / Where / PagedResult | many ✅ | OR query logic (`disjunction`/`conjunction`), String key coercion, `PagedResultList` wiring |
| `SchemaMultiTenantSpec` | 1/1 ✅ | See fix below |
| `HibernateDatastoreSchemaMultiTenancySpec` | 1/1 ✅ | See fix below |

### SCHEMA Multi-Tenancy Fix (2026-05-05)

**Root cause**: `HibernateDatastore.getCurrentSession()` previously returned `new HibernateSession(this, sessionFactory)` with no native session — correct for the parent (it falls back to `SessionFactory.getCurrentSession()` which resolves via Spring's `SpringSessionContext`). For child datastores, `DatastoreUtils.execute()` called `hasCurrentSession()` (which returned `true` via the SF TSM holder from `withTransaction`), then called `getCurrentSession()`, which ignored the SF holder and opened a brand-new standalone session via `connect()`. That standalone session had no active transaction → `TransactionRequiredException` on `flush()`.

**Fix**: Overrode `getCurrentSession()` in `HibernateDatastore` with a priority-based lookup:
1. Custom session resolver
2. GORM session holder (TSM key = datastore)
3. Spring TX `SessionFactory` holder (TSM key = `SessionFactory`) — returns `new HibernateSession(this, sf, nativeSession)` wrapping the transactional session

Also: removed the `if (this instanceof ChildHibernateDatastore) return withNewSession(callable)` guard from `withSession()` — it was too aggressive and caused `withSession` inside `withTransaction` to open a second independent session. The `ChildHibernateDatastore.connect()` override (which opens a native session eagerly) handles the no-transaction case correctly.

---

## Open Issues

*None known — all datamapping/datastore modules passing. H7 dependent modules under test next.*

---

### Architectural Debt Note

`MultiTenancySettings.MultiTenancyMode.isSharedConnection()` returns `true` for **both** SCHEMA
and DISCRIMINATOR modes (in `grails-datastore-core`). This causes `Tenants.withId()` in
`grails-datamapping-core` to call SCHEMA closures bare — with no session opened.

The correct long-term fix belongs in **core**: `Tenants.withId()` should differentiate between
SCHEMA (needs a new session from the child datastore's factory) and DISCRIMINATOR (reuses
the existing session, applies a Hibernate filter). Changing `isSharedConnection()` or adding a
separate predicate in core would let all datastores (H5, H7, MongoDB) benefit automatically.

**Current H7 workaround**: `HibernateDatastore.getCurrentSession()` checks the Spring TX
`SessionFactory` holder (TSM key = `SessionFactory`) as a priority-3 fallback so that
`DatastoreUtils.execute()` reuses the transactional session instead of opening a new one.
`ChildHibernateDatastore.connect()` opens a real Hibernate session eagerly for the no-transaction
case (e.g. bare `Tenants.withId { count() }`). Core is unchanged.

**When to revisit**: if H5 or MongoDB also implement SCHEMA multi-tenancy, the same bare-call
bug will surface — fix it in `Tenants.withId()` at that point.

---

## Constraints & Rules

1. **Can modify `grails-datamapping-core`** — but requires running ALL grails-datamapping-core tests to verify no H5/MongoDB regressions before committing.
2. **Never modify `grails-datastore-core`** — changes spread to H5 and MongoDB with no test coverage here.
2. **`local.properties`** — never delete; comment out old values, append new ones.
3. **No `git push`** until full suite passes.
4. Full suite hangs — run specs in quarters via `run_quarter.py` with `specs_q{1..4}.txt`.

---

## MongoDB Migration (Future)

Unlike H7 (deep session lifecycle rewrites), MongoDB failures are expected to be lighter-weight API changes.

| Area | Expected issue |
|------|---------------|
| Driver API | `FindIterable.first()` replaces `.one()`; deprecated `WriteConcern` constructors |
| Codecs | `CodecConfigurationException` if BSON codec chain broken — start from `MongoMappingContext` |
| Transactions | `MongoTransactionManager` — verify `ClientSessionHolder` binding to `TransactionSynchronizationManager` |
| Multi-tenancy | DATABASE mode — verify `ChildMongoDatastore.getDataSourceName()` returns tenant ID not `DEFAULT` |
| DetachedCriteria | `MongoQuery` BSON translation — evolutionary change, not breaking |

**Key files**: `MongoDatastore.groovy`, `MongoEntityPersister.groovy`, `MongoQuery.groovy`, `MongoMappingContext.groovy`

---

## H7 Dependent Modules (Next Focus)

| Module | Description | Status |
|--------|-------------|--------|
| `grails-data-hibernate7` | Main H7 GORM plugin | 🔲 Not yet run |
| `grails-data-hibernate7-spring-boot` | Spring Boot auto-config | 🔲 Not yet run |
| `grails-data-hibernate7-spring-orm` | Spring ORM integration | 🔲 Not yet run |
| `grails-data-hibernate7-dbmigration` | DB migration plugin | 🔲 Not yet run |

Run each with:
```bash
./gradlew :<module>:test --rerun-tasks
```

---

## Q3 Results (2026-05-05)

**74 PASS  0 FAIL** (previously 1 FAIL — `SchemaMultiTenantSpec` — now fixed)

**3 MISSING (no XML — passed individually, batch ordering issue in `run_quarter.py`)**:
- `grails.gorm.specs.HibernateGormDatastoreSpec`
- `grails.gorm.specs.WhereQueryOldIssueVerificationSpec`
- `grails.gorm.specs.DetachedCriteriaProjectionNullAssociationSpec`

