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

This document provides a high-level overview of the O(M+N) scaling work. For detailed module-specific issue tracking, see the `ISSUES.md` files in the respective directories.

---

## Program Goal
Address performance regressions and memory allocation churn introduced during the migration to decentralized API resolution. Specifically targeting multi-tenant environments with high cardinality of tenants (M) and entities (N).

## Module-Specific Backlogs
- [GORM Core](./grails-datamapping-core/ISSUES.md) - Registry normalization, cache boundaries, and API registries.
- [Hibernate 7](./grails-data-hibernate7/ISSUES.md) - JPA criteria optimization, predicate generation, and modern HQL wiring.
- [Hibernate 5](./grails-data-hibernate5/ISSUES.md) - Parity with H7 scaling patterns for legacy support.
- [MongoDB](./grails-data-mongodb/ISSUES.md) - Pipeline preparation and filter wrapping optimizations.
- [Neo4j](./grails-data-neo4j/ISSUES.md) - Cypher query churn and parameter map optimizations.
- [GraphQL](./grails-data-graphql/ISSUES.md) - Fetcher overhead and schema resolution.
- [SimpleMap](./grails-data-simple/ISSUES.md) - In-memory implementation alignment.

---

## 1) High-Level Core Changes Implemented

### Shared-registry architecture (O(M+N))
- Introduced `GormRegistry` and moved registry responsibilities out of per-tenant duplication paths.
- Refactored `GormEnhancer`, `GormStaticApi`, and `GormInstanceApi` to resolve APIs through shared registry data.
- Updated tenant-aware resolution flow (`Tenants`, enhancer lookup paths, qualifier handling) to match shared registry behavior.

### Datastore integrations aligned to shared model
- Hibernate 7, Hibernate 5, MongoDB, and SimpleMap datastores have been updated to use the new registry approach.

### Query and session behavior hardening
- Refined key query/session paths where registry and tenant context are used.

### Transform and compile-time behavior updates
- Updated service and transactional transform logic to match registry/data access changes.

### Test coverage expanded for scale + regressions
- Added `GormRegistryScalabilitySpec` and `TenantContextProfilingSpec` patterns across core modules.

---

