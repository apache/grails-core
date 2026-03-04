<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# GORM for Hibernate 7
This project implements [GORM](https://gorm.grails.org) for the Hibernate 7.

With the removal of Criterion API in Hibernate 7, we wanted to continue to support the DetachedCriteia in GORM as much as possible. We also wanted to encapsulate the JPA Criteria Building in one class so the following was done:
* DetachedCriteria holds almost all the state of the Query being built. It hold the target class for the query. It does not hold a session.
* HibernateQuery has a session and holds the DetachedCriteria and is a thin wrapper for it. Calling list or singleResult will internally create the Query and execute it. 
* HibernateCriteriaBuilder is a thin wrapper around HibernateQuery. Its main function is to use closures to populate the Hibernate Query and execute it at the end of the closure.
* Only the grails-datastore-gorm-hibernate7 module is being developed at the time.

For testing the following was done:
* Used testcontainers for specific  tests instead of h2 to verify features not supported by h2.
* A more opinionated and fluent HibernateGormDatastoreSpec is used for the specifications.

### Largest Gaps


### Ignored Features

The following tests are currently skipped in the `grails-data-hibernate7:core` test run. They fall into two categories:

#### 1. Local `@Ignore` — tests commented out or explicitly ignored in this module

| File | Feature | Reason |
|------|---------|--------|
| `grails/gorm/specs/SubclassMultipleListCollectionSpec` | `test inheritance with multiple list collections` | `@Ignore` — no reason given; blocked by an unresolved mapping issue |

#### 2. TCK `@IgnoreIf` / `@PendingFeatureIf` — skipped because `hibernate7.gorm.suite=true`

These tests live in `grails-datamapping-tck` and are deliberately excluded for Hibernate 7 because the underlying feature is not yet implemented or behaves differently:

| TCK Spec | # skipped | Skip condition | Reason / notes                                                                                 |
|----------|-----------|----------------|------------------------------------------------------------------------------------------------|
| `DirtyCheckingSpec` | 6 | `@IgnoreIf(hibernate7.gorm.suite == true)` | Hibernate 7 dirty-checking semantics differ; the entire spec is disabled                       |
| `GroovyProxySpec` | 5 | `@IgnoreIf(hibernate5/6/7.gorm.suite)` | Groovy proxy support requires `ByteBuddyGroovyProxyFactory`; excluded for all Hibernate suites |
| `OptimisticLockingSpec` | 3 | `@IgnoreIf` (detects Hibernate datastore on classpath) | Hibernate has its own `Hibernate7OptimisticLockingSpec` replacement                            |
| `UpdateWithProxyPresentSpec` | 2 | `@IgnoreIf(hibernate7.gorm.suite == true)` | Proxy update behaviour differs in Hibernate 7                                                  |
| `RLikeSpec` | 1 | `@IgnoreIf(hibernate7.gorm.suite == true)` | `rlike` not supported in HQL / H2 in Hibernate 7 mode                                          |
| `DirtyCheckingAfterListenerSpec` | 1 | `@PendingFeatureIf(!hibernate5/6/mongodb)` | `test state change from listener update the object` — pending for Hibernate 7                  |
| `DomainEventsSpec` | 1 | `@PendingFeature(reason='Was previously @Ignore')` | `Test bean autowiring` — pending across all suites                                             |
| `WhereQueryConnectionRoutingSpec` | 5 | `@Requires(manager.supportsMultipleDataSources())` | Multiple datasource routing not supported in the TCK test manager                              |



