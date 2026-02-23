# `@CompileStatic` Eligibility Audit — `grails-data-hibernate7`

All `.groovy` files under `src/main` across all four submodules (`core`, `boot-plugin`, `grails-plugin`, `dbmigration`).

Legend:
- ✅ **Done** — `@CompileStatic` at class level, no workarounds needed
- ⚠️ **Partial** — class-level `@CompileStatic` present but some methods still carry `@CompileDynamic`
- ❌ **Not Eligible** — dynamic dispatch is inherent; cannot be made static

---

## `core` submodule

### Config / Model classes

| File | Status | Notes |
|------|--------|-------|
| `cfg/CacheConfig.groovy` | ✅ Done | |
| `cfg/ColumnConfig.groovy` | ✅ Done | |
| `cfg/CompositeIdentity.groovy` | ✅ Done | |
| `cfg/DiscriminatorConfig.groovy` | ✅ Done | |
| `cfg/Identity.groovy` | ✅ Done | |
| `cfg/InstanceProxy.groovy` | ✅ Done | |
| `cfg/JoinTable.groovy` | ✅ Done | |
| `cfg/NaturalId.groovy` | ✅ Done | |
| `cfg/PropertyConfig.groovy` | ✅ Done | |
| `cfg/PropertyDefinitionDelegate.groovy` | ✅ Done | |
| `cfg/SortConfig.groovy` | ✅ Done | |
| `cfg/Table.groovy` | ✅ Done | |
| `cfg/Mapping.groovy` | ⚠️ Partial | `methodMissing` intentionally `@CompileDynamic` — DSL property dispatch hook. No change needed. |

### DSL / Mapping builder

| File | Status | Notes |
|------|--------|-------|
| `cfg/domainbinding/hibernate/HibernateMappingBuilder.groovy` | ✅ Done | Full `@CompileStatic` rewrite; `methodMissing` is `def` (required Groovy hook) |
| `cfg/domainbinding/hibernate/HibernateMappingFactory.groovy` | ✅ Done | |
| `cfg/domainbinding/hibernate/GrailsJpaMappingConfigurationStrategy.groovy` | ✅ Done | |
| `cfg/domainbinding/generator/GrailsSequenceGeneratorEnum.groovy` | ✅ Done | `@CompileStatic` added; spec rewritten to extend `HibernateGormDatastoreSpec` + Postgres Testcontainer (replaces `GroovyMock(global:true)`) |

### GORM APIs

| File | Status | Notes |
|------|--------|-------|
| `HibernateGormEnhancer.groovy` | ✅ Done | |
| `HibernateGormValidationApi.groovy` | ✅ Done | |
| `AbstractHibernateGormValidationApi.groovy` | ✅ Done | |
| `HibernateGormInstanceApi.groovy` | ✅ Done | Stale `@CompileDynamic` removed from `isDirty`, `findDirty`, `getDirtyPropertyNames`; typed with `EntityEntry`, `NonIdentifierAttribute[]`, explicit for-loops |
| `HibernateGormStaticApi.groovy` | ⚠️ Partial | `findWithSql`/`findAllWithSql` fixed. `getAllInternal` still has untyped locals — see detail below |

### Infrastructure

| File | Status | Notes |
|------|--------|-------|
| `GrailsHibernateTransactionManager.groovy` | ✅ Done | |
| `MetadataIntegrator.groovy` | ✅ Done | |
| `HibernateEntity.groovy` | ✅ Done | |
| `mapping/MappingBuilder.groovy` | ✅ Done | |
| `compiler/HibernateEntityTransformation.groovy` | ✅ Done | |
| `connections/HibernateConnectionSourceSettings.groovy` | ✅ Done | |
| `connections/HibernateConnectionSourceSettingsBuilder.groovy` | ✅ Done | |
| `dirty/GrailsEntityDirtinessStrategy.groovy` | ✅ Done | Stale `@CompileDynamic` removed from `getStatus`; typed `EntityEntry`, explicit null check |
| `support/HibernateDatastoreConnectionSourcesRegistrar.groovy` | ✅ Done | |
| `support/HibernateDatastoreFactoryBean.groovy` | ✅ Done | |
| `support/HibernateRuntimeUtils.groovy` | ✅ Done | |
| `support/DataSourceFactoryBean.groovy` | ✅ Done | `@CompileStatic` added |

---

## `boot-plugin` submodule

| File | Status | Notes |
|------|--------|-------|
| `HibernateGormAutoConfiguration.groovy` | ✅ Done | |
| `GormCompilerAutoConfiguration.groovy` | ✅ Done | |

---

## `grails-plugin` submodule

| File | Status | Notes |
|------|--------|-------|
| `HibernateDatastoreSpringInitializer.groovy` | ✅ Done | |
| `HibernateGrailsPlugin.groovy` | ✅ Done | |
| `commands/SchemaExportCommand.groovy` | ✅ Done | |
| `HibernateSpec.groovy` | ✅ Done | |

---

## `dbmigration` submodule

| File | Status | Notes |
|------|--------|-------|
| `DatabaseMigrationException.groovy` | ✅ Done | `@CompileStatic` added |
| `PluginConstants.groovy` | ✅ Done | `@CompileStatic` added |
| `NoopVisitor.groovy` | ✅ Done | `@CompileStatic` added |
| `DatabaseMigrationTransactionManager.groovy` | ❌ Not Eligible | Dynamic `Map` property assignment on `DefaultTransactionDefinition`. Inherently dynamic. |
| `DatabaseMigrationGrailsPlugin.groovy` | ❌ Not Eligible | Grails plugin DSL (`doWithSpring`, `doWithApplicationContext`). Inherently dynamic. |
| `EnvironmentAwareCodeGenConfig.groovy` | ⚠️ Partial | `mergeEnvironmentConfig` uses `.environments?."$environment"` (dynamic GPath). Cannot be made static. |
| `command/DatabaseMigrationCommand.groovy` | ⚠️ Partial | `createDatabase` uses `clazz.decode(password)` via dynamic dispatch on a runtime-loaded codec. Cannot be made static without a typed interface. |
| `command/ApplicationContextDatabaseMigrationCommand.groovy` | ✅ Done | |
| `command/DbmChangelogToGroovy.groovy` | ✅ Done | |
| `command/DbmCreateChangelog.groovy` | ✅ Done | |
| `command/ScriptDatabaseMigrationCommand.groovy` | ✅ Done | |
| `liquibase/GrailsLiquibaseFactory.groovy` | ✅ Done | `@CompileStatic` added |
| `liquibase/GrailsLiquibase.groovy` | ✅ Done | |
| `liquibase/ChangelogXml2Groovy.groovy` | ✅ Done | |
| `liquibase/DatabaseChangeLogBuilder.groovy` | ✅ Done | |
| `liquibase/EmbeddedJarPathHandler.groovy` | ✅ Done | |
| `liquibase/GormColumnSnapshotGenerator.groovy` | ✅ Done | |
| `liquibase/GormDatabase.groovy` | ✅ Done | |
| `liquibase/GroovyChange.groovy` | ✅ Done | |
| `liquibase/GroovyChangeLogSerializer.groovy` | ✅ Done | |
| `liquibase/GroovyDiffToChangeLogCommandStep.groovy` | ✅ Done | |
| `liquibase/GroovyGenerateChangeLogCommandStep.groovy` | ✅ Done | |
| `liquibase/GroovyPrecondition.groovy` | ✅ Done | |
| `liquibase/GroovyChangeLogParser.groovy` | ⚠️ Partial | See §GroovyChangeLogParser detail below |

---

## Detailed Analysis of Remaining Partial Files

### `HibernateGormStaticApi.groovy` — `getAllInternal`

```groovy
@CompileDynamic
private getAllInternal(List ids) {
    def identityType = ...
    def identityName = ...
    def idsMap = [:]
    def root = cq.from(...)
    ...
}
```

Can be fully typed: `Class identityType`, `String identityName`, `Map<Object,Object> idsMap`, `Root<?> root`. Medium effort.

---

### `cfg/Mapping.groovy`

`methodMissing` dispatches property-name calls (e.g. `firstName column: 'f_name'`) to `property(name, closure)`. Inherently dynamic — `@CompileDynamic` on this method alone is correct. No change needed.

---

### `liquibase/GroovyChangeLogParser.groovy`

| Method | Reason dynamic | Can be fixed? |
|--------|---------------|---------------|
| `parseToNode(...)` | `compilerConfiguration.metaClass.respondsTo(...)` metaClass access | **Possibly** — guard can be removed; locals can be typed |
| `setChangeLogProperties(...)` | `changeLogProperties.each { name, value -> ... }` with dynamic `value.contexts` / `value.labels` on `Object` | **Possibly** — type as `Map<String,Object>`, add `instanceof` guards |

---

## Remaining Actions

| Action | File | Effort |
|--------|------|--------|
| Type untyped locals | `getAllInternal` in `HibernateGormStaticApi` | Medium |
| Remove metaClass guard + type locals | `parseToNode` in `GroovyChangeLogParser` | Medium |
| Add `instanceof` guards for dynamic map values | `setChangeLogProperties` in `GroovyChangeLogParser` | Medium |
| Accept as permanently dynamic | `methodMissing` in `Mapping`, `DatabaseMigrationTransactionManager`, `DatabaseMigrationGrailsPlugin`, `createDatabase` in `DatabaseMigrationCommand`, `mergeEnvironmentConfig` in `EnvironmentAwareCodeGenConfig` | No action |
