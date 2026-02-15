/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg;

import groovy.lang.Closure;

import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentPropertyBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider;
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator;
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.IdentityBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.JoinedSubClassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.NaturalIdentifierBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SingleTableSubclassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.UnionSubclassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.VersionBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator;
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyWrapper;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.TableNameFetcher;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.hibernate.MappingException;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.internal.MetadataBuildingContextRootImpl;
import org.hibernate.boot.internal.RootMappingDefaults;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.JoinedSubclass;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.SingleTableSubclass;
import org.hibernate.mapping.Subclass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UnionSubclass;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;

import jakarta.annotation.Nonnull;

import static java.util.Optional.ofNullable;
import static org.hibernate.boot.model.naming.Identifier.toIdentifier;


/**
 * Handles the binding Grails domain classes and properties to the Hibernate runtime meta model.
 * Based on the HbmBinder code in Hibernate core and influenced by AnnotationsBinder.
 *
 * @author Graeme Rocher
 * @since 0.1
 */
public class GrailsDomainBinder
        implements AdditionalMappingContributor, TypeContributor
{

    public static final String FOREIGN_KEY_SUFFIX = "_id";
    private static final String STRING_TYPE = "string";
    public static final String EMPTY_PATH = "";
    public static final char UNDERSCORE = '_';

    public static final String ENUM_TYPE_CLASS = org.grails.orm.hibernate.HibernateLegacyEnumType.class.getName();
    public static final String ENUM_CLASS_PROP = "enumClass";
    public static final Logger LOG = LoggerFactory.getLogger(GrailsDomainBinder.class);



    /**
     * Provider for naming strategies
     */
    private static final NamingStrategyProvider NAMING_STRATEGY_PROVIDER = new NamingStrategyProvider();
    public static final String JPA_DEFAULT_DISCRIMINATOR_TYPE = "DTYPE";


    private final String sessionFactoryName;
    private final String dataSourceName;
    private final HibernateMappingContext hibernateMappingContext;
    private PersistentEntityNamingStrategy namingStrategy;
    private MetadataBuildingContext metadataBuildingContext;
    private final MappingCacheHolder mappingCacheHolder;


    public JdbcEnvironment getJdbcEnvironment() {
        return  metadataBuildingContext.getMetadataCollector().getDatabase().getJdbcEnvironment();
    }

    public GrailsDomainBinder(
            String dataSourceName,
            String sessionFactoryName,
            HibernateMappingContext hibernateMappingContext) {
        this.sessionFactoryName = sessionFactoryName;
        this.dataSourceName = dataSourceName;
        this.hibernateMappingContext = hibernateMappingContext;
        this.mappingCacheHolder = MappingCacheHolder.getInstance();

        // pre-build mappings
        for (GrailsHibernatePersistentEntity persistentEntity : hibernateMappingContext.getHibernatePersistentEntities(dataSourceName)) {
            mappingCacheHolder.cacheMapping(persistentEntity);
        }
    }




    @Override
    public void contribute(AdditionalMappingContributions contributions, InFlightMetadataCollector metadataCollector, ResourceStreamLocator resourceStreamLocator, MetadataBuildingContext buildingContext) {
        this.metadataBuildingContext = new MetadataBuildingContextRootImpl(
                "default",
                metadataCollector.getBootstrapContext(),
                metadataCollector.getMetadataBuildingOptions(),
                metadataCollector
                , null
        );
        CollectionHolder collectionHolder = new CollectionHolder(metadataBuildingContext);
        BackticksRemover backticksRemover = new BackticksRemover();
        PersistentEntityNamingStrategy namingStrategy = getNamingStrategy();
        JdbcEnvironment jdbcEnvironment = getJdbcEnvironment();
        DefaultColumnNameFetcher defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy, backticksRemover);
        ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover);
        SimpleValueBinder simpleValueBinder = new SimpleValueBinder(namingStrategy, jdbcEnvironment);
        EnumTypeBinder enumTypeBinder = new EnumTypeBinder();
        PropertyFromValueCreator propertyFromValueCreator = new PropertyFromValueCreator();
        ClassBinder classBinder = new ClassBinder();
        SimpleValueColumnFetcher simpleValueColumnFetcher = new SimpleValueColumnFetcher();
        CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(
                new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(),
                new TableNameFetcher(namingStrategy),
                namingStrategy,
                defaultColumnNameFetcher,
                backticksRemover,
                simpleValueBinder
        );
        OneToOneBinder oneToOneBinder = new OneToOneBinder(namingStrategy, simpleValueBinder);
        ManyToOneBinder manyToOneBinder = new ManyToOneBinder(namingStrategy, simpleValueBinder, new ManyToOneValuesBinder(), compositeIdentifierToManyToOneBinder, simpleValueColumnFetcher);

        CollectionBinder collectionBinder = new CollectionBinder(
                metadataBuildingContext,
                namingStrategy,
                jdbcEnvironment,
                simpleValueBinder,
                enumTypeBinder,
                manyToOneBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher
        );
        ComponentPropertyBinder componentPropertyBinder = new ComponentPropertyBinder(
                metadataBuildingContext,
                namingStrategy,
                jdbcEnvironment,
                getMappingCacheHolder(),
                collectionHolder,
                enumTypeBinder,
                collectionBinder,
                propertyFromValueCreator,
                null,
                simpleValueBinder,
                oneToOneBinder,
                manyToOneBinder,
                columnNameForPropertyAndPathFetcher
        );
        GrailsPropertyBinder grailsPropertyBinder = new GrailsPropertyBinder(
                metadataBuildingContext,
                namingStrategy,
                collectionHolder,
                enumTypeBinder,
                componentPropertyBinder,
                collectionBinder,
                simpleValueBinder,
                columnNameForPropertyAndPathFetcher,
                oneToOneBinder,
                manyToOneBinder,
                propertyFromValueCreator
        );
        CompositeIdBinder compositeIdBinder = new CompositeIdBinder(metadataBuildingContext, componentPropertyBinder);
        PropertyBinder propertyBinder = new PropertyBinder();
        SimpleIdBinder simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment, new BasicValueIdCreator(jdbcEnvironment), simpleValueBinder, propertyBinder);
        IdentityBinder identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder);
        VersionBinder versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinder, BasicValue::new);
        MultiTenantFilterBinder multiTenantFilterBinder = new MultiTenantFilterBinder();
        JoinedSubClassBinder joinedSubClassBinder = new JoinedSubClassBinder(metadataBuildingContext, namingStrategy, new SimpleValueColumnBinder(), columnNameForPropertyAndPathFetcher, classBinder);
        UnionSubclassBinder unionSubclassBinder = new UnionSubclassBinder(metadataBuildingContext, namingStrategy, classBinder);
        SingleTableSubclassBinder singleTableSubclassBinder = new SingleTableSubclassBinder(classBinder);

        hibernateMappingContext
                .getHibernatePersistentEntities(dataSourceName)
                .stream()
                .filter(persistentEntity -> persistentEntity.forGrailsDomainMapping(dataSourceName))
                .forEach(hibernatePersistentEntity -> bindRoot(hibernatePersistentEntity, metadataCollector, sessionFactoryName, defaultColumnNameFetcher, columnNameForPropertyAndPathFetcher, identityBinder, versionBinder, grailsPropertyBinder, classBinder, propertyFromValueCreator, multiTenantFilterBinder, joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder));
    }



    /**
     * Override the default naming strategy given a Class or a full class name,
     * or an instance of a PhysicalNamingStrategy.
     *
     * @param datasourceName the datasource name
     * @param strategy  the class, name, or instance
     * @throws ClassNotFoundException When the class was not found for specified strategy
     * @throws InstantiationException When an error occurred instantiating the strategy
     * @throws IllegalAccessException When an error occurred instantiating the strategy
     */
    public static void configureNamingStrategy(final String datasourceName, final Object strategy) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        NAMING_STRATEGY_PROVIDER.configureNamingStrategy(datasourceName, strategy);
    }





    /**
     * Binds a root class (one with no super classes) to the runtime meta model
     * based on the supplied Grails domain class
     *
     * @param entity The Grails domain class
     * @param mappings    The Hibernate Mappings object
     * @param sessionFactoryBeanName  the session factory bean name
     */
    protected void bindRoot(@Nonnull GrailsHibernatePersistentEntity entity,@Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName, DefaultColumnNameFetcher defaultColumnNameFetcher, ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher, IdentityBinder identityBinder, VersionBinder versionBinder, GrailsPropertyBinder grailsPropertyBinder, ClassBinder classBinder, PropertyFromValueCreator propertyFromValueCreator, MultiTenantFilterBinder multiTenantFilterBinder, JoinedSubClassBinder joinedSubClassBinder, UnionSubclassBinder unionSubclassBinder, SingleTableSubclassBinder singleTableSubclassBinder) {
        if (mappings.getEntityBinding(entity.getName()) != null) {
            LOG.info("[GrailsDomainBinder] Class [" + entity.getName() + "] is already mapped, skipping.. ");
            return;
        }
        var children = entity.getChildEntities(dataSourceName);
        RootClass root = bindRootPersistentClassCommonValues(entity, children, mappings, sessionFactoryBeanName, identityBinder, versionBinder, grailsPropertyBinder, classBinder, propertyFromValueCreator);
        Mapping m = entity.getMappedForm();
        final Mapping finalMapping = m;
        if (!children.isEmpty() && entity.isTablePerHierarchy()) {
            bindDiscriminatorProperty(root, m);
        }
        // bind the sub classes
        children.forEach(sub -> bindSubClass(sub, root, mappings, sessionFactoryBeanName, finalMapping,mappingCacheHolder, defaultColumnNameFetcher, columnNameForPropertyAndPathFetcher, grailsPropertyBinder, classBinder, propertyFromValueCreator, multiTenantFilterBinder, joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder));

        multiTenantFilterBinder.addMultiTenantFilterIfNecessary(entity, root, mappings, defaultColumnNameFetcher);

        mappings.addEntityBinding(root);
    }



    public PersistentEntityNamingStrategy getNamingStrategy() {
        if (namingStrategy == null) {
            namingStrategy = new NamingStrategyWrapper(NAMING_STRATEGY_PROVIDER.getPhysicalNamingStrategy(sessionFactoryName), getJdbcEnvironment());
        }
        return namingStrategy;
    }

    /**
     * Binds a sub class.
     *
     * @param sub                    The sub domain class instance
     * @param parent                 The parent persistent class instance
     * @param mappings               The mappings instance
     * @param sessionFactoryBeanName the session factory bean name
     * @param mappingCacheHolder
     */
    private void bindSubClass(@Nonnull GrailsHibernatePersistentEntity sub,
                              PersistentClass parent,
                              @Nonnull InFlightMetadataCollector mappings,
                              String sessionFactoryBeanName
                            , Mapping m, MappingCacheHolder mappingCacheHolder, DefaultColumnNameFetcher defaultColumnNameFetcher, ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher, GrailsPropertyBinder grailsPropertyBinder, ClassBinder classBinder, PropertyFromValueCreator propertyFromValueCreator, MultiTenantFilterBinder multiTenantFilterBinder, JoinedSubClassBinder joinedSubClassBinder, UnionSubclassBinder unionSubclassBinder, SingleTableSubclassBinder singleTableSubclassBinder) {
        mappingCacheHolder.cacheMapping(sub);
        Subclass subClass = createSubclassMapping(sub, parent, mappings, sessionFactoryBeanName, m, defaultColumnNameFetcher, columnNameForPropertyAndPathFetcher, grailsPropertyBinder, classBinder, propertyFromValueCreator, multiTenantFilterBinder, joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder);


        parent.addSubclass(subClass);
        mappings.addEntityBinding(subClass);

        multiTenantFilterBinder.addMultiTenantFilterIfNecessary(sub, subClass, mappings, defaultColumnNameFetcher);

        var children = sub.getChildEntities(dataSourceName);
        if (!children.isEmpty()) {
            // bind the sub classes
            children.forEach(sub1 -> bindSubClass(sub1, subClass, mappings, sessionFactoryBeanName, m,mappingCacheHolder, defaultColumnNameFetcher, columnNameForPropertyAndPathFetcher, grailsPropertyBinder, classBinder, propertyFromValueCreator, multiTenantFilterBinder, joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder ));
        }
    }

    private @NonNull Subclass createSubclassMapping(@NonNull GrailsHibernatePersistentEntity subEntity, PersistentClass parent, @NonNull InFlightMetadataCollector mappings, String sessionFactoryBeanName, Mapping m, DefaultColumnNameFetcher defaultColumnNameFetcher, ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher, GrailsPropertyBinder grailsPropertyBinder, ClassBinder classBinder, PropertyFromValueCreator propertyFromValueCreator, MultiTenantFilterBinder multiTenantFilterBinder, JoinedSubClassBinder joinedSubClassBinder, UnionSubclassBinder unionSubclassBinder, SingleTableSubclassBinder singleTableSubclassBinder) {
        Subclass subClass;
        subEntity.configureDerivedProperties();
        if (!m.getTablePerHierarchy() && !m.isTablePerConcreteClass()) {
            var joined = new JoinedSubclass(parent, this.metadataBuildingContext);
            joinedSubClassBinder.bindJoinedSubClass(subEntity, joined, mappings);
            subClass = joined;
        }
        else if(m.isTablePerConcreteClass()) {
            var union  = new UnionSubclass(parent, this.metadataBuildingContext);
            unionSubclassBinder.bindUnionSubclass(subEntity, union, mappings);
            subClass = union;
        }
        else {
            var singleTableSubclass = new SingleTableSubclass(parent, this.metadataBuildingContext);

            singleTableSubclassBinder.bindSubClass(subEntity, singleTableSubclass, mappings);
            subClass = singleTableSubclass;
        }
        subClass.setBatchSize(Optional.ofNullable(m.getBatchSize()).orElse(-1));
        subClass.setDynamicUpdate(m.getDynamicUpdate());
        subClass.setDynamicInsert(m.getDynamicInsert());
        subClass.setCached(parent.isCached());
        subClass.setAbstract(subEntity.isAbstract());
        subClass.setEntityName(subEntity.getName());
        subClass.setJpaEntityName(GrailsHibernateUtil.unqualify(subEntity.getName()));
        createClassProperties(subEntity, subClass, mappings, sessionFactoryBeanName, grailsPropertyBinder, propertyFromValueCreator);
        return subClass;
    }


    /**
     * Binds a sub-class using table-per-hierarchy inheritance mapping
     *
     * @param sub      The Grails domain class instance representing the sub-class
     * @param subClass The Hibernate SubClass instance
     * @param mappings The mappings instance
     */
    /**
     * Creates and binds the discriminator property used in table-per-hierarchy inheritance to
     * discriminate between sub class instances
     *
     * @param entity      The root class entity
     * @param someMapping The mappings instance
     */
    private void bindDiscriminatorProperty(RootClass entity, Mapping someMapping) {
        Table table = entity.getTable();
        SimpleValue d = new BasicValue(metadataBuildingContext, table);
        entity.setDiscriminator(d);
        DiscriminatorConfig discriminatorConfig = someMapping.getDiscriminator();

        boolean hasDiscriminatorConfig = discriminatorConfig != null;
        entity.setDiscriminatorValue(hasDiscriminatorConfig ? discriminatorConfig.getValue() : entity.getClassName());

        if(hasDiscriminatorConfig) {
            if (discriminatorConfig.getInsertable() != null) {
                entity.setDiscriminatorInsertable(discriminatorConfig.getInsertable());
            }
            Object type = discriminatorConfig.getType();
            if (type != null) {
                if(type instanceof Class) {
                    d.setTypeName(((Class)type).getName());
                }
                else {
                    d.setTypeName(type.toString());
                }
            }
        }


        if (hasDiscriminatorConfig && discriminatorConfig.getFormula() != null) {
            Formula formula = new Formula();
            formula.setFormula(discriminatorConfig.getFormula());
            d.addFormula(formula);
        }
        else{
            new SimpleValueColumnBinder().bindSimpleValue(d, STRING_TYPE, JPA_DEFAULT_DISCRIMINATOR_TYPE, false);

            ColumnConfig cc = !hasDiscriminatorConfig ? null : discriminatorConfig.getColumn();
            if (cc != null) {
                Column c = (Column) d.getColumns().iterator().next();
                if (cc.getName() != null) {
                    c.setName(cc.getName());
                }
                new ColumnConfigToColumnBinder().bindColumnConfigToColumn(c, cc, null);
            }
        }
    }

    /*
     * Binds a persistent classes to the table representation and binds the class properties
     */
    private RootClass bindRootPersistentClassCommonValues(@Nonnull GrailsHibernatePersistentEntity domainClass,
                                                       @Nonnull java.util.Collection<GrailsHibernatePersistentEntity> children,
                                                       @Nonnull InFlightMetadataCollector mappings,
                                                       String sessionFactoryBeanName,
                                                       IdentityBinder identityBinder,
                                                       VersionBinder versionBinder,
                                                       GrailsPropertyBinder grailsPropertyBinder,
                                                       ClassBinder classBinder,
                                                       PropertyFromValueCreator propertyFromValueCreator
    ) {

        RootClass root = new RootClass(this.metadataBuildingContext);
        root.setAbstract(domainClass.isAbstract());
        classBinder.bindClass(domainClass, root, mappings);

        // get the schema and catalog names from the configuration
        Mapping gormMapping = domainClass.getMappedForm();

        domainClass.configureDerivedProperties();
        CacheConfig cc = gormMapping.getCache();
        if (cc != null && cc.getEnabled()) {
            root.setCacheConcurrencyStrategy(cc.getUsage());
            root.setCached(true);
            if ("read-only".equals(cc.getUsage())) {
                root.setMutable(false);
            }
            root.setLazyPropertiesCacheable(!"non-lazy".equals(cc.getInclude()));
        }
        root.setBatchSize(ofNullable(gormMapping.getBatchSize()).orElse(0));
        root.setDynamicUpdate(gormMapping.getDynamicUpdate());
        root.setDynamicInsert(gormMapping.getDynamicInsert());


        var schema = domainClass.getSchema(mappings);

        var catalog = domainClass.getCatalog(mappings);


        var isAbstract = !gormMapping.getTablePerHierarchy() && gormMapping.isTablePerConcreteClass() && root.isAbstract();

        // create the table
        var table = mappings.addTable(schema
                , catalog
                , new TableNameFetcher(getNamingStrategy()).getTableName(domainClass)
                , null
                , isAbstract
                , metadataBuildingContext
        );
        root.setTable(table);
        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsDomainBinder] Mapping Grails domain class: " + domainClass.getName() + " -> " + root.getTable().getName());
        }

        identityBinder.bindIdentity(domainClass, root, mappings, gormMapping, sessionFactoryBeanName);
        versionBinder.bindVersion(domainClass.getVersion(), root);
        root.createPrimaryKey();
        createClassProperties(domainClass, root, mappings, sessionFactoryBeanName, grailsPropertyBinder, propertyFromValueCreator);

        return root;
    }


    /**
     * Creates and binds the properties for the specified Grails domain class and PersistentClass
     * and binds them to the Hibernate runtime meta model
     *
     * @param domainClass     The Grails domain class
     * @param persistentClass The Hibernate PersistentClass instance
     * @param mappings        The Hibernate Mappings instance
     * @param sessionFactoryBeanName  the session factory bean name
     */
    private void createClassProperties(@Nonnull GrailsHibernatePersistentEntity domainClass, PersistentClass persistentClass,
                                         @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName, GrailsPropertyBinder grailsPropertyBinder, PropertyFromValueCreator propertyFromValueCreator) {



        for (GrailsHibernatePersistentProperty currentGrailsProp : domainClass.getPersistentPropertiesToBind()) {
           var value = grailsPropertyBinder.bindProperty(persistentClass, currentGrailsProp, mappings, sessionFactoryBeanName);
           persistentClass.addProperty(propertyFromValueCreator.createProperty(value, currentGrailsProp));
        }

        new NaturalIdentifierBinder().bindNaturalIdentifier(domainClass.getMappedForm(), persistentClass);
    }



    public MetadataBuildingContext getMetadataBuildingContext() {
        return metadataBuildingContext;
    }

    public MappingCacheHolder getMappingCacheHolder() {
        return mappingCacheHolder;
    }


    @Override
    public String getContributorName() {
        return "GORM";
    }


    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {

    }
}