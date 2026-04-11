You are a Groovy/Spock testing expert. Your task is to analyze a JaCoCo
coverage markdown report and systematically improve test coverage for an
entire module, working from lowest to highest coverage.

## Inputs

1. **Coverage Report** (Markdown): this document
2. **Target Module**: `[e.g. grails-data-hibernate7-core]`
3. **Source Root**: `[e.g. grails-data-hibernate7-core/src/main/groovy]`
4. **Test Root**: `[e.g. grails-data-hibernate7-core/src/test/groovy]`

## Step 1 — Extract the Module Table

Read the markdown report and find the section with the header:
`## Module: [Target Module]`

Extract all rows from that module's table. The table is already sorted
ascending by % Instructions Covered — do NOT reorder it. This is your
work queue. Process rows strictly top-to-bottom.

## Step 2 — Skip Closure-Only Rows

For rows whose class name contains patterns like `._`, `.new`, `.$`,
or `_closure`:
- Do NOT create a dedicated spec for them
- Note them as sub-targets: when you reach their parent class, these
  closures are your highest-priority branches to trigger inside that spec
- A parent class is the prefix before the first `_` or `.new` in the name

## Step 3 — For Each Class (top-to-bottom)

For each top-level class row, in order:

### 3a. Locate Files
- Source file: resolve `[Source Root]/[package/ClassName].groovy`
- Spec file: look for `[Test Root]/[package/ClassNameSpec.groovy]`
    - If found: read it and note what is already covered
    - If not found: create a new Spock spec from scratch

### 3b. Identify Coverage Gaps
Read the source class and determine uncovered paths:
- Conditionals: if/else, switch, ternary `?:`, null-safe `?.`
- Exception handling: try/catch/finally blocks
- Groovy closures: each{}, collect{}, find{}, with{} — check if any
  corresponding `_closureN` row for this class is at 0.00%
- Edge cases: nulls, empty collections, boundary values

### 3c. Write Tests
Add feature methods to the spec:
- Name: `def "should [behavior] when [condition]"`
- Structure: `given/when/then` or `expect` blocks
- Use `where:` + `@Unroll` for multiple branches of the same method
- Mock all dependencies with `Mock()` or `Stub()`
- For every 0.00% closure row belonging to this class, write at least
  one test that causes that closure to execute
- Never delete or modify existing passing tests

### 3d. Move to Next Row
Once tests are written for the current class, proceed to the next row
in the table without waiting for confirmation.

## Step 4 — Stop Condition

Stop processing when one of these is true:
- All rows in the module table have been processed, OR
- You reach a row at or above 95% coverage

## Step 5 — Final Summary

Output a table of everything done:

| Class | Coverage Before | Tests Added | Closures Triggered |
|-------|-----------------|-------------|--------------------|
| ...   | ...%            | N           | ...                |

# JaCoCo Coverage Report
Generated on: 2026-04-09 19:19:20

## Module: grails-async-gpars
| Class | % Instructions Covered |
| :--- | :--- |
| org.grails.async.factory.gpars.GparsPromiseFactory._onError_closure3 | 0.00% |
| org.grails.async.factory.gpars.LoggingPoolFactory.new RejectedExecutionHandler() {...} | 0.00% |
| org.grails.async.factory.gpars.GparsPromise._accept_closure1 | 0.00% |
| org.grails.async.factory.gpars.LoggingPoolFactory.1.new Thread.UncaughtExceptionHandler() {...} | 0.00% |
| org.grails.async.factory.gpars.GparsPromiseFactory | 55.45% |
| org.grails.async.factory.gpars.GparsPromise | 65.03% |
| org.grails.async.factory.gpars.GparsPromiseFactory.__clinit__closure1 | 71.43% |
| org.grails.async.factory.gpars.LoggingPoolFactory | 72.81% |
| org.grails.async.factory.gpars.GparsPromise._onError_closure3 | 100.00% |
| org.grails.async.factory.gpars.LoggingPoolFactory.new ThreadFactory() {...} | 100.00% |
| org.grails.async.factory.gpars.GparsPromiseFactory._toGparsPromises_closure2 | 100.00% |
| org.grails.async.factory.gpars.GparsPromise._onComplete_closure2 | 100.00% |

## Module: grails-async-rxjava
| Class | % Instructions Covered |
| :--- | :--- |
| org.grails.async.factory.rxjava.RxPromiseFactory._waitAll_closure2 | 0.00% |
| org.grails.async.factory.rxjava.RxPromiseFactory._waitAll_closure1 | 0.00% |
| org.grails.async.factory.rxjava.RxPromiseFactory | 63.08% |
| org.grails.async.factory.rxjava.RxPromise | 73.22% |
| org.grails.async.factory.rxjava.RxPromiseFactory._onComplete_closure3 | 85.71% |
| org.grails.async.factory.rxjava.RxPromise._closure1 | 90.48% |
| org.grails.async.factory.rxjava.RxPromiseFactory._onError_closure4 | 100.00% |

## Module: grails-async-rxjava2
| Class | % Instructions Covered |
| :--- | :--- |
| org.grails.async.factory.rxjava2.RxPromiseFactory._waitAll_closure1 | 0.00% |
| org.grails.async.factory.rxjava2.RxPromiseFactory._waitAll_closure2 | 0.00% |
| org.grails.async.factory.rxjava2.RxPromiseFactory | 63.36% |
| org.grails.async.factory.rxjava2.RxPromise | 74.49% |
| org.grails.async.factory.rxjava2.RxPromiseFactory._onComplete_closure3 | 86.36% |
| org.grails.async.factory.rxjava2.RxPromise._closure1 | 90.48% |
| org.grails.async.factory.rxjava2.RxPromise.new Observer() {...} | 100.00% |
| org.grails.async.factory.rxjava2.RxPromiseFactory._onError_closure4 | 100.00% |

## Module: grails-async-rxjava3
| Class | % Instructions Covered |
| :--- | :--- |
| org.grails.async.factory.rxjava3.RxPromiseFactory._waitAll_closure2 | 0.00% |
| org.grails.async.factory.rxjava3.RxPromiseFactory._waitAll_closure1 | 0.00% |
| org.grails.async.factory.rxjava3.RxPromiseFactory | 63.36% |
| org.grails.async.factory.rxjava3.RxPromise | 74.49% |
| org.grails.async.factory.rxjava3.RxPromiseFactory._onComplete_closure3 | 86.36% |
| org.grails.async.factory.rxjava3.RxPromise._closure1 | 90.48% |
| org.grails.async.factory.rxjava3.RxPromise.new Observer() {...} | 100.00% |
| org.grails.async.factory.rxjava3.RxPromiseFactory._onError_closure4 | 100.00% |

## Module: grails-data-hibernate7-core
| Class | % Instructions Covered |
| :--- | :--- |
| org.grails.orm.hibernate.HibernateDatastore | 73.47% | X
| grails.orm.HibernateCriteriaBuilder | 74.43% | X
| org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor | 78.96% | X
| org.grails.orm.hibernate.HibernateDatastore.SchemaTenantGormEnhancer | 82.95% | X
| org.grails.orm.hibernate.cfg.GrailsHibernateUtil | 83.80% | X
| org.grails.orm.hibernate.HibernateGormInstanceApi | 84.68% | X
| org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory | 85.28% | X
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyCollectionProperty | 85.71% | X
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedPersistentEntity | 85.92% | X
| org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsNativeGenerator | 85.96% | X
| org.grails.orm.hibernate.query.PredicateGenerator | 86.16% | X
| org.grails.orm.hibernate.event.listener.HibernateEventListener | 87.53% | X
| org.grails.orm.hibernate.HibernateGormStaticApi | 87.83% | X
| org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder | 88.24% | X
| org.grails.orm.hibernate.compiler.HibernateEntityTransformation | 88.32% | X
| org.grails.orm.hibernate.cfg.domainbinding.util.NamespaceNameExtractor | 88.46% | X
| org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsTableGenerator | 88.66% | X
| org.grails.orm.hibernate.cfg.IdentityEnumType | 88.82% | X
| org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnBinder | 88.89% | X
| org.grails.orm.hibernate.GrailsHibernateTemplate | 89.03% | X
| org.grails.orm.hibernate.support.SoftKey | 89.09% | X
| org.grails.orm.hibernate.cfg.domainbinding.util.UniqueKeyForColumnsCreator | 89.47% | X
| org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceStyleGenerator | 89.47% | X
| org.grails.orm.hibernate.proxy.ByteBuddyGroovyInterceptor | 89.47% | X
| org.grails.orm.hibernate.query.HibernateHqlQuery | 89.52% | X
| grails.orm.CriteriaMethodInvoker | 89.68% | X
| org.grails.orm.hibernate.access.TraitPropertyAccessStrategy | 89.81% | X
| org.grails.orm.hibernate.query.MutationQueryDelegate | 90.41% | X
| org.grails.orm.hibernate.cfg.ColumnConfig | 90.43% | X
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty | 90.49% | X
| org.grails.orm.hibernate.cfg.domainbinding.binder.ClassPropertiesBinder | 90.54% | X
| org.grails.orm.hibernate.HibernateSession | 90.62% | X
| org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration | 90.95% | X
| org.grails.orm.hibernate.cfg.Mapping | 91.00% | X
| org.grails.orm.hibernate.cfg.domainbinding.binder.RootPersistentClassCommonValuesBinder | 91.24% | X
| org.grails.orm.hibernate.GrailsSessionContext | 91.51% | X
| org.grails.orm.hibernate.query.HibernatePagedResultList | 91.67% | X
| org.grails.orm.hibernate.support.ClosureEventListener | 91.99% | X
| org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder | 92.00% | X
| org.grails.orm.hibernate.dirty.GrailsEntityDirtinessStrategy | 92.02% | X
| org.grails.orm.hibernate.cfg.domainbinding.util.GrailsEnumType | 92.50% | X
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingBuilder | 92.54% | X
| org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator | 92.68% | X
| org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType | 92.68% | X
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty | 92.81% | X
| org.grails.orm.hibernate.support.HibernateRuntimeUtils | 92.89% | X
| org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder | 92.89% | X
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity | 93.02% | X
| org.grails.orm.hibernate.HibernateGormEnhancer | 93.24% | X
| org.grails.orm.hibernate.HibernateGormInstanceApi._delete_closure1 | 93.33% | X
| org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings.HibernateSettings | 93.53% | X
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation | 93.65% |
| org.grails.orm.hibernate.query.HibernateQuery | 93.76% |
| org.grails.orm.hibernate.cfg.NaturalId | 94.00% |
| org.grails.orm.hibernate.cfg.domainbinding.secondpass.UnidirectionalOneToManyBinder | 94.34% |
| org.grails.orm.hibernate.cfg.domainbinding.secondpass.MapSecondPassBinder | 94.74% |
| org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum | 94.83% |
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity | 94.88% |
| org.grails.orm.hibernate.dirty.GrailsEntityDirtinessStrategy._findDirty_closure1 | 94.96% |
| org.grails.orm.hibernate.proxy.GroovyProxyInterceptorLogic | 95.00% |
| org.grails.orm.hibernate.HibernateGormInstanceApi._performPersist_closure5 | 95.24% |
| org.grails.orm.hibernate.multitenancy.MultiTenantEventListener | 95.28% |
| org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder | 95.30% |
| org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder | 95.63% |
| org.grails.orm.hibernate.query.HqlQueryContext | 95.69% |
| org.grails.orm.hibernate.compiler.HibernateEntityTransformation.new GroovyObject() {...} | 95.83% |
| org.grails.orm.hibernate.HibernateGormValidationApi._validate_closure1 | 95.89% |
| org.grails.orm.hibernate.query.HibernateQueryExecutor | 95.90% |
| org.grails.orm.hibernate.cfg.ColumnConfig._getIndexAsMap_closure2 | 96.05% |
| org.grails.orm.hibernate.HibernateGormValidationApi | 96.17% |
| org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder | 96.32% |
| org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyWrapper | 96.33% |
| org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehaviorFetcher | 96.50% |
| org.grails.orm.hibernate.HibernateGormInstanceApi._performMerge_closure4 | 96.55% |
| org.grails.orm.hibernate.query.HqlListQueryBuilder | 96.55% |
| org.grails.orm.hibernate.GrailsHibernateTransactionManager | 96.84% |
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingFactory | 97.03% |
| org.grails.orm.hibernate.cfg.IdentityEnumType.BidiEnumMap | 97.14% |
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty | 97.18% |
| org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings.HibernateSettings.FlushSettings.FlushMode | 97.25% |
| org.grails.orm.hibernate.cfg.domainbinding.util.LogCascadeMapping | 97.30% |
| org.grails.orm.hibernate.cfg.CacheConfig | 97.41% |
| org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder | 97.50% |
| org.grails.orm.hibernate.cfg.CacheConfig.Include | 97.67% |
| org.grails.orm.hibernate.support.HibernateDatastoreConnectionSourcesRegistrar | 97.71% |
| org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingBuilder._id_closure1 | 97.73% |
| grails.gorm.hibernate.mapping.MappingBuilder.ClosureMappingDefinition | 97.78% |
| org.grails.orm.hibernate.proxy.ByteBuddyGroovyProxyFactory | 97.89% |
| org.grails.orm.hibernate.cfg.PropertyConfig | 97.97% |
| org.grails.orm.hibernate.cfg.CacheConfig.Usage | 97.98% |
| org.grails.orm.hibernate.query.ProjectionPredicate | 98.36% |
| org.grails.orm.hibernate.query.HibernateQueryArgument | 98.82% |
| org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueCreator | 98.99% |
| org.grails.orm.hibernate.connections.HibernateConnectionSourceSettingsBuilder | 99.12% |
| org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder | 99.19% |
| org.grails.orm.hibernate.query.JpaCriteriaQueryCreator | 99.26% |
| org.grails.orm.hibernate.query.JpaFromProvider | 99.34% |
| org.grails.orm.hibernate.EventListenerIntegrator | 99.36% |
| org.grails.orm.hibernate.cfg.HibernateMappingContext | 99.40% |
| grails.orm.CriteriaMethods | 99.44% |
| org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder | 99.51% |

