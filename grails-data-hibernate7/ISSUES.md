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
# Critical Issues: Memory Leak in Multi-Tenancy

## Issue: `java.lang.OutOfMemoryError` in Schema Multi-Tenancy

### Description
The `grails-data-hibernate7` implementation suffers from a linear memory leak when using **SCHEMA** or **DATABASE** multi-tenancy modes. This leak is caused by the accumulation of GORM API objects in static maps within the `GormEnhancer` class every time a new tenant is registered or resolved.

### Architectural Issue: DI-Lifecycle Mismatch
Grails is designed around Dependency Injection (Spring/Micronaut), where heavy resources like `SessionFactory` and `HibernateTemplate` are managed singletons. However, GORM's static method bridge (`Book.list()`) operates outside this lifecycle:

1.  **Escape from DI:** `HibernateGormStaticApi` instances are created manually via `new` in `GormEnhancer` and stored in `static` registries. They are not DI-managed beans.
2.  **Singleton Violation:** While `SessionFactory` is a singleton per datastore, `GrailsHibernateTemplate` (which wraps the session factory) is being instantiated tens of thousands of times within the static API objects.
3.  **Domain Binding vs. Static Magic:** Since domain bindings are tied to the Hibernate `SessionFactory` lifecycle, GORM creates a new "static bridge" for every tenant to ensure the correct `SessionFactory` is used. The current implementation makes this "bridge" too heavy by including a fresh template for every class-tenant combination.

### Root Cause
1.  **Static Registry:** `GormEnhancer` maintains four `static ConcurrentHashMap` objects (`STATIC_APIS`, `INSTANCE_APIS`, `VALIDATION_APIS`, `DATASTORES`) that hold strong references to API objects, keyed by the tenant qualifier.
2.  **Redundant Object Creation:** Every `HibernateGormStaticApi` instance creates its own `GrailsHibernateTemplate`. Each `GrailsHibernateTemplate` is a heavy object containing a `SQLErrorCodeSQLExceptionTranslator` (which loads extensive XML mappings).
3.  **Tenant-Class Explosion:** Memory usage grows at a rate of `(Number of Tenants) * (Number of Domain Classes) * (Size of GrailsHibernateTemplate)`. In a system with 100 classes and 1,000 tenants, this creates 100,000 heavy template objects.
4.  **Redundant Registration:** In `HibernateDatastore.java`, `addTenantForSchema` calls `registerAllEntitiesWithEnhancer()` repeatedly, which creates new API and template objects even if the tenant was already registered, rapidly exhausting the heap.
5.  **Broken `close()` Logic:** The `GormEnhancer.close()` method contains a logic error: `DATASTORES.get(q)?.remove(datastore)`. Since the inner map is keyed by **Entity Name** (String), passing the `datastore` (Object) fails to remove the entry, leaving the datastore reference in memory forever.
6.  **Groovy `withDefault` Leak:** `GormEnhancer` uses `ConcurrentHashMap.withDefault` for its static maps. In Groovy, `withDefault` is not just a fallback; it **mutates** the map by adding the default value for any accessed key. This causes registry growth during both registration and cleanup/lookup phases.
7.  **Criteria Session Leaks:** `HibernateCriteriaBuilder` automatically opens and binds Hibernate sessions if one is not present. In complex multi-tenant tests, if these builders are not explicitly closed, it leads to a build-up of open sessions and JPA Criteria builder state, contributing to the OOM.
8.  **Query-State and API Bridge Explosion:** The delegation chain and API initialization cause a combinatorial explosion of heavy state objects:
    -   `HibernateGormStaticApi` creates a `HibernateSession` AND a `GrailsHibernateTemplate`.
    -   `HibernateGormInstanceApi` creates its own `GrailsHibernateTemplate`.
    -   `HibernateGormValidationApi` creates its own `GrailsHibernateTemplate`.
    -   `HibernateSession` (used by bridges) creates its own `GrailsHibernateTemplate` in its constructor.
    -   `HibernateHqlQueryCreator` creates a new `HibernateSession` for *every* HQL query (Select/Mutation).
    -   `HibernateQuery` (used by Criteria) also creates its own `HibernateSession`.
    
    **Result:** For every (Domain Class, Tenant) pair, GORM is maintaining at least 4 redundant `GrailsHibernateTemplate` instances. Since each template holds a heavy `SQLErrorCodeSQLExceptionTranslator`, this leads to massive heap exhaustion in multi-tenant environments.

## Memory Footprint Analysis (Estimated)

For every **(Domain Class × Tenant)** combination, the following object tree is pinned in static memory:

```text
GormEnhancer (Static Registry)
 ├── STATIC_APIS -> HibernateGormStaticApi
 │    └── GrailsHibernateTemplate (~500KB - 2MB)
 │         └── SQLErrorCodeSQLExceptionTranslator (Heavy XML-based map)
 ├── INSTANCE_APIS -> HibernateGormInstanceApi
 │    └── GrailsHibernateTemplate (Redundant Copy)
 └── VALIDATION_APIS -> HibernateGormValidationApi
      └── GrailsHibernateTemplate (Redundant Copy)
```

**Scalability Impact:**
- **100 Classes + 10 Tenants:** ~400 MB - 1 GB Heap
- **100 Classes + 1,000 Tenants:** ~40 GB - 100 GB Heap (CRITICAL)

## Resource Leaks: Sessions and Connections
Beyond heap exhaustion, the implementation poses a risk of **Database Connection Pool exhaustion**:
- `HibernateCriteriaBuilder` and `HibernateHqlQueryCreator` frequently instantiate `HibernateSession`.
- In multi-tenant environments where sessions are opened/closed rapidly, any failure to close a `HibernateSession` wrapper leaks the underlying Hibernate `Session` and its database connection.

## Architectural Analysis: The Static-Dynamic Conflict

The memory exhaustion observed is not a simple bug but a result of **Architectural Friction** between GORM's design and Hibernate 7's requirements:

### 1. Static-at-Startup vs. Dynamic-at-Runtime
GORM's "Magic" (e.g., `Book.list()`) relies on modifying Groovy Metaclasses at startup. In a non-multi-tenant app, this happens once. In multi-tenant modes (SCHEMA/DATABASE), GORM must provide a specific "personality" for every domain class for *every* tenant. GORM implements this by creating unique API instances per tenant. The framework is essentially "re-starting" its metadata engine for every new tenant discovered.

### 2. The Hibernate 7 "Metamodel Tax"
Hibernate 7 has significantly increased the complexity of its internal metamodel and JPA Criteria builder. While Hibernate 7 itself is highly optimized, the GORM "Bridge" (the API objects) has not been updated to use a **Flyweight Pattern**. Instead of sharing immutable metadata, each bridge object (StaticApi, InstanceApi) re-instantiates heavy Hibernate/Spring wrappers.

### 3. Lack of Tenant Lifecycle Management
Grails provides excellent **Tenant Resolution** (finding who the tenant is) but lacks **Tenant Lifecycle Management** (handling the "birth and death" of a tenant's metadata).
- **The Accumulation:** Once a tenant is resolved and its metadata is "GORM-enhanced" and cached in the `GormEnhancer` statics, it is pinned there for the life of the JVM.
- **The Explosion:** In dynamic environments (e.g., SaaS apps where schemas are added on-the-fly), the system has no mechanism to evict inactive tenant metadata, leading to inevitable heap exhaustion.

### 4. Thread-Local "Memory Anchors"
The use of `TransactionSynchronizationManager` to bind sessions in `HibernateCriteriaBuilder` creates "Memory Anchors." If a Criteria query is initialized but never executed or closed (common in complex DSL failures), the session — along with its heavy Hibernate 7 state — remains pinned to the **ThreadLocal** map. In thread-pooled environments (Tomcat/Jetty), this memory is never released and pollutes subsequent requests.

### Proposed Fixes
-   **Flyweight Template:** Refactor `HibernateDatastore` to hold a single, shared instance of `GrailsHibernateTemplate` instead of creating a new one in every `getHibernateTemplate()` call.
-   **DI Managed Bean:** Register the `GrailsHibernateTemplate` as a Spring/Micronaut bean in `HibernateDatastoreSpringInitializer` so it can be shared across all components and datastores using the same `SessionFactory`.
-   **API Bridge Refactoring:** Update `HibernateGormStaticApi`, `HibernateGormInstanceApi`, and `HibernateGormValidationApi` to receive the shared template via constructor injection (or from the datastore) rather than instantiating their own.
-   **Refactor `GormEnhancer`:** Move away from static maps to instance-based maps managed by the `Datastore` instance.
-   **Fix `close()` Bug:** Correct the `DATASTORES.get(q)?.remove(datastore)` logic in `GormEnhancer` to use the entity name as the key.
-   **LRU/Weak Cache:** Implement a `WeakHashMap` or a LRU cache for tenant-specific API objects to allow eviction under memory pressure.
