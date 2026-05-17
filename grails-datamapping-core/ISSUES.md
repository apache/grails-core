# GORM Core O(M+N) Scaling and Performance

## Context
GORM 7 introduced a more decentralized API resolution pattern. For multi-tenant systems with a large number of tenants (M) and entities (N), the previous architecture often led to O(M+N) memory allocation churn due to redundant creation of API wrappers and tenant context lookups.

## Implemented and Validated (Final status: GREEN)

### `GormRegistry` normalization boundary + caches
File: `src/main/groovy/org/grails/datastore/gorm/GormRegistry.groovy`
- Added caches:
  - `normalizedEntityKeysByClass`
  - `normalizedEntityKeysByName`
  - `normalizedQualifiers`
- Added helper methods:
  - `normalizeEntityKey(Class)`
  - `normalizeEntityKey(String)`
  - `normalizeQualifier(String)`
- Wired normalized access into:
  - `getStaticApi/getInstanceApi/getValidationApi`
  - `getDatastore`
  - `registerApi`
  - `registerDatastore`
  - `registerDatastoreByQualifier`
  - `registerEntityDatastore`
  - `registerEntityDatastores`
- Added cache cleanup in `GormRegistry.reset()`.

### API registries normalized key/qualifier usage
Files:
- `src/main/groovy/org/grails/datastore/gorm/AbstractGormApiRegistry.groovy`
- `src/main/groovy/org/grails/datastore/gorm/GormStaticApiRegistry.groovy`
- `src/main/groovy/org/grails/datastore/gorm/GormInstanceApiRegistry.groovy`
- `src/main/groovy/org/grails/datastore/gorm/GormValidationApiRegistry.groovy`

Changes:
- Normalize class keys in `register/get/containsKey`.
- Normalize qualifier before non-default checks and `forQualifier(...)`.

### Audit repeated findDatastore/qualifier fallback chains and collapse duplicate branches
Files:
- `src/main/groovy/org/grails/datastore/gorm/GormApiResolver.groovy`
- `src/main/groovy/org/grails/datastore/gorm/AbstractGormApiRegistry.groovy`
- `src/main/groovy/org/grails/datastore/gorm/GormStaticApiRegistry.groovy`
- `src/main/groovy/org/grails/datastore/gorm/GormInstanceApiRegistry.groovy`
- `src/main/groovy/org/grails/datastore/gorm/GormValidationApiRegistry.groovy`

Changes:
- Reused `get(className, qualifier)` inside API registries to prevent duplicate `forQualifier` instantiations when the datastore does not change.
- Simplified `findDatastore` in `GormApiResolver` by removing redundant duplicate `DEFAULT` lookups.

### Concurrency Testing / Lock Contention Benchmarking
Files:
- `src/test/groovy/org/grails/datastore/gorm/GormRegistryConcurrencySpec.groovy`

Changes:
- Implemented and ran `GormRegistryConcurrencySpec` confirming safe, high-throughput concurrent access to registry lookups over 1 million total operations across 10 threads. Verified no lock contention failures occur with existing `ConcurrentHashMap` semantics.

### Tests added/updated for normalization and API resolution behavior
Files:
- `src/test/groovy/org/grails/datastore/gorm/GormApiRegistrySpec.groovy`
- `src/test/groovy/org/grails/datastore/gorm/GormRegistryEntityRegistrationSpec.groovy`
- `src/test/groovy/org/grails/datastore/gorm/GormInstanceApiRegistrySpec.groovy`
- `src/test/groovy/org/grails/datastore/gorm/GormValidationApiRegistrySpec.groovy`
- `src/test/groovy/org/grails/datastore/gorm/GormStaticApiRegistrySpec.groovy`
- `src/test/groovy/org/grails/datastore/gorm/GormRegistrySpec.groovy`
- `src/test/groovy/org/grails/datastore/gorm/AbstractGormApiRegistrySpec.groovy`

Coverage added:
- `ConnectionSource.OLD_DEFAULT` + blank qualifiers normalize to `default`.
- Entity keys with surrounding whitespace resolve correctly.
- API registry retrieval works with normalized aliases.
- Verified missing branches and explicit fallback mechanisms for abstract/specific API registries and the central `GormRegistry`.

## Potential Optimization Opportunities

### Registry/API hot-path efficiency
**Goal:** reduce repeated lookup overhead under high tenant/entity counts.
- [DONE] Cache normalized entity keys and qualifier maps in one place.
- [DONE] Audit repeated `findDatastore`/qualifier fallback chains and collapse duplicate branches.
- [DONE] Benchmark `computeIfAbsent` and lock contention patterns in registry-heavy paths.

### Tenant context and session routing
**Goal:** lower context-switch overhead and reduce accidental cross-context work.
- [DONE] Profile tenant context wrapping frequency in static API calls.
- Identify places where tenant/session context can be propagated once instead of re-resolved.
