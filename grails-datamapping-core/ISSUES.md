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
- [DONE] Reused `get(className, qualifier)` inside API registries to prevent duplicate `forQualifier` instantiations when the datastore does not change.
- [DONE] Implemented `qualifiedApis` cache in `AbstractGormApiRegistry` to eliminate O(M+N) allocation churn.
- [DONE] Simplified `findDatastore` in `GormApiResolver` by removing redundant duplicate `DEFAULT` lookups.
- [DONE] Optimized `ActiveSessionDatastoreSelector` to use `TransactionSynchronizationManager.getResourceMap()`, reducing fallback lookup from O(M) to O(1) active datastores.

### Concurrency Testing / Lock Contention Benchmarking
Files:
- `src/test/groovy/org/grails/datastore/gorm/GormRegistryConcurrencySpec.groovy`
- `src/test/groovy/org/grails/datastore/gorm/GormRegistryScalabilitySpec.groovy`

Changes:
- Implemented and ran `GormRegistryConcurrencySpec` confirming safe, high-throughput concurrent access to registry lookups over 1 million total operations across 10 threads. Verified no lock contention failures occur with existing `ConcurrentHashMap` semantics.
- Added `GormRegistryScalabilitySpec` to verify O(M+N) memory guarantee and O(1) API retrieval performance.

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

## Current State (Core regressions resolved)

### Recently fixed in `grails-datamapping-core`
- `GormEnhancerAllQualifiersSpec`
  - `registerEntity adds static api under default and secondary for MultiTenant entity`
  - `registerEntity adds static api under default and secondary for non-default datasource`
  - `registerEntity can resolve through injected registry without touching global singleton`
- `GormInstanceApiSpec`
  - `save validate false preserves preexisting skipValidation state`
  - `save validate false skips validation during persist and restores flag`
- `GormRegistryEntityRegistrationSpec`
  - `registry normalizes default qualifier aliases when registering datastores`
- `GormRegistrySpec`
  - `test withTenant and exists with multi-tenant entity in DISCRIMINATOR mode`
- `TransactionalTransformSpec`
  - `Test transactional transform when applied to inheritance`

### Code-level fixes applied
- `GormRegistry.registerEntity(...)` now registers entity datastores using `enhancer.allQualifiers(...)`, restoring correct qualifier expansion/preservation behavior for entity registration.
- `GormStaticApi` now propagates qualifier/registry through `AbstractGormApi` constructor state, fixing tenant qualifier handling in `withTenant(...).exists(...)` execution paths.
- `GormRegistry.findSingleTransactionManager(...)` now throws `IllegalStateException("No GORM implementations configured. Ensure GORM has been initialized correctly")` when no datastore is available, restoring expected transactional transform behavior.
- Specs were adjusted to align with normalized/instance registry APIs (`resolveValidationApi`, `resolveStaticApi`) and unambiguous overloaded datastore lookups.

### Next Steps
1. Keep `grails-datamapping-core` green while validating downstream `grails-data-hibernate7` optimization follow-ups.
2. Re-apply and validate `JpaCriteriaQueryCreator` optimizations in `grails-data-hibernate7` once cross-module verification is complete.
