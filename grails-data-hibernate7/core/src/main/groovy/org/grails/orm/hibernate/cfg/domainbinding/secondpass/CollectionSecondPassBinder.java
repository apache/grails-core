package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.*;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;

import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.*;
import org.hibernate.mapping.Collection;
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.Map;
import java.util.Set;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.*;

/**
 * Refactored from CollectionBinder to handle collection second pass binding.
 */
public class CollectionSecondPassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionSecondPassBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final JdbcEnvironment jdbcEnvironment;
    private final DefaultColumnNameFetcher defaultColumnNameFetcher;
    private final OrderByClauseBuilder orderByClauseBuilder;
    private final SimpleValueBinder simpleValueBinder;
    private final EnumTypeBinder enumTypeBinder;
    private final ManyToOneBinder manyToOneBinder;
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;
    private final SimpleValueColumnFetcher simpleValueColumnFetcher;
    private final PrimaryKeyValueCreator primaryKeyValueCreator;
    private final CollectionKeyColumnUpdater collectionKeyColumnUpdater;
    private final GrailsPropertyResolver grailsPropertyResolver;

    public CollectionSecondPassBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment,
            SimpleValueBinder simpleValueBinder,
            EnumTypeBinder enumTypeBinder,
            ManyToOneBinder manyToOneBinder,
            CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder,
            SimpleValueColumnFetcher simpleValueColumnFetcher,
            PrimaryKeyValueCreator primaryKeyValueCreator,
            CollectionKeyColumnUpdater collectionKeyColumnUpdater,
            GrailsPropertyResolver grailsPropertyResolver) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.jdbcEnvironment = jdbcEnvironment;
        this.simpleValueBinder = simpleValueBinder;
        this.enumTypeBinder = enumTypeBinder;
        this.manyToOneBinder = manyToOneBinder;
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
        this.simpleValueColumnFetcher = simpleValueColumnFetcher;
        this.primaryKeyValueCreator = primaryKeyValueCreator;
        this.collectionKeyColumnUpdater = collectionKeyColumnUpdater;
        this.grailsPropertyResolver = grailsPropertyResolver;
        this.defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy);
        this.orderByClauseBuilder = new OrderByClauseBuilder();
    }



    public void bindCollectionSecondPass(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                         Map<?, ?> persistentClasses, Collection collection) {
        PersistentClass associatedClass = null;

        if (LOG.isDebugEnabled())
            LOG.debug("Mapping collection: "
                    + collection.getRole()
                    + " -> "
                    + collection.getCollectionTable().getName());

        PropertyConfig propConfig = property.getMappedForm();

        GrailsHibernatePersistentEntity referenced = property.getHibernateAssociatedEntity();
        if (StringUtils.hasText(propConfig.getSort())) {
            if (!property.isBidirectional() && (property instanceof HibernateOneToManyProperty)) {
                throw new DatastoreConfigurationException("Default sort for associations ["+property.getHibernateOwner().getName()+"->" + property.getName() +
                        "] are not supported with unidirectional one to many relationships.");
            }
            if (referenced != null) {
                GrailsHibernatePersistentProperty propertyToSortBy = (GrailsHibernatePersistentProperty) referenced.getPropertyByName(propConfig.getSort());

                String associatedClassName = referenced.getName();

                associatedClass = (PersistentClass) persistentClasses.get(associatedClassName);
                if (associatedClass != null) {
                    collection.setOrderBy(orderByClauseBuilder.buildOrderByClause(propertyToSortBy.getName(), associatedClass, collection.getRole(),
                            propConfig.getOrder() != null ? propConfig.getOrder() : "asc"));
                }
            }
        }

        // Configure one-to-many
        if (collection.isOneToMany()) {

            if (referenced != null && referenced.isTablePerHierarchySubclass()) {
                String discriminatorColumnName = referenced.getDiscriminatorColumnName();
                //NOTE: this will build the set for the in clause if it has sublcasses
                Set<String> discSet = referenced.buildDiscriminatorSet();
                String inclause = String.join(",", discSet);

                collection.setWhere(discriminatorColumnName + " in (" + inclause + ")");
            }


            OneToMany oneToMany = (OneToMany) collection.getElement();
            String associatedClassName = oneToMany.getReferencedEntityName();

            associatedClass = (PersistentClass) persistentClasses.get(associatedClassName);
            // if there is no persistent class for the association throw exception
            if (associatedClass == null) {
                throw new MappingException("Association references unmapped class: " + oneToMany.getReferencedEntityName());
            }

            oneToMany.setAssociatedClass(associatedClass);
            if (property.shouldBindWithForeignKey()) {
                collection.setCollectionTable(associatedClass.getTable());
            }

            new CollectionForPropertyConfigBinder().bindCollectionForPropertyConfig(collection, propConfig);
        }

        final boolean isManyToMany = property instanceof HibernateManyToManyProperty;
        if(referenced != null && !isManyToMany && referenced.isMultiTenant()) {
            String filterCondition = referenced.getMultiTenantFilterCondition(defaultColumnNameFetcher);
            if(filterCondition != null) {
                if (property.isUnidirectionalOneToMany()) {
                    collection.addManyToManyFilter(GormProperties.TENANT_IDENTITY, filterCondition, true, Collections.emptyMap(), Collections.emptyMap());
                } else {
                    collection.addFilter(GormProperties.TENANT_IDENTITY, filterCondition, true, Collections.emptyMap(), Collections.emptyMap());
                }
            }
        }

        if (property.isSorted()) {
            collection.setSorted(true);
        }

        // setup the primary key references
        DependantValue key = primaryKeyValueCreator.createPrimaryKeyValue(collection);

        // link a bidirectional relationship
        if (property.isBidirectional()) {

            var otherSide =  property.getHibernateInverseSide();

            if ((otherSide instanceof org.grails.datastore.mapping.model.types.ToOne) && property.shouldBindWithForeignKey()) {

                linkBidirectionalOneToMany(collection, associatedClass, key, otherSide);

            } else if ((otherSide instanceof HibernateManyToManyProperty) || java.util.Map.class.isAssignableFrom(property.getType())) {

                bindDependentKeyValue(property, key);

            }

        } else {

            if (propConfig.hasJoinKeyMapping()) {

                String columnName = propConfig.getJoinTable().getKey().getName();

                new SimpleValueColumnBinder().bindSimpleValue(key, "long", columnName, true);

            } else {

                bindDependentKeyValue(property, key);

            }

        }
        collection.setKey(key);

        // get cache config
        CacheConfig cacheConfig = propConfig.getCache();
        if (cacheConfig != null) {
            collection.setCacheConcurrencyStrategy(cacheConfig.getUsage());
        }

        // if we have a many-to-many
        if (isManyToMany || property.isBidirectionalOneToManyMap()) {
            var otherSide = property.getHibernateInverseSide();

            if (property.isBidirectional()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("[CollectionSecondPassBinder] Mapping other side " + otherSide.getHibernateOwner().getName() + "." + otherSide.getName() + " -> " + collection.getCollectionTable().getName() + " as ManyToOne");
                ManyToOne element = manyToOneBinder.bindManyToOne((Association)otherSide, collection.getCollectionTable(), EMPTY_PATH);
                element.setReferencedEntityName(otherSide.getOwner().getName());
                collection.setElement(element);
                new CollectionForPropertyConfigBinder().bindCollectionForPropertyConfig(collection, propConfig);
                if (property.isCircular()) {
                    collection.setInverse(false);
                }
            } else {
                // TODO support unidirectional many-to-many
            }
        } else if (property.isUnidirectionalOneToMany()) {
            // for non-inverse one-to-many, with a not-null fk, add a backref!
            // there are problems with list and map mappings and join columns relating to duplicate key constraints
            // TODO change this when HHH-1268 is resolved
            if (!property.shouldBindWithForeignKey()) {
                bindCollectionWithJoinTable(property, mappings, collection, propConfig);
            } else {
                bindUnidirectionalOneToMany((HibernateOneToManyProperty) property, mappings, collection);
            }
        } else if (property.supportsJoinColumnMapping()) {
            bindCollectionWithJoinTable(property, mappings, collection, propConfig);
        }
        collectionKeyColumnUpdater.forceNullableAndCheckUpdateable(key, property); // Use the injected service
    }

    private void bindUnidirectionalOneToMany(HibernateOneToManyProperty property, @Nonnull InFlightMetadataCollector mappings, Collection collection) {
        Value v = collection.getElement();
        v.createForeignKey();
        String entityName;
        if (v instanceof ManyToOne) {
            ManyToOne manyToOne = (ManyToOne) v;

            entityName = manyToOne.getReferencedEntityName();
        } else {
            entityName = ((OneToMany) v).getReferencedEntityName();
        }
        collection.setInverse(false);
        PersistentClass referenced = mappings.getEntityBinding(entityName);
        Backref prop = new Backref();
        GrailsHibernatePersistentEntity owner = (GrailsHibernatePersistentEntity) property.getOwner();
        prop.setEntityName(owner.getName());
        String s2 = property.getName();
        prop.setName(UNDERSCORE + new BackticksRemover().apply(owner.getJavaClass().getSimpleName()) + UNDERSCORE + new BackticksRemover().apply(s2) + "Backref");
        prop.setUpdatable(false);
        prop.setInsertable(true);
        prop.setCollectionRole(collection.getRole());
        prop.setValue(collection.getKey());
        prop.setOptional(true);

        referenced.addProperty(prop);
    }

    private void bindCollectionWithJoinTable(HibernateToManyProperty property,
                                             @Nonnull InFlightMetadataCollector mappings, Collection collection, PropertyConfig config) {

        collection.setInverse(false);
        SimpleValue element;
        final boolean isBasicCollectionType = property instanceof Basic;
        if (isBasicCollectionType) {
            element = new BasicValue(metadataBuildingContext, collection.getCollectionTable());
        }
        else {
            // for a normal unidirectional one-to-many we use a join column
            element = new ManyToOne(metadataBuildingContext, collection.getCollectionTable());
            bindUnidirectionalOneToManyInverseValues(property, (ManyToOne) element);
        }

        String columnName;

        var joinColumnMappingOptional = Optional.ofNullable(config).map(PropertyConfig::getJoinTableColumnConfig);
        if (isBasicCollectionType) {
            final Class<?> referencedType = property.getType();
            String className = referencedType.getName();
            final boolean isEnum = referencedType.isEnum();
            if (joinColumnMappingOptional.isPresent()) {
                columnName = joinColumnMappingOptional.get().getName();
            }
            else {
                var clazz = namingStrategy.resolveColumnName(className);
                var prop = namingStrategy.resolveTableName(property.getName());
                columnName = isEnum ? clazz : new BackticksRemover().apply(prop) + UNDERSCORE + new BackticksRemover().apply(clazz);
            }

            if (isEnum) {
                enumTypeBinder.bindEnumType(property, referencedType, element, columnName);
            }
            else {

                Mapping mapping = null;
                GrailsHibernatePersistentEntity domainClass = property.getHibernateOwner();
                if (domainClass != null) {
                    mapping = domainClass.getMappedForm();
                }
                String typeName = property.getTypeName();
                if (typeName == null) {
                    Type type = mappings.getTypeConfiguration().getBasicTypeRegistry().getRegisteredType(className);
                    if (type != null) {
                        typeName = type.getName();
                    }
                }
                if (typeName == null) {
                    String domainName = property.getHibernateOwner().getName();
                    throw new MappingException("Missing type or column for column["+columnName+"] on domain["+domainName+"] referencing["+className+"]");
                }

                new SimpleValueColumnBinder().bindSimpleValue(element, typeName, columnName, true);
                if (joinColumnMappingOptional.isPresent()) {
                    Column column = simpleValueColumnFetcher.getColumnForSimpleValue(element);
                    ColumnConfig columnConfig = joinColumnMappingOptional.get();
                    final PropertyConfig mappedForm = property.getMappedForm();
                    new ColumnConfigToColumnBinder().bindColumnConfigToColumn(column, columnConfig, mappedForm);
                }
            }
        } else {
            final GrailsHibernatePersistentEntity domainClass = property.getHibernateAssociatedEntity();

            Mapping m = null;
            if (domainClass != null) {
                m = domainClass.getMappedForm();
            }
            if (m != null && m.hasCompositeIdentifier()) {
                CompositeIdentity ci = (CompositeIdentity) m.getIdentity();
                compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(property, element, ci, domainClass, EMPTY_PATH);
            }
            else {
                if (joinColumnMappingOptional.isPresent()) {
                    columnName = joinColumnMappingOptional.get().getName();
                }
                else {
                    var decapitalize = domainClass.getHibernateRootEntity().getJavaClass().getSimpleName();
                    columnName = namingStrategy.resolveColumnName(decapitalize) + FOREIGN_KEY_SUFFIX;
                }

                new SimpleValueColumnBinder().bindSimpleValue(element, "long", columnName, true);
            }
        }

        collection.setElement(element);

        new CollectionForPropertyConfigBinder().bindCollectionForPropertyConfig(collection, config);
    }

    private void bindUnidirectionalOneToManyInverseValues(HibernateToManyProperty property, ManyToOne manyToOne) {
        PropertyConfig config = property.getMappedForm();
        manyToOne.setIgnoreNotFound(config.getIgnoreNotFound());
        final FetchMode fetch = config.getFetchMode();
        if(!fetch.equals(FetchMode.JOIN)) {
            manyToOne.setLazy(true);
        }

        final Boolean lazy = config.getLazy();
        if(lazy != null) {
            manyToOne.setLazy(lazy);
        }

        // set referenced entity
        manyToOne.setReferencedEntityName(property.getHibernateAssociatedEntity().getName());
    }

    private void bindDependentKeyValue(GrailsHibernatePersistentProperty property, DependantValue key) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("[CollectionSecondPassBinder] binding  [" + property.getName() + "] with dependant key");
        }

        GrailsHibernatePersistentEntity refDomainClass = property.getHibernateOwner();
        Mapping mapping = refDomainClass.getMappedForm();
        boolean hasCompositeIdentifier = mapping != null && mapping.hasCompositeIdentifier();
        if (hasCompositeIdentifier && property.supportsJoinColumnMapping()) {
            CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
            compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(property, key, ci, refDomainClass, EMPTY_PATH);
        }
        else {
            // set type
            simpleValueBinder.bindSimpleValue(property, null, key, EMPTY_PATH);
        }
    }

    private void linkBidirectionalOneToMany(Collection collection, PersistentClass associatedClass, DependantValue key, GrailsHibernatePersistentProperty otherSide) {
        collection.setInverse(true);

        Iterator<?> mappedByColumns = grailsPropertyResolver.getProperty(associatedClass, otherSide.getName()).getValue().getColumns().iterator();
        while (mappedByColumns.hasNext()) {
            Column column = (Column) mappedByColumns.next();
            linkValueUsingAColumnCopy(otherSide, column, key);
        }
    }

    private void linkValueUsingAColumnCopy(GrailsHibernatePersistentProperty prop, Column column, DependantValue key) {
        Column mappingColumn = new Column();
        mappingColumn.setName(column.getName());
        mappingColumn.setLength(column.getLength());
        mappingColumn.setNullable(prop.isNullable());
        mappingColumn.setSqlType(column.getSqlType());

        mappingColumn.setValue(key);
        key.addColumn(mappingColumn);
        key.getTable().addColumn(mappingColumn);
    }

}
