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

This document provides a high-level overview of the O(M+N) scaling work and the current PR review status.
For detailed module-specific issue tracking, see the `ISSUES.md` files in the respective directories.

---

## Program Goal

Address performance regressions and memory allocation churn introduced during the migration to
decentralized API resolution. Specifically targeting multi-tenant environments with high cardinality
of tenants (M) and entities (N).

## Module-Specific Backlogs

- [GORM Core](./grails-datamapping-core/ISSUES.md) — Registry normalization, cache boundaries, and API registries.
- [Hibernate 7](./grails-data-hibernate7/ISSUES.md) — JPA criteria optimization, predicate generation, and modern HQL wiring.
- [Hibernate 5](./grails-data-hibernate5/ISSUES.md) — Parity with H7 scaling patterns for legacy support.
- [MongoDB](./grails-data-mongodb/ISSUES.md) — Pipeline preparation and filter wrapping optimizations.
- [Neo4j](./grails-data-neo4j/ISSUES.md) — Cypher query churn and parameter map optimizations.
- [GraphQL](./grails-data-graphql/ISSUES.md) — Fetcher overhead and schema resolution.
- [SimpleMap](./grails-data-simple/ISSUES.md) — In-memory implementation alignment.

---

## O(M+N) Implementation Status (branch: 8.0.x-hibernate7.gorm-scaling-clean)

### Completed

**Core architecture (commits e09c9f45f6 – b1fd608aaa)**
- `GormRegistry`: shared normalization caches (entity keys, qualifiers), O(1) lookup paths.
- `GormApiResolver` split into focused selector strategy classes (`PreferredDatastoreSelector`,
  `QualifiedDatastoreSelector`, `ActiveSessionDatastoreSelector`, `DefaultDatastoreSelector`).
- `GormEnhancer` delegates API resolution through `GormRegistry`; backward-compatible constructors
  and static delegate methods added for all callers (`findStaticApi`, `findInstanceApi`,
  `findValidationApi`, `findDatastore`, `findEntity`).
- `ConnectionSource.DEFAULT` corrected from `"DEFAULT"` to `"default"` to match what H7 registers
  internally; `OLD_DEFAULT = "DEFAULT"` kept `@Deprecated` for backward compat; `GormRegistry
  .normalizeQualifier()` coerces old callers transparently.
- `forkEvery = 1` added to both H5 and H7 test configs to prevent `GormRegistry` singleton
  contamination between test classes in the same JVM fork.
- Apache RAT audit fixed: `**/ISSUES.md` excluded (no license header per ASF policy for issue files).

**Compilation regressions fixed (2026-05-24)**
- `grails-testing-support-datamapping` — `DataTest.groovy` used removed `GormEnhancer(Datastore,
  TxMgr, boolean)` constructor; restored via backward-compat delegate.
- `grails-views-gson` — `DefaultJsonViewHelper.groovy` called `GormEnhancer.findEntity(Class)`;
  restored via static delegate to `GormRegistry`.
- `grails-scaffolding` — `GormService.groovy` called `GormEnhancer.findStaticApi(Class)`;
  restored.
- `grails-datamapping-core-test` and `grails-test-examples-hibernate5/7-grails-data-service-
  multi-datasource` — `@Query` GString variables (`p`, `pattern`) flagged as undeclared by static
  type checker. Root cause: `ServiceTransformation.groovy` called `copyAnnotations(method,
  methodImpl)` BEFORE `implementer.implement()`, so the implementation method's copy of `@Query`
  still contained the raw GString after the transform replaced the original. Fixed by moving
  `copyAnnotations` to after `implementer.implement()`.
- `GormApiResolver` NPE when `preferred.mappingContext` is null in unit test stubs; fixed with
  null-safe navigation (`?.`).

**Test suites passing**
- H5: 669 tests, 0 failures.
- H7: 2,960 tests, 0 failures.

### Still Failing (as of 2026-05-24)

| Module | Failing Tests | Suspected Cause |
|--------|--------------|-----------------|
| `grails-datamapping-core` | `ServiceTransformSpec` (11 tests) | Runtime service transform behavior; may be pre-existing or fallout from `GormStaticApi` `@CompileStatic` → `@CompileDynamic` change |
| `grails-data-mongodb` | `SchemaBasedMultiTenancySpec`, `SingleTenancySpec`, `MultiTenancySpec` (8 tests) | May be pre-existing against this base branch |
| `grails-rest-transforms` | `HalJsonRendererSpec`, `VndErrorRenderingSpec` | Likely pre-existing; unrelated to scaling |

### Open Architecture Questions

- `GormStaticApi` changed from `@CompileStatic` to `@CompileDynamic` in the O(M+N) refactor.
  This may affect `ServiceTransformSpec` and potentially other generated-code behaviors.
  Evaluate whether selective `@CompileDynamic` on specific methods is sufficient to restore
  `@CompileStatic` at the class level.

---

## PR Review Status

### PR #15654 — Hibernate 7 Base Structure (step 1)

**Status:** 3 approvals (jamesfredley, sbglasius, jamesfredley re-approved). Needs 1 more.
Blocker: matrei has concerns about unrelated changes.

**matrei feedback:**
> "Revert any changes not directly related to Hibernate 7 compatibility. PMD, Jacoco, and other
> unrelated additions should be split into separate focused PRs."

**sbglasius feedback (approved with caveat):**
> "Why are there so many unrelated changes? Impossible to get through all files. I assumed all
> files in grails-data-hibernate7 are a plain copy of grails-data-hibernate5."

**TestLens:** 4,782 tests passing. 1 flaky: `UserControllerSpec > User list` (intermittent).

**Next step:** Needs matrei approval or one more committer vote to merge.

---

### PR #15568 — Main Hibernate 7 PR (full implementation)

**Status:** jdaugherty approved; active review with open items. TestLens: 21,649 tests passing.

#### Critical Open Items (jdaugherty)

1. **`ConnectionSource.java` — default name change**
   Flagged: "I am OK with it but I'd like to understand the rationale."
   Answer: H7 registers datastores with key `"default"` (lowercase); the old constant `"DEFAULT"`
   caused lookup misses. Fix corrects the constant; backward compat via `OLD_DEFAULT` +
   `normalizeQualifier()`. TODO: document this rationale in a PR response.

2. **`GroovyPagesServlet.java` — thread context class loader change**
   Flagged by PMD. Historically risky. Awaiting `@davydotcom` response. Left unresolved.

3. **MongoDB doc workaround**
   TODO comment left in place. Awaiting `@jamesfredley` guidance on how to handle
   hibernate5/7 doc split in relation to mongo docs.

4. **`ServiceTransformation.groovy` — Out of scope change flagged**
   Reviewers flagged that moving `copyAnnotations` in a core AST transform is out of scope for a Hibernate 7 rewrite.
   **Rationale/Defense:** The O(M+N) architecture changes (specifically compilation and API resolution changes) tightened the Groovy static type checker's evaluation of generated AST nodes. Previously, `copyAnnotations` occurred *before* the `@Query` implementer replaced `GStringExpression`s with `constX(IMPLEMENTED)`, leaving raw GStrings with unresolved variables (like `${pattern}`) in the generated `methodImpl` AST. The type checker suddenly began throwing "undeclared variable" errors, breaking tests in `grails-datamapping-core-test` and `grails-test-examples-*`. Moving the copy operation *after* implementation fixes this latent bug by ensuring the safe, processed annotation is copied.
   **Fallback:** If reviewers insist, extract this 1-line move into a separate PR against `grails-datamapping-core` on the main branch, as it is a standalone backward-compatible bug fix.

#### Build / Plugin Items

| File | Concern |
|------|---------|
| `GrailsCodeStylePlugin.groovy` | Reports written to repo root instead of `build/reports`; codecoverage mixed into codestyle plugin — should be its own plugin |
| `GrailsTestPlugin.groovy` | Poorly named; reinvents Gradle's built-in test aggregation |
| `CompilePlugin.groovy` | Why are `abstractCompile` changes needed? GSP tasks extend from it |
| `build.gradle` | `local.properties` override already doable via Gradle env vars; should go in shared property plugin |
| `gradle/test-config.gradle` | Same: shared property plugin should handle |
| `grails-data-hibernate7/core/build.gradle` | Commented code; should centralize or remove |

#### Test Improvements Requested

Multiple test files across H5 and H7 still use manual `System.setProperty` / `cleanup()` patterns.
jdaugherty asked to adopt `@RestoreSystemProperties` (Spock) instead:
- `MultiTenancyBidirectionalManyToManySpec`
- `MultiTenancyUnidirectionalOneToManySpec`
- `SchemaMultiTenantSpec`
- `SingleTenantSpec`
- `SchemaPerTenantSpec`
- `PartitionedMultiTenancySpec`

Other minor test items:
- `UniqueConstraintHibernateSpec` — double comments; `@Ignore` annotations should be removed
  (use `@DatabaseCleanup` instead)
- `HibernateDirtyCheckingSpec` — forced `markDirty` may be masking a bug
- `simplelogger.properties` — noisy logging should be commented back out

#### H7-Specific Code Review Items

| File | Concern |
|------|---------|
| `CriteriaMethods.java` | Enum approach may prevent users from extending the criteria builder |
| `GrailsHibernateTemplate.java` | Should rediff against H5 to verify intentional divergence |
| `HibernateJtaTransactionManagerAdapter.java` | Line 52 removed — why? |
| `HibernateDatastoreSpringInitializer.groovy` | If removing `return`, also remove the variable assignment |
| `BookController.groovy` (schema-per-tenant) | Line 35 binding change alters test semantics |

#### Documentation Items

Large sections of the H7 docs are currently blank and need content:
- `eventsAutoTimestamping.adoc`, `configurationDefaults.adoc`, `configuration/index.adoc`
- All of `constraints/`, `databaseMigration/`, `gettingStarted/`, `multipleDataSources/`,
  `multiTenancy/`, `services/`, `testing/`
- `learningMore.adoc`

jdaugherty made an AI-assisted pass at docs; still needs review.

#### Structural / Administrative Items

| File | Concern |
|------|---------|
| `.gitignore` | Text/markdown work files should go in a dedicated directory, not root |
| `grails-data-hibernate7/AGENTS.md` | Double header; confirm still needed |
| `grails-data-hibernate7/ISSUES.md` | Shouldn't be distributed in source; needs a shared ignore-able directory |
| `grails-data-hibernate7/README.md` | Double license header |
| `plans/aggregate-style-violations.md` | No longer needed? |
| `@Requires` in TCK | Hardcodes Hibernate implementations; excludes GraphQL (regression); investigate |

#### TCK `@Requires` Regression (critical)

jdaugherty flagged that the `@Requires` annotation in the TCK now hardcodes specific Hibernate
implementations, which causes GraphQL tests to no longer run — a regression. The concern is that
using `@Requires` this way is a symptom of a larger coupling problem. Needs investigation before
merge.

---

## Planning Notes

**To unblock PR #15654 merge:** Address matrei's concern by identifying and reverting or splitting
out changes unrelated to H7 compatibility.

**To unblock PR #15568 review:** The most impactful items to clear first are:
1. Respond to the `ConnectionSource.DEFAULT` question (rationale already clear — just needs a comment)
2. Adopt `@RestoreSystemProperties` across the affected test specs
3. Fix the TCK `@Requires` regression
4. Respond to `CriteriaMethods.java` extensibility concern
5. Fill in the blank documentation sections

**O(M+N) branch next steps:**
1. Investigate and fix `ServiceTransformSpec` runtime failures (11 tests)
2. Investigate `MultiTenantMultiDataSourceSpec` and partitioned/schema multi-tenancy failures
3. Confirm MongoDB failures are pre-existing (run against base `8.0.x` to compare)
4. Evaluate whether `GormStaticApi` can be restored to `@CompileStatic` with targeted `@CompileDynamic`
