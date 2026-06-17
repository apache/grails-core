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

## Historical H7 `gorm` Functional Test Failures - Bug Report

This historical report captured the `grails-test-examples-gorm` failures that drove the Hibernate 7 fixes in this branch. Some entries below have since been fixed by this branch, so treat this file as triage rationale rather than current expected test status.

Before those fixes, running `grails-test-examples-gorm` with `-PhibernateVersion=7` produced 13 failures across 4 specs. Below are the 5 distinct root causes.

---

### Bug 1 (Intentional) - `executeQuery` / `executeUpdate` plain String blocked

| | |
|---|---|
| **Tests** | `test basic HQL query`, `test HQL aggregate functions`, `test HQL group by`, `test executeUpdate for bulk operations` |
| **Spec** | `GormCriteriaQueriesSpec` |
| **Error** | `UnsupportedOperationException: executeQuery(CharSequence) only accepts a Groovy GString with interpolated parameters` |

**Description:** H7 intentionally rejects `executeQuery("from Book where inStock = true")` when no parameters are passed. The same tightening was already applied to `executeUpdate`. Callers must use `executeQuery('...', [:])` or a GString with interpolated params.

> This is by design. The test bodies need to adopt the parameterized form - not a GORM bug.

---

### Bug 2 (Fixed) - `DetachedCriteria.get()` throws `NonUniqueResultException` instead of returning first result

| | |
|---|---|
| **Test** | `test detached criteria as reusable query` |
| **Spec** | `GormCriteriaQueriesSpec:454` |
| **Error** | `jakarta.persistence.NonUniqueResultException: Query did not return a unique result: 2 results were returned` |

**Description:** H5 `DetachedCriteria.get()` returned the first matching row when multiple rows existed. H7's `AbstractSelectionQuery.getSingleResult()` is now strict and throws if the result is not unique.

**Expected fix:** `HibernateQueryExecutor.singleResult()` should apply `setMaxResults(1)` before calling `getSingleResult()`, or switch to `getResultList().stream().findFirst()`.

> **Status:** Fixed. Caught exceptions are updated to check for `jakarta.persistence.NonUniqueResultException` to return the first result, and the tests pass.

---

### Bug 3 (Fixed) - `Found two representations of same collection: gorm.Author.books`

| | |
|---|---|
| **Tests** | `test saving child with belongsTo saves parent reference`, `test dirty checking with associations`, `test belongsTo allows orphan removal`, `test updating multiple children`, `test addTo creates bidirectional link` |
| **Spec** | `GormCascadeOperationsSpec` |
| **Error** | `HibernateSystemException: Found two representations of same collection: gorm.Author.books` |

**Description:** H7 enforces stricter collection identity. After `author.addToBooks(book); author.save(flush: true)`, the session contains two references to the same `Author.books` collection, causing a `HibernateException` on flush. H5 tolerated this.

**Expected fix:** GORM's `addTo*` / cascade-flush path in `grails-data-hibernate7` must synchronize both sides of the bidirectional association and merge/evict stale collection snapshots before flushing.

> **Status:** Fixed. Cascade flush issues and collection representations are now resolved, and `GormCascadeOperationsSpec` passes 100%.

---

### Bug 4 (Fixed) - `@Query` aggregate functions fail with type mismatch

| | |
|---|---|
| **Tests** | `test findAveragePrice`, `test findMaxPageCount` |
| **Spec** | `GormDataServicesSpec` |
| **Errors** | `Incorrect query result type: query produces 'java.lang.Double' but type 'java.lang.Long' was given` / `query produces 'java.lang.Integer' but type 'java.lang.Long' was given` |

**Description:** `HibernateHqlQuery.buildQuery()` always calls `session.createQuery(hql, ctx.targetClass())`. For aggregate HQL (`select avg(b.price) ...`, `select max(b.pageCount) ...`), the query does not return an entity, but `ctx.targetClass()` returns the entity class (e.g., `Book`). H7's `SqmQueryImpl` enforces strict result-type alignment: `avg()` produces `Double`, `max(pageCount)` produces `Integer`, neither is coercible to the bound entity type.

**Expected fix:** `HibernateHqlQuery.buildQuery()` must detect non-entity HQL (aggregates / projections) and call the untyped `session.createQuery(hql)` in those cases, letting GORM handle result casting downstream.

> **Status:** Fixed. Aggregate return types are now dynamically resolved, matching the expected Java type of the aggregate function itself.

---

### Bug 5 (Fixed) - `where { pageCount > price * 10 }` fails with `CoercionException`

| | |
|---|---|
| **Test** | `test where query comparing two properties` |
| **Spec** | `GormWhereQueryAdvancedSpec:175` |
| **Error** | `org.hibernate.type.descriptor.java.CoercionException: Error coercing value` |

**Description:** A where-DSL closure comparing an `Integer` property (`pageCount`) to an arithmetic expression involving a `BigDecimal` property (`price * 10`) worked in H5. H7's SQM type system no longer allows implicit coercion between `Integer` and `BigDecimal` in a comparison predicate.

**Expected fix:** The GORM where-query-to-SQM translator should emit an explicit `CAST` in the SQM tree when the two operands of a comparison have different numeric types.

> **Status:** Fixed. Type coercion in comparison predicates is now handled, and `GormWhereQueryAdvancedSpec` passes 100%.
