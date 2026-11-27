---
name: hibernate-developer
description: Guide for working in the grails-data-hibernate modules (Hibernate 7 on 8.0.x and Hibernate 5 on 7.0.x), covering domain binding, session and transaction lifecycle management across GrailsHibernateTemplate and executeWithNewSession, criteria and SQM subquery correlation, patch-line API minimalism, dialect semantics, and integration testing. Use this when changing code or tests under grails-data-hibernate*.
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0. 
-->

## What I Do

- Provide repository-specific guidance for the `grails-data-hibernate7` project.
- Guide changes around native domain binding: `GrailsDomainGenerator`, `GeneratedDomainClassBinder`, `GormMappingContributor`, the metamodel under `domainbinding.hibernate`, the column helpers and the identifier generators.
- Keep changes aligned with the testing constraints used by the Hibernate 7 modules in this repository.
- Help with migration work inside this framework module (e.g., closing a gap of native binding against what the classic binding of Grails 8 did, updating the metamodel, fixing H7 regressions). Does not cover user-facing application migration guides; those belong in `grails-doc`.

## When to Use Me

Activate this skill when working on the Hibernate modules, especially for:

- Changes under `grails-data-hibernate7/**` or `grails-data-hibernate5/**`.
- Session and transaction lifecycle management across `GrailsHibernateTemplate`, `executeWithNewSession`, and `OuterSessionLifecycleStrategy`.
- Hibernate mapping and metadata binding work.
- Identifier, version, collection, association, or generator binding changes.
- Criteria, DetachedCriteria, SQM AST, alias scoping, and subquery correlation.
- Hibernate regression fixes and migration follow-up tasks.
- Specs that exercise Hibernate-backed mapping behavior or transaction lifecycle rather than lightweight unit behavior.

## Module Context

This skill is for the Grails framework's Hibernate 7 integration module on the 8.0.x line (where 8.1.x is the Spring Boot 4.1 line), not for a Grails application. Prefer guidance from this skill over generic Grails app patterns when working in `grails-data-hibernate7`.

Native domain binding is the only binding: `GrailsDomainGenerator` generates a JPA-annotated class for every GORM entity from the mapping model, Hibernate's own annotation binder binds those classes, and `GeneratedDomainClassBinder` aligns the bound model with what the mapping asks for and points the bound entities at the real domain classes. The classic binder of Grails 8, which built Hibernate's boot model by hand, was removed in 9.0.x; its behaviour is recorded in `core/src/test/resources/classic-oracle/` and the two differential specs compare native binding with it (see `grails-data-hibernate7/README.md`). Changes often ripple through:

- `org.grails.orm.hibernate.cfg` (the mapping DSL and its model)
- `org.grails.orm.hibernate.cfg.domainbinding.hibernate` (the metamodel the generator reads)
- `org.grails.orm.hibernate.cfg.domainbinding.jpa` (the generator, the facets it decides, the aligner)
- `org.grails.orm.hibernate.cfg.domainbinding.column` and `.util` (column and naming helpers)
- `org.grails.orm.hibernate.cfg.domainbinding.generator` (identifier generators)

## Key Classes and Responsibilities

### Binding Flow

- `HibernateMappingContextConfiguration`: builds the session factory; registers `GormMappingContributor` as the first `AdditionalMappingContributor` and `GeneratedDomainClassBinder` as a `SessionFactoryBuilderFactory`.
- `GormMappingContributor`: hands the entities of a data source to Hibernate; resolves the naming strategy (`NamingStrategyWrapper`) against the JDBC environment.
- `GeneratedDomainClassBinder`: generates the classes, binds them with Hibernate's annotation binder, installs the GORM identifier generators and the tenant filter, aligns discriminators, unique keys, join columns and collection tables, then switches the class loader service to the real classes.
- `GrailsDomainGenerator`: decides the facets (`*Facets` records) of every entity and property and emits the annotated class with Byte Buddy. A mapping it cannot describe is refused by name with an `UnsupportedOperationException` that says what to change.

### Native Domain Binding (`domainbinding.jpa`): where to start

Read the package in the order the binding runs:

- `HibernateMappingContextConfiguration` creates a `GeneratedDomainClassBinder` and the `GormMappingContributor` that hands the entities to it.
- `GeneratedDomainClassBinder.contribute`: the entry point; has `GrailsDomainGenerator.generateAll` generate an annotated class per entity and gives them to Hibernate's annotation binder. `getSessionFactoryBuilder` runs once the metadata is complete and points the bound entities at the real classes.
- `GrailsDomainGenerator`: every mapping decision. Reads a `GrailsHibernatePersistentEntity` into the records of `domainbinding.jpa.facets` (what to state) and writes them as annotations with ByteBuddy. `supports` / `unsupportedReason` name what it refuses before Hibernate boots.
- `GeneratedDomainClassLoader` resolves domain class names to the generated classes while Hibernate binds; `GeneratedDomainClassLoaderService` switches those names to the real classes afterwards.
- `GrailsIdGenerator`: marker carrying the GORM id generator the binder installs on a generated identifier.
- Tests: `grails.gorm.tests.generated.*`, then the two differential specs in the `jpa` test package against the recorded classic oracle in `src/test/resources/classic-oracle` (see the README for recording).

### Metamodel

- `GrailsHibernatePersistentEntity`, `HibernatePersistentProperty` and the `Hibernate*Property` types: the GORM mapping model with the Hibernate-specific reads the generator needs (column names, join tables, cascade, lazy, the tenant id).
- `HibernateMappingBuilder`, `Mapping`, `PropertyConfig`, `ColumnConfig`: the mapping DSL and its model.

### Column and Naming Helpers

- `ColumnConfigToColumnBinder`, `StringColumnConstraintsBinder`, `NumericColumnConstraintsBinder`, `IndexBinder` (`domainbinding.column`): apply a column config to a Hibernate `Column`; the generator and the aligner share them.
- `DefaultColumnNameFetcher`, `ColumnNameForPropertyAndPathFetcher`, `TableForManyCalculator`, `NamingStrategyWrapper`, `BackticksRemover`, `CascadeBehaviorFetcher`, `CreateKeyForProps`, `UniqueNameGenerator` (`domainbinding.util`).
- `GrailsDomainGenerator.compositeUserTypeFacets` / `defineCompositeUserTypeField`: a property mapped with a Hibernate `CompositeUserType` becomes an `@Embedded` field of the value class with `@CompositeType` and one `@AttributeOverride` per attribute of the user type's `embeddable()` class (record components, else fields and readable properties, alphabetical; configured columns by position, else `<property>_<attribute>`), the schema Grails 8's classic binder created for it. The user type is resolved by `CompositeUserTypeColumns.userTypeOf` (a managed bean of the configured container, else its default constructor); the attribute columns take their own configuration and, for a numeric attribute, the default precision of the dialect, never the constraints of the property; a unique property, or a group that names it, is one key over all of its columns (`CreateKeyForProps.createKeyForColumns`).
- `GeneratedDomainClassBinder.shareJoinTables` (`SharedJoinTableUniqueKeyBinder`, `SharedJoinTableUniqueness`, `UniqueIndexNulls`): Hibernate's annotation binder knows nothing of a join table that several collections of one owner write, so once the metadata is complete each such collection gets a unique key of its own in the shape the database needs, nullable key and index columns, and no primary key those columns would fill with nulls.

### Generators

- `GrailsIdentityGenerator`, `GrailsIncrementGenerator`, `GrailsNativeGenerator`, `GrailsSequenceStyleGenerator`, `GrailsTableGenerator`: Grails-specific Hibernate 7 generator implementations the generated classes name through `@GrailsIdGenerator`; `GrailsSequenceWrapper`, `BasicValueCreator` and `GeneratorCreationContextWrapper` install them.

## Current Module Guidance

Keep these module-specific expectations in mind:

- A mapping option the generator does not state is a gap, not a refusal: native binding must not refuse a mapping the classic binding of Grails 8 accepted (the manual's "Mappings Native Binding Refuses" lists the only exceptions). Add the facet, state it on the generated class, and prove it with a spec that boots a datastore.
- Utility classes in `domainbinding.util` and `domainbinding.column` should prefer Hibernate-aware GORM types internally, but public signatures may still need base interfaces when Spock mocks require them.
- A new `@Entity` fixture in the core test tree is scanned by the differential specs and needs its records in the classic oracle (see the README for how to record them).
- `GrailsIncrementGenerator` still contains reflection-based Hibernate 7 compatibility workarounds; avoid broad refactors unless the change explicitly addresses that area.

## Transaction and Session Lifecycle Management

Framework code touching `GrailsHibernateTemplate`, `executeWithNewSession`, or session binding operates at the collision point of Spring's transaction synchronization, Hibernate's logical connection lifecycle, and the JDBC connection pool. Follow these non-negotiable rules:

- **Never disconnect active write-bearing transactions**: Disconnecting an outer session in `beforeCompletion` drops uncommitted entity dirty checks and pending flushes. The outer transaction reports commit success, but rows are silently lost. Only disconnect an outer session if it is an idle or read-only `PROPAGATION_SUPPORTS` context that owns no active transaction.
- **Prevent pool starvation on non-lazy data sources**: Calling connection probes (such as `con.getAutoCommit()` or `con.getMetaData()`) on a datasource that is *not* wrapped in `LazyConnectionDataSourceProxy` immediately checks out a physical connection from the pool. If the outer session is idle and the pool is small, this starves the pool and deadlocks inner sessions.
- **Save and restore connection holders and synchronizations symmetrically**: In `executeWithNewSession`, capture the previous `ConnectionHolder` and Spring `TransactionSynchronization` list before rebinding, and strictly restore them in a `finally` block.
- **Coordinate with `SESSION_SYNCHRONIZATION_ORDER`**: When registering synchronizations with Spring's `TransactionSynchronizationManager`, ensure the logical connection disconnects before Spring's `ConnectionSynchronization` closes the physical connection, without interfering with active flushes.
- **Suspend and restore closed bound holders**: When a `SessionHolder` is bound to the thread in Spring's `TransactionSynchronizationManager`, it must be suspended and restored across `withNewSession` even if its underlying session is closed, preventing `IllegalStateException: Already value bound to thread`.
- **Translate post-callback flush exceptions**: Call `flushIfNecessary` inside the exception-translation block (`executeInSession`) so Hibernate flush exceptions (e.g. database constraint overruns) are converted to Spring's `DataAccessException` hierarchy (`DataIntegrityViolationException`) rather than escaping unconverted.
- **Strict patch-line API minimalism**: On maintenance and patch branches (`7.0.x` or `8.0.x`), never introduce new public or protected methods, classes, or extension points. Never introduce fields or internal state that are assigned but never read. Keep bugfix diffs strictly minimal.
- **Avoid test-driven production pollution**: Do not add multi-argument constructor or method overloads to production classes solely to facilitate unit-test mocks; test behavior through real entity execution or pass necessary runtime flags directly.
- **Authentic exception testing**: Exercise real database/ORM constraint overruns (e.g. column length violation) in tests rather than throwing mock `HibernateException` instances directly, verifying real exception conversion paths.

## Criteria, SQM AST, and Subquery Correlation

Hibernate 7 compiles Criteria into an internal Semantic Query Model (SQM) AST. When working with DetachedCriteria, subqueries, and criteria builders:

- **Lexical alias scoping**: Materialize nested aliases strictly on the association scope that declares them. Never fall back to implicit parent aliases or materialize them onto the query root.
- **Sibling association block isolation**: Sibling association blocks querying the same association name must maintain independent joins rather than collapsing into a shared join that leaks predicates between blocks.
- **Subquery correlation over inherited joins**: When correlating a subquery to an outer query through nested or inherited joins, ensure the outer join path remains accessible to the subquery predicates without dropping the comparison or creating redundant inner joins.

## Dialect Matrix and Database Engine Differences

Do not assume H2 or default SQL semantics apply across all supported database engines:

- **Unique index null semantics**:
  - **Single property `unique: true, nullable: true`**: A `null` value is always exempt from uniqueness checks across all datastores and in-memory test environments. Never execute a uniqueness query when the target value is `null`.
  - **Oracle B-Tree index**: Oracle omits index entries where *every* column of the unique index is `null`. Multiple all-null rows are accepted by the database. If even one column in a composite unique key is non-null, Oracle indexes it and enforces uniqueness.
  - **IBM Db2**: In Hibernate 7, `DB2Dialect` appends `exclude null keys` for LUW, while `DB2iDialect` and `DB2zDialect` render `create unique where not null index`. Do not force GORM `value` comparisons on all-null rows that the database dialect omits.
  - **NoSQL / Neo4j**: A null unique association represents the non-existence of a relationship. Never match null unique associations against nodes that have existing relationships.
- **Shared join tables**:
  - Sets on a dialect that compares nulls, Oracle and Db2 LUW, share one unique key across the associations. A separate key per association rejects two valid rows that are null in one association's columns and equal in the element. Dialects that treat nulls as distinct keep a separate key per association. Lists and maps stay indexed, and bags stay without a key.
  - Calling `clear()` or bulk-deleting one association on a shared join table with separate key columns must leave the rows of the other association intact.

## Mandatory Invariant Verification Protocol

Before submitting changes in Hibernate, transaction, or GORM layers, you must evaluate and document the **Invariant Matrix**:

1. **Transaction Matrix**:
   - `PROPAGATION_REQUIRED`: uncommitted writes flushed on commit, no premature disconnect.
   - `PROPAGATION_REQUIRES_NEW`: inner transaction isolated from outer transaction.
   - `PROPAGATION_SUPPORTS` (read-only): outer session remains usable after inner block without connection leaks.
   - Direct `DataSource` vs `LazyConnectionDataSourceProxy`: verify no eager physical connection checkout when session is idle.
2. **Adversarial Audit Questions**:
   - *Does this disconnect an active transaction that has uncommitted dirty entities?*
   - *Does this allocate a pooled connection if the session never executes a query?*
   - *What does the physical database index do when composite keys are all-null vs partially-null, and do sets on shared join tables use one combined key or a key per association?*
   - *What happens when two sibling closures in a criteria query join the same property name?*

## Testing Rules

When touching `grails-data-hibernate7`, test through real Hibernate wiring rather than assuming mocks are enough.

- Use `HibernateGormDatastoreSpec` for Hibernate 7 integration and domain-binding specifications.
- Prefer `manager.registerDomainClasses(...)` in `setupSpec()` to register entities for specs.
- Define test entities as top-level classes in the same Groovy spec file.
- Ensure test domain class names are globally unique within the package. The test suite uses `maxParallelForks > 1`, so multiple specs can run concurrently in the same JVM fork. `HibernateDatastore` caches mapping metadata by entity class name, so two specs registering a domain class with the same simple name in the same package can overwrite each other's mappings and cause flaky failures.
- Prefer real entities over heavy mocking for binder logic.

## Change Workflow

1. Identify which facet of `GrailsDomainGenerator`, which alignment of `GeneratedDomainClassBinder`, or which helper owns the behavior.
2. Trace whether the change affects what the generated class states, what the aligner changes on the bound model, or both.
3. Preserve the existing separation between the mapping decisions (facets) and the emission of the annotated class.
4. Update or add specs in `grails-data-hibernate7` that exercise the affected behavior through the public Hibernate-backed path.
5. Run the relevant Hibernate 7 module tests, and expand test coverage when binder flow or entity registration behavior changes.

## Pitfalls to Avoid

- Do not treat this module like a simple Grails application layer; it is framework and mapping infrastructure code.
- Do not reintroduce duplicated property-creation logic if a shared binder or creator already owns it.
- Do not rely on unit-only mocking for Hibernate internals when the behavior depends on real metadata binding.
- Do not use nested or inner entity classes in Hibernate 7 specs when top-level classes are required for AST transforms and reliable registration.

## Known Status and Constraints

- Native domain binding is the only binding since 9.0.x; the generator describes every mapping the classic binding of Grails 8 could boot.
- `GrailsIncrementGenerator` retains reflection-based workarounds for accessing Hibernate 7 internals; avoid broad refactors in that class unless explicitly targeting that area.

## Source of Truth

This skill is the repository guidance for Hibernate 7 module work. When module conventions change, update this skill directly so agents load the current rules from `.agents/skills/hibernate-developer/SKILL.md`.
