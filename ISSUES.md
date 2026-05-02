# Current Project Status

## grails-datastore-core (COMPLETED & VERIFIED)
*   **Infrastructure**: Implemented the `SessionResolver` interface and `ThreadLocalSessionResolver` implementation.
*   **Encapsulation**: Refactored `DatastoreUtils` and `AbstractDatastore` to use the resolver instead of direct `TransactionSynchronizationManager` (TSM) access.
*   **Stability**: Fixed `DatastoreUtils.bindSession` to be idempotent, preventing `IllegalStateException` during double-binding scenarios (common in TCK lifecycles).
*   **Verification**: All core unit and integration tests are passing.

## grails-data-hibernate7 (IN PROGRESS - RUNTIME FAILURES)
*   **Core Stabilization (COMPLETED)**:
    *   **Dynamic Resolution**: Implemented dynamic `DatastoreResolver` logic in `HibernateGormEnhancer` and all GORM API classes (`Static`, `Instance`, `Validation`).
    *   **Dirty Checking**: Fixed `GrailsEntityDirtinessStrategy` for Hibernate 7.2 `AttributeChecker` API.
    *   **Transaction Lifecycle**: Resolved session leaks in `GrailsHibernateTransactionManager`.
    *   **Resource Management**: Ensured `SessionFactory` closure in `ChildHibernateDatastore`.
*   **Static API Stabilization (COMPLETED)**:
    *   **Null ID Handling**: Updated `HibernateSession.proxy` and `retrieve` to safely handle null identifiers, preventing `IllegalArgumentException`.
    *   **Recursion Fix**: Implemented a re-entrant safe `list()` in `HibernateQuery` using a `wrapping` flag. This prevents infinite recursion when creating `HibernatePagedResultList`.
    *   **Read-Only Support**: Fixed `HibernateGormInstanceApi.read()` to explicitly set instances to read-only in the Hibernate session.
    *   **HQL Support**: Added `HibernateHqlQuery` and implemented `executeQuery`, `executeUpdate`, `find`, and `findAll` (HQL variants) in `HibernateGormStaticApi`.
    *   **Query by Example**: Added `populateQueryByExample` to `HibernateGormStaticApi`.
*   **Compilation Blockers (RESOLVED)**:
    *   **Missing Native SQL methods**: Added `findAllWithNativeSql` and `findWithNativeSql` to `HibernateGormStaticApi` (called by `HibernateEntity` trait).
    *   **Utility Method**: Added `ClassUtils.getIntegerFromMap` in `grails-datastore-core` for type-safe integer extraction under `@CompileStatic`.
    *   **Query list args**: Replaced `query.list(args)` with explicit `max`/`offset` extraction, then `query.list()` (H7 `Query` has no `list(Map)` overload).
    *   **Session.getPersister routing**: Corrected `getPersister(example)` call from `session.getDatastore()` to `session` directly.
    *   **forQualifier field access**: Removed illegal `failOnError`/`markDirty` field copies that don't exist on `GormStaticApi`.
*   **Current Test Failures (3 open — investigated 2026-05-01)**:

    ### Issue H7-1 · `withNewSession` session binding mismatch *(PRIORITY: HIGH)*
    *   **Tests**: `HibernatePersistenceContextInterceptorSpec.test flush and clear` and `HibernateDatastoreSpringInitializerSpec.Test configure multiple data sources`
    *   **Exception**: `org.hibernate.HibernateException: No Session found for current thread`
    *   **Stack pivot**: `HibernateDatastore.withNewSession` (line 978) → `sessionFactory.getCurrentSession()` → `GrailsSessionContext.currentSession()`
    *   **Root cause**: `withNewSession()` opens a new GORM session via `DatastoreUtils.executeWithNewSession`, then inside the callback tries to bind a `SessionHolder` by calling `sessionFactory.getCurrentSession()`. But `GrailsSessionContext.currentSession()` looks in `TransactionSynchronizationManager` for an existing binding — which doesn't exist yet — so it throws. The lambda tries to use `getCurrentSession()` to get the session *before* it has been registered, creating a chicken-and-egg failure.
    *   **Fix**: Replace `sessionFactory.getCurrentSession()` with extraction of the native Hibernate `Session` from the GORM `session` callback parameter (cast to `HibernateSession`, call `getNativeInterface()`), then pass that to `new SessionHolder(nativeSession)`.

    ### Issue H7-2 · H7 `SessionImpl.contains()` throws on secondary-datasource entity *(PRIORITY: MEDIUM)*
    *   **Test**: `MultiDataSourceSessionSpec.CRUD operations work on secondary datasource with OSIV`
    *   **Exception**: `java.lang.IllegalArgumentException: Class '...OsivBook' is not an entity class`
    *   **Stack pivot**: `GrailsHibernateUtil.canModifyReadWriteState(session, target)` → `session.contains(target)` → `SessionImpl.assertInstanceOfEntityType()`
    *   **Root cause**: `OsivBook` has `static mapping = { datasource 'secondary' }`. `GrailsHibernateUtil.setObjectToReadWrite()` calls `sessionFactory.getCurrentSession()` (defaulting to primary) then `session.contains(target)`. In Hibernate 5, `contains()` returned `false` for unknown entity classes. In **Hibernate 7**, `SessionImpl.contains()` calls `assertInstanceOfEntityType()` which **throws `IllegalArgumentException`** when the class is not registered in that session factory's metamodel.
    *   **Fix**: Wrap the `session.contains(target)` call in `canModifyReadWriteState()` with a try-catch for `IllegalArgumentException` and return `false`.

# Current Status (grails-data-simple)

## Resolved Issues
* Resolved `NullPointerException` and `MissingMethodException` during GORM initialization by fixing `SimpleMapDatastore` constructors, multi-tenancy initialization, and ensuring entities are fully registered in the `MappingContext` before GORM enhancer is applied.
* Implemented `MultiTenantCapableDatastore` in `SimpleMapDatastore`.
* Added transaction rollback support in `SimpleMapSession` via `setRollbackOnly()` and overriding `flush()`.
* Fixed `SimpleMapEntityPersister` NPEs and `StackOverflowError` in `ManyToMany` relationships by adding recursion guards and filtering null IDs.
* Handled `SizeEquals`, `SizeGreaterThan`, etc. in `SimpleMapQuery`.
* Added `deleteAll()` support in `SimpleMapQuery`.
* Added subquery (`QueryableCriteria`) evaluation in `SimpleMapQuery`.
* Applied DISCRIMINATOR multi-tenancy filtering directly in `SimpleMapQuery.executeSubQuery`.
* Resolved `WhereMethodSpec` TCK failures (`whereAny`, `ilike`, `in list`) by fixing datastore/session scoping and multi-tenancy rebasing.
* Fixed `SessionCreationEventSpec` and `DirtyCheckingAfterListenerSpec` by correcting `AbstractDatastore` application listener registration and ensuring `withNewSession` correctly binds new sessions.

## Next Steps
* Proceed to Hibernate 5 and MongoDB modules.


# Future Architecture: Core SessionResolver
To eliminate fragmented session and datastore resolution logic across GORM implementations, we plan to introduce a core `SessionResolver` interface.

## Proposal: Core SessionResolver Abstraction
The current GORM design lacks a formal `SessionResolver` interface, forcing each implementation to manually handle binding and lookup via `TransactionSynchronizationManager`.

### The Contract (grails-datastore-core)
```java
public interface SessionResolver<S extends Session> {
    S resolve();
    S resolve(String qualifier);
    void bind(S session);
    void unbind();
}
```

### Transition Strategy
1.  **Augment `Datastore`**: Add `getSessionResolver()` to the base `Datastore` interface.
2.  **Implementation**: Each backend (Hibernate, Mongo, etc.) implements its own `SessionResolver`.
3.  **Refactor**: Replace all direct `TransactionSynchronizationManager` calls in `GormEnhancer`, `GormStaticApi`, and `GrailsHibernateTransactionManager` with `SessionResolver` calls.
4.  **Cleanup**: Remove the temporary patches introduced in the Hibernate 7 migration once the central resolver assumes responsibility for session lifecycle and isolation.


To ensure compatibility with GORM's multi-tenancy, event system, and session management, implementations should follow these patterns learned during the `grails-data-simple` refactoring:

## 1. Multi-Tenancy & Connection Rebasing
In `SCHEMA` or `DATABASE` multi-tenancy modes, child datastores created for specific tenants must report that tenant ID as their default connection name.
*   **Pattern**: Implement an internal `RebasedConnectionSources` that wraps the parent's `ConnectionSources` but overrides `getDefaultConnectionSource()` to return the tenant's `ConnectionSource`.
*   **Rationale**: GORM features like `Tenants.currentId()` and the `@CurrentTenant` AST transformation rely on `datastore.connectionSources.defaultConnectionSource.name` to identify the active tenant.

## 2. Application Event Publisher & Listeners
Implementations must correctly handle listener registration in both Spring-managed and standalone environments.
*   **Pattern**: Use `AbstractDatastore.addApplicationListener(listener)` instead of accessing the publisher directly.
*   **Rationale**: `AbstractDatastore` provides an internal `DefaultApplicationEventPublisher` for standalone use. Direct access to the publisher may bypass this internal implementation, causing events (like `SessionCreationEvent` or `PreInsertEvent`) to be lost.

## 3. Session Management & Events
All session lifecycle events must be published to ensure listeners (like `DomainEventListener`) can react.
*   **Pattern**: Ensure all `connect()` paths eventually call `publishSessionCreationEvent(session)`.
*   **Rationale**: Without these events, GORM cannot initialize domain events (`beforeInsert`, etc.) or handle auto-timestamping for new sessions.

## 4. Transaction Management
*   **Pattern**: Initialize `DatastoreTransactionManager` in the constructor and call `transactionManager.setDatastore(this)`.
*   **Rationale**: Ensures that `@Transactional` methods can correctly bind and manage sessions.

## 5. Scoped GormEnhancer Creation
Avoid creating redundant `GormEnhancer` instances for every child datastore (e.g., in multi-tenancy).
*   **Pattern**: Only instantiate `new GormEnhancer(...)` if the connection name is `ConnectionSource.DEFAULT`.
*   **Rationale**: Redundant enhancers create duplicate static APIs and can cause issues with dynamic finder resolution.

## 6. Tenant-Aware Session Delegation
For datastores using shared state (like maps or caches), the `Session` must delegate data access to the `Datastore`.
*   **Pattern**: In `SimpleMapSession`, always use `datastore.getBackingMap()` and `datastore.getIndices()` instead of local fields.
*   **Rationale**: This allows the `Datastore` to handle the heavy lifting of multi-tenant isolation (e.g., schema-per-tenant maps) while keeping the `Session` logic simple.

# Analysis of HibernateDatastore (Hibernate 7) vs. SimpleMapDatastore

Following the refactoring of `SimpleMapDatastore`, a comparative analysis of `HibernateDatastore` identifies several critical patterns that are missing or inconsistent:

## 1. Multi-Tenancy Connection Name Rebasing
*   **Hibernate Status**: **COMPLETED**. 
*   **Analysis**: `HibernateDatastore` now dynamically resolves its `dataSourceName` from the provided connection sources, and `ChildHibernateDatastore` correctly passes this through.
*   **Impact**: `Tenants.currentId()` now correctly identifies the active tenant in schema/database multi-tenancy.

## 2. Application Event Publisher Inconsistency
*   **Hibernate Status**: **COMPLETED**.
*   **Analysis**: `HibernateDatastore` local `eventPublisher` is now synchronized with the parent `applicationEventPublisher`.
*   **Impact**: Standard GORM listeners now correctly receive Hibernate-specific events.

## 3. Session Creation Events in `withNewSession`
*   **Hibernate Status**: **COMPLETED**.
*   **Analysis**: `HibernateDatastore` and child datastores now use `DatastoreUtils` for session creation.
*   **Impact**: `SessionCreationEvent` is fired correctly, enabling auto-timestamping and domain events in new session blocks.

## 4. Dynamic Finder Resolver Bug
*   **Hibernate Status**: **COMPLETED**.
*   **Analysis**: `HibernateGormEnhancer` now correctly passes the `DatastoreResolver` to dynamic finders.
*   **Impact**: Dynamic finders are now fully multi-datasource and multi-tenant aware.

## 5. Transaction Manager Linkage
*   **Hibernate Status**: **COMPLETED**.
*   **Analysis**: `GrailsHibernateTransactionManager` now correctly manages session binding to datastore instances and handles cleanup.
*   **Impact**: Full isolation in multi-datasource environments.

---

# grails-data-hibernate7-core Test Registry (Binary Search)

_Run 1/4 batches at a time. If a run exceeds 2x expected time (~9 min), stop it, mark those specs SUSPECT, move on._

## Strategy

| Batch | Specs | Expected Time | Stop After |
|-------|-------|--------------|------------|
| Q1 | 1-78 | ~4.5 min | 9 min |
| Q2 | 79-157 | ~4.5 min | 9 min |
| Q3 | 158-235 | ~4.5 min | 9 min |
| Q4 | 236-313 | ~4.5 min | 9 min |

## Timing Baseline

| Metric | Value |
|--------|-------|
| Wall clock per run (fixed startup) | ~151s |
| Test execution time (79 tests) | ~2s |
| Full suite estimate (313 specs) | ~10-11 min |
| 1/4 batch estimate | ~4.5 min |

## Status Legend

| Symbol | Meaning |
|--------|---------|
| PASS | All feature methods passed |
| FAIL | One or more failures |
| SUSPECT | Batch was stopped (hung/timeout) |
| (blank) | Not yet run |

| # | Spec | Status |
|---|------|--------|
| 1 | `grails.gorm.hibernate.mapping.HibernateMappingBuilderSpec` | PASS |
| 2 | `grails.gorm.hibernate.mapping.HibernateOptimisticLockingStyleMappingSpec` | PASS |
| 3 | `grails.gorm.hibernate.mapping.MappingBuilderSpec` | PASS |
| 4 | `grails.gorm.specs.AddToManagedEntitySpec` | FAIL |
| 5 | `grails.gorm.specs.autoimport.AutoImportSpec` | PASS |
| 6 | `grails.gorm.specs.AutoTimestampSpec` | PASS |
| 7 | `grails.gorm.specs.BasicCollectionInQuerySpec` | PASS |
| 8 | `grails.gorm.specs.belongsto.BidirectionalOneToOneWithUniqueSpec` | PASS |
| 9 | `grails.gorm.specs.CascadeToBidirectionalAsssociationSpec` | PASS |
| 10 | `grails.gorm.specs.compositeid.CompositeIdWithDeepOneToManyMappingSpec` | FAIL |
| 11 | `grails.gorm.specs.compositeid.GlobalConstraintWithCompositeIdSpec` | FAIL |
| 12 | `grails.gorm.specs.CompositeIdWithJoinTableSpec` | FAIL |
| 13 | `grails.gorm.specs.CompositeIdWithManyToOneAndSequenceSpec` | PASS |
| 14 | `grails.gorm.specs.CountByWithEmbeddedSpec` | PASS |
| 15 | `grails.gorm.specs.DeleteAllWhereSpec` | PASS |
| 16 | `grails.gorm.specs.detachedcriteria.DetachCriteriaSubquerySpec` | PASS |
| 17 | `grails.gorm.specs.detachedcriteria.DetachedCriteriaCountSpec` | PASS |
| 18 | `grails.gorm.specs.detachedcriteria.DetachedCriteriaJoinSpec` | PASS |
| 19 | `grails.gorm.specs.detachedcriteria.DetachedCriteriaProjectionAliasSpec` | PASS |
| 20 | `grails.gorm.specs.detachedcriteria.DetachedCriteriaProjectionSpec` | PASS |
| 21 | `grails.gorm.specs.DetachedCriteriaProjectionNullAssociationSpec` | PASS |
| 22 | `grails.gorm.specs.dirtychecking.HibernateDirtyCheckingSpec` | PASS |
| 23 | `grails.gorm.specs.dirtychecking.HibernateUpdateFromListenerSpec` | PASS |
| 24 | `grails.gorm.specs.dirtychecking.PropertyFieldSpec` | PASS |
| 25 | `grails.gorm.specs.DomainGetterSpec` | PASS |
| 26 | `grails.gorm.specs.EnumMappingSpec` | PASS |
| 27 | `grails.gorm.specs.events.UpdatePropertyInEventListenerSpec` | PASS |
| 28 | `grails.gorm.specs.ExecuteQueryWithinValidatorSpec` | PASS |
| 29 | `grails.gorm.specs.hasmany.HasManyWithInQuerySpec` | PASS |
| 30 | `grails.gorm.specs.hasmany.ListCollectionSpec` | PASS |
| 31 | `grails.gorm.specs.hasmany.TwoUnidirectionalHasManySpec` | PASS |
| 32 | `grails.gorm.specs.Hibernate7OptimisticLockingSpec` | PASS |
| 33 | `grails.gorm.specs.HibernateEntityTraitGeneratedSpec` | PASS |
| 34 | `grails.gorm.specs.HibernateGormDatastoreSpec` | PASS |
| 35 | `grails.gorm.specs.HibernateMappingFactorySpec` | PASS |
| 36 | `grails.gorm.specs.HibernatePagedResultListSpec` | FAIL |
| 37 | `grails.gorm.specs.hibernatequery.HibernateAssociationQuerySpec` | PASS |
| 38 | `grails.gorm.specs.hibernatequery.HibernateQuerySpec` | PASS |
| 39 | `grails.gorm.specs.hibernatequery.JpaCriteriaQueryCreatorSpec` | PASS |
| 40 | `grails.gorm.specs.hibernatequery.JpaProjectionTranslatorSpec` | PASS |
| 41 | `grails.gorm.specs.hibernatequery.JpaQueryContextSpec` | PASS |
| 42 | `grails.gorm.specs.hibernatequery.PredicateGeneratorSpec` | PASS |
| 43 | `grails.gorm.specs.HibernateValidationSpec` | PASS |
| 44 | `grails.gorm.specs.IdentityEnumTypeSpec` | PASS |
| 45 | `grails.gorm.specs.ImportFromConstraintSpec` | PASS |
| 46 | `grails.gorm.specs.inheritance.SubclassToOneProxySpec` | PASS |
| 47 | `grails.gorm.specs.inheritance.TablePerConcreteClassAndDateCreatedSpec` | PASS |
| 48 | `grails.gorm.specs.inheritance.TablePerConcreteClassImportedSpec` | PASS |
| 49 | `grails.gorm.specs.jpa.SimpleJpaEntitySpec` | PASS |
| 50 | `grails.gorm.specs.LastUpdateWithDynamicUpdateSpec` | FAIL |
| 51 | `grails.gorm.specs.ManyToOneSpec` | PASS |
| 52 | `grails.gorm.specs.mappedby.MultipleOneToOneSpec` | FAIL |
| 53 | `grails.gorm.specs.MultiColumnUniqueConstraintSpec` | PASS |
| 54 | `grails.gorm.specs.multitenancy.MultiTenancyBidirectionalManyToManySpec` | PASS |
| 55 | `grails.gorm.specs.multitenancy.MultiTenancyUnidirectionalOneToManySpec` | FAIL |
| 56 | `grails.gorm.specs.NullableAndLengthSpec` | PASS |
| 57 | `grails.gorm.specs.NullValueEqualSpec` | PASS |
| 58 | `grails.gorm.specs.PagedResultListSpec` | FAIL |
| 59 | `grails.gorm.specs.perf.JoinPerfSpec` | FAIL |
| 60 | `grails.gorm.specs.proxy.Hibernate7GroovyProxySpec` | FAIL |
| 61 | `grails.gorm.specs.ReadOperationSpec` | PASS |
| 62 | `grails.gorm.specs.RLikeHibernate7Spec` | PASS |
| 63 | `grails.gorm.specs.RLikeSpec` | PASS |
| 64 | `grails.gorm.specs.SaveWithExistingValidationErrorSpec` | PASS |
| 65 | `grails.gorm.specs.SchemaNameSpec` | PASS |
| 66 | `grails.gorm.specs.SequenceIdSpec` | PASS |
| 67 | `grails.gorm.specs.services.DataServiceSpec` | FAIL |
| 68 | `grails.gorm.specs.sessioncontext.GrailsSessionContextSpec` | PASS |
| 69 | `grails.gorm.specs.SizeConstraintSpec` | PASS |
| 70 | `grails.gorm.specs.softdelete.SoftDeleteSpec` | PASS |
| 71 | `grails.gorm.specs.SqlQuerySpec` | FAIL |
| 72 | `grails.gorm.specs.SubclassMultipleListCollectionSpec` | PASS |
| 73 | `grails.gorm.specs.SubqueryAliasSpec` | PASS |
| 74 | `grails.gorm.specs.TablePerSubClassAndEmbeddedSpec` | PASS |
| 75 | `grails.gorm.specs.ToOneProxySpec` | PASS |
| 76 | `grails.gorm.specs.traits.InterfacePropertySpec` | PASS |
| 77 | `grails.gorm.specs.traits.TraitPropertySpec` | PASS |
| 78 | `grails.gorm.specs.TwoBidirectionalOneToManySpec` | PASS |
| 79 | `grails.gorm.specs.txs.CustomIsolationLevelSpec` | PASS |
| 80 | `grails.gorm.specs.txs.TransactionalWithinReadOnlySpec` | PASS |
| 81 | `grails.gorm.specs.txs.TransactionPropagationSpec` | PASS |
| 82 | `grails.gorm.specs.UniqueConstraintHibernateSpec` | PASS |
| 83 | `grails.gorm.specs.UniqueWithMultipleDataSourcesSpec` | FAIL |
| 84 | `grails.gorm.specs.uuid.UuidInsertSpec` | PASS |
| 85 | `grails.gorm.specs.validation.BeanValidationSpec` | PASS |
| 86 | `grails.gorm.specs.validation.CascadeValidationSpec` | PASS |
| 87 | `grails.gorm.specs.validation.DeepValidationSpec` | FAIL |
| 88 | `grails.gorm.specs.validation.EmbeddedWithValidationExceptionSpec` | FAIL |
| 89 | `grails.gorm.specs.validation.SaveWithInvalidEntitySpec` | PASS |
| 90 | `grails.gorm.specs.validation.SkipValidationSpec` | FAIL |
| 91 | `grails.gorm.specs.validation.UniqueFalseConstraintSpec` | PASS |
| 92 | `grails.gorm.specs.validation.UniqueInheritanceSpec` | PASS |
| 93 | `grails.gorm.specs.validation.UniqueWithHasOneSpec` | PASS |
| 94 | `grails.gorm.specs.validation.UniqueWithinGroupSpec` | FAIL |
| 95 | `grails.gorm.specs.WhereQueryBugFixSpec` | PASS |
| 96 | `grails.gorm.specs.WhereQueryOldIssueVerificationSpec` | PASS |
| 97 | `grails.gorm.specs.WhereQueryWithAssociationSortSpec` | PASS |
| 98 | `grails.gorm.specs.WithNewSessionAndExistingTransactionSpec` | FAIL |
| 99 | `grails.orm.CriteriaMethodInvokerSpec` | PASS |
| 100 | `grails.orm.HibernateCriteriaBuilderDirectSpec` | PASS |
| 101 | `grails.orm.HibernateCriteriaBuilderSpec` | PASS |
| 102 | `org.grails.datastore.gorm.GormEnhancerCleanupSpec` | FAIL |
| 103 | `org.grails.datastore.mapping.model.PersistentPropertySpec` | PASS |
| 104 | `org.grails.orm.hibernate.access.TraitPropertyAccessStrategySpec` | PASS |
| 105 | `org.grails.orm.hibernate.cfg.CacheConfigSpec` | PASS |
| 106 | `org.grails.orm.hibernate.cfg.ColumnConfigSpec` | PASS |
| 107 | `org.grails.orm.hibernate.cfg.CompositeIdentitySpec` | PASS |
| 108 | `org.grails.orm.hibernate.cfg.DiscriminatorConfigSpec` | PASS |
| 109 | `org.grails.orm.hibernate.cfg.domainbinding.BackticksRemoverSpec` | PASS |
| 110 | `org.grails.orm.hibernate.cfg.domainbinding.BasicValueCreatorSpec` | PASS |
| 111 | `org.grails.orm.hibernate.cfg.domainbinding.binder.ClassPropertiesBinderSpec` | PASS |
| 112 | `org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdaterSpec` | PASS |
| 113 | `org.grails.orm.hibernate.cfg.domainbinding.binder.ConfiguredDiscriminatorBinderSpec` | PASS |
| 114 | `org.grails.orm.hibernate.cfg.domainbinding.binder.DefaultDiscriminatorBinderSpec` | PASS |
| 115 | `org.grails.orm.hibernate.cfg.domainbinding.binder.DiscriminatorPropertyBinderSpec` | PASS |
| 116 | `org.grails.orm.hibernate.cfg.domainbinding.binder.JoinedSubClassBinderSpec` | PASS |
| 117 | `org.grails.orm.hibernate.cfg.domainbinding.binder.RootBinderSpec` | PASS |
| 118 | `org.grails.orm.hibernate.cfg.domainbinding.binder.RootPersistentClassCommonValuesBinderSpec` | PASS |
| 119 | `org.grails.orm.hibernate.cfg.domainbinding.binder.SingleTableSubclassBinderSpec` | PASS |
| 120 | `org.grails.orm.hibernate.cfg.domainbinding.binder.SubClassBinderSpec` | PASS |
| 121 | `org.grails.orm.hibernate.cfg.domainbinding.binder.SubclassMappingBinderSpec` | PASS |
| 122 | `org.grails.orm.hibernate.cfg.domainbinding.binder.UnionSubclassBinderSpec` | PASS |
| 123 | `org.grails.orm.hibernate.cfg.domainbinding.CascadeBehaviorEnumSpec` | PASS |
| 124 | `org.grails.orm.hibernate.cfg.domainbinding.CascadeBehaviorFetcherSpec` | PASS |
| 125 | `org.grails.orm.hibernate.cfg.domainbinding.CascadeBehaviorPersisterSpec` | PASS |
| 126 | `org.grails.orm.hibernate.cfg.domainbinding.ClassBinderSpec` | PASS |
| 127 | `org.grails.orm.hibernate.cfg.domainbinding.CollectionBinderSpec` | PASS |
| 128 | `org.grails.orm.hibernate.cfg.domainbinding.CollectionForPropertyConfigBinderSpec` | PASS |
| 129 | `org.grails.orm.hibernate.cfg.domainbinding.collectionType.BagCollectionTypeSpec` | PASS |
| 130 | `org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolderSpec` | PASS |
| 131 | `org.grails.orm.hibernate.cfg.domainbinding.collectionType.ListCollectionTypeSpec` | PASS |
| 132 | `org.grails.orm.hibernate.cfg.domainbinding.collectionType.MapCollectionTypeSpec` | PASS |
| 133 | `org.grails.orm.hibernate.cfg.domainbinding.collectionType.SetCollectionTypeSpec` | PASS |
| 134 | `org.grails.orm.hibernate.cfg.domainbinding.collectionType.SortedSetCollectionTypeSpec` | PASS |
| 135 | `org.grails.orm.hibernate.cfg.domainbinding.ColumnBinderSpec` | PASS |
| 136 | `org.grails.orm.hibernate.cfg.domainbinding.ColumnConfigToColumnBinderSpec` | PASS |
| 137 | `org.grails.orm.hibernate.cfg.domainbinding.ColumnNameForPropertyAndPathFetcherSpec` | PASS |
| 138 | `org.grails.orm.hibernate.cfg.domainbinding.ComponentBinderSpec` | PASS |
| 139 | `org.grails.orm.hibernate.cfg.domainbinding.CompositeIdBinderSpec` | PASS |
| 140 | `org.grails.orm.hibernate.cfg.domainbinding.CompositeIdentifierToManyToOneBinderSpec` | PASS |
| 141 | `org.grails.orm.hibernate.cfg.domainbinding.ConfigureDerivedPropertiesConsumerSpec` | PASS |
| 142 | `org.grails.orm.hibernate.cfg.domainbinding.CreateKeyForPropsSpec` | PASS |
| 143 | `org.grails.orm.hibernate.cfg.domainbinding.DefaultColumnNameFetcherSpec` | PASS |
| 144 | `org.grails.orm.hibernate.cfg.domainbinding.EnumTypeBinderSpec` | PASS |
| 145 | `org.grails.orm.hibernate.cfg.domainbinding.ForeignKeyColumnCountCalculatorSpec` | PASS |
| 146 | `org.grails.orm.hibernate.cfg.domainbinding.ForeignKeyOneToOneBinderSpec` | PASS |
| 147 | `org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnumSpec` | PASS |
| 148 | `org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceStyleGeneratorSpec` | PASS |
| 149 | `org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapperSpec` | PASS |
| 150 | `org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsTableGeneratorSpec` | PASS |
| 151 | `org.grails.orm.hibernate.cfg.domainbinding.GrailsEnumTypeSpec` | PASS |
| 152 | `org.grails.orm.hibernate.cfg.domainbinding.GrailsIdentityGeneratorSpec` | PASS |
| 153 | `org.grails.orm.hibernate.cfg.domainbinding.GrailsNativeGeneratorSpec` | PASS |
| 154 | `org.grails.orm.hibernate.cfg.domainbinding.GrailsPropertyBinderSpec` | PASS |
| 155 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociationSpec` | PASS |
| 156 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicPropertySpec` | PASS |
| 157 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCompositeIdentityPropertySpec` | PASS |
| 158 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCustomEnumPropertySpec` | PASS |
| 159 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCustomPropertySpec` | PASS |
| 160 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionPropertySpec` | PASS |
| 161 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedPersistentEntitySpec` | PASS |
| 162 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumPropertySpec` | PASS |
| 163 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityMappingSpec` | PASS |
| 164 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyPropertySpec` | PASS |
| 165 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOnePropertySpec` | PASS |
| 166 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingKeywordSpec` | PASS |
| 167 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyPropertySpec` | PASS |
| 168 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneValidationSpec` | PASS |
| 169 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntitySpec` | PASS |
| 170 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentPropertySpec` | PASS |
| 171 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleEnumPropertySpec` | PASS |
| 172 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityPropertySpec` | PASS |
| 173 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimplePropertySpec` | PASS |
| 174 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyCollectionPropertySpec` | PASS |
| 175 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityPropertySpec` | PASS |
| 176 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyPropertySpec` | PASS |
| 177 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOnePropertySpec` | PASS |
| 178 | `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateVersionPropertySpec` | PASS |
| 179 | `org.grails.orm.hibernate.cfg.domainbinding.HibernateOneToOnePropertySpec` | PASS |
| 180 | `org.grails.orm.hibernate.cfg.domainbinding.IdentityBinderSpec` | PASS |
| 181 | `org.grails.orm.hibernate.cfg.domainbinding.IncrementGeneratorSpec` | PASS |
| 182 | `org.grails.orm.hibernate.cfg.domainbinding.IndexBinderSpec` | PASS |
| 183 | `org.grails.orm.hibernate.cfg.domainbinding.LogCascadeMappingSpec` | PASS |
| 184 | `org.grails.orm.hibernate.cfg.domainbinding.ManyToOneBinderSpec` | PASS |
| 185 | `org.grails.orm.hibernate.cfg.domainbinding.ManyToOneValuesBinderSpec` | PASS |
| 186 | `org.grails.orm.hibernate.cfg.domainbinding.NamespaceNameExtractorSpec` | PASS |
| 187 | `org.grails.orm.hibernate.cfg.domainbinding.NamingStrategyProviderSpec` | PASS |
| 188 | `org.grails.orm.hibernate.cfg.domainbinding.NamingStrategyWrapperSpec` | PASS |
| 189 | `org.grails.orm.hibernate.cfg.domainbinding.NaturalIdentifierBinderSpec` | PASS |
| 190 | `org.grails.orm.hibernate.cfg.domainbinding.NumericColumnConstraintsBinderSpec` | PASS |
| 191 | `org.grails.orm.hibernate.cfg.domainbinding.OneToOneBinderSpec` | PASS |
| 192 | `org.grails.orm.hibernate.cfg.domainbinding.OrderByClauseBuilderSpec` | PASS |
| 193 | `org.grails.orm.hibernate.cfg.domainbinding.PropertyBinderSpec` | PASS |
| 194 | `org.grails.orm.hibernate.cfg.domainbinding.PropertyFromValueCreatorSpec` | PASS |
| 195 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.BasicCollectionElementBinderSpec` | PASS |
| 196 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.BidirectionalMapElementBinderSpec` | PASS |
| 197 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.BidirectionalOneToManyLinkerSpec` | PASS |
| 198 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionKeyBinderSpec` | PASS |
| 199 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionKeyColumnUpdaterSpec` | PASS |
| 200 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionSecondPassBinderSpec` | PASS |
| 201 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionWithJoinTableBinderSpec` | PASS |
| 202 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.DependentKeyValueBinderSpec` | PASS |
| 203 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.HibernateToManyEntityOrderByBinderSpec` | PASS |
| 204 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.ListSecondPassBinderSpec` | PASS |
| 205 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.ManyToOneElementBinderSpec` | PASS |
| 206 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.MapSecondPassBinderSpec` | PASS |
| 207 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.PrimaryKeyValueCreatorSpec` | PASS |
| 208 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.ToManyEntityMultiTenantFilterBinderSpec` | PASS |
| 209 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.UnidirectionalOneToManyBinderSpec` | PASS |
| 210 | `org.grails.orm.hibernate.cfg.domainbinding.secondpass.UnidirectionalOneToManyInverseValuesBinderSpec` | PASS |
| 211 | `org.grails.orm.hibernate.cfg.domainbinding.SequenceGeneratorsSpec` | PASS |
| 212 | `org.grails.orm.hibernate.cfg.domainbinding.SimpleIdBinderSpec` | PASS |
| 213 | `org.grails.orm.hibernate.cfg.domainbinding.SimpleValueBinderSpec` | PASS |
| 214 | `org.grails.orm.hibernate.cfg.domainbinding.SimpleValueColumnBinderSpec` | PASS |
| 215 | `org.grails.orm.hibernate.cfg.domainbinding.SimpleValueColumnFetcherSpec` | PASS |
| 216 | `org.grails.orm.hibernate.cfg.domainbinding.StringColumnConstraintsBinderSpec` | PASS |
| 217 | `org.grails.orm.hibernate.cfg.domainbinding.TableForManyCalculatorSpec` | PASS |
| 218 | `org.grails.orm.hibernate.cfg.domainbinding.UniqueKeyForColumnsCreatorSpec` | PASS |
| 219 | `org.grails.orm.hibernate.cfg.domainbinding.UniqueNameGeneratorSpec` | PASS |
| 220 | `org.grails.orm.hibernate.cfg.domainbinding.util.GeneratorCreationContextWrapperSpec` | PASS |
| 221 | `org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolverSpec` | PASS |
| 222 | `org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinderSpec` | PASS |
| 223 | `org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterDefinitionBinderSpec` | PASS |
| 224 | `org.grails.orm.hibernate.cfg.domainbinding.VersionBinderSpec` | PASS |
| 225 | `org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntitySpec` | PASS |
| 226 | `org.grails.orm.hibernate.cfg.GrailsHibernatePersistentPropertySpec` | PASS |
| 227 | `org.grails.orm.hibernate.cfg.GrailsHibernateUtilSpec` | PASS |
| 228 | `org.grails.orm.hibernate.cfg.HibernateMappingContextConfigurationSpec` | PASS |
| 229 | `org.grails.orm.hibernate.cfg.HibernateMappingContextSpec` | PASS |
| 230 | `org.grails.orm.hibernate.cfg.IdentitySpec` | PASS |
| 231 | `org.grails.orm.hibernate.cfg.MappingCacheHolderSpec` | PASS |
| 232 | `org.grails.orm.hibernate.cfg.MappingSpec` | PASS |
| 233 | `org.grails.orm.hibernate.cfg.NaturalIdSpec` | PASS |
| 234 | `org.grails.orm.hibernate.cfg.PropertyConfigSpec` | PASS |
| 235 | `org.grails.orm.hibernate.cfg.PropertyDefinitionDelegateSpec` | PASS |
| 236 | `org.grails.orm.hibernate.cfg.SortConfigSpec` | PASS |
| 237 | `org.grails.orm.hibernate.cfg.TableSpec` | PASS |
| 238 | `org.grails.orm.hibernate.ChildHibernateDatastoreUnitSpec` | FAIL |
| 239 | `org.grails.orm.hibernate.CloseSuppressingInvocationHandlerSpec` | PASS |
| 240 | `org.grails.orm.hibernate.compiler.HibernateEntityTransformationSpec` | PASS |
| 241 | `org.grails.orm.hibernate.connections.DataServiceDatasourceInheritanceSpec` | PASS |
| 242 | `org.grails.orm.hibernate.connections.DataServiceMultiDataSourceSpec` | PASS |
| 243 | `org.grails.orm.hibernate.connections.DataServiceMultiTenantMultiDataSourceSpec` | PASS |
| 244 | `org.grails.orm.hibernate.connections.DataSourceConnectionSourceFactorySpec` | PASS |
| 245 | `org.grails.orm.hibernate.connections.HibernateConnectionSourceFactorySpec` | PASS |
| 246 | `org.grails.orm.hibernate.connections.HibernateConnectionSourceSettingsBuilderSpec` | PASS |
| 247 | `org.grails.orm.hibernate.connections.HibernateConnectionSourceSettingsSpec` | PASS |
| 248 | `org.grails.orm.hibernate.connections.MultipleDataSourceConnectionsSpec` | PASS |
| 249 | `org.grails.orm.hibernate.connections.MultipleDataSourceMetadataSpec` | PASS |
| 250 | `org.grails.orm.hibernate.connections.MultipleDataSourcesWithCachingSpec` | PASS |
| 251 | `org.grails.orm.hibernate.connections.MultipleDataSourcesWithEventsSpec` | PASS |
| 252 | `org.grails.orm.hibernate.connections.PartitionedMultiTenancySpec` | PASS |
| 253 | `org.grails.orm.hibernate.connections.SchemaMultiTenantSpec` | PASS |
| 254 | `org.grails.orm.hibernate.connections.SecondLevelCacheSpec` | PASS |
| 255 | `org.grails.orm.hibernate.connections.SingleTenantSpec` | PASS |
| 256 | `org.grails.orm.hibernate.connections.WhereQueryMultiDataSourceSpec` | PASS |
| 257 | `org.grails.orm.hibernate.DefaultConstraintsSpec` | PASS |
| 258 | `org.grails.orm.hibernate.dirty.GrailsEntityDirtinessStrategySpec` | PASS |
| 259 | `org.grails.orm.hibernate.event.listener.HibernateEventListenerSpec` | PASS |
| 260 | `org.grails.orm.hibernate.EventListenerIntegratorSpec` | PASS |
| 261 | `org.grails.orm.hibernate.exceptions.GrailsQueryExceptionSpec` | PASS |
| 262 | `org.grails.orm.hibernate.ExistsCrossJoinSpec` | FAIL |
| 263 | `org.grails.orm.hibernate.GrailsHibernateTemplateSpec` | PASS |
| 264 | `org.grails.orm.hibernate.HibernateDatastoreIntegrationSpec` | FAIL |
| 265 | `org.grails.orm.hibernate.HibernateDatastoreMultiTenancySpec` | PASS |
| 266 | `org.grails.orm.hibernate.HibernateDatastoreSchemaMultiTenancySpec` | FAIL |
| 267 | `org.grails.orm.hibernate.HibernateDatastoreSpec` | FAIL |
| 268 | `org.grails.orm.hibernate.HibernateDetachedCriteriaSpec` | PASS |
| 269 | `org.grails.orm.hibernate.HibernateEventListenersSpec` | PASS |
| 270 | `org.grails.orm.hibernate.HibernateGormEnhancerSpec` | FAIL |
| 271 | `org.grails.orm.hibernate.HibernateGormInstanceApiSpec` | FAIL |
| 272 | `org.grails.orm.hibernate.HibernateGormStaticApiSpec` | FAIL |
| 273 | `org.grails.orm.hibernate.HibernateGormValidationApiSpec` | PASS |
| 274 | `org.grails.orm.hibernate.HibernateSessionSpec` | PASS |
| 275 | `org.grails.orm.hibernate.InstanceApiHelperSpec` | PASS |
| 276 | `org.grails.orm.hibernate.multitenancy.MultiTenantEventListenerSpec` | PASS |
| 277 | `org.grails.orm.hibernate.proxy.ByteBuddyGroovyInterceptorSpec` | PASS |
| 278 | `org.grails.orm.hibernate.proxy.ByteBuddyGroovyProxyFactorySpec` | PASS |
| 279 | `org.grails.orm.hibernate.proxy.GrailsBytecodeProviderSpec` | PASS |
| 280 | `org.grails.orm.hibernate.proxy.GroovyProxyInterceptorLogicSpec` | PASS |
| 281 | `org.grails.orm.hibernate.proxy.HibernateProxyHandler7Spec` | PASS |
| 282 | `org.grails.orm.hibernate.proxy.SimpleHibernateProxyHandlerSpec` | PASS |
| 283 | `org.grails.orm.hibernate.query.AliasRegistrySpec` | PASS |
| 284 | `org.grails.orm.hibernate.query.DetachedAssociationFunctionSpec` | PASS |
| 285 | `org.grails.orm.hibernate.query.ExpressionResolverSpec` | PASS |
| 286 | `org.grails.orm.hibernate.query.GrailsQueryFlushModeSpec` | PASS |
| 287 | `org.grails.orm.hibernate.query.HibernateHqlQueryCreatorSpec` | PASS |
| 288 | `org.grails.orm.hibernate.query.HqlListQueryBuilderSpec` | PASS |
| 289 | `org.grails.orm.hibernate.query.HqlQueryContextSpec` | PASS |
| 290 | `org.grails.orm.hibernate.query.HqlQueryDelegateSpec` | PASS |
| 291 | `org.grails.orm.hibernate.query.HqlQueryMethodsSpec` | PASS |
| 292 | `org.grails.orm.hibernate.query.JoinTrackerSpec` | PASS |
| 293 | `org.grails.orm.hibernate.query.JpaProjectionAdapterSpec` | PASS |
| 294 | `org.grails.orm.hibernate.query.JpaQueryContextSpec` | PASS |
| 295 | `org.grails.orm.hibernate.query.MutationHqlQuerySpec` | PASS |
| 296 | `org.grails.orm.hibernate.query.MutationQueryDelegateSpec` | PASS |
| 297 | `org.grails.orm.hibernate.query.PropertyReferenceSpec` | PASS |
| 298 | `org.grails.orm.hibernate.query.RegexDialectPatternSpec` | PASS |
| 299 | `org.grails.orm.hibernate.query.SelectHqlQuerySpec` | PASS |
| 300 | `org.grails.orm.hibernate.query.SelectQueryDelegateSpec` | PASS |
| 301 | `org.grails.orm.hibernate.SchemaTenantDataSourceSpec` | PASS |
| 302 | `org.grails.orm.hibernate.SchemaTenantGormEnhancerSpec` | PASS |
| 303 | `org.grails.orm.hibernate.support.ClosureEventListenerSpec` | PASS |
| 304 | `org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptorSpec` | PASS |
| 305 | `org.grails.orm.hibernate.support.hibernate7.ConfigurableJtaPlatformSpec` | PASS |
| 306 | `org.grails.orm.hibernate.support.hibernate7.HibernateExceptionTranslatorSpec` | PASS |
| 307 | `org.grails.orm.hibernate.support.hibernate7.HibernateObjectRetrievalFailureExceptionSpec` | PASS |
| 308 | `org.grails.orm.hibernate.support.hibernate7.HibernateTransactionManagerSpec` | PASS |
| 309 | `org.grails.orm.hibernate.support.hibernate7.LocalSessionFactorySpec` | PASS |
| 310 | `org.grails.orm.hibernate.support.HibernateDatastoreConnectionSourcesRegistrarSpec` | PASS |
| 311 | `org.grails.orm.hibernate.support.HibernateRuntimeUtilsSpec` | PASS |
| 312 | `org.grails.orm.hibernate.support.HibernateVersionSupportSpec` | PASS |
| 313 | `org.grails.orm.hibernate.support.SoftKeySpec` | PASS |
___BEGIN___COMMAND_DONE_MARKER___0
