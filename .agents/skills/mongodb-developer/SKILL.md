---
name: mongodb-developer
description: Guide for working in the grails-data-mongodb module (GORM for MongoDB) — codec-based document mapping, GeoJSON, embedded documents, TTL indexes, multi-tenancy, and Testcontainers-backed tests. Use this when changing code or tests under grails-data-mongodb.
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Provide repository-specific guidance for the `grails-data-mongodb` project (GORM for MongoDB — a document-database GORM implementation, not a relational one; do not bring Hibernate/binder mental models here).
- Guide changes around `MongoMappingContext`, `BsonPersistentEntityCodec`/`PersistentEntityCodec`, and the codec-registry-based encode/decode pipeline.
- Keep changes aligned with the Testcontainers-backed testing pattern this module actually uses.

## When to Use Me

Activate this skill when working on the MongoDB module, especially for:

- Changes under `grails-data-mongodb/**` (any of its 9 subprojects — see Module Structure).
- Document/collection mapping, codec, or GeoJSON-type work.
- Index (including TTL) creation/reconciliation logic.
- Multi-tenancy or multi-connection MongoDB work.

## Module Context

`grails-data-mongodb` has no `settings.gradle` of its own — it's part of the root aggregate build, same as `grails-data-hibernate7`. All of root `AGENTS.md`'s rules (jakarta, `@GrailsCompileStatic`, CodeNarc/Checkstyle/PMD/SpotBugs, `dependencies.gradle`/BOM) apply here unmodified.

Nine Gradle projects live under this directory, mapped to real Gradle project names (not always `grails-data-mongodb-<dirname>` — check before assuming):

| Directory | Gradle project | Role |
|---|---|---|
| `core/` | `:grails-data-mongodb-core` | The actual GORM-for-MongoDB implementation: `MongoDatastore`, `MongoMappingContext`, `MongoQuery`, GeoJSON types |
| `bson/` | `:grails-data-mongodb-bson` | Low-level, GORM-independent BSON codec machinery (`BsonPersistentEntityCodec`, per-type property codecs, hand-rolled JSON tokenizer) |
| `ext/` | `:grails-data-mongodb-ext` | A single file, Groovy extension methods on the raw MongoDB driver (`distinct`, `watch`/change streams, `deleteMany`, `createAndGetCollection` with driver collection options) |
| `boot-plugin/` | `:grails-data-mongodb-spring-boot` | Spring Boot auto-configuration (`MongoDbGormAutoConfiguration`) |
| `spring-data/` | `:grails-data-mongodb-spring-data` | Lets GORM-for-MongoDB and Spring Data MongoDB share a `MongoClient`/codecs/transaction |
| `grails-plugin/` | `:grails-data-mongodb` (note: not `-grails-plugin` suffixed) | Classic Grails plugin descriptor (`MongodbGrailsPlugin`); also ships the app-facing `grails.test.mongodb.MongoSpec` test base via `testFixtures` |
| `gson-templates/` | `:grails-data-mongodb-gson-templates` | JSON Views (`.gson`) for `ObjectId` and GeoJSON types |
| `embedded/` | `:grails-data-mongodb-embedded` | Starts an embedded MongoDB server before the application context refreshes and publishes its URL into the configured properties; for applications, not for this module's own tests. Two backends: `InMemoryMongoBackend` over `de.bwaldvogel:mongo-java-server` (an `api` dependency, so it ships with the module) and `FlapdoodleMongoBackend` for a real `mongod` (`compileOnly`; the application adds Flapdoodle itself). `EmbeddedMongoInitializer` picks Flapdoodle when it is on the classpath and the in-memory backend otherwise; `embedded.mongodb.backend` (`in-memory` or `flapdoodle`) selects one explicitly |
| `docs/` | `:grails-data-mongodb-docs` | Asciidoc manual + groovydoc aggregation only, no source |

## Key Classes and Responsibilities

There is no single `GrailsDomainBinder`-equivalent class — the responsibility splits across three cooperating layers:

### Mapping (the binder equivalent)

- `MongoMappingContext` (`core/.../mongo/config/MongoMappingContext.java`) — owns the private inner `MongoDocumentMappingFactory extends AbstractGormMappingFactory<MongoCollection, MongoAttribute>`, which is the actual binder: forces the `_id` field name, applies the global `stringIdDefaultStoredAs` default, wires codec-registry-backed custom types, and registers all GeoJSON custom types.
- `MongoCollection` (`core/.../config/MongoCollection.groovy`) — entity-level mapping (collection name, database, `writeConcern`, indices, sort).
- `MongoAttribute` (`core/.../config/MongoAttribute.groovy`) — property-level mapping (`reference` for DBRef vs. plain identifier, `field`, `geoIndex`, `index`, `indexAttributes`).
- `MappingBuilder.document { ... }` (`core/.../grails/mongodb/mapping/MappingBuilder.groovy`) — the programmatic DSL entry point.

### Codec pipeline (the runtime read/write engine)

- `BsonPersistentEntityCodec` (`bson/.../BsonPersistentEntityCodec.groovy`) and its Mongo subclass `PersistentEntityCodec` (`core/.../engine/codecs/PersistentEntityCodec.groovy`) — a static `ENCODERS`/`DECODERS` registry keyed by `PersistentProperty` subtype (`Identity`, `TenantId`, `Simple`, `Embedded`, `EmbeddedCollection`, `Custom`, `Basic`, plus Mongo-specific `OneToOne`/`ManyToOne`/`OneToMany`/`ManyToMany`). `encode`/`decode` walk `entity.persistentProperties` and dispatch per property kind. `PersistentEntityCodec` also implements MongoDB's partial-update path (`encodeUpdate`) via `DirtyCheckable`, producing `$set`/`$unset` documents directly rather than rewriting the whole document.

### Datastore

- `MongoDatastore` (`core/.../mongo/MongoDatastore.java`) — owns index creation/reconciliation against live MongoDB and transaction-capability detection based on cluster topology.

## Confirmed MongoDB-Specific Concepts (implemented — don't assume beyond this list)

- **Embedded documents & embedded collections** — `Embedded`/`EmbeddedCollection` property kinds, with dedicated in-place `$set`/`$unset` update logic distinct from top-level property updates.
- **GeoJSON types** — `grails.mongodb.geo.{Point,LineString,Polygon,MultiPoint,MultiLineString,MultiPolygon,GeometryCollection,Box,Circle,Sphere,Shape}`, each with a custom-type marshaller. Query support: `near`, `nearSphere`, `withinCircle`, `withinBox`, `withinPolygon`, `geoWithin`, `geoIntersects`. Legacy `2d`/`2dsphere` indexes via `MongoAttribute.geoIndex(String)`.
- **TTL indexes** — `indexAttributes: [expireAfterSeconds: N]` alone does not create an index; property-level `index: true` is also required (e.g. `created index: true, indexAttributes: [expireAfterSeconds: 100]`), reconciled in place via `collMod` rather than drop/rebuild (see Pitfalls).
- **DBRef vs. plain identifier** — `MongoAttribute.reference` selects how a `ToOne`/`ToMany` association is stored: `true` writes a `com.mongodb.DBRef`, `false` (the default) writes the associated identifier. It does not embed; embedding is `static embedded = [...]`, a different property kind (see the first bullet). An agent that sets `reference: false` to embed a document will store an id instead.
- **Schemaless/dynamic attributes** — `DynamicAttributes` trait support baked into `BsonPersistentEntityCodec` and `MongoEntity`.
- **Change streams** — `MongoExtensions.watch(...)`, a thin Groovy-extension wrapper over the driver's native `watch()`, not a GORM-level abstraction.
- **Multi-document transactions** — opt-in via `grails.mongodb.transactional`. A detected `STANDALONE` topology disables transactions (falling back to flush-only behavior with a one-time warning). A detected `REPLICA_SET`, `SHARDED`, or `LOAD_BALANCED` cluster enables and latches transactions. An `UNKNOWN` topology, or an inspection that throws, assumes transactions are available without latching, allowing discovery to complete later.
- **Multi-tenancy / multiple named connections** — supported in all three modes: `DATABASE` (`MongoDatastore.getDatastoreForTenantId` routes to the per-connection datastore), `SCHEMA` (the tenant resolver is wrapped in an `AllTenantsResolver`, and the collection lookup honours it) and `DISCRIMINATOR`; plus `grails.mongodb.connections`. The module's `multiTenancy/configuration.adoc` uses `mode: DATABASE` as its example, and `grails-test-examples/mongodb/database-per-tenant` exercises it.

### Not implemented — do not document or rely on these

- **`shard "name"` in the mapping DSL is a no-op.** There is no `shard` method on `Collection`/`MongoCollection`. The call silently falls through to the generic `Entity.methodMissing` catch-all, which just records it as an ad hoc property config — it never issues a MongoDB `shardCollection` command. Verified: zero `shardCollection` calls anywhere in `core/src/main`. If you see `shard "..."` in a mapping block, treat it as dead syntax, not a real feature.
- **Capped collections in the mapping DSL** — there is no option for them on `MongoCollection`. `MongoExtensions.createAndGetCollection(db, name, options)` in `ext/` does map its options onto the driver's `CreateCollectionOptions`, so `[capped: true, sizeInBytes: n]` creates a capped collection on the raw driver (`MongoExtensionsSpec` covers it); a domain class cannot ask for one.

## Testing Rules

The module's tests use both embedded servers and Testcontainers depending on the specification base:

- **`EmbeddedReplicaSetSpec`** (`grails-testing-support-mongodb`) — starts Flapdoodle via `EmbeddedMongoInitializer` as a single-node replica set for specifications requiring multi-document transactions (e.g. `MongoTransactionSpec`, `MongoReadOnlyTransactionSpec`, `MongoTransactionDisabledSpec`, `MongoFlushListenerQuerySpec`, `UnifiedMongoTransactionSpec`). These specs run without Docker or containers.
- **`GrailsDataTckSpec<GrailsDataMongoTckManager>`** is the dominant pattern (111+ specs) — the same shared TCK harness the Hibernate modules use (`grails-datamapping-tck`), parameterized with `GrailsDataMongoTckManager` (`core/src/test/groovy/.../GrailsDataMongoTckManager.groovy`), which starts a real `MongoDBContainer` in `setupSpec()`/stops it in `cleanupSpec()`. Its `destroy()` after each feature deletes the documents of every non-system database but keeps the collections and their indexes, so a later feature in the same fork can inherit an index an earlier one created; a spec that creates an index has to drop it itself. Requires Docker or Podman.
  ```groovy
  class DocumentMappingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
      void setupSpec() {
          manager.registerDomainClasses(CustomMapping)
      }
      void "test custom document mapping"() { ... }
  }
  ```
  Domain classes are top-level classes in the same spec file, registered via `manager.registerDomainClasses(...)` in `setupSpec()` — same convention as `HibernateGormDatastoreSpec`.
- **`AutoStartedMongoSpec`** (`grails-testing-support-mongodb`) — a lighter-weight base used by 44 specs (41 in `core`, roughly a quarter of its specs, and 3 in `boot-plugin`); a Spock global extension auto-injects `@Shared MongoDBContainer dbContainer` and auto-constructs a `@Shared MongoDatastore` field if present. Requires Docker or Podman.
- **`grails.test.mongodb.MongoSpec`** — a separate, *app-facing* base class shipped via `testFixtures` in `grails-plugin/`, for Grails-application-level Mongo integration tests. Don't confuse it with the two internal patterns above — a spec being both a `MongoSpec` and an `AutoStartedMongoSpec` is explicitly forbidden.
- The MongoDB image version comes from `-PmongodbContainerVersion`, which CI always passes; see `gradle/mongodb-forked-test-config.gradle`.
- `core` applies `gradle/mongodb-forked-test-config.gradle` (`maxParallelForks = rootProject.ext.configuredTestParallel`, the forked, parallel layout); every other subproject (`bson`, `ext`, `embedded`, `spring-data`, `boot-plugin`, `grails-plugin`, `gson-templates`) applies `gradle/mongodb-test-config.gradle`, which runs serially (`maxParallelForks = 1`). Check which file a subproject applies before changing its parallelism.
- `-PskipMongodbTests` / `-PonlyMongodbTests` gate these tests at the root. The `Container missing` row in root `AGENTS.md`'s Common Issues table says `-PskipTests`; `-PskipMongodbTests` is the narrower switch that skips only the MongoDB tests, for when Docker isn't available.

## Change Workflow

1. Identify which layer owns the behavior: mapping (`MongoMappingContext`/`MongoCollection`/`MongoAttribute`) vs. codec/runtime (`BsonPersistentEntityCodec`/`PersistentEntityCodec`) vs. datastore-level (`MongoDatastore`, index/transaction handling).
2. Check whether the change affects the general `bson/` codec machinery (used by any BSON-backed consumer) or is genuinely Mongo-specific (`core/`) — don't put Mongo-only logic in `bson/`.
3. Update or add specs on the appropriate base: `GrailsDataTckSpec<GrailsDataMongoTckManager>` for general mapping and CRUD TCK features (registering any new domain classes as top-level classes in the same spec file), `EmbeddedReplicaSetSpec` for multi-document transaction features, or `AutoStartedMongoSpec` for standalone datastore specs.
4. Run the affected subproject's tests. `EmbeddedReplicaSetSpec` specs run locally with Flapdoodle without Docker; TCK and `AutoStartedMongoSpec` tests require Docker/Podman running, with `core` tests running in parallel forks (`-PmaxTestParallel=1` makes them serial).

## Pitfalls to Avoid

- Do not treat `shard "name"` as a functioning DSL keyword — it's a documented no-op (see above). Don't build new features assuming it works, and flag it if you're asked to "fix" sharding — the DSL surface exists but the implementation doesn't.
- Do not change a subproject's test parallelism without reading the test config file it applies. The subprojects on `mongodb-test-config.gradle` run serially but do not share one container: `bson`, `ext` and `grails-plugin` extend plain `Specification` and start nothing; `embedded` and `spring-data` start an embedded server, not a Testcontainers one; only `boot-plugin`'s three `AutoStartedMongoSpec` specs get a container, from `MongoContainerHolder`, one per thread.
- TTL index changes go through `collMod`, not drop/rebuild — a single-field `expireAfterSeconds` change should reconcile in place. MongoDB silently ignores the TTL option on a compound index; don't declare one there.
- The global `stringIds.defaultStoredAs` setting must be read by `MongoMappingContext` **before** `initialize(classes)` runs (`createIdentity` is invoked during entity registration and depends on it) — if you touch mapping-context initialization order, this dependency is easy to break silently.
- Known unaddressed gaps (real `// TODO`s in the encode/decode path, not exhaustive but worth checking before assuming behavior): unprocessed `OneToMany` associations in `encodeUpdate` (`PersistentEntityCodec.groovy`), `Map` handling in the embedded-collection update path, and embedded-collection support in the base `BsonPersistentEntityCodec.encodeUpdate` (only the Mongo subclass handles it).

## Known Status and Constraints

- Multi-document transactions are opt-in. A detected `STANDALONE` topology disables them with a one-time warning. A detected `REPLICA_SET`, `SHARDED`, or `LOAD_BALANCED` cluster enables and caches support. `UNKNOWN` topology, or an inspection that throws, returns enabled without caching so discovery in progress does not disable transactions prematurely.
- A `MongoClient` is closed only by whoever created it, not when it was supplied as an external bean. The tracking is `MongoDatastore.ownsClient()`, which `close()` consults. In `MongoDbGormAutoConfiguration` it is carried by the two nested configurations: `ApplicationClientConfiguration` (`@ConditionalOnBean(MongoClient)`, the application's client is used as is) and `GormClientConfiguration` (`@ConditionalOnMissingBean`, whose `mongo` bean has `destroyMethod = ''` so Spring does not close the datastore's client a second time). Preserve both if you touch shutdown logic.

## Source of Truth

This skill is the repository guidance for `grails-data-mongodb` work. When module conventions change, update this skill directly so agents load current rules from `.agents/skills/mongodb-developer/SKILL.md`.
