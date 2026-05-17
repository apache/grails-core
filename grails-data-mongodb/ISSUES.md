# MongoDB O(M+N) Scaling and Performance

## Context
MongoDB integration in GORM 7 must handle high-cardinality multi-tenancy without linear memory/CPU growth per tenant.

## Identified Issues
- **Redundant Tenant Lookups**: `MongoStaticApi` methods like `wrapFilterWithMultiTenancy` and `preparePipeline` call `Tenants.currentId()` repeatedly, even when invoked via a tenant-qualified API instance.
- **Object Allocation Churn**: Pipeline preparation and filter wrapping create new Bson objects per query, which is exacerbated by redundant context lookups.

## Fix Strategy
1. **Leverage Qualifier**: Refactor `MongoStaticApi` to use its `qualifier` as the `tenantId` if it is not the default, avoiding `Tenants.currentId()` lookups.
2. **Propagate Context**: Pass the resolved `tenantId` into lower-level query preparation methods.
3. **Validation**: Use `MongoTenantContextProfilingSpec` to verify overhead reduction.

## Targets for B.2 Refactoring
- `org.grails.datastore.gorm.mongo.api.MongoStaticApi`
- `org.grails.datastore.mapping.mongo.query.MongoQuery`
