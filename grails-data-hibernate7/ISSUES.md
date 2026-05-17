# Hibernate 7 O(M+N) Scaling and Performance

## Context
Hibernate 7 integration in GORM 8 introduces a modern persistence baseline. The O(M+N) scaling work ensures that multi-tenant applications remain efficient as the number of tenants grows.

## Implemented and Validated

### Datastore integration aligned to shared model
- Primary target for the shared-registry architecture refactor.
- Updated all API entry points to leverage centralized lookups and normalized keys.

### Query and session behavior hardening
- Comprehensive refactor of query paths to reduce allocation churn.
- [DONE] Refactor `JpaCriteriaQueryCreator` to inject `PredicateGenerator` (eliminated redundant object churn).

### Verification
- Added `HibernateTenantContextProfilingSpec` to measure tenant wrapping overhead.
- Integrated `GormRegistryScalabilitySpec` to ensure linear (or better) scaling with entity and tenant counts.

## Potential Optimization Opportunities
- Further tracing of JpaCriteria query construction to identify remaining minor allocation hotspots.
