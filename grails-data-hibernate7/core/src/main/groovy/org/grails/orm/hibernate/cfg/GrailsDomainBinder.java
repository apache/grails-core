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

import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.domainbinding.ClassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.EnumTypeBinder;
import org.grails.orm.hibernate.cfg.domainbinding.NamingStrategyProvider;
import org.grails.orm.hibernate.cfg.domainbinding.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.*;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;

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
import org.hibernate.mapping.Collection;
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
import java.util.Map;
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
    public static final String BACKTICK = "`";

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
    private final ClassBinder classBinding;
    private final EnumTypeBinder enumTypeBinder;
    private final PropertyFromValueCreator propertyFromValueCreator;
    private ComponentPropertyBinder componentPropertyBinder;
    private Closure defaultMapping;
    private PersistentEntityNamingStrategy namingStrategy;
    private MetadataBuildingContext metadataBuildingContext;
    private MappingCacheHolder mappingCacheHolder;
    private CollectionHolder collectionHolder;
    private GrailsPropertyBinder grailsPropertyBinder;
    private CollectionBinder collectionBinder;


    public JdbcEnvironment getJdbcEnvironment() {
        return  metadataBuildingContext.getMetadataCollector().getDatabase().getJdbcEnvironment();
    }

    public GrailsDomainBinder(String dataSourceName
                            , String sessionFactoryName
                            , HibernateMappingContext hibernateMappingContext
                            , ClassBinder classBinding
                            , EnumTypeBinder enumTypeBinder) {
        this.sessionFactoryName = sessionFactoryName;
        this.dataSourceName = dataSourceName;
        this.hibernateMappingContext = hibernateMappingContext;
        this.classBinding = classBinding;
        this.enumTypeBinder = enumTypeBinder;
        this.propertyFromValueCreator = new PropertyFromValueCreator();
        this.mappingCacheHolder = MappingCacheHolder.getInstance();
        this.collectionHolder = new CollectionHolder(this);
        this.collectionBinder = new CollectionBinder(null, this, null);
        this.componentPropertyBinder = new ComponentPropertyBinder(null, null, mappingCacheHolder, collectionHolder, enumTypeBinder, collectionBinder, propertyFromValueCreator);
        // pre-build mappings
        for (GrailsHibernatePersistentEntity persistentEntity : hibernateMappingContext.getHibernatePersistentEntities()) {
            mappingCacheHolder.cacheMapping(persistentEntity);
        }
    }




    public GrailsDomainBinder(
            String dataSourceName,
            String sessionFactoryName,
            HibernateMappingContext hibernateMappingContext) {
        this(dataSourceName
                , sessionFactoryName
                , hibernateMappingContext
                , new ClassBinder()
                , new EnumTypeBinder()
        );

    }


    /**
     * The default mapping defined by {@link org.grails.datastore.mapping.config.Settings#SETTING_DEFAULT_MAPPING}
     * @param defaultMapping The default mapping
     */
    public void setDefaultMapping(Closure defaultMapping) {
        this.defaultMapping = defaultMapping;
    }

    /**
     *
     * @param namingStrategy Custom naming strategy to plugin into table naming
     */
    public void setNamingStrategy(PersistentEntityNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }


    @Override
    public void contribute(AdditionalMappingContributions contributions, InFlightMetadataCollector metadataCollector, ResourceStreamLocator resourceStreamLocator, MetadataBuildingContext buildingContext) {
        RootMappingDefaults rootMappingDefaults = null;
        this.metadataBuildingContext = new MetadataBuildingContextRootImpl(
                "default",
                metadataCollector.getBootstrapContext(),
                metadataCollector.getMetadataBuildingOptions(),
                metadataCollector
                , rootMappingDefaults
        );
        this.collectionBinder = new CollectionBinder(metadataBuildingContext, this, getNamingStrategy());
        this.componentPropertyBinder = new ComponentPropertyBinder(metadataBuildingContext, getNamingStrategy(), getMappingCacheHolder(), getCollectionHolder(), enumTypeBinder, collectionBinder, propertyFromValueCreator);
        this.grailsPropertyBinder = new GrailsPropertyBinder(metadataBuildingContext, getNamingStrategy(), getCollectionHolder(), enumTypeBinder, componentPropertyBinder, collectionBinder, propertyFromValueCreator);

        hibernateMappingContext.getHibernatePersistentEntities().stream()
                .filter(persistentEntity -> persistentEntity.forGrailsDomainMapping(dataSourceName))
                .forEach(hibernatePersistentEntity -> bindRoot(hibernatePersistentEntity, metadataCollector, sessionFactoryName));
    }


    /**
     * Override the default naming strategy for the default datasource given a Class or a full class name.
     * @param strategy the class or name
     * @throws ClassNotFoundException When the class was not found for specified strategy
     * @throws InstantiationException When an error occurred instantiating the strategy
     * @throws IllegalAccessException When an error occurred instantiating the strategy
     */
    public static void configureNamingStrategy(final Object strategy) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        configureNamingStrategy(ConnectionSource.DEFAULT, strategy);
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
     * First pass to bind collection to Hibernate metamodel, sets up second pass
     *
     * @param property   The GrailsDomainClassProperty instance
     * @param collection The collection
     * @param owner      The owning persistent class
     * @param mappings   The Hibernate mappings instance
     * @param path
     */
    public void bindCollection(HibernateToManyProperty property, Collection collection,
                               PersistentClass owner, @Nonnull InFlightMetadataCollector mappings, String path, String sessionFactoryBeanName) {
        collectionBinder.bindCollection(property, collection, owner, mappings, path, sessionFactoryBeanName);
    }

    /**
     * Binds a Grails domain class to the Hibernate runtime meta model
     *
     * @param entity The domain class to bind
     * @param mappings    The existing mappings
     * @param sessionFactoryBeanName  the session factory bean name
     * @throws MappingException Thrown if the domain class uses inheritance which is not supported
     */
    public void bindClass(@Nonnull PersistentEntity entity, @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName)
            throws MappingException {
        //if (domainClass.getClazz().getSuperclass() == Object.class) {
        if (entity.isRoot()) {
            bindRoot((GrailsHibernatePersistentEntity) entity, mappings, sessionFactoryBeanName);
        }
    }




    /**
     * Binds a root class (one with no super classes) to the runtime meta model
     * based on the supplied Grails domain class
     *
     * @param entity The Grails domain class
     * @param mappings    The Hibernate Mappings object
     * @param sessionFactoryBeanName  the session factory bean name
     */
    protected void bindRoot(@Nonnull GrailsHibernatePersistentEntity entity,@Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        if (mappings.getEntityBinding(entity.getName()) != null) {
            LOG.info("[GrailsDomainBinder] Class [" + entity.getName() + "] is already mapped, skipping.. ");
            return;
        }
        var children = entity.getChildEntities(dataSourceName);
        RootClass root = bindRootPersistentClassCommonValues(entity, children, mappings, sessionFactoryBeanName);
        if (root.isPolymorphic()) {
            Mapping m = entity.getMappedForm();
            final Mapping finalMapping = m;
            boolean tablePerSubclass = !m.getTablePerHierarchy();
            if (!tablePerSubclass) {
                // if the root class has children create a discriminator property

                bindDiscriminatorProperty(root.getTable(), root, m);
            }
            // bind the sub classes
            children.forEach(sub -> bindSubClass(sub, root, mappings, sessionFactoryBeanName, finalMapping,mappingCacheHolder ));
        }

        addMultiTenantFilterIfNecessary(entity, root, mappings);

        mappings.addEntityBinding(root);
    }



    public PersistentEntityNamingStrategy getNamingStrategy() {
        if (namingStrategy == null) {
            namingStrategy = new NamingStrategyWrapper(NAMING_STRATEGY_PROVIDER.getPhysicalNamingStrategy(sessionFactoryName), getJdbcEnvironment());
        }
        return namingStrategy;
    }

    /**
     * Add a Hibernate filter for multitenancy if the persistent class is multitenant
     *
     * @param entity          target persistent entity for get tenant information
     * @param persistentClass persistent class for add the filter and get tenant property info
     * @param mappings        mappings to add the filter
     */
    private void addMultiTenantFilterIfNecessary(
            @Nonnull GrailsHibernatePersistentEntity entity, PersistentClass persistentClass,
            @Nonnull InFlightMetadataCollector mappings) {

        if (entity.isMultiTenant()) {
            TenantId tenantId = entity.getTenantId();

            if (tenantId != null) {
                String filterCondition = collectionBinder.getMultiTenantFilterCondition(entity);

                persistentClass.addFilter(
                        GormProperties.TENANT_IDENTITY,
                        filterCondition,
                        true,
                        Collections.emptyMap(),
                        Collections.emptyMap()
                );

                Property property = collectionBinder.grailsDomainBinder.getProperty(persistentClass, tenantId.getName());
                if (property.getValue() instanceof BasicValue basicValue) {
                    JdbcMapping jdbcMapping = basicValue.resolve().getJdbcMapping();
                    var stringVMap = Collections.singletonMap(GormProperties.TENANT_IDENTITY, jdbcMapping);
                    FilterDefinition definition = new FilterDefinition(
                            GormProperties.TENANT_IDENTITY,
                            filterCondition,
                            stringVMap
                    );
                    mappings.addFilterDefinition(definition);
                }
            }
        }
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
                            , Mapping m, MappingCacheHolder mappingCacheHolder) {
        mappingCacheHolder.cacheMapping(sub);
        Subclass subClass = createSubclassMapping(sub, parent, mappings, sessionFactoryBeanName, m);


        parent.addSubclass(subClass);
        mappings.addEntityBinding(subClass);

        addMultiTenantFilterIfNecessary(sub, subClass, mappings);

        var children = sub.getChildEntities(dataSourceName);
        if (!children.isEmpty()) {
            // bind the sub classes
            children.forEach(sub1 -> bindSubClass(sub1, subClass, mappings, sessionFactoryBeanName, m,mappingCacheHolder ));
        }
    }

    private @NonNull Subclass createSubclassMapping(@NonNull GrailsHibernatePersistentEntity subEntity, PersistentClass parent, @NonNull InFlightMetadataCollector mappings, String sessionFactoryBeanName, Mapping m) {
        Subclass subClass;
        subEntity.configureDerivedProperties();
        if (!m.getTablePerHierarchy() && !m.isTablePerConcreteClass()) {
            subClass = new JoinedSubclass(parent, this.metadataBuildingContext);
            bindJoinedSubClass(subEntity, (JoinedSubclass) subClass, mappings, m, sessionFactoryBeanName);
        }
        else if(m.isTablePerConcreteClass()) {
            subClass = new UnionSubclass(parent, this.metadataBuildingContext);
            bindUnionSubclass(subEntity, (UnionSubclass) subClass, mappings, sessionFactoryBeanName);
        }
        else {
            subClass = new SingleTableSubclass(parent, this.metadataBuildingContext);
            subClass.setDiscriminatorValue(subEntity.getDiscriminatorValue());
            bindSubClass(subEntity, subClass, mappings, sessionFactoryBeanName);
        }
        subClass.setBatchSize(Optional.ofNullable(m.getBatchSize()).orElse(-1));
        subClass.setDynamicUpdate(m.getDynamicUpdate());
        subClass.setDynamicInsert(m.getDynamicInsert());
        subClass.setCached(parent.isCached());
        subClass.setAbstract(subEntity.isAbstract());
        subClass.setEntityName(subEntity.getName());
        subClass.setJpaEntityName(GrailsHibernateUtil.unqualify(subEntity.getName()));
        return subClass;
    }


    private void bindUnionSubclass(@Nonnull GrailsHibernatePersistentEntity subClass, UnionSubclass unionSubclass,
                                  @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
        classBinding.bindClass(subClass, unionSubclass, mappings);

        Mapping subMapping = subClass.getMappedForm();

        //TODO Verify if needed at all
//        if ( unionSubclass.getEntityPersisterClass() == null ) {
//            unionSubclass.getRootClass().setEntityPersisterClass(
//                    UnionSubclassEntityPersister.class );
//        }

        String schema = subMapping != null && subMapping.getTable().getSchema() != null ?
                subMapping.getTable().getSchema() : null;

        String catalog = subMapping != null && subMapping.getTable().getCatalog() != null ?
                subMapping.getTable().getCatalog() : null;

        Table denormalizedSuperTable = unionSubclass.getSuperclass().getTable();
        Table mytable = mappings.addDenormalizedTable(
                schema,
                catalog,
                new TableNameFetcher(getNamingStrategy()).getTableName(subClass),
                unionSubclass.isAbstract() != null && unionSubclass.isAbstract(),
                null,
                denormalizedSuperTable, metadataBuildingContext
        );
        unionSubclass.setTable( mytable );
        unionSubclass.setClassName(subClass.getName());

        LOG.info(
                "Mapping union-subclass: " + unionSubclass.getEntityName() +
                        " -> " + unionSubclass.getTable().getName()
        );

        createClassProperties(subClass, unionSubclass, mappings, sessionFactoryBeanName);

    }
    /**
     * Binds a joined sub-class mapping using table-per-subclass
     *
     * @param sub            The Grails sub class
     * @param joinedSubclass The Hibernate Subclass object
     * @param mappings       The mappings Object
     * @param gormMapping    The GORM mapping object
     * @param sessionFactoryBeanName  the session factory bean name
     */
    private void bindJoinedSubClass(GrailsHibernatePersistentEntity sub, JoinedSubclass joinedSubclass,
                                      InFlightMetadataCollector mappings, Mapping gormMapping, String sessionFactoryBeanName) {
        classBinding.bindClass(sub, joinedSubclass, mappings);

        String schemaName = new NamespaceNameExtractor().getSchemaName(mappings);
        String catalogName = new NamespaceNameExtractor().getCatalogName(mappings);

        Table mytable = mappings.addTable(
                schemaName, catalogName,
                getJoinedSubClassTableName(sub, joinedSubclass, null, mappings, sessionFactoryBeanName),
                null, false, metadataBuildingContext);

        joinedSubclass.setTable(mytable);
        LOG.info("Mapping joined-subclass: " + joinedSubclass.getEntityName() +
                " -> " + joinedSubclass.getTable().getName());

        SimpleValue key = new DependantValue(metadataBuildingContext, mytable, joinedSubclass.getIdentifier());
        joinedSubclass.setKey(key);
        final PersistentProperty identifier = sub.getIdentity();
        String columnName = new ColumnNameForPropertyAndPathFetcher(namingStrategy).getColumnNameForPropertyAndPath(identifier, EMPTY_PATH, null);
        new SimpleValueColumnBinder().bindSimpleValue(key, identifier.getType().getName(), columnName, false);

        joinedSubclass.createPrimaryKey();
        joinedSubclass.createForeignKey();

        // properties
        createClassProperties(sub, joinedSubclass, mappings, sessionFactoryBeanName);
    }

    private String getJoinedSubClassTableName(
            GrailsHibernatePersistentEntity sub, PersistentClass model, Table denormalizedSuperTable,
            InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        String logicalTableName = GrailsHibernateUtil.unqualify(model.getEntityName());
        String physicalTableName = new TableNameFetcher(getNamingStrategy()).getTableName(sub);

        String schemaName = new NamespaceNameExtractor().getSchemaName(mappings);
        String catalogName = new NamespaceNameExtractor().getCatalogName(mappings);

        mappings.addTableNameBinding(schemaName, catalogName, logicalTableName, physicalTableName, denormalizedSuperTable);
        return physicalTableName;
    }

    /**
     * Binds a sub-class using table-per-hierarchy inheritance mapping
     *
     * @param sub      The Grails domain class instance representing the sub-class
     * @param subClass The Hibernate SubClass instance
     * @param mappings The mappings instance
     */
    private void bindSubClass(@Nonnull GrailsHibernatePersistentEntity sub, Subclass subClass, @Nonnull InFlightMetadataCollector mappings,
                                String sessionFactoryBeanName) {
        classBinding.bindClass(sub, subClass, mappings);

        if (LOG.isDebugEnabled())
            LOG.debug("Mapping subclass: " + subClass.getEntityName() +
                    " -> " + subClass.getTable().getName());

        // properties
        createClassProperties(sub, subClass, mappings, sessionFactoryBeanName);
    }

    /**
     * Creates and binds the discriminator property used in table-per-hierarchy inheritance to
     * discriminate between sub class instances
     *
     * @param table       The table to bind onto
     * @param entity      The root class entity
     * @param someMapping The mappings instance
     */
    private void bindDiscriminatorProperty(Table table, RootClass entity, Mapping someMapping) {
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

        entity.setPolymorphic(true);
    }

    /*
     * Binds a persistent classes to the table representation and binds the class properties
     */
    private RootClass bindRootPersistentClassCommonValues(@Nonnull GrailsHibernatePersistentEntity domainClass,
                                                       @Nonnull java.util.Collection<GrailsHibernatePersistentEntity> children,
                                                       @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        RootClass root = new RootClass(this.metadataBuildingContext);
        root.setAbstract(domainClass.isAbstract());
        root.setPolymorphic(!children.isEmpty());
        classBinding.bindClass(domainClass, root, mappings);

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


        var schema = ofNullable(gormMapping.getTable())
                .map(org.grails.orm.hibernate.cfg.Table::getSchema)
                .orElse(new NamespaceNameExtractor().getSchemaName(mappings));

        var catalog = ofNullable(gormMapping.getTable())
                .map(org.grails.orm.hibernate.cfg.Table::getCatalog)
                .orElse(new NamespaceNameExtractor().getCatalogName(mappings));


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
        bindIdentity(domainClass, root, mappings, gormMapping, sessionFactoryBeanName);
        new VersionBinder(metadataBuildingContext, namingStrategy).bindVersion(domainClass.getVersion(), root);
        root.createPrimaryKey();
        createClassProperties(domainClass, root, mappings, sessionFactoryBeanName);

        return root;
    }



    public void bindIdentity(
            @Nonnull GrailsHibernatePersistentEntity domainClass,
            RootClass root,
            @Nonnull InFlightMetadataCollector mappings,
            Mapping gormMapping,
            String sessionFactoryBeanName) {

        CompositeIdBinder compositeIdBinder = new CompositeIdBinder(metadataBuildingContext, componentPropertyBinder);
        new IdentityBinder(metadataBuildingContext, getNamingStrategy(), getJdbcEnvironment(), compositeIdBinder)
                .bindIdentity(domainClass, root, mappings, gormMapping, sessionFactoryBeanName);
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
                                         @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {



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

    public CollectionHolder getCollectionHolder() {
        return collectionHolder;
    }


    public PropertyFromValueCreator getPropertyFromValueCreator() {
        return propertyFromValueCreator;
    }

    public GrailsPropertyBinder getGrailsPropertyBinder() {
        return grailsPropertyBinder;
    }

    public CollectionBinder getCollectionBinder() {
        return collectionBinder;
    }

    @Override
    public String getContributorName() {
        return "GORM";
    }


    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {

    }

    public Property getProperty(PersistentClass associatedClass, String propertyName) throws MappingException {
        try {
            return associatedClass.getProperty(propertyName);
        }
        catch (MappingException e) {
            //maybe it's squirreled away in a composite primary key
            if (associatedClass.getKey() instanceof Component) {
                return ((Component) associatedClass.getKey()).getProperty(propertyName);
            }
            throw e;
        }
    }
}