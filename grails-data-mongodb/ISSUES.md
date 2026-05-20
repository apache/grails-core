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

# MongoDB O(M+N) Scaling and Performance

## Context
MongoDB integration in GORM 7 must handle high-cardinality multi-tenancy without linear memory/CPU growth per tenant.

## Identified Issues
- **Redundant Tenant Lookups**: `MongoStaticApi` methods like `wrapFilterWithMultiTenancy` and `preparePipeline` call `Tenants.currentId()` repeatedly, even when invoked via a tenant-qualified API instance.
- **Object Allocation Churn**: Pipeline preparation and filter wrapping create new Bson objects per query, which is exacerbated by redundant context lookups.

## Fix Strategy
1. **Leverage Qualifier**: Refactor `MongoStaticApi` to use its `qualifier` as the `tenantId` if it is not the default, avoiding `Tenants.currentId()` lookups.
2. **Propagate Context**: Pass the resolved `tenantId` into lower-level query preparation methods.
3. **Validation**: Use `MongoTenantContextProfilingSpec` to verify overhead reduction.

## Targets for B.2 Refactoring
- `org.grails.datastore.gorm.mongo.api.MongoStaticApi`
- `org.grails.datastore.mapping.mongo.query.MongoQuery`
