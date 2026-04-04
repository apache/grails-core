# GORM Hibernate 7 Architectural Analysis

PR [#15530](https://github.com/apache/grails-core/pull/15530) - Branch `8.0.x-hibernate7`

This document provides a comprehensive architectural analysis of the Hibernate 7 integration
into GORM/Grails, comparing it to the existing Hibernate 5 implementation and documenting
breaking changes, shared module impacts, and recommendations.

### Current Status

- **GString criteria fix**: Committed - [PR #15549](https://github.com/apache/grails-core/pull/15549) (adds `normalizeValue()` to `PredicateGenerator.java`)
- **Groovy proxy `isInitialized()` fix**: Submitted - [PR #15548](https://github.com/apache/grails-core/pull/15548)
- **H7 test regressions**: 0 remaining (all resolved)
- **H5/shared module regressions**: 0 (BUILD SUCCESSFUL)
- **All open questions**: Investigated and resolved (MongoDB impact, PagedList, HQL joins, UserType migration)

## Table of Contents

- [1. Executive Summary](#1-executive-summary)
- [2. Why Hibernate 7](#2-why-hibernate-7)
- [3. Architecture Overview](#3-architecture-overview)
- [4. Query Engine - Complete Rebuild](#4-query-engine---complete-rebuild)
- [5. Domain Binding - Monolith to Modules](#5-domain-binding---monolith-to-modules)
- [6. Proxy and Lazy Loading](#6-proxy-and-lazy-loading)
- [7. Session and Transaction Management](#7-session-and-transaction-management)
- [8. Shared Module Changes (Impact on ALL Datastores)](#8-shared-module-changes-impact-on-all-datastores)
- [9. BOM Strategy](#9-bom-strategy)
- [10. Breaking Changes for Grails App Developers](#10-breaking-changes-for-grails-app-developers)
- [11. Migration Tooling - Liquibase](#11-migration-tooling---liquibase)
- [12. Test Coverage Status](#12-test-coverage-status)
  - [12.1 Non-Hibernate-7 Modules](#121-non-hibernate-7-modules-phase-1---complete)
  - [12.2 Hibernate 7 vs Hibernate 5 Parity](#122-hibernate-7-vs-hibernate-5-parity-phase-2---complete)
  - [12.3 H7 Test Exclusions](#123-h7-test-exclusions-tests-skipped-or-failing-on-h7)
- [13. Risks and Resolved Questions](#13-risks-and-resolved-questions)
- [14. Recommendations](#14-recommendations)

---

## 1. Executive Summary

Hibernate 7 (built on the Hibernate 6.x lineage) is the most significant architectural shift
in Hibernate's history. The legacy Criteria API was removed entirely, the Type system was
rewritten, Session API methods were renamed to align with JPA, and Spring Framework 7 dropped
its `org.springframework.orm.hibernate5` package. This PR introduces a parallel GORM
implementation (`grails-data-hibernate7`) alongside the existing `grails-data-hibernate5`,
sharing the same core datamapping abstractions.

**Key facts about this PR:**

- ~1,790 files changed across ~913 commits
- ~256 new test files in the h7 module (entire module is new)
- Shared modules (`datamapping-core`, `datastore-core`, `datamapping-tck`) have ~124 files
  changed with +4,035/-1,229 lines
- The GORM DSL API is preserved - app-level query code (`where`, `criteria`, dynamic finders)
  remains unchanged
- Internal implementation is entirely rebuilt for JPA Criteria, ByteBuddy proxies, and
  Jakarta EE 10

---

## 2. Why Hibernate 7

Grails uses **Hibernate 5.6.15.Final** via the `hibernate-core-jakarta` artifact (the
Jakarta EE repackaging of Hibernate 5.6) and **Hibernate 7.2.5.Final**.

| Factor | Hibernate 5.6 Jakarta | Hibernate 7.2 |
|--------|----------------------|---------------|
| Artifact | `org.hibernate:hibernate-core-jakarta:5.6.15.Final` | `org.hibernate.orm:hibernate-core:7.2.5.Final` |
| JPA version | JPA 3.0 (`jakarta.persistence`) | JPA 3.2 (`jakarta.persistence`) |
| Criteria API | Legacy `org.hibernate.Criteria` + JPA Criteria | JPA Criteria only (legacy removed) |
| Type system | `org.hibernate.type.Type` (read-by-name) | `JavaType` / `JdbcType` (read-by-position) |
| Session methods | `save()`, `update()`, `delete()`, `load()`, `saveOrUpdate()` | JPA-aligned: `persist()`, `merge()`, `remove()`, `getReference()` (legacy methods still exist but `saveOrUpdate()` was removed) |
| Proxy generation | ByteBuddy (default since 5.3; Javassist still available) | ByteBuddy only (Javassist removed) |
| Spring ORM | `spring-orm` `hibernate5` package | Package removed in Spring 7 (Grails forks it) |
| JDBC result reading | By column name | By position (performance improvement) |
| ID type constraint | `Serializable` required | `Object` (relaxed) |
| Sequence naming | Global `hibernate_sequence` | Per-entity `<entity>_seq` |
| Group ID | `org.hibernate` | `org.hibernate.orm` |

**Note on Jakarta**: Because Grails uses the `hibernate-core-jakarta` artifact, both H5 and
H7 already use `jakarta.persistence` - the `javax` to `jakarta` migration is NOT a factor in
this upgrade. The breaking changes are purely Hibernate API-level.

Hibernate 5.6 is end-of-life. Spring Boot 4 / Spring Framework 7 only supports Hibernate 6.6+.
Grails 8 must support Hibernate 7 to remain on supported infrastructure.

---

## 3. Architecture Overview

### Module Layout

```
grails-core/
  grails-datastore-core/          # Abstract datastore model (shared by ALL)
  grails-datamapping-core/        # GORM API, DetachedCriteria, finders (shared by ALL)
  grails-datamapping-tck/         # Test Compatibility Kit (shared by ALL)
  grails-data-hibernate5/         # Hibernate 5 GORM implementation
    core/
  grails-data-hibernate7/         # Hibernate 7 GORM implementation (NEW)
    core/
  grails-data-mongodb/            # MongoDB GORM implementation
  grails-bom/                     # Base BOM
  grails-hibernate5-bom/          # H5-specific version overrides
  grails-hibernate7-bom/          # H7-specific version overrides (NEW)
```

### Dependency Flow

```
App build.gradle
  |
  +-- platform('grails-hibernate7-bom')    // or grails-hibernate5-bom
  +-- implementation('grails-data-hibernate7')  // or grails-data-hibernate5
        |
        +-- grails-datamapping-core (shared GORM API)
        |     +-- grails-datastore-core (shared model)
        +-- hibernate-core 7.x
        +-- byte-buddy
```

Both h5 and h7 modules depend on the same `grails-datamapping-core` and `grails-datastore-core`.
Changes to these shared modules affect **all** datastores (h5, h7, MongoDB).

---

## 4. Query Engine - Complete Rebuild

### The Problem

Hibernate 7.2 completely removed the legacy Criteria API (`org.hibernate.Criteria`,
`Restrictions`, `Projections`). GORM's entire query infrastructure was built on this API.

### H5.6 Architecture

```
GORM DSL (where/criteria/dynamic finders)
    |
    v
AbstractHibernateQuery (extends Query)
    |
    v
Hibernate Criteria API (org.hibernate.Criteria)
    |-- Restrictions.eq(), .like(), .between()
    |-- Projections.count(), .sum()
    |-- Criteria.createAlias() for joins
    v
SQL
```

Key classes:
- `AbstractHibernateQuery` - base query class using `org.hibernate.Criteria` directly
- `AbstractHibernateCriterionAdapter` - translates GORM criteria to Hibernate `Criterion`
- `HibernateCriteriaBuilder` - DSL builder using `AbstractHibernateCriteriaBuilder`

### H7.2 Architecture

```
GORM DSL (where/criteria/dynamic finders)
    |
    v
HibernateQuery (extends Query)
    |
    v
DetachedCriteria (holds query state)
    |
    v
JpaCriteriaQueryCreator (translates to JPA)
    |-- PredicateGenerator (GORM criteria -> JPA Predicate)
    |-- JpaFromProvider (manages FROM/JOIN clauses)
    |-- HibernateCriteriaBuilder (JPA criteria builder)
    v
JPA CriteriaQuery -> SQL
```

Key new classes:
- `HibernateQuery` - bridges GORM to JPA Criteria via `DetachedCriteria`
- `JpaCriteriaQueryCreator` - translates `DetachedCriteria` to `CriteriaQuery` with
  projections, joins, and predicates
- `PredicateGenerator` - converts GORM criteria to JPA `Predicate` objects using
  `HibernateCriteriaBuilder`
- `JpaFromProvider` - manages FROM clauses, aliases, and joins; automatically applies LEFT
  joins for projected associations to preserve null rows
- `HibernateHqlQuery` - handles HQL queries with the split `SelectionQuery` / `MutationQuery`
  types (Hibernate 7 splits query execution by type)

### Feature Comparison

| Feature | H5.6 | H7.2 |
|---------|------|------|
| Basic criteria (eq, like, between) | `Restrictions.*` | `PredicateGenerator` -> JPA `Predicate` |
| Projections (count, sum, avg) | `Projections.*` | `JpaCriteriaQueryCreator.projectionToJpaExpression()` |
| Joins / Associations | `Criteria.createAlias()` with Hibernate `JoinType` | `JpaFromProvider` with JPA `JoinType` |
| Subqueries | `DetachedCriteria` + `Restrictions` | `JpaSubQuery` in `PredicateGenerator.handleSubqueryCriterion()` |
| RLIKE / Regex | Abstract `createRlikeExpression()` per dialect | Custom JPA function via `GrailsRLikeFunctionContributor` |
| Count with projections | Workaround: loads all rows into memory (logged warning) | `Query.countResults()` - same fallback, but cleaner path |
| HQL mutations (delete/update) | `Query.executeUpdate()` | `MutationQuery.executeUpdate()` (separate type) |

### User-Facing Impact

**None for typical GORM usage.** The GORM DSL (`where`, `criteria`, dynamic finders) is
preserved. The translation layer is entirely internal.

**Edge cases:**
- Custom `HibernateCriteriaBuilder` subclasses will break (different base class hierarchy)
- Direct Hibernate `Session` usage via `withSession { session -> session.createCriteria(...) }`
  will fail - `createCriteria()` no longer exists
- HQL queries that relied on "pass-through" tokens (unknown tokens silently passed to SQL)
  must now use `sql(...)` wrapper
- Implicit join behavior changed: `from Person p join p.address` now returns `List<Person>`
  instead of `List<Object[]>`

---

## 5. Domain Binding - Monolith to Modules

### The Problem

H5.6's `GrailsDomainBinder` was a ~4,000+ line monolithic class responsible for all domain-to-
Hibernate mapping translation. This was extremely difficult to test, maintain, or extend.

### H5.6 Architecture

```
GrailsDomainBinder (monolithic, ~4,000 lines)
    |-- handles all property types
    |-- handles all association types
    |-- handles all inheritance strategies
    |-- handles all ID generation strategies
    |-- handles second-pass binding
    v
Hibernate Mapping Model
```

### H7.2 Architecture

The monolithic binder was decomposed into ~100+ specialized classes across four categories:

**Binders (37 classes)** - one per mapping concern:

| Category | Classes |
|----------|---------|
| Identity | `SimpleIdBinder`, `CompositeIdBinder`, `IdentityBinder`, `NaturalIdentifierBinder` |
| Properties | `PropertyBinder`, `GrailsPropertyBinder`, `SimpleValueBinder`, `ColumnBinder` |
| Associations | `ManyToOneBinder`, `OneToOneBinder`, `ForeignKeyOneToOneBinder`, `CollectionBinder` |
| Inheritance | `SubclassMappingBinder`, `SingleTableSubclassBinder`, `JoinedSubClassBinder`, `UnionSubclassBinder` |
| Discriminators | `DefaultDiscriminatorBinder`, `ConfiguredDiscriminatorBinder`, `DiscriminatorPropertyBinder` |
| Constraints | `StringColumnConstraintsBinder`, `NumericColumnConstraintsBinder` |
| Other | `RootBinder`, `ClassBinder`, `ClassPropertiesBinder`, `VersionBinder`, `EnumTypeBinder`, `IndexBinder` |

**Second-Pass Binders (20 classes)** - deferred binding logic:

- `GrailsSecondPass`, `SetSecondPass`, `ListSecondPass`, `MapSecondPass`
- `CollectionSecondPassBinder`, `CollectionKeyBinder`, `CollectionOrderByBinder`
- `UnidirectionalOneToManyBinder`, `BidirectionalOneToManyLinker`
- `ManyToManyElementBinder`, `BasicCollectionElementBinder`
- `CollectionWithJoinTableBinder`, `CollectionMultiTenantFilterBinder`

**ID Generators (8 classes):**

- `GrailsIdentityGenerator`, `GrailsNativeGenerator`, `GrailsSequenceStyleGenerator`
- `GrailsIncrementGenerator`, `GrailsTableGenerator`, `GrailsSequenceWrapper`

**Hibernate Model Classes (30+ classes):**

- `HibernatePersistentEntity`, `HibernatePersistentProperty`, `HibernateClassMapping`
- `HibernateSimpleProperty`, `HibernateEnumProperty`, `HibernateEmbeddedProperty`
- `HibernateToOneProperty`, `HibernateManyToOneProperty`, `HibernateOneToManyProperty`
- `HibernateMappingBuilder` (DSL builder), `HibernateMappingFactory`

### Mapping DSL Compatibility

**All standard H5.6 mapping DSL options are supported in H7.2.** No known removals for
typical mapping configurations.

```groovy
// All of these continue to work in H7.2:
static mapping = {
    table 'my_table'
    version false
    cache usage: 'read-write'
    id generator: 'sequence', params: [sequence_name: 'my_seq']
    name column: 'full_name', length: 255
    books sort: 'title', order: 'asc', lazy: false
}
```

The identity/generator strategy differs internally: H5.6 used a single
`GrailsIdentifierGeneratorFactory` for all strategies, while H7.2 provides dedicated generator
classes for each strategy. This is transparent to app developers.

### User-Facing Impact

**None.** The mapping DSL is fully preserved. The decomposition is purely internal.

The benefit is dramatically improved testability - H7.2 has dedicated unit tests for each
binder class, compared to H5.6's integration-test-only approach.

---

## 6. Proxy and Lazy Loading

### H5.6 Approach

- ByteBuddy proxies (default since Hibernate 5.3; Javassist still available but not used)
- `HibernateProxy` interface for detection
- `PersistentCollection.wasInitialized()` for collection initialization checks
- Groovy proxy detection via direct `ProxyInstanceMetaClass` checks in `HibernateProxyHandler`

### H7.2 Approach

- **ByteBuddy-only** proxies via custom `ByteBuddyGroovyProxyFactory` and
  `GrailsBytecodeProvider`
- `LazyInitializable.wasInitialized()` replaces `PersistentCollection.wasInitialized()`
- Groovy proxy logic delegated to `GroovyProxyInterceptorLogic` (cleaner separation)
- `Hibernate.createDetachedProxy()` for proxy creation

### Key Differences

| Aspect | H5.6 Jakarta | H7.2 |
|--------|-------------|------|
| Proxy library | ByteBuddy (Javassist still available) | ByteBuddy only (Javassist removed) |
| Collection init check | `PersistentCollection.wasInitialized()` | `LazyInitializable.wasInitialized()` |
| Groovy proxy logic | Inline in `HibernateProxyHandler` | Delegated to `GroovyProxyInterceptorLogic` |
| Proxy factory | Hibernate's default | Custom `ByteBuddyGroovyProxyFactory` |
| Bytecode provider | Hibernate's default | Custom `GrailsBytecodeProvider` |

### Bug Found During Review

H7.2's `HibernateProxyHandler.isInitialized()` was missing Groovy proxy support. The
`ProxyInstanceMetaClass` check that exists in H5.6 was not carried over. Since
`Hibernate.isInitialized()` returns `true` for any non-Hibernate object, uninitialized Groovy
proxies were incorrectly reported as initialized.

**Fix**: Added `GroovyProxyInterceptorLogic.isInitialized()` helper and integrated it into
H7.2's `HibernateProxyHandler`. Submitted as [PR #15548](https://github.com/apache/grails-core/pull/15548).

### User-Facing Impact

**Minimal for typical usage.** Proxy behavior should be functionally identical.

**Edge cases:**
- `instanceof HibernateProxy` checks continue to work (ByteBuddy proxies still implement it).
  The risk is code relying on Javassist-specific proxy implementation classes or behavior -
  Javassist support was removed entirely in H7
- Custom `ProxyHandler` implementations may need updating for the new interfaces
- Lazy loading timing could differ slightly due to ByteBuddy implementation

---

## 7. Session and Transaction Management

### Session

| Aspect | H5.6 Jakarta | H7.2 |
|--------|-------------|------|
| Base class | `AbstractHibernateSession` | `AbstractAttributeStoringSession` |
| Template | `GrailsHibernateTemplate` (Spring's) | Forked `GrailsHibernateTemplate` (Spring ORM fork, still heavily used) |
| Bulk operations | `Query.executeUpdate()` | `MutationQuery.executeUpdate()` |
| Query creation | Via Criteria API | Direct `HibernateQuery` construction |
| Flush mode | Default: `COMMIT`; `AUTO` = Hibernate `AUTO` | Default: `COMMIT`; `AUTO` = Hibernate `AUTO` |
| Interfaces | `Session` | `Session` + `QueryAliasAwareSession` |

**Flush mode behavior**: Both H5 and H7 default to `COMMIT` mode, with `AUTO` available when
`autoFlush: true` is configured. Hibernate 7's native `AUTO` flush mode is less aggressive
than Hibernate 5's (H7 uses smart dirty checking, flushing only when queries might return
stale data). However, **GORM mitigates this difference**: the shared `Query.flushBeforeQuery()`
method explicitly flushes on `FlushModeType.AUTO` before every query, and both H5 and H7 HQL
query wrappers call it. The behavioral risk applies primarily to apps using raw Hibernate
`Session` queries outside of GORM's query layer.

### Transaction Management

**H5.6**: Uses Spring's `org.springframework.orm.hibernate5.HibernateTransactionManager`.

**H7.2**: Uses a **forked** `org.grails.orm.hibernate.support.hibernate7.HibernateTransactionManager`.

**Why forked?** Spring Framework 7 removed its `spring-orm` Hibernate-specific transaction
manager entirely. Spring 7 treats Hibernate purely as a JPA provider, expecting apps to use
`JpaTransactionManager`. However, GORM needs Hibernate-specific transaction semantics
(session binding, flush mode control), so the Spring 6 implementation was forked, migrated
to Jakarta, and maintained within Grails.

Forked Spring ORM classes in `support/hibernate7/` (22 classes total):

- `HibernateTransactionManager` - core transaction management
- `SessionFactoryUtils` - session factory utilities
- `HibernateTemplate` / `HibernateCallback` / `HibernateOperations` - template API
- `SpringSessionContext` / `SpringJtaSessionContext` - session context
- `SessionHolder` - session holder for thread-local binding
- `LocalSessionFactoryBean` / `LocalSessionFactoryBuilder` - factory configuration
- `HibernateExceptionTranslator` - exception translation to Spring DataAccessException
- `HibernateJdbcException`, `HibernateObjectRetrievalFailureException`,
  `HibernateOptimisticLockingFailureException`, `HibernateQueryException`,
  `HibernateSystemException` - exception hierarchy
- `ConfigurableJtaPlatform` - JTA integration
- `SpringBeanContainer` - bean container for Hibernate
- `SpringFlushSynchronization` / `SpringSessionSynchronization` - synchronization
- `support/AsyncRequestInterceptor` - async request handling
- `support/OpenSessionInViewInterceptor` - OSIV support

### Datastore Initialization

**H5.6**: Uses anonymous inner classes for child datastores in multi-datasource setups.

**H7.2**: Refactored multi-datasource initialization pattern, which:
- Prevents infinite recursion during multi-datasource initialization
- Uses `Action.interpretHbm2ddlSetting()` instead of `SchemaAutoTooling.interpret()`
- Injects `GrailsBytecodeProvider` for proxy support

### User-Facing Impact

- **Flush mode**: Apps using raw Hibernate `Session` queries outside of GORM with
  `autoFlush: true` may see different flush timing. GORM's own query layer
  (`Query.flushBeforeQuery()`) mitigates this by explicitly flushing before queries. Explicit
  `flush: true` or `flushMode: ALWAYS` can be used for raw Session queries where needed.
  (Note: Default GORM mode is `COMMIT` in both H5 and H7, so most apps are unaffected.)
- **Transaction manager**: Same public API, transparent to app code.
- **Multi-datasource**: Should be more stable in H7.2 due to the refactored initialization
  pattern.

---

## 8. Shared Module Changes (Impact on ALL Datastores)

These changes affect `grails-datamapping-core`, `grails-datastore-core`, and
`grails-datamapping-tck` - modules shared by Hibernate 5, Hibernate 7, AND MongoDB.

### 8.1 Query.java - Core Query Model

**Changes:**

| Change | Before | After | Impact |
|--------|--------|-------|--------|
| `max` / `offset` fields | `int max = -1; int offset = 0` | `Integer max; Integer offset` | Nullable allows distinguishing "not set" from "set to 0" |
| New `getMax()` / `getOffset()` getters | None (field access) | Public `Integer` getters | Better encapsulation |
| New `countResults()` method | N/A | Default implementation with projection fallback | Shared count logic, overridable by datastores |
| New `QueryElement` interface | N/A | Parent of both `Criterion` and `Projection` | Unifies query elements for h7's JPA translation |
| `Projection` | Plain class | Implements `QueryElement` | Enables uniform handling in h7 query creator |
| `distinct()` | No-op | Adds `Projections.distinct()` | **Behavioral change** - distinct now actually adds a projection |

**Risk**: The `distinct()` fix is a behavioral change. Previously `distinct()` on a
`ProjectionList` was a no-op. Now it adds a distinct projection. This could affect query
results for all datastores if any code path called `distinct()` expecting it to do nothing.

### 8.2 MappingFactory.java - Property Creation

**Changes:**

All property creation methods (`createIdentity`, `createSimple`, `createOneToOne`,
`createManyToOne`, `createOneToMany`, `createManyToMany`, `createEmbedded`,
`createEmbeddedCollection`, `createBasicCollection`, `createCustom`, `createTenantId`) were
refactored from **anonymous inner classes** to **named concrete classes**.

Before (H5 pattern):
```java
public Simple<T> createSimple(...) {
    return new Simple<>(owner, context, pd) {
        PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
        public PropertyMapping<T> getMapping() {
            return propertyMapping;
        }
    };
}
```

After (H7 pattern):
```java
public Simple<T> createSimple(...) {
    SimpleWithMapping<T> simple = new SimpleWithMapping<>(owner, context, pd);
    simple.setMapping(createPropertyMapping(simple, owner));
    return simple;
}
```

12 new `*WithMapping` classes added to `grails-datastore-core`:
- `IdentityWithMapping`, `TenantIdWithMapping`, `SimpleWithMapping`, `CustomWithMapping`
- `OneToOneWithMapping`, `ManyToOneWithMapping`, `OneToManyWithMapping`, `ManyToManyWithMapping`
- `EmbeddedWithMapping`, `EmbeddedCollectionWithMapping`, `BasicWithMapping`
- `PropertyWithMapping` (base interface with `setMapping()`)

Also: `PropertyMapping` anonymous inner classes replaced with `DefaultPropertyMapping`,
and `IdentityMapping` anonymous inner classes replaced with `DefaultIdentityMapping`.

Additionally, `createDerivedPropertyMapping` was changed from `private` to `protected`,
allowing H7's `HibernateMappingFactory` to override it.

**Risk**: Low. The returned types are the same base types (`Simple`, `OneToMany`, etc.).
Subclasses of `MappingFactory` that override these methods will still work. The concrete
`*WithMapping` types add a `setMapping()` capability that anonymous inner classes lacked,
which is needed by H7's two-phase binding but is backward compatible.

### 8.3 PersistentProperty.java - New Default Methods

**Added default methods:**

| Method | Purpose |
|--------|---------|
| `getMappedForm()` | Convenience - `getMapping().getMappedForm()` with null safety |
| `isUnidirectionalOneToMany()` | Type check + bidirectional check |
| `isLazyAble()` | Whether property supports lazy loading |
| `isBidirectionalManyToOne()` | Type check + bidirectional check |
| `supportsJoinColumnMapping()` | `ManyToMany` or unidirectional `OneToMany` or `Basic` |
| `isSorted()` | Whether collection type is `SortedSet` |
| `isCompositeIdProperty()` | Whether property is part of composite identity |
| `isIdentityProperty()` | Whether property is the identity |
| `getOwnerClassName()` | Owner's class name with null safety |

**Risk**: Low. These are all `default` interface methods. They add convenience without
breaking existing implementations. However, if any implementation has a method with the same
signature but different semantics, it could shadow the default. MongoDB's property
implementations should be checked.

### 8.4 GormStaticApi.groovy - Behavioral Changes

**`merge()` method - BEHAVIORAL CHANGE:**

Before:
```groovy
D merge(D d) {
    execute({ Session session ->
        session.persist(d)
        return d  // returns the ORIGINAL object
    } as SessionCallback)
}
```

After:
```groovy
D merge(D d) {
    execute({ Session session ->
        Object merged = session.merge(d)
        return (D) merged  // returns the MERGED object (may be different instance)
    } as SessionCallback)
}
```

This is significant: `merge()` previously called `persist()` and returned the original
object. Now it calls `session.merge()` and returns the result. In Hibernate/JPA, `merge()`
returns a **new managed instance** while the original remains detached. Code that keeps a
reference to the original object after calling `merge()` may now be working with a stale
detached instance.

**Scope of `merge()` change**: This affects all three entry points:
- **Instance method**: `domainObj.merge()` (most common in app code)
- **Static method**: `DomainClass.merge(domainObj)`
- **Raw Session**: `session.merge(entity)` (already had this semantic in both H5 and H7)

The behavioral change is in GORM's `GormStaticApi.merge()`, which both the instance and
static methods delegate to. Raw `Session.merge()` always returned a new instance in both
Hibernate versions - the change is that GORM now correctly propagates this semantic.

**Other changes (style/formatting only, no behavioral impact):**
- Import reordering
- Removed redundant `else` blocks
- Parentheses removal from method calls (`session.persist(x)` -> `session.persist x`)
- `public` modifier added to several generic methods
- Removed trailing spaces after casts

### 8.5 GormEntity.groovy - Named Query Accessor Removal

**Removed:**
- `getNamedQuery(String queryName)` - deprecated since Grails 3.2
- `getNamedQuery(String queryName, Object... args)` - deprecated since Grails 3.2
- Import of `GormQueryOperations`

**Note**: Named query *support* itself is not removed - `GormStaticApi.methodMissing()` still
dispatches named queries at runtime. Only the explicit `getNamedQuery()` accessor methods
(which were marked `forRemoval = true`) have been removed.

**Risk**: Low for most apps. The deprecated accessors have had `forRemoval = true` markers
for years. Apps calling `getNamedQuery()` directly will get a compile error; apps using named
queries via method-missing dispatch (the standard usage pattern) are unaffected. The
recommended replacement for explicit accessor calls is `where` queries.

### 8.6 DetachedCriteria.groovy - Query Behavior Changes

**Changes:**

1. **`list()` method - BEHAVIORAL CHANGE:**

   Before: Only created a `PagedResultList` when `args?.max` was set.
   After: Creates a `PagedResultList` when `args` is non-null and non-empty, AND calls
   `DynamicFinder.populateArgumentsForCriteria()` to apply sorting/pagination.

   This means passing `[sort: 'name']` without `max` will now return a `PagedResultList`
   instead of a plain `List`. Previously, sort-only args would return a plain list.

2. **`count()` method - SIMPLIFIED:**

   Before: Had a special code path that loaded all rows when user-defined projections
   existed (with a warning log). Used `@Slf4j` for logging.
   After: Delegates to `query.countResults()` (the new `Query` method), which has the same
   fallback behavior but without the logging. Removed `@Slf4j` annotation.

3. **`clone()` - visibility change:** `protected` -> default (public in Groovy)

### 8.7 DynamicFinder.java - Query Junction Refactoring

**New methods:**

- `getJunction(DynamicFinderInvocation)` - builds the query junction from expressions,
  handling the AND/OR operator. Extracted from `AbstractFindByFinder` for reuse.
- `buildQuery(DynamicFinderInvocation, Session)` - builds a complete query from an
  invocation. Previously this logic was spread across `FindAllByFinder` and
  `AbstractFindByFinder`.
- `firstExpressionIsRequiredBoolean()` - hook for subclasses (default: `false`). Used by
  `CountByFinder` to handle boolean expressions in counting queries.

**Finder class simplification:** `AbstractFindByFinder` and `FindAllByFinder` were
significantly simplified (-89 lines) by extracting common logic up to `DynamicFinder`.

### 8.8 PagedResultList / PagedList - Interface Extraction

**New interface**: `PagedList<E>` - extracted from `PagedResultList`.

```java
public interface PagedList<E> extends List<E>, Serializable {
    int getTotalCount();
    List<E> getResultList();
    // ... default method implementations delegating to getResultList()
}
```

`PagedResultList` now implements `PagedList` instead of directly implementing `List` +
`Serializable`.

**New behavior in `PagedResultList.initTotalCount()`**: When cloning the query for counting,
it now resets `offset(0)` and `max(-1)` to ensure the count query counts ALL rows, not just
the current page.

**Risk**: Code that does `instanceof PagedResultList` will still work. Code that does
`instanceof List` will still work. The `PagedList` interface enables H7 to provide its own
`PagedResultList` implementation (extending Hibernate's `PagedResultList` wrapper) while
sharing the interface with the core module. H7.2 provides `HibernatePagedResultList` which
extends Hibernate 7's own `PagedResultList` wrapper while implementing the shared `PagedList`
interface.

### 8.9 TCK (Test Compatibility Kit) Changes

The TCK (`grails-datamapping-tck`) had extensive changes:

- **`GrailsDataTckManager`**: Refactored - `domainClasses` field changed from public to
  private; must use `addAllDomainClasses(Collection<Class>)` instead of field access.
- **New domain classes**: `ChildPersister`, `Child_BT_Default_P`, `EagerOwner`,
  `Owner_Default_Bi_P`, `Owner_Default_Uni_P`, `SimpleCountry` - for testing
  association/persistence patterns
- **New test spec**: `PagedResultSpecHibernate` - Hibernate-specific paged result tests
  (separate from the shared `PagedResultSpec`)
- **New test spec**: `RLikeSpec` - regex query tests
- **Expanded specs**: `EnumSpec` (+135 lines), `FindByMethodSpec` (+206 lines),
  `OptimisticLockingSpec` (+144 lines), `SizeQuerySpec` (+274 lines)
- **Numerous spec refinements**: Added `setupSpec`/`cleanupSpec` blocks, improved assertion
  specificity, added edge case coverage

---

## 9. BOM Strategy

### Current Structure

```
grails-bom (base)
  |-- defaults to h5-compatible liquibase (4.27.0)
  |-- includes all framework module versions
  |
  +-- grails-hibernate5-bom
  |     |-- extends grails-bom (no overrides - pure alias)
  |
  +-- grails-hibernate7-bom
        |-- extends grails-bom
        |-- overrides liquibase to h7-compatible versions
        |-- adds liquibase-hibernate7 (replaces liquibase-hibernate5)
```

### What Differs Between BOMs

The primary difference is the **Liquibase extension artifact**:

| Dependency | Base/H5 BOM | H7 BOM |
|------------|-------------|--------|
| `org.liquibase:liquibase-core` | 4.27.0 (strictly) | 4.27.0 (strictly) |
| `org.liquibase:liquibase-cdi` | 4.27.0 (strictly) | 4.27.0 (strictly) |
| `org.liquibase.ext:liquibase-hibernate5` | 4.27.0 (strictly) | N/A |
| `org.liquibase.ext:liquibase-hibernate7` | N/A | 4.27.0 (strictly) |

Note: Currently both BOMs use 4.27.0 for all Liquibase dependencies. The versions are
managed via `gradle.properties` (`liquibaseHibernate5Version`, `liquibaseHibernate7CoreVersion`,
etc.) and may diverge in the future as Hibernate 7 requires newer Liquibase features.

The Hibernate version itself is NOT in any BOM - it comes transitively from
`grails-data-hibernate5` or `grails-data-hibernate7`.

### Assessment

The split BOM approach is the industry-standard pattern for this problem. Spring Boot does
the same (their BOM picks one version, and overrides are required for alternatives).

The base `grails-bom` defaults to H5-compatible versions, and `grails-hibernate5-bom`
inherits it as-is (convenience alias for symmetry), while `grails-hibernate7-bom` inherits
and overrides only what differs. This keeps the upgrade path simple: existing apps importing
`grails-bom` continue to work unchanged with H5, and switching to H7 is a single BOM swap.

---

## 10. Breaking Changes for Grails App Developers

**Impact varies by usage pattern:**

1. **Standard GORM DSL users** (where queries, criteria DSL, dynamic finders) - minimal impact. The GORM API is preserved.
2. **Raw Hibernate Session/HQL users** (`withSession`, `withNewSession`, direct HQL) - moderate impact. Session API method names changed, flush behavior differs for raw queries.
3. **Hibernate SPI users** (custom `UserType`, proxy handlers, criteria extensions) - significant impact. Type system, proxy internals, and criteria API all changed.

### High Impact

| Change | Who's Affected | Migration |
|--------|---------------|-----------|
| `merge()` returns new instance | Apps calling `.merge()` and keeping reference to original | Use the returned object: `obj = obj.merge()` |
| Named query accessors removed | Apps calling `getNamedQuery()` directly (deprecated since 3.2) | Convert to `where` queries or rely on method-missing dispatch |
| Flush mode `AUTO` behavior | Apps using raw Hibernate `Session` queries with `autoFlush: true` | GORM queries are mitigated; add explicit `flush: true` for raw Session queries |
| Hibernate Criteria API gone | Apps with `withSession { session.createCriteria(...) }` | Use GORM criteria DSL or HQL |

### Medium Impact

| Change | Who's Affected | Migration |
|--------|---------------|-----------|
| Sequence naming default | Apps with existing schemas using `hibernate_sequence` | Set `hibernate.id.db_structure_naming_strategy=legacy` |
| `saveOrUpdate()` removed | Apps using raw Hibernate `session.saveOrUpdate()` | Use `session.persist()` / `session.merge()` |
| `session.load()` / `session.delete()` deprecated | Apps using raw Hibernate `session.load()` or `session.delete()` | Prefer `session.getReference()` / `session.remove()` (legacy methods still work but JPA-aligned methods are recommended) |
| Serializable ID constraint relaxed | Custom ID types not implementing Serializable | Generally a non-issue (relaxation, not restriction) |
| Boolean type mappings | Custom `yes_no`, `true_false`, `numeric_boolean` types | Use JPA `AttributeConverter` (e.g., `YesNoConverter`) |
| `DetachedCriteria.list()` with non-max args | Code passing sort-only args expecting plain List | Handle `PagedList` return type |

### Low Impact (Internal Only)

| Change | Who's Affected | Notes |
|--------|---------------|-------|
| `distinct()` now adds projection | Internal query builders | Behavioral fix, previously was a no-op |
| `countResults()` fallback | Datastores with custom count | Can override for optimization |
| `PersistentProperty` new defaults | Custom datastore implementations | Additive - won't break existing code |
| `*WithMapping` classes | Custom `MappingFactory` subclasses | Backward compatible - same return types |
| TCK `GrailsDataTckManager` | Custom TCK test suites | Use `addAllDomainClasses()` instead of field access |

### Not Breaking (Preserved)

- GORM `where` queries
- GORM criteria DSL
- Dynamic finders (`findBy*`, `findAllBy*`, `countBy*`)
- Domain class mapping DSL
- Validation and constraints
- Multi-tenancy API
- `withTransaction`, `withNewSession`, etc.
- `save()`, `delete()`, `get()`, `list()`, `count()` on domain classes
- GORM events and listeners

---

## 11. Migration Tooling - Liquibase

Liquibase support is version-specific:

| Hibernate | Liquibase Core | Liquibase Extension |
|-----------|---------------|---------------------|
| 5.x | 4.27.0 | `liquibase-hibernate5` 4.27.0 |
| 7.x | 4.27.0 | `liquibase-hibernate7` 4.27.0 |

Note: Both currently use 4.27.0. The versions are managed separately in `gradle.properties`
and may diverge in the future.

The `liquibase-hibernate5` and `liquibase-hibernate7` extensions are different artifacts
because they use Hibernate-version-specific APIs for schema introspection. This is the
primary reason the BOMs must differ.

**Repo-level detail**: The `grails-data-hibernate7` module includes Grails-maintained
Liquibase integration code for H7, not just an external artifact swap. The Liquibase H7
extension uses Hibernate 7's metadata APIs for schema diff/generation, which changed
significantly from H5.

---

## 12. Test Coverage Status

### 12.1 Non-Hibernate-7 Modules (Phase 1 - COMPLETE)

All test coverage was preserved for:

- `grails-datamapping-core-test` (in-memory datastore)
- `grails-data-hibernate5` (Hibernate 5)
- `grails-data-mongodb` (MongoDB)

### 12.2 Hibernate 7 vs Hibernate 5 Parity (Phase 2 - COMPLETE)

H7 has **dramatically more** test files than H5 (~256 vs ~107) due to the domain binder
decomposition creating many focused unit tests.

Only real coverage gap found: `HibernateProxyHandler7Spec` had 5 tests vs H5.6's 20.
**Fixed** - expanded to 21 tests (20 matching H5.6 + 1 new Groovy proxy test).

### 12.3 H7 Test Exclusions (Tests Skipped or Failing on H7)

A systematic comparison of `@Ignore`, `@PendingFeature`, and `@Requires` annotations across
both H5 and H7 test suites.

#### True H7 Regressions (work on H5, fail on H7)

~~Only **one** test was a genuine H7-specific regression:~~

**RESOLVED** - [PR #15549](https://github.com/apache/grails-core/pull/15549) (branch `fix/h7-multi-datasource-executequery`)

#### Shared Failures (broken on both H5 and H7)

These tests are excluded on H7, but are **also** excluded on H5 - they are pre-existing issues,
not H7 regressions:

| Test | H7 Annotation | H5 Annotation | Issue |
|------|---------------|---------------|-------|
| `TwoUnidirectionalHasManySpec."test two JPA unidirectional one to many references"` | `@PendingFeature` ("JPA @OneToMany unidirectional mapping generates non-nullable join column in Hibernate 7") | `@Ignore` (2 tests) | Unidirectional `@OneToMany` mapping issue exists in both versions |
| `SubclassMultipleListCollectionSpec` (entire class) | `@Ignore` | `@Ignore` | Same issue on both versions |

#### H7 Tests Requiring Docker (Not Failures)

These H7 tests are gated by `@Requires` for Docker/Testcontainers availability. They are
H7-only tests with no H5 equivalent - they represent **new** coverage, not gaps:

| Test | Annotation | Purpose |
|------|------------|---------|
| `GrailsSequenceGeneratorEnumSpec` | `@Requires({ DockerHelper.isAvailable() })` | Enum sequence generation with PostgreSQL |
| `HibernateDatastoreIntegrationSpec` | `@Requires({ DockerHelper.isAvailable() })` | Full datastore integration with PostgreSQL |
| `RLikeHibernate7Spec` | `@Requires({ DockerHelper.isAvailable() })` | Regex query support with PostgreSQL |

#### Previously Broken, Now Fixed in H7

These H7 tests have **commented-out** `@Ignore` annotations, showing issues that were resolved
during development:

| Test | Original Annotation | Status |
|------|---------------------|--------|
| `GlobalConstraintWithCompositeIdSpec."idEq()"` | `@Ignore("DDL not working for composite id")` (commented out) | Now passes |
| `HibernateQuerySpec."idEq()"` | `@Ignore("Need better implementation of Predicate")` (commented out) | Now passes |

#### H5 Test Files With No Direct H7 Counterpart

Three H5 test files have no single matching H7 file, but all have **equivalent or better**
coverage spread across multiple H7 specs:

| H5 Test File | H5 Tests | H7 Coverage | H7 Tests |
|-------------|----------|-------------|----------|
| `ByteBuddyProxySpec` | 5 | Split across `Hibernate7GroovyProxySpec`, `ByteBuddyGroovyProxyFactorySpec`, `GroovyProxyInterceptorLogicSpec`, `ToOneProxySpec`, `HibernateProxyHandler7Spec` | More total coverage |
| `Hibernate5OptimisticLockingSpec` | 2 | `Hibernate7OptimisticLockingSpec` | 3 (more coverage) |
| `HibernateProxyHandler5Spec` | 20 | `HibernateProxyHandler7Spec` | 21 (more coverage) |

#### H5 Exclusions (for reference)

The H5 test suite has its own set of exclusions - several of which are **not** present in H7:

| H5 Test | Annotation | Also in H7? |
|---------|------------|-------------|
| `UniqueConstraintHibernateSpec` | 1 `@Ignore` | No (H7 clean) |
| `ByteBuddyProxySpec` | 3 `@PendingFeatureIf` (fail without yakworks library) | No (different proxy architecture) |
| `HasManyWithInQuerySpec` | 1 `@Ignore` | No (H7 clean) |
| `TwoUnidirectionalHasManySpec` | 2 `@Ignore` | Yes (1 `@PendingFeature`) |
| `SaveWithInvalidEntitySpec` | 1 `@Ignore` | No (H7 clean) |
| `SubclassMultipleListCollectionSpec` | Class `@Ignore` + 1 method `@Ignore` | Yes (class `@Ignore`) |
| `UniqueWithMultipleDataSourcesSpec` | 1 `@Ignore` | No (H7 clean) |
| `WhereQueryOldIssueVerificationSpec` | 0 exclusions | 0 exclusions (clean on both) |

#### Summary

- **0 true H7 regressions**: The one regression (`MultipleDataSourceConnectionsSpec` GString
  coercion) has been **fixed** in [PR #15549](https://github.com/apache/grails-core/pull/15549)
- **2 shared failures**: Pre-existing issues in both H5 and H7, not regressions
- **3 Docker-gated tests**: New H7-only coverage requiring Testcontainers (not failures)
- **2 tests fixed**: Previously broken in H7 development, now passing
- **0 coverage gaps**: All H5 test files have equivalent or better H7 coverage
- **H5 has more exclusions than H7**: H5 has 8 files with exclusions vs H7's 2 (excluding Docker-gated)

---

## 13. Risks and Resolved Questions

### Risks

1. **Flush mode behavioral change (raw Session only)**: Hibernate 7's native `AUTO` flush
   mode is less aggressive than Hibernate 5's (smart dirty checking vs flush-before-every-query).
   However, GORM's shared `Query.flushBeforeQuery()` explicitly flushes on `FlushModeType.AUTO`,
   mitigating this for all GORM queries. The risk applies only to apps using raw Hibernate
   `Session` queries outside GORM's query layer with `autoFlush: true`. (Default GORM mode is
   `COMMIT` in both, so apps without `autoFlush: true` are unaffected.)

2. **`merge()` return value**: The change from returning the original object to returning the
   merged instance is a semantic change. Apps that call `merge()` but continue using the
   original reference may silently work with stale data.

3. **`DetachedCriteria.list()` with args**: Returning `PagedResultList` for any non-empty
   args (not just when `max` is set) could change behavior for code that passes sort-only
   arguments.

4. **Spring ORM fork maintenance**: The forked `HibernateTransactionManager` and related
   classes will need ongoing maintenance as they diverge from Spring's JPA-based approach.
   Security patches in Spring's transaction management won't automatically flow to the fork.

5. **`distinct()` behavioral fix**: Previously a no-op, now actually adds a distinct
   projection. If any code path relied on `distinct()` being a no-op, query results could
   change.

### Resolved Questions

1. **MongoDB impact of shared changes** - **SAFE, no issues found.**

   MongoDB GORM does not implement custom `PersistentProperty` classes. It uses the standard
   property types from `grails-datastore-core` (`Identity`, `Basic`, `ToOne`, `ManyToOne`,
   `OneToMany`, `ManyToMany`, `Embedded`, etc.) created by the shared `MappingFactory`. All
   new default methods use `instanceof` checks against these same core types, so they apply
   correctly to MongoDB's property instances.

   - `isLazyAble()` - checks `ToOne`, `Embedded`, `Association` - correct for MongoDB
   - `isBidirectionalManyToOne()` - checks `ManyToOne` + bidirectionality - correct
   - `isUnidirectionalOneToMany()` - checks `OneToMany` + bidirectionality - correct
   - `supportsJoinColumnMapping()` - returns true for `ManyToMany`/unidirectional `OneToMany`/`Basic` -
     join columns don't exist in MongoDB documents, but this method isn't used in MongoDB code
   - `isCompositeIdProperty()`, `isIdentityProperty()`, `isSorted()`, `getMappedForm()`,
     `getOwnerClassName()` - all generic, datastore-agnostic

   MongoDB has 100+ test specs covering associations, embedded, identity, etc. with no reported
   failures from these defaults.

2. **PagedList interface adoption** - **MongoDB has no paged result implementation to update.**

   The `PagedList<E>` interface (in `grails-datamapping-core`) defines `getTotalCount()` and
   `getResultList()`, extending `List<E>`. The shared `PagedResultList` implements it. Hibernate
   5 has two `PagedResultList` classes extending the shared implementation. MongoDB has **no**
   `PagedResultList` implementation in `grails-data-mongodb/` - it uses the shared one directly.
   No action needed.

3. **HQL implicit join behavior** - **No affected queries found in the codebase.**

   The Hibernate 7 change (`from Person p join p.address` returning `List<Person>` instead of
   `List<Object[]>`) does not affect existing GORM queries. GORM's HQL queries either select
   single entities or use explicit joins for filtering rather than expecting array results from
   implicit joins. No code in the codebase processes HQL join results as `Object[]` arrays.

   This remains a risk for **end-user applications** that write raw HQL with implicit joins
   expecting array results, but it is not a framework-level issue.

4. **Custom UserType migration** - **Compatibility layer exists, but method signatures changed.**

   `org.hibernate.usertype.UserType` still exists in Hibernate 7 as a generic interface
   (`UserType<J>`). It has NOT been removed. However:

   - `nullSafeGet(ResultSet, int, SharedSessionContractImplementor, Object)` is **deprecated
     for removal** - replace with `nullSafeGet(ResultSet, int, WrapperOptions)`
   - `nullSafeSet(PreparedStatement, J, int, SharedSessionContractImplementor)` is **deprecated
     for removal** - replace with `nullSafeSet(PreparedStatement, J, int, WrapperOptions)`
   - `getSqlType()` now returns `int` from `org.hibernate.type.SqlTypes`
   - Hibernate 7 includes `UserTypeLegacyBridge` as an internal adapter

   **Recommended migration path**: For simple types, use JPA `AttributeConverter` instead
   (limitation: cannot be used for `@Id`, `@Version`, or association attributes). For complex
   types, update `UserType` method signatures to use `WrapperOptions` instead of
   `SharedSessionContractImplementor`.

   GORM's internal custom types (like `IdentityEnumType`) have already been migrated to the
   new signatures in the H7 module. The `@Type` annotation now takes the class directly:
   `@Type(MyCustomType.class)`.

   | Feature | Hibernate 5.6 | Hibernate 7.2 |
   |---------|---------------|---------------|
   | `UserType` | Primary custom type API | Supported (deprecated SPI methods) |
   | `JavaType` / `JdbcType` | Internal | Primary underlying system |
   | `AttributeConverter` | Supported | Recommended for simple types |
   | `nullSafeGet` (Session param) | Active | Deprecated for removal |
   | `nullSafeGet` (WrapperOptions param) | N/A | Mandatory replacement |

---

## 14. Recommendations

### For the PR

**Verified finding**: MongoDB compatibility is confirmed safe - shared module changes
(`PersistentProperty` defaults, `DetachedCriteria.list()` behavior, `PagedList` interface)
do not affect MongoDB. No action needed.

1. **Document the `merge()` behavioral change** in migration notes. This is the most likely
   source of subtle bugs for upgrading apps.

2. **Document the flush mode nuance** (Hibernate 7's native `AUTO` is less aggressive than
   Hibernate 5's, but GORM's `Query.flushBeforeQuery()` mitigates this for GORM queries).
   Note that the risk applies only to raw Hibernate `Session` usage with `autoFlush: true`.
   The default GORM mode (`COMMIT`) is unchanged.

3. **For apps with raw Session queries and `autoFlush: true`**, suggest using explicit
   `flush: true` on those specific operations, or `flushMode: ALWAYS` as a broader workaround:
   ```yaml
   grails:
     gorm:
       flushMode: ALWAYS  # only if raw Session flush timing is critical
   ```

### For Grails 8 Migration Guide

1. Provide a "Hibernate 7 Migration Checklist" covering:
   - `merge()` return value
   - Flush mode
   - Named query accessor removal
   - Sequence naming strategy
   - Custom `UserType` migration (update `nullSafeGet`/`nullSafeSet` signatures from
     `SharedSessionContractImplementor` to `WrapperOptions`, or migrate to `AttributeConverter`)
   - Direct Hibernate Session API changes (`saveOrUpdate()` removed; prefer `persist()`/`merge()`/`remove()`/`getReference()` over legacy equivalents)
   - HQL implicit join return type change (array to entity) for apps with raw HQL queries

2. Provide Liquibase migration instructions (different extension artifact).

3. Clearly document that the GORM DSL is preserved - most app code needs zero changes.

### For Long-Term Maintenance

1. **Plan for Hibernate 5 deprecation** timeline - when does H5 support end in Grails?
2. **Monitor the Spring ORM fork** for security implications.
3. **Consider contributing** the forked transaction manager back to Spring as a Hibernate 7
   module, or explore whether `JpaTransactionManager` can be adapted.
