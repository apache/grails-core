# Current Project Status

## grails-datastore-core (COMPLETED & VERIFIED)
*   **Infrastructure**: Implemented the `SessionResolver` interface and `ThreadLocalSessionResolver` implementation.
*   **Encapsulation**: Refactored `DatastoreUtils` and `AbstractDatastore` to use the resolver instead of direct `TransactionSynchronizationManager` (TSM) access.
*   **Stability**: Fixed `DatastoreUtils.bindSession` to be idempotent, preventing `IllegalStateException` during double-binding scenarios (common in TCK lifecycles).
*   **Verification**: All core unit and integration tests are passing.

## grails-data-hibernate7 (COMPLETED & VERIFIED)
*   **Dynamic Resolution**: Implemented dynamic `DatastoreResolver` logic in `HibernateGormEnhancer` and all GORM API classes (`Static`, `Instance`, `Validation`). This ensures that operations like `count()`, `save()`, and `validate()` correctly target the active connection in multi-datasource scenarios.
*   **Dirty Checking Stabilization**: Fixed `GrailsEntityDirtinessStrategy` by updating it to the Hibernate 7.2 `AttributeChecker` API. Corrected `shouldFlush` logic in `HibernateGormInstanceApi` to properly handle the `flush: true` argument.
*   **Transaction Lifecycle**: Resolved a major session leak in `GrailsHibernateTransactionManager` by ensuring datastore-bound sessions are correctly unbound upon transaction completion.
*   **Resource Management**: Updated `HibernateDatastore` and `ChildHibernateDatastore` to ensure all session factories are properly closed during destruction, enabling clean database shutdowns in TCK tests.
*   **Visibility & Compilation**: Fixed multiple static compilation errors and expanded `HibernateTransactionObject` visibility to `public static` to support cross-module access.
*   **Verification**: `HibernateDirtyCheckingSpec`, `DomainMultiDataSourceSpec`, and the new `HibernateTransactionManagerSpec` are all passing (100%).

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
