# Current Issues & Progress - GORM 7 Stateless Refactor

## Status Summary
- **TCK Progress**: ~92 failures -> **47 failures remaining**.
- **Memory Scaling**: O(M+N) verified; physical map sharing with logical prefixing (Hybrid Isolation) is stable.
- **Multi-Tenancy**: Connection-based isolation significantly improved; recursion in datastore creation resolved.

## Completed in Last Session
1.  **GORM API Improvements**:
    - **Implemented "Find by Example"**: Fixed `GormStaticApi.find(D example)` and `findAll(D example)` to correctly query by entity properties instead of just ID. This resolved the "Fred and Bart" persistence issue in `WhereMethodSpec`.
    - Added `GormEnhancer.getPreferredDatastore()` to safely retrieve thread-bound datastores.
    - Added `Tenants.withTenant()` public helper to allow safe tenant switching from Java code.
2.  **SimpleMapDatastore Major Fixes**:
    - **Recursion Fix**: Resolved infinite loop in `getDatastoreForConnection` by returning `this` when names match.
    - **Multi-Tenancy Registration**: Fixed bug where child datastores were overwriting each other in `GormRegistry`.
    - **Session Binding**: Implemented proper tenant-aware session binding in `withNewSession` using `Tenants.withTenant`.
    - **Initialization**: Added missing constructors for package scanning and multi-connection setup required by TCK.
3.  **SimpleMap Persistence & Query**:
    - **Index Fix**: Fixed `SimpleMapEntityPersister` index lookups that were using an undefined `allIndices` variable.
    - **Many-to-Many**: Implemented `getManyToManyKeys` to support M2M associations in the stateless map datastore.
    - **State Isolation**: Ensured shared state is cleared on datastore closure to prevent data leakage between tests.
4.  **Compilation & Build**:
    - Fixed duplicate `getSessionFactory()` methods and access modifier conflicts in Hibernate 5, Hibernate 7, and MongoDB modules.

## Classes Touched (Verification Required)
The following classes were modified but have not yet had specific, isolated unit tests added (verification relied on TCK):
- `org.grails.orm.hibernate.AbstractHibernateGormInstanceApi` (Compilation fix)
- `org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister` (Index and M2M fixes)
- `org.grails.datastore.gorm.GormStaticApi` (Find-by-example implementation)
- `org.grails.datastore.mapping.simple.SimpleMapDatastore` (Major multi-tenancy and init overhaul)
- `org.grails.datastore.gorm.GormEnhancer` (Added `getPreferredDatastore`)
- `grails.gorm.multitenancy.Tenants` (Added `withTenant`)
- `org.grails.datastore.mapping.simple.query.SimpleMapQuery` (Subquery and result mapping fixes)

## In Progress / Blocked
1.  **[BUG] TenantServiceSpec Isolation**: Despite isolation fixes, `TenantServiceSpec` still fails. Logs show the `Team` entities are correctly stored in `two:grails.gorm.tests.Team`, but `findByName` on the `default` connection is still finding them or failing to isolate correctly in certain edge cases.
2.  **[BUG] Many-to-Many NPE**: `ManyToManySpec` reports NPE in `AbstractSession.retrieveAll` when initializing collections.
3.  **[FEATURE] Value-side Function Support**: (Still Pending) Need to support value-side functions (e.g., `name == lower('HOMER')`) in `SimpleMapQuery`.

## Next Steps
1.  Investigate `AbstractSession.retrieveAll` NPE in `ManyToManySpec`.
2.  Debug the remaining isolation leak in `TenantServiceSpec`.
3.  Address remaining 47 TCK failures (Subqueries, Dirty Checking, and complex associations).
