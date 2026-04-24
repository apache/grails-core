/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

# GORM Stateless Class-Singleton Refactor Status

## Objective
Complete the transition of GORM 7 to a stateless, singleton-based architecture while resolving multi-tenancy and TCK failures.

## Current Status: [IN PROGRESS]
- **Core TCK (`grails-datamapping-core-test`)**: **~88 failures remaining** (Stabilized after GORM 7/Groovy 4 method resolution refactor).
- **Major Milestone**: Achieved **O(M+N) memory scaling** by converting GORM APIs to stateless class-singletons.
- **Victory**: ALL Multi-Tenancy and Multi-Datasource service specs (DatabasePerTenant, MultipleDataSource, TenantService) are passing consistently.

## Modified Classes Needing Direct Unit Testing
The following classes have been refactored for statelessness but lack direct unit tests covering the new dispatch and isolation logic. This gap likely hides the root cause of the remaining 88 TCK failures.

| Class | Status | Risk Area |
| :--- | :--- | :--- |
| **`GormEnhancer.groovy`** | Refactored | Dynamic delegation via `methodMissing` in Groovy 4 context. |
| **`GormStaticApi.groovy`** | Refactored | Complex interface alignment (`GormAllOperations`) and tenant-routing logic. |
| **`SimpleMapDatastore.java`** | Updated | State-sharing between parent/child instances for multi-tenant TCK specs. |
| **`SimpleMapQuery.groovy`** | Updated | Projection transposition and logical family prefixing (tenant isolation). |
| **`SimpleMapEntityPersister.groovy`** | Updated | Property indexer isolation. Current indices are root-shared, potentially leaking across tenants. |

## Strategies Tried
### 1. Stateless Architecture
- **Class-Singleton APIs**: Removed field-level datastore/transaction manager state from `GormStaticApi` and `GormInstanceApi`.
- **Dynamic Resolution**: All lookups now go through `GormEnhancer.findDatastore(persistentClass)`, supporting tenant-swaps without proxy creation.

### 2. Multi-Tenancy & Isolation
- **Hybrid Isolation**: Implemented physical state sharing (map/indices) combined with logical family prefixing (`tenantId:familyName`) to satisfy TCK cross-tenant visibility and isolation.

## Remaining Issues & Technical Hurdles
### 1. Property Indexer Tenant-Awareness
- **Issue**: Dynamic finders sometimes return 0 or incorrect results in multi-tenant contexts.
- **Hypothesis**: While entity storage is isolated by prefix, `PropertyValueIndexer` might still be using root-entity-shared keys.

### 2. Auto-Timestamp NPEs
- **Issue**: `CustomAutoTimestampSpec` regresses with NPEs.
- **Cause**: Stateless path might be skipping property initialization formerly handled during instance proxying.

## Task State
1. [DONE] Remove field injections and verify linear memory growth elimination.
2. [DONE] Resolve `StackOverflowError` in static resolution.
3. [DONE] Stabilize Multi-Tenancy/Multi-Datasource (DatabasePerTenantSpec, etc.).
4. [DONE] Stabilize Criteria Projections (Transposition).
5. [IN PROGRESS] Implement unit tests for `GormStaticApi` to flesh out dynamic finder resolution gaps.
6. [TODO] Implement Marshalling-aware comparisons for Custom types.
7. [TODO] Fix Auto-Timestamp regressions in stateless path.
