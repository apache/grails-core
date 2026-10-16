---
name: hibernate-developer
description: Guide for working in the grails-data-hibernate7 module, especially Hibernate 7 domain binding, mapping migration, generators, and integration tests. Use this when changing code or tests under grails-data-hibernate7.
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

Activate this skill when working on the Hibernate 7 module, especially for:

- Changes under `grails-data-hibernate7/**`.
- Hibernate 7 mapping and metadata binding work.
- Identifier, version, collection, association, or generator binding changes.
- Hibernate 7 regression fixes and migration follow-up tasks.
- Specs that exercise Hibernate-backed mapping behavior rather than lightweight unit behavior.

## Module Context

This skill is for the Grails framework's Hibernate 7 integration module, not for a Grails application. Prefer guidance from this skill over generic Grails app patterns when working in `grails-data-hibernate7`.

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

### Metamodel

- `GrailsHibernatePersistentEntity`, `HibernatePersistentProperty` and the `Hibernate*Property` types: the GORM mapping model with the Hibernate-specific reads the generator needs (column names, join tables, cascade, lazy, the tenant id).
- `HibernateMappingBuilder`, `Mapping`, `PropertyConfig`, `ColumnConfig`: the mapping DSL and its model.

### Column and Naming Helpers

- `ColumnConfigToColumnBinder`, `StringColumnConstraintsBinder`, `NumericColumnConstraintsBinder`, `IndexBinder` (`domainbinding.column`): apply a column config to a Hibernate `Column`; the generator and the aligner share them.
- `DefaultColumnNameFetcher`, `ColumnNameForPropertyAndPathFetcher`, `TableForManyCalculator`, `NamingStrategyWrapper`, `BackticksRemover`, `CascadeBehaviorFetcher`, `CreateKeyForProps`, `UniqueNameGenerator` (`domainbinding.util`).

### Generators

- `GrailsIdentityGenerator`, `GrailsIncrementGenerator`, `GrailsNativeGenerator`, `GrailsSequenceStyleGenerator`, `GrailsTableGenerator`: Grails-specific Hibernate 7 generator implementations the generated classes name through `@GrailsIdGenerator`; `GrailsSequenceWrapper`, `BasicValueCreator` and `GeneratorCreationContextWrapper` install them.

## Current Module Guidance

Keep these module-specific expectations in mind:

- A mapping option the generator does not state is a gap, not a refusal: native binding must not refuse a mapping the classic binding of Grails 8 accepted (the manual's "Mappings Native Binding Refuses" lists the only exceptions). Add the facet, state it on the generated class, and prove it with a spec that boots a datastore.
- Utility classes in `domainbinding.util` and `domainbinding.column` should prefer Hibernate-aware GORM types internally, but public signatures may still need base interfaces when Spock mocks require them.
- A new `@Entity` fixture in the core test tree is scanned by the differential specs and needs its records in the classic oracle (see the README for how to record them).
- `GrailsIncrementGenerator` still contains reflection-based Hibernate 7 compatibility workarounds; avoid broad refactors unless the change explicitly addresses that area.

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
