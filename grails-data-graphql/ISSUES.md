# GraphQL O(M+N) Scaling and Performance

## Context
GraphQL GORM integration maps GORM entities to a GraphQL schema. In multi-tenant environments, the schema resolution and data fetching layers must handle tenant context switches efficiently to avoid the O(M+N) performance trap.

## Identified Issues
- **Fetcher Overhead**: GORM Data Fetchers may perform redundant tenant resolution for each field in a deeply nested GraphQL query.
- **Schema Duplication**: If schemas are being re-generated or re-validated per-tenant without caching, it leads to significant CPU and memory pressure.

## Fix Strategy
1. **Context-Aware Fetchers**: Ensure `DataFetcher` implementations capture the tenant ID from the initial execution context and propagate it to GORM static API calls (e.g., using `withTenant(id)` or passing the ID directly to refactored static methods).
2. **Profile Execution**: Use `GraphqlTenantContextProfilingSpec` to measure the cost of fetching data across multiple tenants.

## Targets for B.2 Refactoring
- `org.grails.gorm.graphql.fetcher.PogoDataFetcher`
- `org.grails.gorm.graphql.fetcher.GormEntityDataFetcher`
- `org.grails.gorm.graphql.interceptor.GraphQLInterceptor`
