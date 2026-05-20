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

# Neo4j O(M+N) Scaling and Performance

## Context
GORM 7 and Hibernate 7 migration introduced a more decentralized API resolution pattern. For multi-tenant systems with a large number of tenants (M) and entities (N), the previous architecture often led to O(M+N) memory allocation churn due to redundant creation of API wrappers and tenant context lookups.

## Identified Issues
- **Cypher Query Churn**: The Neo4j implementation currently constructs Cypher queries and parameter maps in a way that may trigger redundant `Tenants.currentId()` lookups during the query building phase.
- **Redundant Registry Lookups**: Shared lookups in `GormRegistry` have been optimized at the core level, but the Neo4j-specific static and instance APIs may still bypass these caches or perform redundant normalization.

## Fix Strategy
1. **Propagate Tenant Context**: Refactor entry points in `Neo4jGormStaticApi` and `Neo4jGormInstanceApi` to resolve the `tenantId` once and pass it down into the `Neo4jSession` and Cypher query builders.
2. **Stateless Query Builders**: Ensure that the builders generating Cypher are either stateless or reuse injected schema information instead of resolving it per-invocation.
3. **Baseline Verification**: Use `Neo4jTenantContextProfilingSpec` to measure the overhead of wrapped vs. unwrapped calls.

## Targets for B.2 Refactoring
- `org.grails.datastore.gorm.neo4j.Neo4jDatastore`
- `org.grails.datastore.gorm.neo4j.api.Neo4jGormStaticApi`
- `org.grails.datastore.gorm.neo4j.api.Neo4jGormInstanceApi`
- Cypher query generation logic in `org.grails.datastore.gorm.neo4j.engine`.
