# Hibernate 5 O(M+N) Scaling and Performance

## Context
Hibernate 5 integration in GORM 7 has been updated to use the shared-registry architecture to address O(M+N) scaling issues in multi-tenant environments.

## Implemented and Validated

### Datastore integration aligned to shared model
- Updated static, instance, and enhancer APIs to resolve through the shared `GormRegistry`.
- Wiring of datastore, session, and query components updated to match the new registry resolution flow.
- Ensured API behavior stays consistent with Hibernate 7.

### Query and session behavior hardening
- Refined key query and session paths where registry and tenant context are used.
- Adjusted session-resolver and runtime utilities to maintain stability under high tenant/entity cardinality.

### Verification
- Added `GormRegistryScalabilitySpec` to verify performance under scale.
- Verified no functional regressions in standard multi-tenancy scenarios.
