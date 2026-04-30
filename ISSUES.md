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
* Proceed to Hibernate 7, Hibernate 5, and MongoDB modules.


# Implementation Guidelines for `AbstractDatastore`

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
*   **Hibernate Status**: **MISSING**. 
*   **Analysis**: `HibernateDatastore` hardcodes `this.dataSourceName = ConnectionSource.DEFAULT` in its constructors. even `ChildHibernateDatastore` (used for tenant-specific instances) inherits this behavior.
*   **Impact**: `Tenants.currentId(datastore)` will always return `"DEFAULT"`, which breaks functionality relying on tenant identification, such as the `@CurrentTenant` AST transformation and multi-tenant GORM events.
*   **Comparison**: `SimpleMapDatastore` was fixed to set its connection name from `connectionSources.getDefaultConnectionSource().getName()`.

## 2. Application Event Publisher Inconsistency
*   **Hibernate Status**: **INCONSISTENT**.
*   **Analysis**: `HibernateDatastore` defines a duplicate `protected final ConfigurableApplicationEventPublisher eventPublisher` field. While it overrides `getApplicationEventPublisher()` to return this field, it passes `null` to `super(...)`.
*   **Impact**: `AbstractDatastore` initializes its own internal `applicationEventPublisher`. Standard GORM listeners added via `datastore.addApplicationListener(listener)` go to the internal publisher, but Hibernate events are published to the local field. The two never meet, breaking many standard listeners.
*   **Comparison**: `SimpleMapDatastore` explicitly synchronizes these fields in the constructor.

## 3. Session Creation Events in `withNewSession`
*   **Hibernate Status**: **MISSING**.
*   **Analysis**: Both `HibernateDatastore` and `HibernateGormStaticApi` implement `withNewSession` by calling native Hibernate `sessionFactory.openSession()`. 
*   **Impact**: This completely bypasses `AbstractDatastore.connect()`, meaning `SessionCreationEvent` is never fired. Any logic relying on session initialization (like auto-timestamping or custom session-scoped setup) will fail in `withNewSession` blocks.
*   **Comparison**: `SimpleMapDatastore` was updated to use `DatastoreUtils.executeWithNewSession`, which ensures the standard GORM `connect()` lifecycle is followed.

## 4. Dynamic Finder Resolver Bug
*   **Hibernate Status**: **BUGGY**.
*   **Analysis**: `HibernateGormEnhancer.getStaticApi` overrides the core logic but incorrectly initializes dynamic finders using `StaticDatastoreResolver(hibernateDatastore)` instead of the provided resolver.
*   **Impact**: Even if a tenant-aware `DatastoreResolver` is provided to the API, the dynamic finders themselves remain hardcoded to the default datastore.
*   **Comparison**: Core `GormEnhancer` (and thus `SimpleMapDatastore`) uses `createDynamicFinders(resolver, ...)` to ensure finders are tenant-aware.

## 5. Transaction Manager Linkage
*   **Hibernate Status**: **OK (but suboptimal)**.
*   **Analysis**: `GrailsHibernateTransactionManager` is used, but it doesn't explicitly call `setDatastore(this)` like the `DatastoreTransactionManager` does. 
*   **Impact**: While Hibernate handles this via `SessionFactory` lookups, it diverges from the pattern established in other GORM implementations.

# Implementation Path for Hibernate 7 Fixes

To resolve the identified inconsistencies, the following code changes should be applied to the `grails-data-hibernate7` module:

## 1. Fix Connection Name & Event Publisher in `HibernateDatastore.java`
*   **Change**: Update the main constructor to dynamically resolve the `dataSourceName`.
    ```java
    // Instead of: this.dataSourceName = ConnectionSource.DEFAULT;
    this.dataSourceName = connectionSources.getDefaultConnectionSource().getName();
    ```
*   **Change**: Synchronize the local `eventPublisher` with the parent `applicationEventPublisher`.
    ```java
    // In constructor:
    this.eventPublisher = eventPublisher;
    setApplicationEventPublisher(eventPublisher); // Sync with super.applicationEventPublisher
    ```
*   **Change**: Refactor `withNewSession` to use `DatastoreUtils`.
    ```java
    @Override
    public <T> T withNewSession(final Closure<T> callable) {
        return DatastoreUtils.executeWithNewSession(this, (SessionCallback<T>) session -> {
            Closure<T> multiTenantCallable = prepareMultiTenantClosure(callable);
            return multiTenantCallable.call(session);
        });
    }
    ```

## 2. Fix Dynamic Finder Resolution in `HibernateGormEnhancer.groovy`
*   **Change**: Update `getStaticApi` to pass the `resolver` to `createDynamicFinders`.
    ```groovy
    @Override
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, DatastoreResolver resolver, String qualifier) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        new HibernateGormStaticApi<D>(
                cls,
                hibernateDatastore.mappingContext,
                createDynamicFinders(resolver, hibernateDatastore.mappingContext), // Fix: pass resolver
                resolver,
                qualifier,
                hibernateDatastore.mappingContext.getMappingFactory().getClass().getClassLoader()
        )
    }
    ```

## 3. Ensure Child Datastore Rebasing in `ChildHibernateDatastore.java`
*   **Change**: Verify the constructor correctly passes the rebased connection source.
    ```java
    public ChildHibernateDatastore(...) {
        super(connectionSources, mappingContext, eventPublisher, ...);
        // HibernateDatastore constructor will now pick up the rebased name automatically
    }
    ```

## 4. Update GORM Static API Delegation in `HibernateGormStaticApi.groovy`
*   **Change**: Ensure `withNewSession(Serializable tenantId, ...)` uses the correctly resolved datastore.
    ```groovy
    @Override
    def <T> T withNewSession(Serializable tenantId, Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore.getDatastoreForTenantId(tenantId)
        hibernateDatastore.withNewSession(callable)
    }
    ```

## 5. Verify Event Propagation
*   Run `SessionCreationEventSpec` (TCK) against Hibernate 7 to confirm that `withNewSession` now triggers the expected events.
*   Run `CurrentTenantTransformSpec` to confirm that `Tenants.currentId()` returns the rebased connection name.



