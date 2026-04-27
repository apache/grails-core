# GORM 7 Stateless Refactor - Session Checkpoint

## Architectural Mandate: The O(M+N) Memory Problem
- **Problem**: Historically, GORM's Simple Map implementation (used for TCK testing) created $M \times N$ separate maps (where M is connections and N is entities). In large-scale multi-tenant or multi-datasource environments, this caused exponential memory growth and "connection leakage."
- **Solution**: Implemented a **Hybrid Isolation Model** in `SimpleMapDatastore`. 
    - **Physical Tier**: A single shared map structure.
    - **Logical Tier**: Partitioning via `ScopedMap`. Scale is now linear $O(M+N)$.
    - **Isolation Mode**: In `DISCRIMINATOR` multi-tenancy, `SimpleMapSession` now dynamically resolves the backing map for the current tenant at runtime.

## Core Stability & Event Infrastructure
- **SimpleMapDatastore Lifecycle Hardening**: 
    - **Early Mode Binding**: Refactored the internal constructor to set `multiTenancyMode` on the `MappingContext` at the very beginning. This ensures that any entities added during `super()` or early initialization (common in GORM Services) are correctly identified as multi-tenant.
    - **Dynamic Entity Registration**: Implemented a `MappingContext.Listener` to automatically register newly added persistent entities with the `GormEnhancer` and `GormRegistry`.
    - **Listener Propagation**: Ensured that all child datastores created for specific tenants share the parent's `ApplicationEventPublisher` and explicitly register the `MultiTenantEventListener`.
- **Multi-Tenancy Event Refinement**:
    - **Equality-Based Validation**: Updated `MultiTenantEventListener.isValidSource` to use `equals()` for datastore comparisons, resolving instance mismatches between parent and child datastores.
    - **Dynamic Tenant Identification**: Refactored `AbstractPersistentEntity` to resolve the `TenantId` property dynamically and re-evaluate `multiTenancyEnabled` status against the context's current mode during `initialize()`.
- **Tenants ThreadLocal & Recursion Fix**: 
    - **StackOverflow Prevention**: Fixed a circular delegation bug in `DatastoreLocator` within `Tenants.groovy`.
    - **Dual-Binding Implementation**: Refactored `Tenants.groovy` to use a `Map<Object, Serializable>` in its `ThreadLocal`. Binding tenant IDs to *both* the datastore class and the specific instance fixes the TCK "Zero Results" bug.

## TCK Progress & Metrics
- **Failures remaining**: ~25 (Major drop following the `PartitionMultiTenancySpec` and `Tenants` fixes).
- **Passing Highlights**: `PartitionMultiTenancySpec` (Full DISCRIMINATOR support), `SchemaPerTenantSpec`, `DomainEventsSpec`, `DirtyCheckingSpec`, `PagedResultSpec`.
- **Active Focus**: `Association Projections` and `Range Queries`.

## Pending Fixes / Next Steps
1.  **Range Queries**: Investigate why `between` queries in `SimpleMapQuery` are failing.
2.  **Association Projections**: Resolve result size mismatches in `NestedAssociationQuerySpec`.
3.  **H5/H7 Compilation**: Ensure my `ValidationEvent` and `CascadingValidator` import fixes are stable across both Hibernate core modules.

## File System State
- **CWD**: `/Users/walterduquedeestrada/IdeaProjects/grails-core`
- **Critical Modified Files**: 
    - `grails-data-simple/src/main/groovy/org/grails/datastore/mapping/simple/SimpleMapDatastore.java`
    - `grails-datastore-core/src/main/groovy/org/grails/datastore/mapping/model/AbstractPersistentEntity.java`
    - `grails-datamapping-core/src/main/groovy/grails/gorm/multitenancy/Tenants.groovy`
    - `grails-datamapping-core/src/main/groovy/org/grails/datastore/gorm/multitenancy/MultiTenantEventListener.java`
