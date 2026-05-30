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

# GORM O(M+N) Scaling Branch — Build-Stability Handoff

**Branch:** `8.0.x-hibernate7.gorm-scaling-clean` (PR #15678) · **Base for diffs:** `origin/8.0.x-hibernate7`
**Goal of this document:** give an incoming agent everything needed to drive CI to fully green.
**Definition of done:** every CI job on the branch passes — Build, all Functional Tests, all
Hibernate5/Hibernate7/Mongodb Functional Tests, Code Style, Code Analysis.

> This branch is a *clean rebuild* of the O(M+N) GORM scaling rewrite (original work in commit
> `8f1500dd03`). The rewrite replaced `GormEnhancer`'s per-entity/per-tenant static maps with a single
> process-wide `GormRegistry`. **Almost every remaining failure is a downstream symptom of a core
> contract that regressed in that rewrite.** Fix at the root, not at the symptom.

---

## 1. Current CI status (as of commit `39eadadf00`, 2026-05-29)

Pull the job matrix with `gh run list --branch 8.0.x-hibernate7.gorm-scaling-clean` then
`gh run view <CI_run_id>`.

### Green ✅
- **All `Build Grails-Core`** jobs (macOS, Ubuntu 21/25, Windows, *and* "Rerunning all Tasks") —
  the core unit + integration suite passes on every platform.
- **All `Mongodb Functional Tests`** (Mongo 7/8, Java 21/25, indy on/off).
- Build Gradle Plugins, Build Grails Forge, Validate Dependency Versions, CodeQL, RAT, publishGradle.

### Red ❌ — three functional clusters remain
| CI job | Failing module(s)/specs | Theme |
|--------|-------------------------|-------|
| **Functional Tests** (Java 21/25, indy on/off) | `grails-test-examples-graphql-grails-test-app:integrationTest` → `CommentIntegrationSpec`, `TagIntegrationSpec`, `UserRoleIntegrationSpec` | composite-id query **over-counting** (`outCount == 2`, expected 1) + a query-log-capture assertion |
| **Hibernate5 Functional Tests** (Java 21/25, indy on/off) | `database-per-tenant`, `schema-per-tenant`, `partitioned-multi-tenancy`, `grails-hibernate-groovy-proxy` (`ProxySpec`), `grails-data-hibernate5-core:test` (1) | multi-tenancy + groovy proxy |
| **Hibernate7 Functional Tests** (Java 21/25, indy on/off) | `database-per-tenant`, `schema-per-tenant`, `multiple-datasources`, **`DataServiceDatasourceInheritanceSpec` (8)**, **`DataServiceMultiDataSourceSpec` (several)**, `DatabasePerTenantIntegrationSpec` | multi-tenancy + **multi-datasource DataService routing** |

### Code Style ❌ (fix is ready but unpushed)
The `ServiceTransformation.groovy` (CodeNarc) and `HibernateDatastore.java` (Checkstyle) violations
are **already fixed in the working tree** (staged, not yet committed — see §6). Pushing that commit
clears the Code Style job.

**Dominant theme:** the H5/H7 red clusters are overwhelmingly **multi-tenancy + multi-datasource
routing in real Hibernate apps**. These are very likely a *single shared root cause* in how
`DataService`/connection routing resolves a datastore under the single `GormRegistry`. Diagnose one
representative spec (suggest `DataServiceMultiDataSourceSpec`) before fan-out — one fix probably
clears most of them.

---

## 2. How to reproduce & verify (do this locally — CI costs money)

Most of this is runnable **for free locally**. Only Mongo (needs Docker; available) and Geb/browser
truly need containers. Verify locally and batch fixes into one push; do **not** find→push→find→push.

```bash
# Run only selected modules (edit local.properties → grails.test.modules=:mod1,:mod2,...)
./gradlew -I local-tasks.gradle clean testSelected -PdoNotCacheTests

# Run one module / one spec / one feature
./gradlew :grails-test-examples-graphql-grails-test-app:integrationTest --tests "grails.test.app.CommentIntegrationSpec" -PdoNotCacheTests
./gradlew :module:test --tests "pkg.SomeSpec.feature name" -PdoNotCacheTests

# Targeted style check for a module
./gradlew :grails-datamapping-core:codenarcMain :grails-data-hibernate7-core:checkstyleMain --rerun-tasks

# Full violations gate (heavy; CLAUDE.md #12 mandates before an automated commit)
./gradlew clean aggregateViolations :grails-test-report:check --continue
# then read build/reports/violations/{CHECKSTYLE,CODENARC,PMD,SPOTBUGS}_VIOLATIONS.md
```

**Fetching CI failure detail (traces are NOT in the job summary):**
```bash
# A completed-but-mid-run job's full log (works even while the parent run is in_progress —
# unlike `gh run view --log-failed`, which blocks until the whole run finishes):
gh api repos/apache/grails-core/actions/jobs/<JOB_ID>/logs > job.log
grep -nE " FAILED$|tests? completed.*failed|Task :.*(test|integrationTest) FAILED|Execution failed for task" job.log
# then sed -n '<line>,<line+20>p' job.log to read the stack trace / assertion.
```

**Confirm a failure is a real regression vs a test smell** (this branch inherits unchanged tests):
1. `git diff origin/8.0.x-hibernate7 -- <test-file>` — empty diff ⇒ the test is unchanged, so a
   failure is a *regression*, not a mis-written test. Do **not** edit the test to pass.
2. Check whether the same pattern works for the simpler case (e.g. default vs non-default datasource).
3. A `git worktree` on `origin/8.0.x-hibernate7` is the ground truth for "did this pass before."

**Test-isolation gotcha:** CI runs `maxParallelForks` up to 4 and `forkEvery=50`
(`gradle/test-config.gradle`). The `GormRegistry` is a **process-wide singleton**; cross-spec
pollution within a JVM fork surfaces only in full-suite runs, not isolated specs. If something passes
alone but fails in the suite, suspect registry/session state leaking between specs.

---

## 3. Regressions already fixed — do NOT redo (committed in `39eadadf00` and `bd1f997093`)

| Symptom (downstream) | Root cause (core) | Fix location | Guard test |
|---|---|---|---|
| NPE `currentGormInstanceApi() is null` (VndError, HalJsonRenderer, Table) | parent `GormEnhancer.find*Api` *threw* `IllegalStateException` when an `@Entity` was unregistered; the rewrite returned `null` → NPE in callers that catch the exception and fall back | trait accessors `GormEntity.currentGorm{Instance,Static}Api()`, `GormValidateable`, `GormEntityDirtyCheckable` now throw `IllegalStateException` | existing rest-transforms specs |
| `withTenant(id).count()` NPE (PartitionedMultiTenancy, full-suite only) | `forQualifier` builds a new `GormStaticApi` whose `getGormPersistentEntity()` relied on registry resolution that returned null under cross-spec DISCRIMINATOR state | `GormStaticApi.getGormPersistentEntity()` falls back to the construction-time `mappingContext` | — |
| app1 `BookControllerSpec` save/delete `count()==0` | **`SimpleMapSession` rollback poisons the session**: a rolled-back tx set a session-level `rollbackOnly` flag that was never cleared, permanently turning `flush()` into a no-op | `SimpleMapSession`: `clearRollbackOnly()` on commit/rollback + reset in `beginTransactionInternal` | `SimpleMapSessionSpec` (3 tests) |
| demo33 `CarSpec` `count()==0` (non-default `datasource`) | **DataTest harness predates the single `GormRegistry`**: a non-default-datasource domain now resolves to a dedicated per-connection child datastore, but the harness only bound a session for the default datastore → throwaway per-call sessions lost `save()` without flush | `DataTestSetupInterceptor`/`DataTestCleanupInterceptor` bind & unbind a session per connection source (no-op for single-datasource specs) | `NonDefaultDatasourceFlushSpec` (grails-test-suite-uber) |
| CrossDatasourceTransactionSpec read-only tx | `GormStaticApi.withTransaction(Map)`/`withNewTransaction(Map)` called `definition.setProperty(k,v)` — no such method on the Java bean `DefaultTransactionDefinition` | restored `definition[k as String] = v` idiom (both overloads) | — |
| TeamSpec HAL `version` (views-functional-tests) | a prior fix wrongly stripped `version` from embedded HAL; Hibernate embedded output legitimately renders it | reverted `DefaultHalViewHelper`; updated `HalEmbeddedSpec` expectation (`Person` auto-maps a version under the GORM KeyValue strategy) | `HalEmbeddedSpec` |
| demo33 `UniqueConstraintOnHasOneSpec` "passes unexpectedly" | stale `@NotYetImplemented` (a 2nd copy of an already-fixed spec) | removed annotation | — |
| Bar/FooIntegrationSpec (graphql-multi-datastore) | tests probed the removed **internal** `org.grails.datastore.gorm.GormEnhancer.findStaticApi` | assert via public observable behavior (Mongo `new ObjectId(id)` / Hibernate `obj.id==1`) | — |

> Net effect of `39eadadf00`: the **Functional Tests** job dropped from 6 failing tasks to 1, the
> **Build** suite went fully green, and **Mongodb Functional** is green.

---

## 4. The remaining work

### 4a. graphql composite-id over-counting (Functional Tests job)
`grails-test-examples-graphql-grails-test-app:integrationTest`:
- `CommentIntegrationSpec > test querying a comment with only the parent id` — `outCount == 2` (expected 1)
- `UserRoleIntegrationSpec > test reading an entity with a complex composite id` — `outCount == 2` (expected 1)
- `TagIntegrationSpec > test a custom property can reference a domain with using joins` — asserts
  `queries[0]` matches a SQL pattern, but `queries[0]` is a Hibernate *deprecation WARN* log line
  (query-capture picks up log noise). Possibly environmental/log-config rather than a query bug.

Hibernate-backed (H2), so reproducible locally for free. The composite-id reads return **2 rows
where 1 is expected** — investigate the composite-id query generation / association join in the H7
runtime. Not yet root-caused.

### 4b. Multi-tenancy + multi-datasource cluster (Hibernate5 AND Hibernate7 Functional jobs)
This is the bulk of the red and the highest-value target. Failing specs include:
- `DatabasePerTenantSpec` / `DatabasePerTenantIntegrationSpec` ("Test database per tenant", "should
  rollback changes in a previous test", "saveBook with normal service")
- `SchemaPerTenantSpec` / `SchemaPerTenantIntegrationSpec`
- `PartitionedMultiTenancySpec` (H5 functional)
- `MultipleDataSourcesSpec` (H7)
- `DataServiceDatasourceInheritanceSpec` (H7, ~8 specs: "routes to inherited datasource")
- `DataServiceMultiDataSourceSpec` (H7: "save/get/count routes to books datasource")
- `grails-hibernate-groovy-proxy:ProxySpec` (H5)

**Hypothesis (verify before fanning out):** a single root cause in `DataService`/connection-source
routing resolution under the single `GormRegistry` — the runtime resolves the wrong (or default)
datastore for a non-default connection/tenant, analogous to the *test-harness* version already fixed
in §3 (CarSpec). Note: earlier work wired `setTargetDatastore(...)` in the TCK managers
(`GrailsDataHibernate5/7TckManager`) and that cleared the **unit/TCK** DataService failures, but the
**functional example apps** (real multi-datasource H2 apps) still fail — so the runtime routing path,
not just the test wiring, needs a fix.

**Suggested approach (root-cause, unit-test-first):**
1. Pick `DataServiceMultiDataSourceSpec` (H7). Pull its trace via the `gh api .../jobs/<id>/logs`
   recipe in §2.
2. Reproduce locally: add the relevant H7 example modules to `local.properties` `grails.test.modules`
   and run with `-I local-tasks.gradle testSelected`.
3. Trace how a `@Service` resolves its datastore for a non-default `connection`/`datasource` through
   `GormRegistry`/`GormApiResolver` (selector strategies: `PreferredDatastoreSelector`,
   `QualifiedDatastoreSelector`, `ActiveSessionDatastoreSelector`, `DefaultDatastoreSelector`).
4. Add a failing **unit test in the owning core module** that captures the contract, then fix, then
   confirm the functional specs go green as a side effect.

---

## 5. Working methodology (this is what has been clearing the moles)

1. **Root-cause, not symptom.** Each failure traces to a core GORM contract; fix it in
   `grails-datamapping-core` / `grails-data-simple` / the datastore module, with a **failing unit
   test in the owning module first** (red → fix → green). The functional/integration failure then
   clears as a side effect. Don't edit downstream functional tests to pass.
2. **Verify locally, batch, one push.** SimpleMap + H2 specs run free; Mongo via Docker. CI is billed.
3. **Prove regression vs smell** with the unchanged-from-parent diff + simpler-case comparison (§2).
4. **Watch the singleton.** `GormRegistry` is process-wide; suspect cross-spec pollution for
   passes-alone/fails-in-suite behavior.

---

## 6. Repo state & immediate next action

- **HEAD = `39eadadf00`** (pushed). Working tree additionally has a **staged-but-uncommitted**
  Code-Style cleanup (2 files): `ServiceTransformation.groovy` (drop redundant `GeneralUtils.*`
  wildcard — `constX` added explicitly; remove unused `AstAnnotationUtils` + duplicate
  `ZERO_PARAMETERS`; collapse blank lines) and `HibernateDatastore.java` (remove unused
  `SessionCallback` import). **Commit + push this to clear the Code Style job.** A draft message is at
  `/tmp/grails_style_msg.txt` (regenerate if gone).
- **Commit hygiene:** branch off the target release branch for PRs; squash; end commit messages with
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. Run the violations gate
  (§2) before automated commits (CLAUDE.md #12).

---

## 7. Resolved design decisions — do NOT revert (these are intentional)

- **`ConnectionSource.DEFAULT` = `"default"`** (lowercase), not `"DEFAULT"`. H7 registers datastores
  under the lowercase key; the old constant caused lookup misses. `OLD_DEFAULT = "DEFAULT"` is kept
  `@Deprecated`; `GormRegistry.normalizeQualifier()` coerces old callers.
- **`ServiceTransformation.copyAnnotations` runs AFTER `implementer.implement()`.** Doing it before
  left raw `@Query` GStrings (`${pattern}`) in the generated `methodImpl` AST, which the tightened
  static type checker rejected as undeclared variables. Order matters — keep it after.
- **`GormStaticApi` uses `@CompileDynamic`** (was `@CompileStatic`). Verified robust under both
  Hibernate and Mongo; no action needed.
- **The DataTest harness now binds a session per connection source** (§3 CarSpec fix). This is
  required by the single-`GormRegistry` child-datastore model; do not "simplify" it back to
  default-only binding.

---

## 8. Module-specific backlogs (performance/optimization, separate from build stability)

- [GORM Core](./grails-datamapping-core/ISSUES.md) — Registry normalization, cache boundaries, API registries.
- [Hibernate 7](./grails-data-hibernate7/ISSUES.md) — JPA criteria, predicate generation, HQL wiring.
- [Hibernate 5](./grails-data-hibernate5/ISSUES.md) — Parity with H7 scaling patterns.
- [MongoDB](./grails-data-mongodb/ISSUES.md) — Pipeline prep and filter wrapping.
- [Neo4j](./grails-data-neo4j/ISSUES.md) — Cypher churn and parameter maps.
- [GraphQL](./grails-data-graphql/ISSUES.md) — Fetcher overhead and schema resolution.
- [SimpleMap](./grails-data-simple/ISSUES.md) — In-memory implementation alignment.
