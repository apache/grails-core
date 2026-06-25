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
- Guide changes around `GrailsDomainBinder`, `GrailsPropertyBinder`, `IdentityBinder`, `VersionBinder`, collection binders, and related utilities.
- Keep changes aligned with the testing constraints used by the Hibernate 7 modules in this repository.
- Help with migration work inside this framework module (e.g., porting domain-binding behaviour from H5 to H7, updating binder internals, fixing H7 regressions). Does not cover user-facing application migration guides; those belong in `grails-doc`.

## When to Use Me

Activate this skill when working on the Hibernate 7 module, especially for:

- Changes under `grails-data-hibernate7/**`.
- Hibernate 7 mapping and metadata binding work.
- Identifier, version, collection, association, or generator binding changes.
- Hibernate 7 regression fixes and migration follow-up tasks.
- Specs that exercise Hibernate-backed mapping behavior rather than lightweight unit behavior.

## Module Context

This skill is for the Grails framework's Hibernate 7 integration module, not for a Grails application. Prefer guidance from this skill over generic Grails app patterns when working in `grails-data-hibernate7`.

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
