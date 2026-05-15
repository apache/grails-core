<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# GORM Scaling Program — Change Log and Optimization Backlog

This document now tracks what has already changed for the O(M+N) scaling work and what can still be optimized. It is no longer a failure tracker.

---

## 1) Core changes implemented

### Shared-registry architecture (O(M+N))
- Introduced `GormRegistry` and moved registry responsibilities out of per-tenant duplication paths.
- Refactored `GormEnhancer`, `GormStaticApi`, and `GormInstanceApi` to resolve APIs through shared registry data.
- Updated tenant-aware resolution flow (`Tenants`, enhancer lookup paths, qualifier handling) to match shared registry behavior.

### Datastore integrations aligned to shared model
- Hibernate 7: updated static/instance/enhancer APIs and related datastore/session/query wiring to use the new registry approach.
- Hibernate 5: parallel alignment with H7 so API behavior stays consistent across both engines.
- MongoDB: updated static API and codec/persister integration points to align with shared registry and tenant resolution changes.
- SimpleMap datastore: large query/persister/session updates to keep core behavior consistent with new registry flow.

### Query and session behavior hardening
- Refined key query/session paths in Hibernate and SimpleMap implementations where registry and tenant context are used.
- Added or adjusted session-resolver/runtime utilities where needed to keep API behavior stable under multi-tenancy.

### Transform and compile-time behavior updates
- Updated service and transactional transform logic to match registry/data access changes.
- Reorganized transform test assets (including moved/expanded transform specs/classes).

### Test coverage expanded for scale + regressions
- Added `GormRegistryScalabilitySpec` coverage across core modules (core, H5, H7, Mongo).
- Added and updated regression coverage around enhancer, static API, transaction manager, and transform behavior.

### Local selected-module workflow improvements
- Selected-module runs now enforce CodeNarc/Checkstyle aggregation without forcing PMD/SpotBugs in that path.
- `testSelected` flow remains focused on selected modules with aggregated test/style outputs.

---

## 2) Potential optimization opportunities

## A. Registry/API hot-path efficiency
**Goal:** reduce repeated lookup overhead under high tenant/entity counts.

1. Cache normalized entity keys and qualifier maps in one place to reduce repeated normalization work.
2. Audit repeated `findDatastore`/qualifier fallback chains and collapse duplicate branches.
3. Benchmark `computeIfAbsent` and lock contention patterns in registry-heavy paths.

## B. Tenant context and session routing
**Goal:** lower context-switch overhead and reduce accidental cross-context work.

1. Profile tenant context wrapping frequency in static API calls.
2. Identify places where tenant/session context can be propagated once instead of re-resolved.
3. Add targeted perf specs for DISCRIMINATOR and SCHEMA mode query loops.

## C. Query builder/runtime allocation pressure
**Goal:** reduce temporary object churn in frequently executed query paths.

1. Review HQL/criteria builder allocation patterns in H5/H7 and SimpleMap query implementations.
2. Reuse immutable query metadata where safe.
3. Add microbenchmarks for common `list/find/count` paths with multi-tenant datasets.

## D. Transform pipeline cost
**Goal:** reduce compile-time overhead from service/transaction transforms.

1. Measure transform execution hotspots after recent refactors.
2. Consolidate duplicated transform helper logic.
3. Add performance-oriented transform tests focused on large service sets.

## E. Test/runtime throughput
**Goal:** keep regression coverage while reducing local/CI cycle time.

1. Keep scalability specs, but tune dataset sizes for stable signal-to-cost ratio.
2. Split long-running integration groups where practical.
3. Continue selected-module execution strategy for local iteration, full-suite in CI.

---

## 3) Suggested tracking format for future updates

When adding new work items to this file:
- Record the **change** under section 1 with module + behavior impact.
- Record follow-up work under section 2 with a short **goal** and concrete next actions.
- Avoid adding pass/fail snapshots here; keep this file architectural and optimization-focused.

---

## 4) Failing Tests from 0_build_grails.txt
**Note**: The following pass/fail snapshot was explicitly requested.

### Failing Integration/Compilation Tasks
- `:grails-test-examples-gorm:compileGroovy`
- `:grails-test-examples-app3:integrationTest`
- `:grails-test-examples-app1:integrationTest`
- `:grails-test-examples-exploded:integrationTest`
- `:grails-test-examples-datasources:integrationTest`
- `:grails-test-examples-hibernate5-grails-data-service-multi-datasource:integrationTest`
- `:grails-test-examples-hibernate5-grails-data-service:integrationTest`
- `:grails-test-examples-hibernate5-grails-database-per-tenant:integrationTest`
- `:grails-test-examples-hibernate5-grails-multiple-datasources:integrationTest`
- `:grails-test-examples-hibernate5-grails-multitenant-multi-datasource:integrationTest`
- `:grails-test-examples-hibernate5-grails-partitioned-multi-tenancy:integrationTest`
- `:grails-test-examples-hibernate5-grails-schema-per-tenant:integrationTest`
- `:grails-test-examples-hibernate5-issue450:integrationTest`
- `:grails-test-examples-hibernate7-grails-data-service-multi-datasource:integrationTest`
- `:grails-test-examples-hibernate7-grails-database-per-tenant:integrationTest`
- `:grails-test-examples-hibernate7-grails-multiple-datasources:integrationTest`
- `:grails-test-examples-hibernate7-grails-multitenant-multi-datasource:integrationTest`
- `:grails-test-examples-hibernate7-grails-partitioned-multi-tenancy:integrationTest`
- `:grails-test-examples-hibernate7-grails-schema-per-tenant:integrationTest`
- `:grails-test-examples-hibernate7-issue450:integrationTest`
- `:grails-test-examples-mongodb-test-data-service:integrationTest`
- `:grails-test-examples-plugins-exploded:integrationTest`
- `:grails-test-examples-mongodb-hibernate5:integrationTest`
- `:grails-test-examples-views-functional-tests:integrationTest`
- `:grails-test-examples-scaffolding-fields:integrationTest`

### Modules with Failed Tests
- `grails-data-hibernate5`
- `grails-data-hibernate5-core`
- `grails-data-hibernate5-dbmigration`
- `grails-data-hibernate7`
- `grails-data-hibernate7-core`
- `grails-data-hibernate7-dbmigration`
- `grails-data-mongodb-core`
- `grails-data-simple`
- `grails-datamapping-core`
- `grails-datamapping-core-test`
- `grails-fields`
- `grails-test-examples-app1`
- `grails-test-examples-demo33`
- `grails-test-examples-hibernate5-grails-database-per-tenant`
- `grails-test-examples-hibernate5-grails-hibernate-groovy-proxy`
- `grails-test-examples-hibernate5-grails-partitioned-multi-tenancy`
- `grails-test-examples-hibernate5-grails-schema-per-tenant`
- `grails-test-examples-hibernate7-grails-database-per-tenant`
- `grails-test-examples-hibernate7-grails-partitioned-multi-tenancy`
- `grails-test-examples-hibernate7-grails-schema-per-tenant`
- `grails-test-suite-uber`
- `grails-views-gson`
