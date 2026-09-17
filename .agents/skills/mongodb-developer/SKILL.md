---
name: mongodb-developer
description: Guide for working in the grails-data-mongodb module (GORM for MongoDB) — codec-based document mapping, GeoJSON, embedded documents, TTL indexes, multi-tenancy, and Testcontainers-backed tests. Use this when changing code or tests under grails-data-mongodb.
license: Apache-2.0
paths: grails-data-mongodb/**
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

- Changes under `grails-data-mongodb/**` (any of its 8 subprojects — see Module Structure).
- Document/collection mapping, codec, or GeoJSON-type work.
- Index (including TTL) creation/reconciliation logic.
- Multi-tenancy or multi-connection MongoDB work.

## Module Context

`grails-data-mongodb` has no `settings.gradle` of its own — it's part of the root aggregate build, same as `grails-data-hibernate7`. All of root `AGENTS.md`'s rules (jakarta, `@GrailsCompileStatic`, CodeNarc/Checkstyle/PMD/SpotBugs, `dependencies.gradle`/BOM) apply here unmodified.

Eight Gradle projects live under this directory, mapped to real Gradle project names (not always `grails-data-mongodb-<dirname>` — check before assuming):

| Directory | Gradle project | Role |
|---|---|---|
| `core/` | `:grails-data-mongodb-core` | The actual GORM-for-MongoDB implementation: `MongoDatastore`, `MongoMappingContext`, `MongoQuery`, GeoJSON types |
| `bson/` | `:grails-data-mongodb-bson` | Low-level, GORM-independent BSON codec machinery (`BsonPersistentEntityCodec`, per-type property codecs, hand-rolled JSON tokenizer) |
| `ext/` | `:grails-data-mongodb-ext` | A single file, Groovy extension methods on the raw MongoDB driver (`distinct`, `watch`/change streams, `deleteMany`) |
| `boot-plugin/` | `:grails-data-mongodb-spring-boot` | Spring Boot auto-configuration (`MongoDbGormAutoConfiguration`) |
| `spring-data/` | `:grails-data-mongodb-spring-data` | Lets GORM-for-MongoDB and Spring Data MongoDB share a `MongoClient`/codecs/transaction |
| `grails-plugin/` | `:grails-data-mongodb` (note: not `-grails-plugin` suffixed) | Classic Grails plugin descriptor (`MongodbGrailsPlugin`); also ships the app-facing `grails.test.mongodb.MongoSpec` test base via `testFixtures` |
| `gson-templates/` | `:grails-data-mongodb-gson-templates` | JSON Views (`.gson`) for `ObjectId` and GeoJSON types |
| `docs/` | `:grails-data-mongodb-docs` | Asciidoc manual + groovydoc aggregation only, no source |

## Key Classes and Responsibilities

There is no single `GrailsDomainBinder`-equivalent class — the responsibility splits across three cooperating layers:

### Mapping (the binder equivalent)

- `MongoMappingContext` (`core/.../mongo/config/MongoMappingContext.java`) — owns the private inner `MongoDocumentMappingFactory extends AbstractGormMappingFactory<MongoCollection, MongoAttribute>`, which is the actual binder: forces the `_id` field name, applies the global `stringIdDefaultStoredAs` default, wires codec-registry-backed custom types, and registers all GeoJSON custom types.
- `MongoCollection` (`core/.../config/MongoCollection.groovy`) — entity-level mapping (collection name, database, `writeConcern`, indices, sort).
- `MongoAttribute` (`core/.../config/MongoAttribute.groovy`) — property-level mapping (`reference` for DBRef vs. embed, `field`, `geoIndex`, `index`, `indexAttributes`).
- `MappingBuilder.document { ... }` (`core/.../grails/mongodb/mapping/MappingBuilder.groovy`) — the programmatic DSL entry point.

### Codec pipeline (the runtime read/write engine)

- `BsonPersistentEntityCodec` (`bson/.../BsonPersistentEntityCodec.groovy`) and its Mongo subclass `PersistentEntityCodec` (`core/.../engine/codecs/PersistentEntityCodec.groovy`) — a static `ENCODERS`/`DECODERS` registry keyed by `PersistentProperty` subtype (`Identity`, `TenantId`, `Simple`, `Embedded`, `EmbeddedCollection`, `Custom`, `Basic`, plus Mongo-specific `OneToOne`/`ManyToOne`/`OneToMany`/`ManyToMany`). `encode`/`decode` walk `entity.persistentProperties` and dispatch per property kind. `PersistentEntityCodec` also implements MongoDB's partial-update path (`encodeUpdate`) via `DirtyCheckable`, producing `$set`/`$unset` documents directly rather than rewriting the whole document.

### Datastore

- `MongoDatastore` (`core/.../mongo/MongoDatastore.java`) — owns index creation/reconciliation against live MongoDB and transaction-capability detection based on cluster topology.

## Confirmed MongoDB-Specific Concepts (implemented — don't assume beyond this list)

- **Embedded documents & embedded collections** — `Embedded`/`EmbeddedCollection` property kinds, with dedicated in-place `$set`/`$unset` update logic distinct from top-level property updates.
- **GeoJSON types** — `grails.mongodb.geo.{Point,LineString,Polygon,MultiPoint,MultiLineString,MultiPolygon,GeometryCollection,Box,Circle,Sphere,Shape}`, each with a custom-type marshaller. Query support: `near`, `nearSphere`, `withinCircle`, `withinBox`, `withinPolygon`, `geoWithin`, `geoIntersects`. Legacy `2d`/`2dsphere` indexes via `MongoAttribute.geoIndex(String)`.
- **TTL indexes** — property-level `indexAttributes: [expireAfterSeconds: N]` materializes a MongoDB TTL index, reconciled in place via `collMod` rather than drop/rebuild (see Pitfalls).
- **DBRef vs. embedding** — `MongoAttribute.reference` toggles whether a `ToOne`/`ToMany` association is inline-embedded or a `com.mongodb.DBRef`.
- **Schemaless/dynamic attributes** — `DynamicAttributes` trait support baked into `BsonPersistentEntityCodec` and `MongoEntity`.
- **Change streams** — `MongoExtensions.watch(...)`, a thin Groovy-extension wrapper over the driver's native `watch()`, not a GORM-level abstraction.
- **Multi-document transactions** — opt-in via `grails.mongodb.transactional`, gated on cluster topology (`REPLICA_SET`/`SHARDED`/`LOAD_BALANCED` supported; `STANDALONE` falls back with a one-time warning).
- **Multi-tenancy / multiple named connections** — supported (discriminator-based multi-tenancy, `grails.mongodb.connections`).

### Not implemented — do not document or rely on these

- **`shard "name"` in the mapping DSL is a no-op.** There is no `shard` method on `Collection`/`MongoCollection`. The call silently falls through to the generic `Entity.methodMissing` catch-all, which just records it as an ad hoc property config — it never issues a MongoDB `shardCollection` command. Verified: zero `shardCollection` calls anywhere in `core/src/main`. If you see `shard "..."` in a mapping block, treat it as dead syntax, not a real feature.
- **Capped collections** — not implemented anywhere in this module.

## Testing Rules

Real MongoDB via Testcontainers is used — there is no embedded/in-memory Mongo fallback anywhere in this module. Docker or Podman must be running on the host.

- **`GrailsDataTckSpec<GrailsDataMongoTckManager>`** is the dominant pattern (111+ specs) — the same shared TCK harness the Hibernate modules use (`grails-datamapping-tck`), parameterized with `GrailsDataMongoTckManager` (`core/src/test/groovy/.../GrailsDataMongoTckManager.groovy`), which starts a real `MongoDBContainer` in `setupSpec()`/stops it in `cleanupSpec()`, and drops all non-system databases after each test.
  ```groovy
  class DocumentMappingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
      void setupSpec() {
          manager.registerDomainClasses(CustomMapping)
      }
      void "test custom document mapping"() { ... }
  }
  ```
  Domain classes are top-level classes in the same spec file, registered via `manager.registerDomainClasses(...)` in `setupSpec()` — same convention as `HibernateGormDatastoreSpec`.
- **`AutoStartedMongoSpec`** (`grails-testing-support-mongodb`) — a lighter-weight base used by a handful of specs; a Spock global extension auto-injects `@Shared MongoDBContainer dbContainer` and auto-constructs a `@Shared MongoDatastore` field if present.
- **`grails.test.mongodb.MongoSpec`** — a separate, *app-facing* base class shipped via `testFixtures` in `grails-plugin/`, for Grails-application-level Mongo integration tests. Don't confuse it with the two internal patterns above — a spec being both a `MongoSpec` and an `AutoStartedMongoSpec` is explicitly forbidden.
- Default Testcontainers image: `mongo:7.0.19` (overridable via `-DmongodbContainerVersion=...`).
- The `core` module's tests run with **`maxParallelForks = 1`** (serial), via `gradle/mongodb-forked-test-config.gradle` — a deliberate constraint for Testcontainers-backed integration tests. Other subprojects (`bson`, `spring-data`, `boot-plugin`, `grails-plugin`, `gson-templates`) use the normal parallel `mongodb-test-config.gradle`. Don't "fix" `core`'s serial execution without understanding why it's there.
- `-PskipMongodbTests` / `-PonlyMongodbTests` gate these tests at the root, matching the `Container missing` row in root `AGENTS.md`'s Common Issues table — use `-PskipMongodbTests` when Docker isn't available.

## Change Workflow

1. Identify which layer owns the behavior: mapping (`MongoMappingContext`/`MongoCollection`/`MongoAttribute`) vs. codec/runtime (`BsonPersistentEntityCodec`/`PersistentEntityCodec`) vs. datastore-level (`MongoDatastore`, index/transaction handling).
2. Check whether the change affects the general `bson/` codec machinery (used by any BSON-backed consumer) or is genuinely Mongo-specific (`core/`) — don't put Mongo-only logic in `bson/`.
3. Update or add specs via `GrailsDataTckSpec<GrailsDataMongoTckManager>`, registering any new domain classes as top-level classes in the same spec file.
4. Run the affected subproject's tests with Docker/Podman running; expect `core` tests to run serially.

## Pitfalls to Avoid

- Do not treat `shard "name"` as a functioning DSL keyword — it's a documented no-op (see above). Don't build new features assuming it works, and flag it if you're asked to "fix" sharding — the DSL surface exists but the implementation doesn't.
- Do not casually raise `core`'s `maxParallelForks = 1` — it's deliberate for Testcontainers stability, not an oversight.
- TTL index changes go through `collMod`, not drop/rebuild — a single-field `expireAfterSeconds` change should reconcile in place. MongoDB silently ignores the TTL option on a compound index; don't declare one there.
- The global `stringIds.defaultStoredAs` setting must be read by `MongoMappingContext` **before** `initialize(classes)` runs (`createIdentity` is invoked during entity registration and depends on it) — if you touch mapping-context initialization order, this dependency is easy to break silently.
- Known unaddressed gaps (real `// TODO`s in the encode/decode path, not exhaustive but worth checking before assuming behavior): unprocessed `OneToMany` associations in `encodeUpdate` (`PersistentEntityCodec.groovy`), `Map` handling in the embedded-collection update path, and embedded-collection support in the base `BsonPersistentEntityCodec.encodeUpdate` (only the Mongo subclass handles it).

## Known Status and Constraints

- Multi-document transactions require positive cluster-topology detection (`REPLICA_SET`/`SHARDED`/`LOAD_BALANCED`); a `STANDALONE` cluster falls back to legacy client-side flush behavior with a one-time warning, not a hard failure.
- `MongoDbGormAutoConfiguration` only closes a `MongoClient` it created itself, not one supplied as an external bean — preserve that ownership tracking if you touch auto-configuration shutdown logic.

## Source of Truth

This skill is the repository guidance for `grails-data-mongodb` work. When module conventions change, update this skill directly so agents load current rules from `.agents/skills/mongodb-developer/SKILL.md`.
