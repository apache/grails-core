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

# `grails-data-hibernate7` — Goals Status

> Goals defined in [`UPGRADE.md`](UPGRADE.md). Last updated: 2026-02-24.

---

## Section 0.3 — Hibernate Dual-Version Support

| Goal | Status | Notes |
|------|--------|-------|
| `grails-data-hibernate6` removed | ✅ Done | Only `hibernate5` and `hibernate7` in `settings.gradle` |
| Fully decomposed `domainbinding/binder/` hierarchy | ✅ Done | 20+ binders: `ClassBinder`, `ComponentBinder`, `ManyToOneBinder`, `CollectionBinder`, etc. |
| `HibernateCriteriaBuilder` with `CriteriaMethodInvoker`/`CriteriaMethods` | ✅ Done | Refactored; direct-call specs written for JaCoCo visibility |
| `HibernateHqlQuery`/`HqlQueryContext` split | ✅ Done | `HqlQueryContext` extracted into its own class |
| Each binder has its own Spock spec | ✅ Done | 240 test specs, 1609 tests, 0 failures |
| Hibernate 7 functional test examples | ✅ Done | 10 example projects (grails-hibernate, multi-datasource, multi-tenancy, data-service, etc.) |
| Apply binder refactoring to `grails-data-hibernate5` | ❌ Not done | `grails-data-hibernate5` still uses monolithic `GrailsDomainBinder` |

---

## Section 1.4 — Static Compilation (Target: 90%+)

| Metric | Status | Notes |
|--------|--------|-------|
| Core module Groovy files with `@CompileStatic` | ✅ 94% (62/66) | |
| Missing 4 files | ✅ Acceptable | All in `dbmigration/` submodule (scripts + plugin descriptor) |
| JaCoCo branch coverage | ⚠️ 66% branches | No enforced minimum; gaps are in `transaction` and `datasource` packages (0%) and dynamic dispatch paths in `grails.orm` |

---

## Section 2.6 — GORM Query Safety Audit

| Item | Status | Notes |
|------|--------|-------|
| `DefaultSchemaHandler` schema name DDL injection | ✅ Fixed | `quoteName()` with JDBC identifier quoting; 9 tests |
| `find/findAll/executeQuery/executeUpdate(CharSequence)` plain `String` guard | ✅ Fixed | `requireGString()` throws `UnsupportedOperationException`; 43 tests |
| `findWithSql`/`findAllWithSql` renamed to `findWithNativeSql`/`findAllWithNativeSql` | ✅ Fixed | Old names `@Deprecated` and delegating |
| `nextId()` dead code removed | ✅ Fixed | No callers; left over from Hibernate 5 |
| Safe query patterns documented | ✅ Done | `hql.adoc`, `nativeSql.adoc`, `upgradeNotes.adoc` |
| Compile-time GString warning (AST transform) | ✅ Superseded | Runtime exception at API boundary is stronger than a compile-time warning |

Full detail: [`GORM-QUERY-SAFETY-AUDIT.md`](GORM-QUERY-SAFETY-AUDIT.md)

---

## Section 4.1 — ExpandoMetaClass Elimination

| Item | Status | Notes |
|------|--------|-------|
| `HibernateMappingBuilder.methodMissing` DSL replacement | ⚠️ Not started | Explicitly scoped to **8.1+** in `UPGRADE.md` — expected |
| `HibernateGormStaticApi.propertyMissing` | ⚠️ Present | Named criteria proxy dispatch; also 8.1+ scope |

---

## Test Coverage

| Package | Coverage | Status |
|---------|----------|--------|
| `domainbinding/binder` | ~94% instructions | ✅ |
| `domainbinding/util` | ~93% instructions | ✅ |
| `compiler` | ~93% instructions | ✅ |
| `query` | ~84% instructions | ✅ |
| `grails.orm` | ~79% instructions | ⚠️ |
| `org.grails.orm.hibernate` | ~74% instructions / ~54% branches | ⚠️ |
| `transaction` | 0% | ❌ No tests at all |
| `datasource` | 0% | ❌ No tests at all |
| **Overall** | **80% instructions / 66% branches** | ⚠️ |

---

## Summary

| Tier | Item | Status |
|------|------|--------|
| 0.3 | `hibernate6` removal | ✅ Done |
| 0.3 | Binder decomposition (`hibernate7`) | ✅ Done |
| 0.3 | Binder refactoring applied to `hibernate5` | ❌ Not done |
| 1.4 | Static compilation — core module | ✅ 94% |
| 2.6 | GORM query safety audit | ✅ Done |
| 4.1 | `methodMissing` elimination | ⚠️ 8.1+ scope |
| — | `transaction`/`datasource` test coverage | ❌ 0% |
