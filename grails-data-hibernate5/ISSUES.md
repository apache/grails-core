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
