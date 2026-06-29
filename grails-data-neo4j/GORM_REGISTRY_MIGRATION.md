# Neo4j → GormRegistry: migration plan

`grails-data-neo4j` is a **separate Gradle build** that resolves GORM via a published
`datastoreVersion`, on an older baseline (Groovy 3.0.25 / Grails 6.0.0 / `javax.*`). To wire it to
the GormRegistry O(M+N) work (core-impl, `8.0.0-SNAPSHOT`, Groovy 4 / Java 21 / Jakarta) it must first
be moved onto that baseline. This is split into two PRs.

## PR1 — baseline migration (this branch: `build/neo4j-groovy4-baseline`)

Goal: the Neo4j separate build compiles and tests against core-impl's GORM, with **no** GormRegistry
behavioural change yet.

1. **`grails-data-neo4j/gradle.properties`**
   - `datastoreVersion` `8.0.4` → `8.0.0-SNAPSHOT` (consume core-impl)
   - `groovyVersion` `3.0.25` → 4.0.x (match core-impl)
   - `grailsVersion` `6.0.0` → 8.0.x; bump `grailsGradlePluginVersion`, `hibernateDatastoreVersion`,
     and Java to 21 as needed
2. **Jakarta migration** (`javax.*` → `jakarta.*`) across the ~8 affected main files:
   - `javax.persistence.{FlushModeType,FetchType,LockModeType,CascadeType}` → `jakarta.persistence.*`
   - `javax.servlet.{ServletException,http.HttpServletRequest,http.HttpServletResponse}` → `jakarta.servlet.*`
   - `javax.annotation.PreDestroy` → `jakarta.annotation.PreDestroy`
3. **Recompile + fix** the Groovy 3→4 / Grails 6→8 / new-GORM-API fallout across the module
   (97 main source files).
4. **Verify locally** (the separate build can't see core-impl's local modules directly):
   - from repo root: `./gradlew publishToMavenLocal -PskipTests` (publishes GORM `8.0.0-SNAPSHOT`)
   - then: `cd grails-data-neo4j && ./gradlew build`

## PR2 — GormRegistry wiring (stacked on PR1)

Goal: route Neo4j through `GormRegistry` instead of the legacy `GormEnhancer` static maps.

1. **Add `Neo4jGormApiFactory`** — mirror `MongoGormApiFactory`: extend `DefaultGormApiFactory`,
   override `createStaticApi` to return
   `new Neo4jGormStaticApi<>(persistentClass, neo4jDatastore, finders, txManager)`.
   Without it `GormRegistry` falls back to the generic `GormStaticApi`, breaking the
   `(Neo4jGormStaticApi) findStaticApi(...)` casts in `Neo4jEntity` (the exact bug fixed for Mongo).
2. **Register it** in `Neo4jDatastore.initialize(...)` — replace the anonymous
   `new GormEnhancer(...) { getStaticApi override }` with
   `GormRegistry.instance.registerApiFactory(Neo4jDatastore, new Neo4jGormApiFactory())`.
3. **Rewrite the entity traits** (`Neo4jEntity`, `Node`, `Relationship`):
   `GormEnhancer.findStaticApi/findDatastore` → `GormRegistry.instance.findStaticApi/apiResolver.findDatastore`.
4. **Verify** as in PR1 step 4.

## Sequencing

Do PR1/PR2 once core-impl (#15780) CI is green, so the migration targets a settled GormRegistry SPI
rather than a moving one. Tracking: this is the Neo4j follow-up referenced from #15780.
