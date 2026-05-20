<!--
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
- **PredicateGenerator LHS caching:** The left-hand-side (LHS) elements generated for each field of a Root element are stable and can be cached per entity type. Caching these can reduce repeated construction overhead in high-churn query scenarios.
