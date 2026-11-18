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

- Provide repository-specific guidance for the `grails-data-hibernate7` project on the 8.0.x line.
- Guide changes around `GrailsDomainBinder`, `GrailsPropertyBinder`, `IdentityBinder`, `VersionBinder`, collection binders, and related utilities.
- Guide transaction and session lifecycle management across `GrailsHibernateTemplate`, `executeWithNewSession`, and Spring transaction synchronization.
- Guide criteria translation, Hibernate 7 SQM AST construction, alias scoping, and subquery correlation.
- Enforce dialect-specific SQL semantics (Oracle, Db2, PostgreSQL, MySQL) and database engine constraints.
- Keep changes aligned with the testing constraints used by the Hibernate 7 modules in this repository.
- Help with migration work inside this framework module (e.g., porting domain-binding behaviour from H5 to H7, updating binder internals, fixing H7 regressions). Does not cover user-facing application migration guides; those belong in `grails-doc`.

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

`GrailsDomainBinder` is the main entry point for binding Grails domain classes to Hibernate metadata. Changes often ripple through:

- `org.grails.orm.hibernate.cfg`
- `org.grails.orm.hibernate.cfg.domainbinding`
- `org.grails.orm.hibernate.cfg.domainbinding.collectionType`
- `org.grails.orm.hibernate.cfg.domainbinding.secondpass`
- `org.grails.orm.hibernate.cfg.domainbinding.generator`

## Key Classes and Responsibilities

### Main Binding Flow

- `GrailsDomainBinder`: central coordinator for Hibernate 7 mapping contribution.
- `GrailsPropertyBinder`: main coordinator for converting persistent properties into Hibernate `Value` instances.
- `PropertyFromValueCreator`: shared utility for creating Hibernate `Property` instances from a bound `Value`.

### Identifier and Version Binding

- `IdentityBinder`: coordinates identifier binding.
- `SimpleIdBinder`: handles simple identifiers.
- `CompositeIdBinder`: handles composite identifiers.
- `VersionBinder`: binds optimistic locking version properties.
- `NaturalIdentifierBinder`: binds `naturalId` properties.

### Associations and Collections

- `OneToOneBinder`, `ManyToOneBinder`, `ManyToOneValuesBinder`: association binding.
- `CollectionBinder`: collection mapping.
- `CollectionSecondPassBinder`, `ListSecondPassBinder`, `MapSecondPassBinder`: second-pass association and collection binding.
- `CollectionHolder` plus the collection type classes: carry collection metadata through binding.

### Value and Column Binding

- `SimpleValueBinder`: binds simple properties.
- `SimpleValueColumnBinder`: binds columns to simple values.
- `ComponentBinder`, `ComponentPropertyBinder`: embedded/component binding.
- `CompositeUserTypeBinder`: binds a property mapped with a Hibernate `CompositeUserType` as a `Component` (one column per attribute of the user type's `embeddable()` class, in alphabetical attribute order, with `returnedClass()` as the component class); a `BasicValue` cannot carry a composite user type. The attributes and the column of each one come from `CompositeUserTypeColumns` (in `domainbinding.util`), which `ColumnNameForPropertyAndPathFetcher.getColumnNamesForPropertyAndPath` also uses, so a uniqueness group that names such a property spans all of its columns. Its columns are bound with `ColumnBinder.bindColumnWithoutUniqueness`, which takes the type of the attribute and defines the column from its own configuration (the constraints of the property describe the whole value; `PropertyConfig.getScale()` and friends throw on a multi-column property), and its uniqueness with one `CreateKeyForProps.createKeyForColumns` key across the bound `Column` objects themselves (which keeps their quoting), never a unique key per column. The user type is instantiated only through `CompositeUserTypeColumns.userTypeOf`, which is CDI-aware when it has the `MetadataBuildingContext`, so pass the context to `ColumnNameForPropertyAndPathFetcher`, `ColumnBinder` and `CompositeUserTypeColumns` wherever it is available. `GrailsPropertyBinder` checks for a composite user type before the enum branch, since an enum property may map one.
- `EnumTypeBinder`: enum mapping.

### Generators

- `BasicValueCreator`: creates identifier values and generators.
- `GrailsSequenceWrapper`, `GrailsSequenceGeneratorEnum`: generator integration helpers.
- `GrailsIdentityGenerator`, `GrailsIncrementGenerator`, `GrailsNativeGenerator`, `GrailsSequenceStyleGenerator`, `GrailsTableGenerator`: Grails-specific Hibernate 7 generator implementations.

## Current Module Guidance

Keep these module-specific expectations in mind:

- `GrailsPropertyBinder` has already been simplified to a unified binder-dispatch structure. Preserve that consolidation instead of reintroducing scattered property creation or ad hoc branching.
- Property creation and addition should stay centralized through callers using `PropertyFromValueCreator` where applicable.
- Utility classes in `domainbinding.util` should prefer Hibernate-aware GORM types internally, but public signatures may still need base interfaces when Spock mocks require them.
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

1. Identify which binder, creator, generator, fetcher, or second-pass class owns the behavior.
2. Trace whether the change affects only `Value` creation, `Property` creation, or both.
3. Preserve the existing separation between logical mapping decisions and Hibernate object construction.
4. Update or add specs in `grails-data-hibernate7` that exercise the affected behavior through the public Hibernate-backed path.
5. Run the relevant Hibernate 7 module tests, and expand test coverage when binder flow or entity registration behavior changes.

## Pitfalls to Avoid

- Do not treat this module like a simple Grails application layer; it is framework and mapping infrastructure code.
- Do not reintroduce duplicated property-creation logic if a shared binder or creator already owns it.
- Do not rely on unit-only mocking for Hibernate internals when the behavior depends on real metadata binding.
- Do not use nested or inner entity classes in Hibernate 7 specs when top-level classes are required for AST transforms and reliable registration.

## Known Status and Constraints

- The Hibernate 7 binder migration is complete: all main binders, collection types, second-pass binders, generators, and utilities have been migrated.
- `GrailsIncrementGenerator` retains reflection-based workarounds for accessing Hibernate 7 internals; avoid broad refactors in that class unless explicitly targeting that area.

## Source of Truth

This skill is the repository guidance for Hibernate 7 module work. When module conventions change, update this skill directly so agents load the current rules from `.agents/skills/hibernate-developer/SKILL.md`.
