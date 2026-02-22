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

# Grails 8: Prioritized Modernization Roadmap

**Target**: Q2 2026 milestone releases
**Stack**: Spring Boot 4.0.x / Spring Framework 7.0.x / Groovy 5 / Hibernate 7.x/5.6.x / Java 17+ (25 recommended) / Gradle 9

---

## Table of Contents

- [Current State of Grails 8 Work](#current-state-of-grails-8-work)
- [Tier 0 - Gate Blockers](#tier-0---gate-blockers-nothing-ships-without-these)
- [Tier 1 - Performance and Startup](#tier-1---performance-and-startup)
- [Tier 2 - Security Hardening](#tier-2---security-hardening)
- [Tier 3 - Modern Runtime Features](#tier-3---modern-runtime-features)
- [Tier 4 - Compile-Time Shift](#tier-4---compile-time-shift)
- [Tier 5 - Developer Experience](#tier-5---developer-experience)
- [Tier 6 - Ecosystem and Future-Proofing](#tier-6---ecosystem-and-future-proofing)
- [What NOT to Do in Grails 8](#what-not-to-do-in-grails-8)
- [Appendix: Competitive Landscape](#appendix-competitive-landscape)

---

## Current State of Grails 8 Work

### Active Branches

| Branch | Focus | Scale |
|--------|-------|-------|
| `8.0.x` | Main Grails 8 integration branch | 145 files changed from 7.0.x, URL mapping fixes, constraint expansion, multi-datasource fixes |
| `8.0.x-hibernate7` | Hibernate 7 + GrailsDomainBinder refactor | **2,821 files changed, 145k+ insertions** - massive refactor decomposing monolithic binder into proper binder hierarchy. Contains three hibernate modules (`hibernate5`, `hibernate6`, `hibernate7`); **hibernate6 will be removed** (see Section 0.3) |

### Open PRs Targeting Grails 8 (`relates-to:v8`)

| PR | Title | Status | Impact |
|----|-------|--------|--------|
| [#15354](https://github.com/apache/grails-core/pull/15354) | **Spring Boot 4.0.1 + Spring Framework 7.0.2** | CI passing, tests green | Foundation - all other v8 work depends on this |
| [#15183](https://github.com/apache/grails-core/pull/15183) | **Groovy 5 Compatibility** | Blocker label, 23 commits, 31 files | Foundation - required for static compilation improvements and modern Groovy features |
| [#15365](https://github.com/apache/grails-core/pull/15365) | **Gradle 9.3.1 + Micronaut 4** | CI passing | Build infrastructure - fixes deprecated APIs, enables Gradle 10 readiness |
| [#15367](https://github.com/apache/grails-core/pull/15367) | **JLine 3.30.6 + Jansi 2.4.2** | CI passing, 100+ new tests | CLI modernization - JLine 2 is EOL |

### Open Issues Targeting Grails 8

| Issue | Title | Severity |
|-------|-------|----------|
| [#13757](https://github.com/apache/grails-core/issues/13757) | Grails 8 Feature Set tracking issue | Tracking |
| [#15374](https://github.com/apache/grails-core/issues/15374) | **Indy 4x performance regression** from metaclass invalidation | Critical |
| [#14956](https://github.com/apache/grails-core/issues/14956) | Hibernate 7.x.x support | High |
| [#14738](https://github.com/apache/grails-core/issues/14738) | Gradle 9 support | High |
| [#14736](https://github.com/apache/grails-core/issues/14736) | Groovy 5 support | Blocker |
| [#14915](https://github.com/apache/grails-core/issues/14915) | Spring 7 - XML configuration no longer supported (Bean Builder rework) | Blocker |
| [#15377](https://github.com/apache/grails-core/issues/15377) | hibernate5-dbmigration bootWar NPE in WAR deployment | High |

---

## Tier 0 - Gate Blockers (Nothing Ships Without These)

These are the highest-priority items for Grails 8.

### 0.1 Spring Boot 4 + Spring Framework 7 Compatibility

**PR**: [#15354](https://github.com/apache/grails-core/pull/15354) (ready for merge)
**Status**: CI green, all test suites passing. Uses Groovy 4.0.x + Hibernate 5.6-jakarta as an interim.

**Remaining work after merge**:
- Update `grails-spring-security` plugin for `ReflectionUtils.getApplication()` removal
- Fix SiteMesh3 decorator integration for Spring Framework 7
- Fix JSP rendering under Spring Boot 4 servlet configuration
- Address JSON Views error handling timeout
- Rework Bean Builder - Spring 7 drops XML configuration support ([#14915](https://github.com/apache/grails-core/issues/14915)); the current BeanBuilder is backed by XML; needs programmatic bean registration

**Breaking changes apps must handle**:
- Package relocations (6 autoconfigure classes moved)
- MongoDB property renames (`spring.data.mongodb.*` to `spring.mongodb.*`)
- `DefaultTransactionStatus` constructor now requires 8 parameters
- `HandlerAdapter.getLastModified()` removed
- Theme support removed from DispatcherServlet (vendored into Grails for GSP)

#### PR #15354 Changes

| Category | Change | Files |
|----------|--------|-------|
| **Vendored Spring Framework code** | Spring 7 removed theme support from `DispatcherServlet`. GSP requires it. Vendored `ThemeResolver`, `SessionThemeResolver`, `ResourceBundleThemeSource`, `UiApplicationContextUtils`, `SimpleTheme` etc. into `grails-spring` | 12 new files in `grails-spring/src/main/java/org/springframework/` |
| **Vendored Spring ORM code** | Spring 7 removed Hibernate 5 ORM integration classes. Vendored `HibernateTemplate`, `HibernateTransactionManager`, `HibernateOperations`, `LocalSessionFactoryBean`, `LocalSessionFactoryBuilder`, `SessionFactoryUtils`, `SessionHolder`, etc. into `grails-data-hibernate5` | 16 new files in `grails-data-hibernate5/core/src/main/java/.../hibernate5/` |
| **Package relocations** | Updated imports for 6 autoconfigure classes that moved in Spring Boot 4 | `ApplicationClassInjector`, `GrailsApplicationCompilerAutoConfiguration`, `ControllersAutoConfiguration`, `GrailsFilters`, `MongoDbGormAutoConfiguration` |
| **MongoDB property renames** | `spring.data.mongodb.*` to `spring.mongodb.*` | `application.yml`, `StartMongoGrailsIntegrationExtension`, MongoDB test specs |
| **Removed API replacements** | `SecurityProperties.DEFAULT_FILTER_ORDER` replaced with hardcoded `-100`; `AnnotationConfigServletWebApplicationContext` replaced with `GenericWebApplicationContext`; `MappedInterceptor.matches(String,PathMatcher)` replaced with `matches(HttpServletRequest)` | `GrailsFilters`, `GrailsApplicationBuilder`, `UrlMappingsHandlerMapping` |
| **Constructor signature changes** | `DefaultTransactionStatus` now requires 8 params (added `transactionName` and `nested`) | `TransactionalTransformSpec` |
| **Deprecated method removal** | `HandlerAdapter.getLastModified()` removed from interface | `UrlMappingsInfoHandlerAdapter` |
| **Test support updates** | `MockApplicationContext` added `getBeanProvider(ParameterizedTypeReference)`; `AbstractGrailsTagTests` added static constants for removed theme attributes | `MockApplicationContext.java`, `AbstractGrailsTagTests` |

#### PR #15354 Workarounds

| Workaround | Reason | Impact |
|------------|--------|--------|
| Vendored Spring theme classes into `grails-spring` | Spring 7 removed theme support; GSP layouts depend on `ThemeResolver` | Maintenance burden - vendored code won't get Spring updates. Should migrate GSP away from theme API in 8.1+ |
| Vendored Spring Hibernate 5 ORM classes into `grails-data-hibernate5` | Spring 7 removed Hibernate 5 integration | Required only for the Hibernate 5.6-jakarta path. Goes away when apps migrate to Hibernate 7.2 |
| Hardcoded `DEFAULT_FILTER_ORDER = -100` in `GrailsFilters` | `SecurityProperties.DEFAULT_FILTER_ORDER` removed | Fragile if Spring Security changes the default. Should use Spring Security's constant when security plugin is updated |

#### PR #15354 Disabled Tests (External Plugin Incompatibilities)

| Test | Plugin/Library | Root Cause | Action Needed |
|------|---------------|------------|---------------|
| `app3/LoadAfterSpec` | Spring Security 7.0.1-SNAPSHOT | `ReflectionUtils.getApplication()` removed in Spring Boot 4 | Update grails-spring-security plugin |
| `exploded/LoadAfterSpec` | Spring Security | Same as above | Same |
| `plugins/exploded/PluginDependencySpec` | Spring Security | Same as above | Same |
| `mongodb/test-data-service/TestServiceSpec` | Spring Security | Same as above | Same |
| `mongodb/test-data-service/StudentServiceSpec` | Spring Security | Same as above | Same |
| `gsp-sitemesh3/GrailsLayoutSpec` | SiteMesh3 | Decorator/layout not compatible with Spring Framework 7 | Update SiteMesh3 integration |
| `gsp-sitemesh3/EndToEndSpec` | SiteMesh3 | Same as above | Same |
| `gsp-layout/GrailsLayoutSpec` (JSP test only) | JSP support | JSP pages not rendering under Spring Boot 4 servlet config | Fix servlet JSP config or document as unsupported |
| `issue-views-182/CustomErrorSpec` | JSON Views | Error handling response times out | Debug timeout root cause |
| `RenderMethodTests.testRenderFile` (`@PendingFeature`) | MockHttpServletResponse | Behavior changed in Spring Framework 7 | Update test expectations |

#### PR #15354 Open Decisions

| Decision | Context | Options |
|----------|---------|---------|
| **GSP theme support long-term** | Vendored Spring theme classes are a maintenance liability | (a) Keep vendored code for 8.x lifecycle, (b) Migrate GSP to a Grails-native theming mechanism, (c) Deprecate theme support entirely |
| **SiteMesh3** | SiteMesh3 is incompatible with Spring 7 | (a) Request update to SiteMesh3, (b) Replace with Grails-native layout mechanism |
| **JSP support** | JSP rendering broken under Spring Boot 4 | (a) Fix and continue supporting, (b) Deprecate JSP in Grails 8 (GSP is the primary view technology) |

**Priority**: **CRITICAL** - Foundation for everything else.
**Effort**: Medium (PR exists, needs merge + follow-up fixes)

---

### 0.2 Groovy 5 Compatibility

**PR**: [#15183](https://github.com/apache/grails-core/pull/15183) (blocker label, by matrei)
**Status**: 23 commits, 31 files changed. Supersedes earlier #14737.

**Why this is a gate blocker**:
- Groovy 5 drops legacy APIs that Grails 7 relies on
- Groovy 5 brings improved static compilation that enables Tier 4 (compile-time shift)
- Groovy 5 introduces records, sealed classes, and GINQ improvements
- JLine 2 dependency in Groovy 4.x groovysh is removed in Groovy 5 (unblocks [#15367](https://github.com/apache/grails-core/pull/15367) cleanup)

#### PR #15183 Changes

| Category | Change | Files |
|----------|--------|-------|
| **Groovy version bump** | `dependencies.gradle` updated to Groovy 5.0.5-SNAPSHOT | `dependencies.gradle` |
| **Static trait method fix** | `Validateable.getConstraintsMap()` - static trait method dispatch changed | `Validateable.groovy` |
| **Closure type workarounds** | `@CompileDynamic` added where Groovy 5 rejects closure union types | `BoundPromise.groovy`, `JspTagImpl.groovy` |
| **VariableScopeVisitor NPE guards** | Groovy 5 changed AST visitor behavior; added try-catch wrappers | `GrailsASTUtils.java`, `AstUtils.groovy`, `LoggingTransformer.java` |
| **ConfigObject recursion fix** | `NavigableMap.merge()` hits infinite recursion with Groovy 5 ConfigObject | `NavigableMap.groovy` |
| **Trait static method extraction** | Moved static SQL methods out of `HibernateEntity` trait (joint compilation issue) | `HibernateEntity.groovy`, `HibernateMappingContext.java` |
| **ClosureExpression VariableScope** | Groovy 5 requires non-null VariableScope on programmatic closures | `AbstractMethodDecoratingTransformation.groovy`, `ResourceTransform.groovy` |
| **InnerClassNode handling** | `GormEntityTransformation` no longer checks `InnerClassNode` | `GormEntityTransformation.groovy` |
| **@ApiDelegate default** | Added `default Object.class` for Groovy 5 annotation processing | `ApiDelegate.java`, `ApiDelegateTransformation.java` |
| **@Builder retention change** | `ConfigurationBuilder` handles `@Builder` being SOURCE-only in Groovy 5 | `ConfigurationBuilder.groovy` |
| **i18n fallback** | `AbstractConstraint` falls back to direct `ResourceBundle` lookup | `AbstractConstraint.java` |
| **Spock/test compatibility** | `@IgnoreIf` for final method mocking; removed `spock-core transitive=false` | 30+ `build.gradle` files, multiple test specs |
| **MongoDB codec fixes** | Updated for Groovy 5 type changes | `BsonPersistentEntityCodec`, `PersistentEntityCodec`, `MongoCodecSession` |

#### PR #15183 Workarounds

| Workaround | Reason | Permanent Fix Path |
|------------|--------|--------------------|
| `@CompileDynamic` on `BoundPromise.then()` | Groovy 5 rejects `Closure<T>` union types under static compilation | Redesign method signature or wait for upstream Groovy fix |
| `@CompileDynamic` on `JspTagImpl.applyAttributes()` | Same closure union type issue with `instanceof` checks | Same |
| `metaClass.invokeStaticMethod` in `Validateable` | Static trait method resolves to trait's version, not implementor's override | File upstream Groovy bug or move `getConstraintsMap()` out of trait |
| Try-catch around `VariableScopeVisitor` calls | Groovy 5 throws NPE in certain AST transformation scenarios | Report upstream - Groovy 5 should handle these states gracefully |
| `NavigableMap` ConfigObject conversion | ConfigObject infinite recursion in Groovy 5 | Decide NavigableMap future ([#14005](https://github.com/apache/grails-core/issues/14005)) |
| `@IgnoreIf` on tests mocking final methods | Groovy 5/ByteBuddy cannot mock finals | Rewrite tests to avoid mocking finals |

#### Groovy 5 Compatibility Fixes, Workarounds, and Open Decisions (Detailed)

Based on commit [`9574fe8`](https://github.com/apache/grails-core/commit/9574fe8f7f6a1d230b895c8b1df76fd5b6860df9) (66 files changed across 30+ modules), the following is a categorized inventory of what Groovy 5 breaks and the current fix status:

**Workarounds (temporary - need proper fix or upstream Groovy resolution):**

| Area | File(s) | Issue | Current Workaround | Decision Needed |
|------|---------|-------|--------------------|-----------------|
| Closure union types | `BoundPromise.groovy`, `JspTagImpl.groovy` | Groovy 5 static compiler rejects closure return types that vary (`Closure<T>` union types, `instanceof` checks in closures) | Added `@CompileDynamic` to affected methods | Upstream Groovy bug? Or redesign method signatures to avoid union types so `@CompileStatic` can be restored |
| Static trait method resolution | `Validateable.groovy` | `this.defaultNullable()` in a static trait method resolves to the trait's version instead of the implementing class's override in Groovy 5 | Replaced with `metaClass.invokeStaticMethod` (dynamic dispatch) | This is a behavioral regression in Groovy 5. File upstream bug? Or redesign Validateable to not rely on static method overrides in traits |
| VariableScopeVisitor NPE | `GrailsASTUtils.java`, `AstUtils.groovy` | Groovy 5 changed how `VariableScopeVisitor` handles certain AST states; throws NPE during transformations | Wrapped calls in try-catch | Report upstream. These are AST transform edge cases that Groovy 5 should handle gracefully |
| LogASTTransformation NPE | `LoggingTransformer.java` | `LogASTTransformation.visit()` throws NPE in `VariableScopeVisitor` during Groovy 5 | Wrapped in try-catch | Same root cause as VariableScopeVisitor NPE above - single upstream fix would resolve both |
| ConfigObject infinite recursion | `NavigableMap.groovy` | Merging a `ConfigObject` into `NavigableMap` causes infinite recursion in Groovy 5 (ConfigObject behavior changed) | Added explicit `ConfigObject` conversion method before merge | Decide: is `NavigableMap` still needed? ([#14005](https://github.com/apache/grails-core/issues/14005) proposes optimizing/replacing it). If kept, needs proper ConfigObject handling, not a workaround |

**Proper Fixes (completed - no further action needed):**

| Area | File(s) | Issue | Fix Applied |
|------|---------|-------|-------------|
| `@Builder` SOURCE retention | `ConfigurationBuilder.groovy` | Groovy 5 changed `@Builder` annotation retention; `@Builder(builderStrategy=SimpleStrategy)` is now SOURCE-only | Added `ConverterNotFoundException` handling and guarded builder-prefix method detection to work without runtime annotation |
| Static methods in traits (joint compilation) | `HibernateEntity.groovy`, `HibernateMappingContext.java` | See detailed analysis below | See detailed analysis below |
| ClosureExpression VariableScope | `AbstractMethodDecoratingTransformation.groovy`, `ResourceTransform.groovy` | Groovy 5 requires `ClosureExpression` to have a non-null `VariableScope` for bytecode generation | Set explicit `VariableScope` on all programmatically-created closures in AST transforms |
| InnerClassNode handling | `GormEntityTransformation.groovy` | Groovy 5 changed `InnerClassNode` classification | Removed `InnerClassNode` import; updated `instanceof` check to use `classNode.isEnum()` only |
| `@ApiDelegate` default value | `ApiDelegate.java`, `ApiDelegateTransformation.java` | Groovy 5 requires annotation attributes with no default to be explicitly provided | Added `default Object.class` to `@ApiDelegate.value()`; transformation checks for default before using |
| i18n message fallback | `AbstractConstraint.java` | `ConstrainedProperty.DEFAULT_MESSAGES` static map not populated in Groovy 5 context | Added `getDefaultMessageFromBundle()` method that falls back to direct `ResourceBundle` lookup from `MESSAGE_BUNDLE` |
| Spock test mocking | Multiple test specs | Groovy 5 / ByteBuddy cannot mock final methods (Groovy 5 makes more methods final) | Added `@IgnoreIf` with Groovy 5 version detection; standardized `isGroovy5()` helper across specs |
| spock-core transitive deps | 30+ `build.gradle` files | Spock 2.3 with Groovy 5 requires different transitive dependency handling | Removed `transitive=false` from spock-core dependencies; let Gradle resolve Groovy 5-compatible transitives |

**Static Methods in Traits - Joint Compilation Problem (detailed analysis from [PR #15183 comment](https://github.com/apache/grails-core/pull/15183#issuecomment-3854397217)):**

The issue is **NOT** that Groovy 5 prohibits static methods in traits. `GormEntity` has 87+ static methods and works fine. The issue is **joint compilation with Java files that directly import the trait**.

| Trait | Module | Static Methods | Java Files Import It? | Result in Groovy 5 |
|-------|--------|----------------|----------------------|-------------------|
| `GormEntity` | grails-datamapping-core | 87 | **No** | Works |
| `HibernateEntity` | grails-data-hibernate5-core | 5 | **Yes** (`HibernateMappingContext.java`) | **Fails** |
| `MongoEntity` | grails-data-mongodb | 18 | No | Works |
| `Neo4jEntity` | grails-data-neo4j | 10 | No | Works |

When a Java file in the same module imports a Groovy trait with static methods that return generic type parameters (`D`), the Groovy stub generator creates invalid Java code:

```java
// Generated stub (INVALID Java)
@groovy.transform.Generated()
static java.util.List<D> findAllWithSql(java.lang.CharSequence sql);
//                    ^ ERROR: non-static type variable D cannot be
//                      referenced from static context
```

In Java, you cannot use a class-level type parameter (`D`) in a static method signature. The Groovy trait works because Groovy handles this differently at runtime, but the stub generator doesn't account for this.

**Potential solutions** (decision needed):

| Approach | Description | Trade-off |
|----------|-------------|-----------|
| **Remove Java import via reflection** | Modify `HibernateMappingContext.java` to load `HibernateEntity` via `Class.forName()` instead of direct import, avoiding stub generation ([commit `425d2da`](https://github.com/apache/grails-core/commit/425d2da3b633fa471ad64dbb21a92c1327a5268b)) | Loses compile-time type safety in `HibernateMappingContext`; adds reflection call |
| **Move class to another module** | Move `HibernateMappingContext.java` to a module that doesn't jointly compile with `HibernateEntity.groovy` | Module restructuring; may create circular dependency |
| **Convert Java class to Groovy** | Rewrite `HibernateMappingContext.java` as `HibernateMappingContext.groovy` so it goes through Groovy compilation, not Java stub compilation | Groovy compilation is slower; may affect `@CompileStatic` coverage |
| **Move static methods to companion** | Extract the 5 static SQL methods to a `HibernateEntityStaticApi` helper class, keep the trait pure instance methods | Changes API surface; existing code calling `HibernateEntity.findAllWithSql()` must change |
| **Fix Groovy stub generator** | File upstream Groovy bug to generate proper `<D>` type parameter on static stubs | Depends on Groovy team timeline; not in our control |

**Open Decisions (require architectural discussion):**

| Decision | Context | Options |
|----------|---------|---------|
| **`@CompileDynamic` workarounds - accept or fix?** | Two methods (`BoundPromise.then()`, `JspTagImpl.applyAttributes()`) dropped from `@CompileStatic` to `@CompileDynamic`. This is a performance regression on hot paths (Indy call site caching defeated). | (a) Accept for 8.0.0, fix in 8.1 if Groovy 5 adjusts for Grails, (b) Redesign method signatures now to avoid union types, (c) File Groovy JIRA and wait for upstream fix |
| **Validateable static trait dispatch** | The `metaClass.invokeStaticMethod` workaround defeats static compilation for constraint evaluation. Every `validate()` call goes through dynamic dispatch. | (a) Accept perf hit, (b) Move `getConstraintsMap()` out of the trait into a registry/helper class, (c) File Groovy 5 bug for static trait method resolution |
| **NavigableMap future** | NavigableMap is deprecated ([#14005](https://github.com/apache/grails-core/issues/14005)). The Groovy 5 ConfigObject fix is a band-aid. | (a) Remove NavigableMap entirely in 8.0 (breaking), (b) Keep with workaround, remove in 8.1, (c) Replace with flattened `PropertySource`-based config |
| **HibernateEntity static trait methods** | 5 static methods on `HibernateEntity` fail joint compilation with `HibernateMappingContext.java` due to Groovy 5 stub generator producing invalid generic type parameters in static context. See detailed analysis above. | (a) Reflection-based import avoidance (proven in `425d2da`), (b) Move Java class to different module, (c) Convert Java to Groovy, (d) Move static methods to companion class, (e) Fix Groovy stub generator upstream |
| **Test coverage gaps from `@IgnoreIf`** | Several tests are skipped on Groovy 5 due to mocking limitations. These test features (mocking final methods, static method mocking) that Groovy 5 restricts. | (a) Rewrite tests to not mock finals (preferred), (b) Use PowerMock/Mockito inline for final mocking, (c) Accept coverage gap |
| **Groovy 5 SNAPSHOT vs release** | Current work targets Groovy 5.0.5-SNAPSHOT. API surface may still shift before GA. | Track Groovy 5 release candidates closely. Avoid relying on any behavior that is explicitly marked as "subject to change" in Groovy 5 release notes. |

**Priority**: **CRITICAL** - Blocks modern language features and Indy fixes.
**Effort**: High (active work, 66 files touched, multiple open architectural decisions)

---

### 0.3 Hibernate Dual-Version Support (5.6-jakarta + 7.2)

**Branch**: `8.0.x-hibernate7` (2,821 files changed, 145k+ insertions)
**Issue**: [#14956](https://github.com/apache/grails-core/issues/14956)

Grails 8 can ship with a **choice** between Hibernate 5.6-jakarta (legacy, proven) and Hibernate 7.2 (modern, Spring Boot 4 native). The Spring Boot 4 PR ([#15354](https://github.com/apache/grails-core/pull/15354)) already passes CI with Hibernate 5.6-jakarta, proving the 5.6 path works today.

#### Module Consolidation: Remove `grails-data-hibernate6`

The `8.0.x-hibernate7` branch currently contains three hibernate modules: `grails-data-hibernate5` (588 files), `grails-data-hibernate6` (588 files), and `grails-data-hibernate7` (708 files). **`grails-data-hibernate6` should be removed.** It is a development stepping stone that served its purpose during the binder refactoring but has no place in the final release:

| Module | Hibernate | Binder Architecture | Gaps | Ship in Grails 8? |
|--------|-----------|---------------------|------|--------------------|
| `grails-data-hibernate5` | 5.6-jakarta | Monolithic `GrailsDomainBinder` (to be refactored) | None (production) | **Yes** - legacy path |
| `grails-data-hibernate6` | 6.x | Partial decomposition - still has `AbstractGrailsDomainBinder` + flat `domainbinding/` | Multitenancy, Proxy support | **No - remove** |
| `grails-data-hibernate7` | 7.2 | Fully decomposed `domainbinding/binder/` hierarchy + generators + entity hierarchy | None | **Yes** - modern path |

**Why hibernate6 is redundant:**
- Hibernate 6.x is not Spring Boot 4's target (that's 7.2) and not the legacy path (that's 5.6-jakarta)
- hibernate7 supersedes all hibernate6 work - the 56 files unique to hibernate6 are the old binder pattern that hibernate7 replaced with 176 new files in the decomposed hierarchy
- hibernate6 still has known gaps (multitenancy, proxy support) that hibernate7 resolved
- Maintaining 3 modules instead of 2 triples the surface area for Hibernate API changes

**Removal plan:**
1. Delete `grails-data-hibernate6/` directory and all `grails-data-hibernate6-*` entries from `settings.gradle`
2. Port any valuable `grails-test-examples/hibernate6/` functional tests to `hibernate7/` equivalents, then delete the hibernate6 test examples
3. Verify no other modules reference `grails-data-hibernate6` in their build files

#### Binder Refactoring (Apply to Both Versions)

The massive refactoring on `8.0.x-hibernate7` (in the `grails-data-hibernate7` module) decomposes the monolithic `GrailsDomainBinder` into a proper hierarchy:
- `RootBinder`, `SubClassBinder`, `SubclassMappingBinder`, `DiscriminatorPropertyBinder`
- `ComponentBinder`, `CollectionSecondPassBinder`, `DependentKeyValueBinder`
- `UnidirectionalOneToManyBinder`, `SimpleValueColumnBinder`, `PrimaryKeyValueCreator`
- `HibernateCriteriaBuilder` refactored with `CriteriaMethodInvoker` and `CriteriaMethods` enum
- `GrailsHibernatePersistentEntity` and `GrailsHibernatePersistentProperty` hierarchy enriched
- Each binder now has its own Spock spec (testable in isolation)

**This refactoring should be applied to the Hibernate 5.6 codebase as well.** The binder decomposition improves maintainability, testability, and code clarity regardless of the Hibernate version underneath. Having the 5.6 variant use a monolithic `GrailsDomainBinder` while the 7.2 variant has proper binder separation would make long-term maintenance significantly harder.

#### Version Selection Mechanism

Use **Maven classifier** and **Gradle `requireCapability`** so apps choose their Hibernate version at dependency resolution time:

```groovy
// Gradle - user's build.gradle
dependencies {
    // Option A: Hibernate 5.6-jakarta (legacy, default for migration)
    implementation 'org.apache.grails:grails-data-hibernate:8.0.0:hibernate56'

    // Option B: Hibernate 7.2 (modern, recommended for new projects)
    implementation 'org.apache.grails:grails-data-hibernate:8.0.0:hibernate72'
}
```

Or via Gradle's feature variants and `requireCapability`:

```groovy
dependencies {
    implementation('org.apache.grails:grails-data-hibernate:8.0.0') {
        requireCapability 'org.apache.grails:grails-data-hibernate-7.2'
    }
}
```

This ensures:
- No classpath conflicts (only one Hibernate version on classpath)
- BOM/platform support for transitive Hibernate version alignment
- Clear migration path: start with 5.6, switch to 7.2 when ready
- Grails Forge can offer the choice at project creation time

#### Liquibase / Database Migration Plugin Concerns

**Hibernate 5.6 path**: No license issue. The Database Migration plugin pins to Liquibase 4.27.0 and `liquibase-hibernate5`, both Apache 2.0 licensed. This continues to work exactly as it does in Grails 7.

**Hibernate 7.2 path**: PR [liquibase/liquibase-hibernate#844](https://github.com/liquibase/liquibase-hibernate/pull/844) adds Hibernate 7 support to `liquibase-hibernate`, but it targets Liquibase 5.x. Liquibase core v5.0.0 was the first release under the new **FSL-1.1-ALv2** license (Functional Source License, Version 1.1, Apache License v2 Future License). All Liquibase core versions prior to 5.0.0 remain Apache 2.0. Under FSL-1.1-ALv2, each release reverts to Apache 2.0 two years after its release date.

**ASF policy allows this as an optional dependency.** Per the [ASF third-party license policy](https://www.apache.org/legal/resolved.html): *"You may rely on them when they support an optional feature. Apache projects can rely on components under prohibited licenses if the component is only needed for optional features. When doing so, a project shall provide the user with instructions on how to obtain and install the non-included work."* The Database Migration plugin with Liquibase 5.x is optional - it is not bundled with Grails, not required for standard use, and users install it themselves. Grails must provide clear documentation on how to obtain and configure the Liquibase 5.x-based migration plugin for the Hibernate 7.2 path.


#### Why Hibernate 7 Matters

- Jakarta Persistence 3.2 compliance (Jakarta EE 11)
- Spring Boot 4 ships with Hibernate 7.2
- Hibernate 7 drops many deprecated 5.x APIs that GORM wraps
- Better SQL generation, stateless session improvements, annotation processor support

**Priority**: **CRITICAL** - Both paths must work. 5.6-jakarta is the safe default; 7.2 is the modern target.
**Effort**: Very High (binder refactoring underway; must apply to both versions)

---

### 0.4 Gradle 9 Support

**PR**: [#15365](https://github.com/apache/grails-core/pull/15365) (CI passing)
**Issue**: [#14738](https://github.com/apache/grails-core/issues/14738)

**What it does**:
- Gradle 8.14.4 to 9.3.1 across all modules
- Fixes all deprecated Gradle APIs (`convention` to `extensions`, `ConfigureUtil` to `ClosureBackedAction`, `Task.project` at execution time)
- Upgrades Develocity plugin from 4.1.1 to 4.3.2
- Upgrades grails-forge from Micronaut 3.10.4 to 4.10.7 (including javax to jakarta migration)

**Why it's a gate blocker**:
- Gradle 8 will eventually hit EOL; Grails 8 users need Gradle 9 support
- Gradle 9 enforces stricter task isolation (fixes prepare for Gradle 10)
- Micronaut 4 upgrade in grails-forge is required for modern forge features

#### PR #15365 Changes

| Category | Change | Files |
|----------|--------|-------|
| **Gradle wrapper** | 8.14.4 to 9.3.1 across all projects (root, build-logic, grails-gradle, grails-forge) | `gradle-wrapper.properties` (4), `gradlew`/`gradlew.bat` (4), `.sdkmanrc` |
| **`convention` to `extensions`** | Replace removed `convention` API | `SbomPlugin`, `RockerPlugin`, `RockerSourceSetProperty` |
| **`ConfigureUtil` to `ClosureBackedAction`** | Replace removed Gradle API | Gradle plugin sources |
| **`JavaPluginConvention` to `JavaPluginExtension`** | Replace removed convention type | Build plugins |
| **`outputFile` to `destinationFile`** | Replace deprecated `WriteProperties` API | Build plugins |
| **`Task.project` at execution time** | Capture project properties at configuration time (required for Gradle 10) | `SbomPlugin` (projectName, projectPath, buildDate), `PublishPlugin` (PublishingExtension), `GrailsGradlePlugin` (buildDir, antBuilder), `GrailsProfileGradlePlugin` (replaced `project.sync` with `Sync` task) |
| **Develocity plugin** | 4.1.1 to 4.3.2; `common-custom-user-data-gradle-plugin` 2.3 to 2.4.0 | `settings.gradle` files |
| **Develocity migration** | `grails-data-neo4j` and `grails-data-graphql` from deprecated `gradle-enterprise` to `develocity` | `settings.gradle` for both subprojects |
| **JUnit Platform launcher** | Added dependency required by Gradle 9 for test execution | `test-config.gradle`, `functional-test-config.gradle` |
| **Micronaut 4 (grails-forge)** | 3.10.4 to 4.10.7; Groovy 3.x to 4.0.30; Spock 2.1 to 2.3 | Forge `gradle.properties`, all forge `build.gradle` files |
| **javax to jakarta (grails-forge)** | `javax.validation.*` to `jakarta.validation.*`; `javax.transaction.*` to `jakarta.transaction.*` | Forge controllers, services |
| **Forge dependency coordinates** | `org.codehaus.groovy` to `org.apache.groovy`; `io.micronaut:micronaut-bom` to `io.micronaut.platform:micronaut-platform` | Forge build files |
| **ByteBuddy for Spock** | Added for Spock mocking on Java 17+ (CGLIB doesn't support Java 17 class files) | Test dependencies |
| **`--add-opens` JVM arg** | `java.base/java.lang=ALL-UNNAMED` for test execution on Java 17+ | Test config |
| **Coherence BOM** | Updated license mapping for version 25.03.2 | `SbomPlugin` |

#### PR #15365 Workarounds

None - all changes in this PR are proper fixes, not workarounds.

#### PR #15365 Known Remaining Deprecations

| Warning | Source | Resolution |
|---------|--------|------------|
| `StartParameter.isConfigurationCacheRequested` | `org.asciidoctor.jvm.convert` plugin 4.0.5 | Fix in asciidoctor-gradle-plugin 5.0.0+ (currently alpha). Tracked: [asciidoctor#756](https://github.com/asciidoctor/asciidoctor-gradle-plugin/issues/756) |

#### PR #15365 Open Decisions

| Decision | Context | Options |
|----------|---------|---------|
| **Asciidoctor plugin upgrade** | 4.0.5 has Gradle 10 deprecation warning; 5.0.0 is alpha | (a) Stay on 4.0.5 until 5.0.0 GA, (b) Accept deprecation warning |
| **Coherence BOM license** | Updated mapping for 25.03.2 | Verify license compatibility with ASF policy |

**Priority**: **CRITICAL** - Build tooling must be current.
**Effort**: Low (PR exists and is passing)

---

### 0.5 Bean Builder Rework for Spring 7

**Issue**: [#14915](https://github.com/apache/grails-core/issues/14915)

Spring Framework 7 no longer supports XML-driven MVC configuration. Grails' `BeanBuilder` is ultimately backed by XML bean definitions. Spring 7 is adding programmatic bean registration APIs as a replacement.

> **Note**: The Spring Boot 4 PR ([#15354](https://github.com/apache/grails-core/pull/15354)) is somehow passing CI with the current BeanBuilder implementation. This needs investigation - it may be that Spring 7 still supports the programmatic `GenericBeanDefinition` path that BeanBuilder actually uses (as opposed to XML file parsing), or that the deprecated codepath hasn't been fully removed yet. Either way, the rework should still be planned to avoid relying on potentially removed internals.

**Required action**: Rewrite `BeanBuilder` to use Spring's new programmatic `BeanRegistry` API instead of XML-backed `GenericBeanDefinition`. This is architectural - it touches how every Grails plugin registers beans via `doWithSpring`.

**Priority**: **CRITICAL** - Without this, plugin bean registration breaks on Spring 7.
**Effort**: High (architectural change, touches every plugin)

---

## Tier 1 - Performance and Startup

### 1.1 Fix Invoke Dynamic (Indy) 4x Performance Regression

**Issue**: [#15374](https://github.com/apache/grails-core/issues/15374)
**Severity**: HIGH

**Root cause**: Grails frequently modifies metaclasses during request processing (GORM operations, codec registration, taglib dispatch). Each metaclass modification invalidates ALL `CacheableCallSite` targets across the entire application, forcing JIT recompilation. With Indy enabled in Groovy 4, this causes a 4x regression vs Grails 6.

**Attack plan (from issue analysis)**:

1. **Batch metaclass modifications at startup** - All `ExpandoMetaClass` changes should complete before the first request. No metaclass modifications during request processing.
2. **Pre-register all GORM dynamic finders at startup** - Instead of `methodMissing` adding metaclass methods lazily on first call, register all `findBy*`, `countBy*`, `listOrderBy*` methods for all persistent properties during `GormEnhancer.enhanceAll()`.
3. **Replace runtime metaclass codec registration** - `CodecMetaClassSupport.configureCodecMethods()` adds `encodeAsHTML()` etc. via ExpandoMetaClass. Replace with Groovy extension modules (resolved at compile time with `@CompileStatic`).
4. **Replace taglib methodMissing dispatch** - `TagLibraryMetaUtils.methodMissingForTagLib` resolves tags dynamically. Generate a dispatch table `Map<String, Closure>` at startup.
5. **Add a metaclass modification freeze** - After startup, set a flag that prevents (or logs warnings for) any further metaclass modifications. This makes violations detectable.

**Priority**: **P0** - 4x performance regression significantly impacts user experience and adoption.
**Effort**: High (requires systematic audit and rewrite of metaclass modification patterns across GORM, codecs, and taglibs)

---

### 1.2 Spring AOT Integration

**Current state**: Zero Spring AOT support. No `RuntimeHints`, no `AotProcessor`, no `BeanRegistrationAotProcessor`, no `spring-context-indexer`.

**What Spring AOT gives for free** (if integrated):
- Ahead-of-time bean definition generation (no runtime reflection for bean wiring)
- Compile-time proxy generation (no CGLIB at runtime)
- Reflection metadata pre-computation
- GraalVM native image readiness (prerequisite)
- 30-40% startup time reduction

**Required work**:
1. Implement `BeanFactoryInitializationAotProcessor` for Grails plugin `doWithSpring` closures
2. Implement `BeanRegistrationAotProcessor` for artefact beans (controllers, services, taglibs)
3. Generate `RuntimeHints` for all unavoidable reflection (domain classes, command objects, config binding)
4. Add `spring-context-indexer` support - generate `META-INF/spring.components` at build time to eliminate runtime classpath scanning

**Priority**: **P0** - Prerequisite for GraalVM native image and major startup improvement.
**Effort**: Very High (new subsystem, touches bean lifecycle fundamentals)

---

### 1.3 Virtual Thread Readiness

**Current state**: Multiple `ThreadLocal` usages that will break with virtual threads:

| Location | Usage | Severity |
|----------|-------|----------|
| `ChainedEncoder` (grails-encoder) | ThreadLocal for encoder caching | High - per-request encoding |
| `DatastoreUtils` (grails-datastore-core) | NamedThreadLocal for deferred session close | High - every GORM operation |
| `SoftThreadLocalMap` (grails-datastore-core) | InheritableThreadLocal for session map | High - session management |
| `ConvertersConfigurationHolder` (grails-converters) | ThreadLocal for converter config | Medium - JSON/XML rendering |
| `Tenants` (grails-datamapping-core) | ThreadLocal for current tenant ID | High - multi-tenancy |
| `DeferredBindingActions` (grails-core) | ThreadLocal for binding action queue | Medium - data binding |
| `GroovyPageMetaInfo` (grails-gsp) | ThreadLocal for page instances | Medium - GSP rendering |

**Why this matters**: Spring Boot 4 and Spring Framework 7 have first-class virtual thread support. Spring Boot 4 auto-configures virtual threads when available on Java 21+. If Grails' ThreadLocal usage pins virtual threads to platform threads, users get worse performance than Grails 7 when enabling virtual threads.

**Required work**:
1. Audit ALL ThreadLocal/InheritableThreadLocal usages across the codebase
2. Replace with `ScopedValue` (Java 21+ preview, Java 25 final) where possible
3. For Java 17 baseline: replace with request-scoped beans or explicit context propagation
4. Test under virtual thread executor to verify no pinning

**Priority**: **P1** - Java 21 virtual threads are the biggest JVM performance improvement in a decade. Grails must not regress.
**Effort**: Medium (systematic but each replacement is straightforward)

---

### 1.4 Static Compilation Coverage Expansion

**Current state in grails-core/src/main**: 45 out of 73 Groovy files (62%) have `@CompileStatic` or `@GrailsCompileStatic`.

Without `@CompileStatic`, every Groovy method call goes through dynamic dispatch:
- MetaClass lookup per call
- No JIT inlining
- Indy call site caching defeated by metaclass changes (ties into 1.1)
- GraalVM native image impossible for dynamically-dispatched code

**Required work**:
1. Audit all `.groovy` files in `src/main` across every module for `@CompileStatic` eligibility
2. Add `@CompileStatic` to all artefact handlers (simple suffix-matching, pure static logic)
3. Add `@CompileStatic` to all utility classes (`GrailsNameUtils`, `GrailsClassUtils` - already type-safe)
4. Add `@CompileStatic` to all configuration classes
5. Create a `compileStaticCheck` Gradle task that fails on new Groovy files without `@CompileStatic` (unless explicitly exempted with `@CompileStatic(TypeCheckingMode.SKIP)`)

**Target**: 90%+ static compilation across framework source by Grails 8 GA.

**Modules with highest opportunity**:

| Module | Estimated Eligible | Currently Static |
|--------|-------------------|-----------------|
| grails-web-url-mappings | ~15 files | ~2 |
| grails-converters | ~10 files | ~0 |
| grails-controllers | ~8 files | ~3 |
| grails-validation | ~8 files | ~3 |
| grails-interceptors | ~6 files | ~1 |
| grails-databinding | ~6 files | ~2 |
| grails-services | ~5 files | ~1 |
| grails-mimetypes | ~5 files | ~1 |

**Priority**: **P1** - Direct performance impact, enables GraalVM, prevents Indy regression.
**Effort**: Medium (systematic, most files need only annotation addition + minor type fixes)

---

### 1.5 Artefact Discovery at Build Time

**Current state**: Runtime scanning via `ArtefactHandler.isArtefact(Class)` for every application class at startup.

**Required work**:
1. Generate `META-INF/grails/artefact-index.properties` at build time (Gradle plugin or annotation processor)
2. Extend existing AST transforms (`ControllerArtefactTypeTransformation`, `TagLibArtefactTypeAstTransformation`) to write index entries
3. `DefaultGrailsApplication.configureLoadedClasses()` reads the pre-built index instead of iterating all handlers
4. Fall back to runtime scanning only for classes not in the index (backward compat)

**Priority**: **P1** - 10-15% startup reduction, enables Spring AOT integration.
**Effort**: Medium

---

## Tier 2 - Security Hardening

### 2.1 Default Security Headers

**Current state**: Grails does not set modern security headers out of the box. Applications must configure these manually or rely on the Spring Security plugin.

**Required defaults for Grails 8**:

| Header | Value | Purpose |
|--------|-------|---------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Force HTTPS |
| `X-Content-Type-Options` | `nosniff` | Prevent MIME sniffing |
| `X-Frame-Options` | `DENY` | Prevent clickjacking |
| `Content-Security-Policy` | `default-src 'self'` (configurable) | Prevent XSS/injection |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Limit referrer leakage |
| `Permissions-Policy` | Deny camera, microphone, geolocation by default | Limit browser API abuse |
| `X-XSS-Protection` | `0` (modern approach - let CSP handle it) | Disable buggy browser XSS filter |

**Implementation**: Add a `SecurityHeadersFilter` registered by default in `ControllersAutoConfiguration`. Allow full override via `application.yml` under `grails.security.headers.*`. Disable with `grails.security.headers.enabled: false`.

**Priority**: **P1** - Security defaults should be secure. Opt-out, not opt-in.
**Effort**: Low

---

### 2.2 CSRF Protection by Default

**Current state**: No built-in CSRF protection. Entirely delegated to Spring Security plugin (optional dependency).

**Potentially for Grails 8**:
- Enable CSRF token generation and validation for all state-changing requests (POST, PUT, DELETE, PATCH) by default
- Integrate with Spring Security's `CsrfFilter` when Spring Security is present
- Provide standalone CSRF filter when Spring Security is NOT present
- Add GSP tag `<g:csrfToken/>` or auto-inject into `<g:form>` tags
- Support both synchronizer token pattern and double-submit cookie pattern
- Support SPA/API mode with `X-CSRF-TOKEN` header

**Priority**: **P1** - OWASP Top 10. Should not require a plugin.
**Effort**: Medium

---

### 2.3 Secure Data Binding (`secureBindData()`)

**Current state**: `bindData()` supports `whiteList`/`blackList` parameters via `DefaultASTDatabindingHelper` compile-time generation, but there is no enforcement that callers provide an explicit allowed params list. Omitting the list silently binds all request parameters - a mass-assignment vulnerability.

**Two new binding methods for Grails 8**:

| Method | Behavior | Use Case |
|--------|----------|----------|
| `secureBindData(target, params, allowedParams)` | Binds only properties in `allowedParams`. **Compile error if `allowedParams` is omitted.** | Standard secure binding - prevents mass-assignment by requiring an explicit allowlist |
| `secureBindData(target, params, allowedParams, nullMissing: true)` | Binds properties in `allowedParams`. Properties that are in `allowedParams` but **missing from the request** are set to `null` on the target. | Prevents stale data attacks - ensures an update form that omits a field actually clears that field, rather than preserving whatever value was previously persisted |

**Example**:

```groovy
// Compile error - allowedParams required
secureBindData(book, params)  // FAILS

// Only title and author are bound; all other params ignored
secureBindData(book, params, ['title', 'author'])

// title bound from params; author is in allowed list but missing
// from request, so book.author is set to null
secureBindData(book, params, ['title', 'author'], nullMissing: true)
```

**Implementation**: AST transform enforces the `allowedParams` parameter at compile time. The `nullMissing` variant iterates the allowed list after binding and sets any property not present in the request params to `null`.

**Priority**: **P1** - Mass-assignment is OWASP Top 10. Secure binding should be easy to use correctly.
**Effort**: Medium

---

### 2.4 Encode-by-Default in GSP

**Current state**: GSP has codec-aware output, but the default encoding behavior varies. `${expression}` output encoding depends on the codec configured for the page.

**Potentially for Grails 8**:
- Default all GSP expression output to HTML encoding (`encodeAsHTML()`)
- Require explicit `raw()` or `${raw(expression)}` for unencoded output
- Add compile-time warnings for `raw()` usage (via AST transform or CodeNarc rule)
- Ensure JSON views default to proper JSON encoding

**Priority**: **P1** - XSS is OWASP #1. Encode by default is industry standard.
**Effort**: Low (GSP already has codec infrastructure; this is a default-change + audit)

---

### 2.5 Dependency Vulnerability Scanning in Build

**Current state**: CycloneDX SBOM generation exists (`gradleCycloneDxPluginVersion=2.4.1`) but no automated vulnerability scanning in CI.

**Potentially for Grails 8**:
- Add OWASP Dependency-Check or Gradle's built-in dependency verification to CI
- Document known CVE exposure in release notes
- Pin transitive dependencies that have known CVEs
- Consider adopting Gradle's dependency locking for reproducible builds

**Priority**: **P2** - Supply chain security is table stakes for enterprise adoption.
**Effort**: Low

---

### 2.6 GORM Query Safety Audit

**Current state**: GORM's HQL/`@Query` support accepts string interpolation in queries. Dynamic finders are safe by design (parameterized). But `executeQuery()`, `executeUpdate()`, and `@Query` with GString interpolation can produce SQL injection.

**Potentially for Grails 8**:
- Add compile-time warning for GString-interpolated HQL queries (AST transform or type checking extension)
- Document safe query patterns prominently
- Consider deprecating `executeQuery(String)` in favor of parameterized-only API
- Audit all internal GORM query construction for injection vectors

**Priority**: **P2** - SQL injection is OWASP #3. Framework should make the safe path the easy path.
**Effort**: Medium

---

## Tier 3 - Modern Runtime Features

### 3.1 Observability (OpenTelemetry / Micrometer)

**Current state**: Basic Micrometer integration exists through Spring Boot autoconfiguration. No Grails-specific metrics, traces, or spans.

**Potentially for Grails 8**:
- Auto-instrument GORM operations (query timing, cache hits, connection pool stats)
- Auto-instrument controller actions (request timing, status codes, exception types)
- Auto-instrument interceptor chain execution
- Auto-instrument GSP rendering time
- Support OpenTelemetry trace context propagation through GORM operations and async calls
- Expose Grails-specific health indicators (plugin status, artefact counts, GORM session stats)

**Implementation**: Use Micrometer's `ObservationRegistry` (Spring Framework 6.1+/7.0). Add `@Observed` or manual `Observation.start()` at key framework extension points.

**Priority**: **P1** - Observability is common in modern frameworks and increasingly expected by enterprise users.
**Effort**: Medium

---

### 3.2 HTTP Interface Clients

**Current state**: No built-in HTTP client abstraction. Applications use `RestTemplate`, `WebClient`, or third-party libraries directly.

**Spring Framework 7** introduces `@HttpExchange` interface clients - declare an interface with annotated methods, and Spring generates the implementation.

**Potentially for Grails 8**:
- Auto-configure `HttpServiceProxyFactory` for `@HttpExchange` interfaces
- Support `@HttpExchange` interface scanning in `src/main/groovy`
- Add GORM-style convention support (optional): `BookClient` interface in `clients/` directory auto-configured
- Document migration from `RestTemplate` to `@HttpExchange`

**Priority**: **P2** - Nice-to-have for Grails 8.0, but aligns with Grails' convention-based philosophy.
**Effort**: Low (leverage Spring's existing implementation)

---

### 3.3 Structured Logging

**Current state**: Standard SLF4J logging. No structured logging support.

**Potentially for Grails 8**:
- Support structured logging output (JSON format) via configuration
- Auto-add request context (requestId, sessionId, userId) to MDC
- Auto-add GORM context (datasource, tenant) to MDC
- Support Spring Boot 4's structured logging configuration
- Correlation ID propagation across async operations

**Priority**: **P2** - Required for cloud-native deployments with log aggregation.
**Effort**: Low

---

## Tier 4 - Compile-Time Shift

> These items are detailed in `GRAILS8-COMPILE-TIME-OPPORTUNITIES.md`. This section prioritizes
> them in the context of the full Grails 8 roadmap and adjusts priorities based on what's
> realistic for the 8.0.x timeline vs. 8.1+.

### 4.1 Eliminate ExpandoMetaClass from Hot Paths (8.0.x)

**185+ usages across 57 files.** The biggest source of Indy regression, GraalVM incompatibility, and startup overhead.

**8.0.x scope** (most impactful, manageable effort):
1. Replace `CodecMetaClassSupport` EMC methods with Groovy extension modules
2. Replace `TagLibraryMetaUtils.methodMissing` with startup-built dispatch table
3. Batch all GORM `GormEnhancer.enhance()` metaclass modifications to startup
4. Replace `HttpServletRequestExtension.propertyMissing` with extension methods
5. Replace `HttpSessionExtension.propertyMissing` with extension methods

**8.1+ scope** (requires Groovy 5 features, deeper changes):
- Replace GORM dynamic finders with AST-generated static methods
- Replace `HibernateMappingBuilder.methodMissing` DSL with `@DelegatesTo` typed builder
- Replace `NamedCriteriaProxy.methodMissing` with generated methods

**Priority**: **P1** for 8.0.x scope, **P2** for deeper changes.
**Effort**: High overall, but individual replacements are well-scoped

---

### 4.2 URL Mapping Pre-computation (8.0.x)

**Current state**: All URL mappings are evaluated from Groovy closures at startup, then matched via linear iteration + regex per request.

**8.0.x scope**:
1. Pre-compile URL mapping patterns at build time, output `META-INF/grails/url-mappings.json`
2. Replace linear matching with trie-based routing (O(path_length) vs O(num_mappings))
3. Pre-compute reverse routing tables for link generation

**Priority**: **P1** - Direct per-request latency improvement for every Grails app.
**Effort**: High

---

### 4.3 Data Binding Code Generation (8.1+)

Replace reflection-based `BeanWrapper` property binding with AST-generated type-safe binders. 89+ usages of `BeanWrapper`/`BeanWrapperImpl` across 33 files.

**Priority**: **P2** - Important for GraalVM and startup, but large effort.
**Effort**: Very High

---

### 4.4 GrailsNameUtils Pre-computation (8.0.x)

80+ files call `GrailsNameUtils` for convention name resolution at runtime. These are pure string functions called thousands of times.

**8.0.x scope**: Extend artefact AST transforms to generate `GRAILS_LOGICAL_NAME`, `GRAILS_PROPERTY_NAME` constants on each artefact class. Framework reads constants instead of computing.

**Priority**: **P2** - Moderate startup improvement, easy win.
**Effort**: Medium

---

### 4.5 Plugin Loading Optimization (8.0.x)

Generate `META-INF/grails/plugin-registry.properties` at build time with plugin classes and pre-computed dependency order. Eliminates runtime classpath scanning and topological sorting.

**Priority**: **P2** - 5-10% startup improvement.
**Effort**: Medium

---

## Tier 5 - Developer Experience

### 5.1 JLine 3 CLI Modernization

**PR**: [#15367](https://github.com/apache/grails-core/pull/15367) (ready)

Upgrades from EOL JLine 2 to JLine 3, with 100+ new test cases, improved completion, and better terminal handling. JLine 2 dependency retained only for Groovy 4.x groovysh; removable after Groovy 5.

#### PR #15367 Changes

| Category | Change | Files |
|----------|--------|-------|
| **JLine 2 to 3 core migration** | `jline.console.ConsoleReader` to `org.jline.reader.LineReader` / `org.jline.terminal.Terminal` | `GrailsConsole.java` (108 lines changed), `GrailsCli.groovy` |
| **Terminal creation** | Direct `ConsoleReader()` to `TerminalBuilder.builder().build()` + `LineReaderBuilder` | `GrailsConsole.java` |
| **Completer API migration** | All completers: `complete(String, int, List<CharSequence>)` to `complete(LineReader, ParsedLine, List<Candidate>)` | `StringsCompleter`, `RegexCompletor`, `EscapingFileNameCompletor`, `SimpleOrFileNameCompletor`, `ClosureCompleter`, `CommandCompleter`, `SortedAggregateCompleter` (7 files) |
| **Candidate objects** | `CharSequence` candidates replaced with `Candidate` objects (full names for flags, profiles, features) | All completer classes |
| **AggregateCompleter** | Supports multiple completers in GrailsConsole | `SortedAggregateCompleter.java` |
| **History handling** | Attached to LineReader via builder instead of separate ConsoleReader API | `GrailsConsole.java`, `GrailsCli.groovy` |
| **Dumb terminal detection** | `"dumb".equals(terminal.getType())` | `GrailsConsole.java` |
| **Efficient completer updates** | `updateCompleter()` instead of rebuilding LineReader | `GrailsConsole.java` |
| **Auto-complete common prefix** | `CandidateListCompletionHandler` auto-completes common prefix in buffer | `CandidateListCompletionHandler.java` |
| **CTRL+C handling** | Better cancellation via terminal interrupt masking and keypress polling | `GrailsConsole.java` |
| **Dependency versions** | `jline: 3.30.6`, `jansi: 2.4.2`, added `jline2: 2.14.6` for Groovy 4 compat | `dependencies.gradle` |
| **New test coverage** | 8 new spec files, 100+ test cases | `StringsCompleterSpec` (20+), `SortedAggregateCompleterSpec` (10), `ClosureCompleterSpec` (8), `EscapingFileNameCompletorSpec` (10), `SimpleOrFileNameCompletorSpec` (12), `CommandCompleterSpec` (12), `CandidateListCompletionHandlerSpec` (20+), `GrailsConsoleCompleterSpec` (15+) |
| **Enhanced existing tests** | Edge case coverage added | `RegexCompletorSpec` (10+ new cases) |

#### PR #15367 Workarounds

| Workaround | Reason | Removal Path |
|------------|--------|--------------|
| JLine 2 (`jline:jline:2.14.6`) retained as dependency | Groovy 4.x `groovysh` module requires JLine 2 | **Remove after Groovy 5 merge** (PR #15183) - Groovy 5 drops JLine 2. Both coexist safely (different package namespaces: `jline.*` vs `org.jline.*`) |
| TODO comments marking JLine 2 deps | Breadcrumbs for cleanup | Remove when JLine 2 is removed |

#### PR #15367 Open Decisions

| Decision | Context | Options |
|----------|---------|---------|
| **JLine 2 removal timing** | Only needed for Groovy 4.x groovysh. Once Groovy 5 lands, removable. | (a) Remove in same release as Groovy 5, (b) Keep one release for backward compat |
| **Eclipse console support** | `GrailsEclipseConsole` updated minimally. Eclipse/STS Grails support is effectively unmaintained. | (a) Keep minimal support, (b) Deprecate in 8.0, remove in 8.1 |

**Priority**: **P1** - CLI is the first thing developers touch.
**Effort**: Done (PR exists)

---

### 5.2 Improved Error Messages and Diagnostics

**Current state**: Many framework errors produce stack traces that point to internal framework code, not user code. GORM dynamic finder typos (`Book.findByTitel("...")`) fail at runtime with `MissingMethodException`.

**Potentially for Grails 8**:
- Add compile-time validation of dynamic finder property names (AST type checking extension)
- Improve controller action not-found messages (suggest closest match)
- Add URL mapping validation at startup (report conflicts, unreachable mappings)
- Add plugin compatibility checker at startup (report version mismatches)

**Priority**: **P2**
**Effort**: Medium

---

### 5.3 Configuration Documentation and Validation

**PR**: [#15426](https://github.com/apache/grails-core/pull/15426) (hybrid ConfigReportCommand)
**PR**: [#15407](https://github.com/apache/grails-core/pull/15407) (configuration reference docs)

These PRs add ~160 documented configuration properties with descriptions and defaults. Grails 8 should ship with:
- Built-in configuration validation at startup (warn on unknown properties)
- IDE-friendly configuration metadata (`META-INF/spring-configuration-metadata.json`)
- `grails config-report` command for runtime configuration inspection

**Priority**: **P2**
**Effort**: Low (PRs in progress)

---

### 5.4 Continuous Testing Mode

**What Quarkus offers**: Automatic re-run of affected tests when source changes are detected.

**What Grails 8 should add**:
- Leverage Gradle's continuous build (`--continuous`) with smarter test filtering
- Add `grails test-app --watch` that re-runs only affected tests
- Integrate with Gradle's test retry plugin for flaky test mitigation

**Priority**: **P3** - Nice to have, not blocking.
**Effort**: Medium

---

## Tier 6 - Ecosystem and Future-Proofing

### 6.1 GraalVM Native Image Support

**Current state**: Zero support in framework (grails-forge has some reflect-config.json for Micronaut-based forge app only). No `RuntimeHints`, no native-image.properties in any framework module.

**Dependency chain**: This requires Tier 0 (Spring Boot 4), Tier 1.2 (Spring AOT), Tier 1.4 (static compilation), and Tier 4.1 (ExpandoMetaClass elimination). It is NOT achievable in 8.0.x.

**8.1+ roadmap**:
1. Complete Spring AOT integration
2. Generate `RuntimeHints` for all remaining reflection
3. Eliminate or hint all `Class.forName` calls (48+ in production code)
4. Test full Grails app under GraalVM native image
5. Publish native image configuration as part of build artifacts

**Priority**: **P2** for 8.0.x preparation, **P1** for 8.1.
**Effort**: Very High (depends on Tiers 0-4)

---

### 6.2 JPMS Module System Readiness

**Current state**: Zero `module-info.java` files. No automatic module names in manifests.

**Potentially for Grails 8**:
1. Add `Automatic-Module-Name` manifest entries to all published JARs (non-breaking, enables modular consumers)
2. Plan `module-info.java` files for 8.1+ (breaking for split package issues)

**Priority**: **P3** - Modular JDK adoption is slow but accelerating.
**Effort**: Low for automatic module names, High for full JPMS

---

### 6.3 Subproject Dependency Cleanup

**Current state**: Mixed dependency versions across subprojects. `grails-data-neo4j` targets Java 11 with Hibernate 5.5.7. `grails-data-graphql/core` targets Java 8. `grails-forge` uses Micronaut 3.10.4 (upgraded to 4 in [#15365](https://github.com/apache/grails-core/pull/15365)).

**Potentially for Grails 8**:
- Unify Java baseline to 17 across ALL modules (drop Java 8/11 compat in neo4j and graphql)
- Align all Hibernate versions
- Align all Spock versions
- Consider version catalog (`libs.versions.toml`) for centralized dependency management

**Priority**: **P2** - Reduces maintenance burden and dependency conflicts.
**Effort**: Medium

---

### 6.4 Multi-Datasource and Multi-Tenant Robustness

**Active work on 7.0.x**: PRs [#15425](https://github.com/apache/grails-core/pull/15425) (OSIV for all datasources), [#15429](https://github.com/apache/grails-core/pull/15429) (TCK data service connection routing), [#15423](https://github.com/apache/grails-core/pull/15423) (@Query connection routing tests).

These fixes and tests should merge into 8.0.x. Multi-datasource and multi-tenant are enterprise requirements that need to be rock-solid.

**Priority**: **P1** - Enterprise feature that's currently buggy.
**Effort**: Low (work already in progress on 7.0.x, merge forward)

---

## What NOT to Do in Grails 8

| Temptation | Why Not | Instead |
|------------|---------|---------|
| Full GORM dynamic finder compilation | Too large for 8.0.x, risk of breaking backward compat | Batch metaclass mods at startup (1.1), generate in 8.1+ |
| Drop Groovy dynamic features entirely | Breaks Grails' identity and existing apps | Static-by-default, dynamic-by-choice |
| Rewrite GSP from scratch | Massive effort, uncertain payoff | Enforce pre-compilation, improve encoding defaults |
| Add reactive/R2DBC stack | Fragmentary ecosystem, Loom makes it unnecessary | Virtual thread support (1.3) is the modern answer |
| Full microservices framework | Not Grails' niche; that's Micronaut's territory | Focus on what Grails does best - productive full-stack web |
| Drop Hibernate 5.6 support immediately | Many apps can't upgrade overnight | Ship with Hibernate 7 default, keep 5.6-jakarta as opt-in for 8.0.x. Hibernate 6 module removed (stepping stone only). |
| Implement custom DI container | Spring is Grails' foundation, not a liability | Leverage Spring AOT, don't compete with it |
| Chase every Java 25 feature | Java 17 is the baseline; don't fragment | Target 17 baseline, 21 recommended, 25 tested |

---

## Priority Summary Matrix

| # | Item | Tier | Priority | Effort | Release |
|---|------|------|----------|--------|---------|
| 0.1 | Spring Boot 4 + Spring Framework 7 | Gate | CRITICAL | Medium | 8.0.0-M1 |
| 0.2 | Groovy 5 Compatibility | Gate | CRITICAL | High | 8.0.0-M1 |
| 0.3 | Hibernate 7 Support | Gate | CRITICAL | Very High | 8.0.0-M2 |
| 0.4 | Gradle 9 Support | Gate | CRITICAL | Low | 8.0.0-M1 |
| 0.5 | Bean Builder Rework | Gate | CRITICAL | High | 8.0.0-M2 |
| 1.1 | Fix Indy 4x Regression | Perf | P0 | High | 8.0.0-M1 |
| 1.2 | Spring AOT Integration | Perf | P0 | Very High | 8.0.0-RC |
| 1.3 | Virtual Thread Readiness | Perf | P1 | Medium | 8.0.0-M2 |
| 1.4 | Static Compilation Expansion | Perf | P1 | Medium | 8.0.0-M2 |
| 1.5 | Artefact Discovery at Build Time | Perf | P1 | Medium | 8.0.0-RC |
| 2.1 | Default Security Headers | Security | P1 | Low | 8.0.0-M1 |
| 2.2 | CSRF Protection by Default | Security | P1 | Medium | 8.0.0-M2 |
| 2.3 | Secure Data Binding (`secureBindData()`) | Security | P1 | Medium | 8.0.0-M2 |
| 2.4 | Encode-by-Default in GSP | Security | P1 | Low | 8.0.0-M1 |
| 2.5 | Dependency Vulnerability Scanning | Security | P2 | Low | 8.0.0-M1 |
| 2.6 | GORM Query Safety Audit | Security | P2 | Medium | 8.0.0-RC |
| 3.1 | Observability (OTel/Micrometer) | Runtime | P1 | Medium | 8.0.0-RC |
| 3.2 | HTTP Interface Clients | Runtime | P2 | Low | 8.0.x |
| 3.3 | Structured Logging | Runtime | P2 | Low | 8.0.0-M2 |
| 4.1 | Eliminate ExpandoMetaClass Hot Paths | Compile | P1 | High | 8.0.0-RC |
| 4.2 | URL Mapping Pre-computation | Compile | P1 | High | 8.1.x |
| 4.3 | Data Binding Code Generation | Compile | P2 | Very High | 8.1.x |
| 4.4 | GrailsNameUtils Pre-computation | Compile | P2 | Medium | 8.0.x |
| 4.5 | Plugin Loading Optimization | Compile | P2 | Medium | 8.0.x |
| 5.1 | JLine 3 CLI | DX | P1 | Done | 8.0.0-M1 |
| 5.2 | Improved Error Messages | DX | P2 | Medium | 8.0.x |
| 5.3 | Configuration Docs/Validation | DX | P2 | Low | 8.0.0-M2 |
| 5.4 | Continuous Testing Mode | DX | P3 | Medium | 8.1.x |
| 6.1 | GraalVM Native Image | Future | P2 | Very High | 8.1.x |
| 6.2 | JPMS Module System | Future | P3 | Low-High | 8.0.x-8.1.x |
| 6.3 | Subproject Dependency Cleanup | Future | P2 | Medium | 8.0.0-M1 |
| 6.4 | Multi-Datasource Robustness | Future | P1 | Low | 8.0.0-M1 |

---

## Appendix: Competitive Landscape

### What Grails 8 Must Match

| Feature | Spring Boot 4 | Micronaut 4 | Quarkus 3 | Grails 8 Status |
|---------|--------------|-------------|-----------|-----------------|
| AOT compilation | Full Spring AOT | Core architecture | Core architecture | **Zero** - needs 1.2 |
| GraalVM native | Supported + tested | Supported + tested | Supported + tested | **Zero** - needs 6.1 |
| Virtual threads | Auto-configured | Supported | Supported | **Broken** - needs 1.3 |
| Startup time (simple app) | ~1s | ~0.5s | ~0.3s | **~3-5s** - needs Tier 1 |
| Security headers | Spring Security defaults | Manual | Built-in | **None** - needs 2.1 |
| CSRF protection | Spring Security | Manual | Built-in | **None** - needs 2.2 |
| Observability | Micrometer + OTel | Micrometer + OTel | MicroProfile + OTel | **Basic** - needs 3.1 |
| Structured logging | Built-in (Boot 3.4+) | Built-in | Built-in | **None** - needs 3.3 |
| HTTP clients | @HttpExchange | @Client | REST Client | **None** - needs 3.2 |
| Build-time DI | Partial (AOT) | Full | Full | **None** - needs 1.2 |
| Static compilation | N/A (Java) | Default | Default | **62%** - needs 1.4 |

### Grails' Competitive Advantages to Preserve

| Strength | Description | Don't Break It |
|----------|-------------|----------------|
| Convention over configuration | Artefact-based development with zero boilerplate | Keep conventions, just make them compile-time |
| GORM | Most productive ORM in the JVM ecosystem | Fix Indy regression, don't rewrite |
| GSP + Views | Server-side rendering that just works | Improve encoding, don't replace |
| Plugin ecosystem | Rich library of community plugins | Bean Builder rework must be backward-compatible |
| Groovy productivity | Dynamic when you want, static when you need | Static-by-default, dynamic-by-choice |
| Full-stack simplicity | One framework for everything | Don't fragment into microservices pieces |