# Hibernate 7 Test Status

| Test File | Status | Notes |
|-----------|--------|-------|
| `src/test/groovy/org/grails/datastore/mapping/model/PersistentPropertySpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/DefaultConstraintsSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/HibernateGormStaticApiSpec.groovy` | FAILED | withSession fails with persistence context issue; HQL escape test fails. |
| `src/test/groovy/org/grails/orm/hibernate/HibernateGormInstanceApiSpec.groovy` | FAILED | delete() method missing/renamed? remove() called instead. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/ForeignKeyColumnCountCalculatorSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/SimpleValueBinderSpec.groovy` | FAILED | Generator binding issues. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/NamingStrategyProviderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/ColumnConfigToColumnBinderSpec.groovy` | FAILED | Sealed class org.hibernate.mapping.Column cannot be mocked. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/BackTigsTrimmerSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/UniqueNameGeneratorSpec.groovy` | FAILED | Sealed class org.hibernate.mapping.Column cannot be mocked. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/ClassBinderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/ColumnNameForPropertyAndPathFetcherSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/CascadeBehaviorPersisterSpec.groovy` | FAILED | Unsupported cascade style: save-update. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/ManyToOneBinderSpec.groovy` | FAILED | Final class org.hibernate.mapping.ManyToOne cannot be mocked. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/NamingStrategyWrapperSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/CascadeBehaviorFetcherSpec.groovy` | FAILED | Expected save-update but got all. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/TableNameFetcherSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/StringColumnConstraintsBinderSpec.groovy` | FAILED | Sealed class org.hibernate.mapping.Column cannot be mocked. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/SimpleValueColumnBinderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/HibernateEntityWrapperSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/ManyToOneValuesBinderSpec.groovy` | FAILED | Final class org.hibernate.mapping.ManyToOne cannot be mocked. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/NaturalIdentifierBinderSpec.groovy` | FAILED | Final/Sealed class issues with RootClass and Column. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/TypeNameProviderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/ColumnBinderSpec.groovy` | FAILED | Column name mismatch: got "test" instead of expected. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/BackticksRemoverSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/IndexBinderSpec.groovy` | FAILED | Sealed class org.hibernate.mapping.Column cannot be mocked. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/CompositeIdentifierToManyToOneBinderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/TableForManyCalculatorSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/PropertyBinderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/NamespaceNameExtractorSpec.groovy` | FAILED | Final class org.hibernate.boot.model.relational.Namespace$Name cannot be mocked. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/UniqueKeyForColumnsCreatorSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/CollectionForPropertyConfigBinderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/ShouldCollectionBindWithJoinColumnSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/NumericColumnConstraintsBinderSpec.groovy` | FAILED | Sealed class org.hibernate.mapping.Column cannot be mocked. |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/DefaultColumnNameFetcherSpec.groovy` | FAILED | Package/class name parts in generated column names use dots instead of underscores? |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/EnumTypeBinderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/domainbinding/CreateKeyForPropsSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/HibernateMappingContextSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/GrailsDomainBinderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/cfg/MappingSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/MultipleDataSourceConnectionsSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/SecondLevelCacheSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/HibernateConnectionSourceSettingsSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/DataSourceConnectionSourceFactorySpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/SingleTenantSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/MultipleDataSourcesWithEventsSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/MultipleDataSourceMetadataSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/PartitionedMultiTenancySpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/HibernateConnectionSourceFactorySpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/MultipleDataSourcesWithCachingSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/connections/SchemaMultiTenantSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/support/HibernateVersionSupportSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/HibernateDatastoreSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/HibernateCriteriaBuilderSpec.groovy` | PASSED | |
| `src/test/groovy/org/grails/orm/hibernate/compiler/HibernateEntityTransformationSpec.groovy` | FAILED | Compilation error: Can't have an abstract method in a non-abstract class. |
| `src/test/groovy/org/grails/orm/hibernate/BidirectionalManyToOneWithListMappingSpec.groovy` | FAILED | Final class org.hibernate.mapping.ManyToOne cannot be mocked. |
| `src/test/groovy/grails/gorm/specs/UniqueConstraintHibernateSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/WhereQueryWithAssociationSortSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/HibernateGormDatastoreSpec.groovy` | PENDING | |
| `src/test/groovy/grails/gorm/specs/CompositeIdWithJoinTableSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/CascadeToBidirectionalAsssociationSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/SizeConstraintSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/TwoBidirectionalOneToManySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/ExecuteQueryWithinValidatorSpec.groovy` | FAILED | Hibernate 7 removal: Session.save() method missing. |
| `src/test/groovy/grails/gorm/specs/proxy/Hibernate6GroovyProxySpec.groovy` | FAILED | Hibernate 7 change: location.isInitialized() method missing. |
| `src/test/groovy/grails/gorm/specs/proxy/ByteBuddyProxySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/WithNewSessionAndExistingTransactionSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/txs/TransactionalWithinReadOnlySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/txs/TransactionPropagationSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/txs/CustomIsolationLevelSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/TablePerSubClassAndEmbeddedSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/traits/TraitPropertySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/traits/InterfacePropertySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/hasmany/TwoUnidirectionalHasManySpec.groovy` | FAILED | SQL Syntax error: Qualified column names in DDL. |
| `src/test/groovy/grails/gorm/specs/hasmany/ListCollectionSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/hasmany/HasManyWithInQuerySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/detachedcriteria/DetachedCriteriaProjectionAliasSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/detachedcriteria/DetachedCriteriaProjectionSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/detachedcriteria/DetachedCriteriaJoinSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/detachedcriteria/DetachCriteriaSubquerySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/IdentityEnumTypeSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/mappedby/MultipleOneToOneSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/ImportFromConstraintSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/SchemaNameSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/CompositeIdWithManyToOneAndSequenceSpec.groovy` | FAILED | NPE in Hibernate 7 SequenceStyleGenerator. |
| `src/test/groovy/grails/gorm/specs/belongsto/BidirectionalOneToOneWithUniqueSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/SubqueryAliasSpec.groovy` | SKIPPED | |
| `src/test/groovy/grails/gorm/specs/jpa/SimpleJpaEntitySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/LastUpdateWithDynamicUpdateSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/Hibernate6OptimisticLockingSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/EnumMappingSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/autoimport/AutoImportSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/UniqueWithMultipleDataSourcesSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/SaveWithExistingValidationErrorSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/multitenancy/MultiTenancyBidirectionalManyToManySpec.groovy` | FAILED | Found two representations of same collection. |
| `src/test/groovy/grails/gorm/specs/multitenancy/MultiTenancyUnidirectionalOneToManySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/inheritance/SubclassToOneProxySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/inheritance/TablePerConcreteClassImportedSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/inheritance/TablePerConcreteClassAndDateCreatedSpec.groovy` | FAILED | NPE in Hibernate 7 IncrementGenerator. |
| `src/test/groovy/grails/gorm/specs/MultiColumnUniqueConstraintSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/dirtychecking/HibernateDirtyCheckingSpec.groovy` | FAILED | Dirty checking issues in Hibernate 7. |
| `src/test/groovy/grails/gorm/specs/dirtychecking/HibernateUpdateFromListenerSpec.groovy` | FAILED | Dirty checking issues in Hibernate 7. |
| `src/test/groovy/grails/gorm/specs/dirtychecking/PropertyFieldSpec.groovy` | FAILED | Dirty checking issues in Hibernate 7. |
| `src/test/groovy/grails/gorm/specs/NullableAndLengthSpec.groovy` | FAILED | Mapping/Constraint issues. |
| `src/test/groovy/grails/gorm/specs/HibernateEntityTraitGeneratedSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/ToOneProxySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/SubclassMultipleListCollectionSpec.groovy` | FAILED | SQL Syntax error: Qualified column names in DDL. |
| `src/test/groovy/grails/gorm/specs/hibernatequery/HibernateQuerySpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/softdelete/SoftDeleteSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/AutoTimestampSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/RLikeSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/events/UpdatePropertyInEventListenerSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/DeleteAllWhereSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/CountByWithEmbeddedSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/DomainGetterSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/SequenceIdSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/perf/JoinPerfSpec.groovy` | FAILED | Unique constraint violation. |
| `src/test/groovy/grails/gorm/specs/uuid/UuidInsertSpec.groovy` | FAILED | Logic error: name mismatch after insert/update. |
| `src/test/groovy/grails/gorm/specs/ReadOperationSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/ManyToOneSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/services/DataServiceSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/validation/BeanValidationSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/validation/SkipValidationSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/validation/CascadeValidationSpec.groovy` | FAILED | SQL Syntax error: Qualified column names in DDL. |
| `src/test/groovy/grails/gorm/specs/validation/SaveWithInvalidEntitySpec.groovy` | FAILED | Hibernate 7 change: got EntityActionVetoException instead of IllegalStateException. |
| `src/test/groovy/grails/gorm/specs/validation/UniqueFalseConstraintSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/validation/DeepValidationSpec.groovy` | FAILED | SQL Syntax error: Qualified column names in DDL. |
| `src/test/groovy/grails/gorm/specs/validation/UniqueWithHasOneSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/validation/UniqueWithinGroupSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/validation/UniqueInheritanceSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/validation/EmbeddedWithValidationExceptionSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/compositeid/CompositeIdWithDeepOneToManyMappingSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/compositeid/GlobalConstraintWithCompositeIdSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/specs/NullValueEqualSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/hibernate/mapping/HibernateOptimisticLockingStyleMappingSpec.groovy` | PASSED | |
| `src/test/groovy/grails/gorm/hibernate/mapping/MappingBuilderSpec.groovy` | PASSED | |
