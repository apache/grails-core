# Known Issues in Hibernate 7 Migration

## [TODO] Review impact of changing `ConnectionSource.DEFAULT` to "default"

**Description:**
The value of `ConnectionSource.DEFAULT` was changed from `"DEFAULT"` to `"default"` (lowercase) to align with Grails 7 official conventions. 
A new constant `ConnectionSource.OLD_DEFAULT = "DEFAULT"` was added for backward compatibility.

In Grails 7:
- GORM Connection Name: `"default"` (via `ConnectionSource.DEFAULT`)
- Spring Bean ID: `"dataSource"`
- Configuration Key: `"dataSource"`

**Actions taken in H7:**
- `HibernateDatastore.getDatastoreForConnection` supports `"dataSource"`, `ConnectionSource.DEFAULT` ("default"), and `ConnectionSource.OLD_DEFAULT` ("DEFAULT").
- Raw `"default"` and `"DEFAULT"` strings in H7 production code and key tests have been replaced with `ConnectionSource.DEFAULT`.
- `HibernateMappingContextConfiguration` and `HibernateDatastore` correctly use `ConnectionSource.DEFAULT` for the primary datasource name.
- **Fixed Issues in GORM Querying (Hibernate 7):**
    - Refactored `JpaFromProvider` to correctly handle root aliases and hierarchical joins for dot-notated projection/criteria paths.
    - Fixed `ClassCastException` in `PredicateGenerator` by ensuring all association paths are properly pre-joined in the JPA metamodel.
    - Enhanced `PredicateGenerator.handleExists` to properly support correlated subqueries with their own join providers.
    - Ensured basic collection joins (e.g., `nicknames`) are automatically handled during query construction.

**Risk & Potential Propagation:**
- This change might affect other GORM modules (Neo4j, MongoDB, etc.) if they rely on the uppercase `"DEFAULT"` string literal and don't yet support the lowercase `"default"`.
- Production systems referencing the raw string `"DEFAULT"` should be encouraged to use the `ConnectionSource.DEFAULT` constant.

**Action Required:**
- Audit other GORM implementations for consistency.

## Static Analysis Violations (hibernate7-core)

### Checkstyle
| Class | Line | Error |
|-------|------|-------|
| `org.grails.orm.hibernate.proxy.HibernateProxyHandler` | 119-121 | '||' should be on the previous line. |
| `org.grails.orm.hibernate.proxy.ByteBuddyGroovyInterceptor` | 67 | '+' should be on the previous line. |
| `org.grails.orm.hibernate.proxy.ByteBuddyGroovyInterceptor` | 71-72 | '||' should be on the previous line. |
| `org.grails.orm.hibernate.proxy.GroovyProxyInterceptorLogic` | 44 | '&&' should be on the previous line. |
| `org.grails.orm.hibernate.proxy.GroovyProxyInterceptorLogic` | 64-67 | '||' should be on the previous line. |
| `org.grails.orm.hibernate.access.TraitPropertyAccessStrategy` | 72 | '&&' should be on the previous line. |
| `org.grails.orm.hibernate.access.TraitPropertyAccessStrategy` | 73 | '||' should be on the previous line. |
| `org.grails.orm.hibernate.access.TraitPropertyAccessStrategy` | 80-113 | '+' should be on the previous line. (Multiple occurrences) |
| `org.grails.orm.hibernate.HibernateDatastore` | 34 | 'javax.sql.DataSource' should be separated from previous imports. |
| `org.grails.orm.hibernate.HibernateDatastore` | 332 | '||' should be on the previous line. |
| `org.grails.orm.hibernate.HibernateDatastore` | 339-823 | '+' should be on the previous line. (Multiple occurrences) |
| `org.grails.orm.hibernate.HibernateDatastore` | 927 | '?' should be on the previous line. |
| `org.grails.orm.hibernate.HibernateDatastore` | 928 | ':' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator` | 81 | '+' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider` | 92 | '?' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider` | 93 | ':' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher` | 27 | '?' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher` | 28 | ':' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator` | 73 | '?' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator` | 75 | ':' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior` | 107 | '||' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher` | 69-81 | '+' should be on the previous line. (Multiple occurrences) |
| `org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder` | 107-108 | '||' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder` | 109 | '&&' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher` | 52-53 | '+' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnBinder` | 130-149 | '+' should be on the previous line. (Multiple occurrences) |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.JoinedSubClassBinder` | 93-94 | '+' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder` | 105 | '?' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder` | 107 | ':' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.UnionSubclassBinder` | 89-90 | '+' should be on the previous line. |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.ForeignKeyOneToOneBinder` | 71 | '&&' should be on the previous line. |

### PMD
| Class | Line | Error | Rule |
|-------|------|-------|------|
| `org.grails.orm.hibernate.GrailsHibernateTemplate` | 341 | New exception is thrown in catch block, original stack trace may be lost | PreserveStackTrace |
| `org.grails.orm.hibernate.HibernateDatastore` | 661 | Avoid using Literals in Conditional Statements | AvoidLiteralsInIfCondition |
| `org.grails.orm.hibernate.cfg.GrailsHibernateUtil` | 274 | Position literals first in String comparisons | LiteralsFirstInComparisons |
| `org.grails.orm.hibernate.cfg.GrailsHibernateUtil` | 292 | Logger calls should be surrounded by log level guards. | GuardLogStatement |
| `org.grails.orm.hibernate.cfg.GrailsHibernateUtil` | 294 | Logger calls should be surrounded by log level guards. | GuardLogStatement |
| `org.grails.orm.hibernate.cfg.HibernateMappingContext` | 123 | Avoid reassigning parameters such as 'name' | AvoidReassigningParameters |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | 106 | Field 'hibernateMappingContext' is of non-serializable type | NonSerializableClass |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | 108 | Field 'hibernateEventListeners' is of non-serializable type | NonSerializableClass |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | 114 | Field 'namingStrategyProvider' is of non-serializable type | NonSerializableClass |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | 146 | The String literal "false" appears 5 times in this file | AvoidDuplicateLiterals |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | 187 | The method 'addAnnotatedClasses(Class...)' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | 207 | The method 'addPackages(String...)' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | 348 | The method 'sessionFactoryClosed(SessionFactory)' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.IdentityEnumType$BidiEnumMap` | 203 | Logger calls should be surrounded by log level guards. | GuardLogStatement |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder` | 69 | Avoid unused local variables such as 'table'. | UnusedLocalVariable |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder` | 78 | Avoid unused local variables such as 'table'. | UnusedLocalVariable |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder` | 107 | Switch statements should be exhaustive, add a default case | SwitchStmtsShouldHaveDefault |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.RootBinder` | 75 | Logger calls should be surrounded by log level guards. | GuardLogStatement |
| `org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder` | 36 | Avoid unused constructor parameters such as 'ignore'. | UnusedFormalParameter |
| `org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsNativeGenerator` | 73 | You should not modify visibility using setAccessible() | AvoidAccessibilityAlteration |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity` | 50 | The method 'getMappedForm()' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation` | 89 | The method 'isBidirectionalManyToOneWithListMapping(Property)' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation` | 103 | The method 'getTypeName(Class, PropertyConfig, Mapping)' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty` | 38 | The method 'getCollection()' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty` | 42 | The method 'setCollection(Collection)' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty` | 43 | The method 'getCollection()' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty` | 47 | The method 'setCollection(Collection)' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedPersistentEntity` | 35 | The method 'getMappedForm()' is missing an @Override annotation. | MissingOverride |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityMapping` | 60 | Returning 'DEFAULT_IDENTITY_MAPPING' may expose an internal array. | MethodReturnsInternalArray |
| `org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty` | 44 | The method 'getReferencedEntityName()' is missing an @Override annotation. | MissingOverride |

### SpotBugs
| Class | Line | Error | Bug Type |
|-------|------|-------|----------|
| `org.grails.orm.hibernate.query.PagedResultList` | 50-93 | Class shadows the simple name of the superclass | NM_SAME_SIMPLE_NAME_AS_SUPERCLASS |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | N/A | Non-serializable field `hibernateEventListeners` | SE_BAD_FIELD |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | N/A | Non-serializable field `hibernateMappingContext` | SE_BAD_FIELD |
| `org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration` | N/A | Non-serializable field `namingStrategyProvider` | SE_BAD_FIELD |
| `org.grails.orm.hibernate.proxy.ByteBuddyGroovyInterceptor` | N/A | Potential null pointer dereference in proxy logic | NP_NULL_ON_SOME_PATH |
| `org.grails.orm.hibernate.proxy.ByteBuddyGroovyProxyFactory` | N/A | Class implements Serializable but doesn't define serialVersionUID | SE_NO_SERIALVERSIONID |
