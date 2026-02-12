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

import org.grails.orm.hibernate.cfg.domainbinding.GrailsPropertyCreator;
import groovy.lang.Closure;
import jakarta.persistence.Entity;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.orm.hibernate.cfg.domainbinding.ClassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.ConfigureDerivedPropertiesConsumer;
import org.grails.orm.hibernate.cfg.domainbinding.EnumTypeBinder;
import org.grails.orm.hibernate.cfg.domainbinding.NamingStrategyProvider;
import org.grails.orm.hibernate.cfg.domainbinding.PersistentPropertyToPropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.TypeNameProvider;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType;
import org.grails.orm.hibernate.cfg.domainbinding.*;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.boot.internal.MetadataBuildingContextRootImpl;
import org.hibernate.boot.model.internal.BinderHelper;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataContributor;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.mapping.Backref;
import org.hibernate.mapping.Bag;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.IndexBackref;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.JoinedSubclass;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.SingleTableSubclass;
import org.hibernate.mapping.Subclass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UnionSubclass;
import org.hibernate.mapping.Value;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.persister.entity.UnionSubclassEntityPersister;
import org.hibernate.type.ForeignKeyDirection;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;
import org.hibernate.usertype.UserCollectionType;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;

import static java.util.Optional.ofNullable;
import static org.hibernate.boot.model.naming.Identifier.toIdentifier;


/**
 * Handles the binding Grails domain classes and properties to the Hibernate runtime meta model.
 * Based on the HbmBinder code in Hibernate core and influenced by AnnotationsBinder.
 *
 * @author Graeme Rocher
 * @since 0.1
 */
@SuppressWarnings("WeakerAccess")
public class GrailsDomainBinder implements MetadataContributor {

    private static final String CASCADE_ALL_DELETE_ORPHAN = "all-delete-orphan";
    public static final String FOREIGN_KEY_SUFFIX = "_id";
    private static final String STRING_TYPE = "string";
    private static final String EMPTY_PATH = "";
    public static final char UNDERSCORE = '_';
    public static final String CASCADE_ALL = "all";
    public static final String CASCADE_SAVE_UPDATE = "save-update";
    public static final String CASCADE_NONE = "none";

    public static final String ENUM_TYPE_CLASS = org.grails.orm.hibernate.HibernateLegacyEnumType.class.getName();
    public static final String ENUM_CLASS_PROP = "enumClass";
    private static final String ENUM_TYPE_PROP = "type";
    public static final String DEFAULT_ENUM_TYPE = "default";
    private static final Logger LOG = LoggerFactory.getLogger(GrailsDomainBinder.class);
    public static final String SEQUENCE_KEY = "sequence";



    /**
     * Provider for naming strategies
     */
    private static final NamingStrategyProvider NAMING_STRATEGY_PROVIDER = new NamingStrategyProvider();

    private final CollectionType CT = new CollectionType(null, this) {
        @Override
        public Collection create(ToMany property, PersistentClass owner, String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
            return null;
        }
    };

    private final String sessionFactoryName;
    private final String dataSourceName;
    private final HibernateMappingContext hibernateMappingContext;
    private final ClassBinder classBinding;
    private Closure defaultMapping;
    private PersistentEntityNamingStrategy namingStrategy;
    private MetadataBuildingContext metadataBuildingContext;
    private GrailsPropertyCreator grailsPropertyCreator;


    public JdbcEnvironment getJdbcEnvironment() {
        return  metadataBuildingContext.getMetadataCollector().getDatabase().getJdbcEnvironment();
    }

    public GrailsDomainBinder(String dataSourceName
                            , String sessionFactoryName
                            , HibernateMappingContext hibernateMappingContext
                            , ClassBinder classBinding) {
        this.sessionFactoryName = sessionFactoryName;
        this.dataSourceName = dataSourceName;
        this.hibernateMappingContext = hibernateMappingContext;
        this.classBinding = classBinding;
        // pre-build mappings
        for (PersistentEntity persistentEntity : hibernateMappingContext.getPersistentEntities()) {
            evaluateMapping(persistentEntity);
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
    public void contribute(InFlightMetadataCollector metadataCollector, IndexView jandexIndex) {

        this.metadataBuildingContext = new MetadataBuildingContextRootImpl(
                "default",
                metadataCollector.getBootstrapContext(),
                metadataCollector.getMetadataBuildingOptions(),
                metadataCollector
        );
        this.grailsPropertyCreator = new GrailsPropertyCreator(metadataCollector, new PropertyBinder());

        filterHibernateEntities(hibernateMappingContext.getHibernatePersistentEntities())
                    .forEach(hibernatePersistentEntity -> bindRoot(hibernatePersistentEntity, metadataCollector, sessionFactoryName));

    }

    private List<HibernatePersistentEntity> filterHibernateEntities(java.util.Collection<HibernatePersistentEntity> persistentEntities) {
        return persistentEntities.stream()
                .filter(this::isNotAnnotatedEntity)
                .filter(this::usesConnectionSource)
                .filter(HibernatePersistentEntity::isRoot).toList();
    }

    private boolean usesConnectionSource(HibernatePersistentEntity persistentEntity) {
        return ConnectionSourcesSupport.usesConnectionSource(persistentEntity, dataSourceName);
    }

    private boolean isNotAnnotatedEntity(HibernatePersistentEntity persistentEntity) {
        return !persistentEntity.getJavaClass().isAnnotationPresent(Entity.class);
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

    private void bindMapSecondPass(ToMany property, InFlightMetadataCollector mappings,
                                     Map<?, ?> persistentClasses, org.hibernate.mapping.Map map, String sessionFactoryBeanName) {
        bindCollectionSecondPass(property, mappings, persistentClasses, map, sessionFactoryBeanName);

        SimpleValue value = new BasicValue(metadataBuildingContext, map.getCollectionTable());

        String type = getIndexColumnType(property, STRING_TYPE);
        String columnName1 = getIndexColumnName(property, sessionFactoryBeanName);
        new SimpleValueColumnBinder().bindSimpleValue(value, type, columnName1, true);
        PropertyConfig mappedForm = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
        if (mappedForm.getIndexColumn() != null) {
            Column column = new SimpleValueColumnFetcher().getColumnForSimpleValue(value);
            ColumnConfig columnConfig = getSingleColumnConfig(mappedForm.getIndexColumn());
            new ColumnConfigToColumnBinder().bindColumnConfigToColumn(column, columnConfig, mappedForm);
        }

        if (!value.isTypeSpecified()) {
            throw new MappingException("map index element must specify a type: " + map.getRole());
        }
        map.setIndex(value);

        if(!(property instanceof org.grails.datastore.mapping.model.types.OneToMany) && !(property instanceof ManyToMany)) {

            SimpleValue elt = new BasicValue(metadataBuildingContext, map.getCollectionTable());
            map.setElement(elt);

            Mapping mapping = new HibernateEntityWrapper().getMappedForm(property.getOwner());
            String typeName = new TypeNameProvider().getTypeName(property, mapping);
            if (typeName == null ) {

                if(property instanceof Basic) {
                    Basic basic = (Basic) property;
                    typeName = basic.getComponentType().getName();
                }
            }
            if(typeName == null || typeName.equals(Object.class.getName())) {
                typeName = StandardBasicTypes.STRING.getName();
            }
            String columnName = getMapElementName(property, sessionFactoryBeanName);
            new SimpleValueColumnBinder().bindSimpleValue(elt, typeName, columnName, false);

            elt.setTypeName(typeName);
        }

        map.setInverse(false);
    }

    private ColumnConfig getSingleColumnConfig(PropertyConfig propertyConfig) {
        if (propertyConfig != null) {
            List<ColumnConfig> columns = propertyConfig.getColumns();
            if (columns != null && !columns.isEmpty()) {
                return columns.get(0);
            }
        }
        return null;
    }

    /**
     * @TODO NOT TESTED
     * @param property
     * @param mappings
     * @param persistentClasses
     * @param list
     * @param sessionFactoryBeanName
     */
    private void bindListSecondPass(ToMany property, InFlightMetadataCollector mappings,
                                      Map<?, ?> persistentClasses, org.hibernate.mapping.List list, String sessionFactoryBeanName) {

        bindCollectionSecondPass(property, mappings, persistentClasses, list, sessionFactoryBeanName);

        String columnName = getIndexColumnName(property, sessionFactoryBeanName);
        final boolean isManyToMany = property instanceof ManyToMany;

        if (isManyToMany && !property.isOwningSide()) {
            throw new MappingException("Invalid association [" + property +
                    "]. List collection types only supported on the owning side of a many-to-many relationship.");
        }

        Table collectionTable = list.getCollectionTable();
        SimpleValue iv = new BasicValue(metadataBuildingContext, collectionTable);
        new SimpleValueColumnBinder().bindSimpleValue(iv, "integer", columnName, true);
        iv.setTypeName("integer");
        list.setIndex(iv);
        list.setBaseIndex(0);
        list.setInverse(false);

        Value v = list.getElement();
        v.createForeignKey();

        if (property.isBidirectional()) {

            String entityName;
            Value element = list.getElement();
            if (element instanceof ManyToOne) {
                ManyToOne manyToOne = (ManyToOne) element;
                entityName = manyToOne.getReferencedEntityName();
            } else {
                entityName = ((OneToMany) element).getReferencedEntityName();
            }

            PersistentClass referenced = mappings.getEntityBinding(entityName);

            Class<?> mappedClass = referenced.getMappedClass();
            Mapping m = getMapping(mappedClass);

            boolean compositeIdProperty = m.isCompositeIdProperty(property.getInverseSide());
            if (!compositeIdProperty) {
                Backref prop = new Backref();
                final PersistentEntity owner = property.getOwner();
                prop.setEntityName(owner.getName());
                String s2 = property.getName();
                prop.setName(UNDERSCORE + new BackticksRemover().apply(owner.getJavaClass().getSimpleName()) + UNDERSCORE + new BackticksRemover().apply(s2) + "Backref");
                prop.setSelectable(false);
                prop.setUpdateable(false);
                if (isManyToMany) {
                    prop.setInsertable(false);
                }
                prop.setCollectionRole(list.getRole());
                prop.setValue(list.getKey());

                DependantValue value = (DependantValue) prop.getValue();
                if (!property.isCircular()) {
                    value.setNullable(false);
                }
                value.setUpdateable(true);
                prop.setOptional(false);

                referenced.addProperty(prop);
            }

            if ((!list.getKey().isNullable() && !list.isInverse()) || compositeIdProperty) {
                IndexBackref ib = new IndexBackref();
                ib.setName(UNDERSCORE + property.getName() + "IndexBackref");
                ib.setUpdateable(false);
                ib.setSelectable(false);
                if (isManyToMany) {
                    ib.setInsertable(false);
                }
                ib.setCollectionRole(list.getRole());
                ib.setEntityName(list.getOwner().getEntityName());
                ib.setValue(list.getIndex());
                referenced.addProperty(ib);
            }
        }
    }

    private void bindCollectionSecondPass(ToMany property, InFlightMetadataCollector mappings,
                                            Map<?, ?> persistentClasses, Collection collection, String sessionFactoryBeanName) {

        PersistentClass associatedClass = null;

        if (LOG.isDebugEnabled())
            LOG.debug("Mapping collection: "
                    + collection.getRole()
                    + " -> "
                    + collection.getCollectionTable().getName());

        PropertyConfig propConfig = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);

        PersistentEntity referenced = property.getAssociatedEntity();
        if (StringUtils.hasText(propConfig.getSort())) {
            if (!property.isBidirectional() && (property instanceof org.grails.datastore.mapping.model.types.OneToMany)) {
                throw new DatastoreConfigurationException("Default sort for associations ["+property.getOwner().getName()+"->" + property.getName() +
                        "] are not supported with unidirectional one to many relationships.");
            }
            if (referenced != null) {
                PersistentProperty propertyToSortBy = referenced.getPropertyByName(propConfig.getSort());

                String associatedClassName = property.getAssociatedEntity().getName();

                associatedClass = (PersistentClass) persistentClasses.get(associatedClassName);
                if (associatedClass != null) {
                    collection.setOrderBy(buildOrderByClause(propertyToSortBy.getName(), associatedClass, collection.getRole(),
                            propConfig.getOrder() != null ? propConfig.getOrder() : "asc"));
                }
            }
        }

        // Configure one-to-many
        if (collection.isOneToMany()) {

            Mapping m = getRootMapping(referenced);
            boolean tablePerSubclass = m != null && !m.getTablePerHierarchy();

            if (referenced != null && !referenced.isRoot() && !tablePerSubclass) {
                Mapping rootMapping = getRootMapping(referenced);
                String discriminatorColumnName = RootClass.DEFAULT_DISCRIMINATOR_COLUMN_NAME;

                if (rootMapping != null) {
                    DiscriminatorConfig discriminatorConfig = rootMapping.getDiscriminator();
                    if(discriminatorConfig != null) {
                        final ColumnConfig discriminatorColumn = discriminatorConfig.getColumn();
                        if (discriminatorColumn != null) {
                            discriminatorColumnName = discriminatorColumn.getName();
                        }
                        if (discriminatorConfig.getFormula() != null) {
                            discriminatorColumnName = discriminatorConfig.getFormula();
                        }
                    }
                }
                //NOTE: this will build the set for the in clause if it has sublcasses
                Set<String> discSet = buildDiscriminatorSet((HibernatePersistentEntity) referenced);
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
            if (shouldBindCollectionWithForeignKey(property)) {
                collection.setCollectionTable(associatedClass.getTable());
            }

            new CollectionForPropertyConfigBinder().bindCollectionForPropertyConfig(collection, propConfig);
        }

        final boolean isManyToMany = property instanceof ManyToMany;
        if(referenced != null && !isManyToMany && referenced.isMultiTenant()) {
            String filterCondition = getMultiTenantFilterCondition(sessionFactoryBeanName, referenced);
            if(filterCondition != null) {
                if (property.isUnidirectionalOneToMany()) {
                    collection.addManyToManyFilter(GormProperties.TENANT_IDENTITY, filterCondition, true, Collections.emptyMap(), Collections.emptyMap());
                } else {
                    collection.addFilter(GormProperties.TENANT_IDENTITY, filterCondition, true, Collections.emptyMap(), Collections.emptyMap());
                }
            }
        }

        if (isSorted(property)) {
            collection.setSorted(true);
        }

        // setup the primary key references
        DependantValue key = createPrimaryKeyValue(mappings, property, collection, persistentClasses);

        // link a bidirectional relationship
        if (property.isBidirectional()) {
            Association otherSide = property.getInverseSide();
            if ((otherSide instanceof org.grails.datastore.mapping.model.types.ToOne) && shouldBindCollectionWithForeignKey(property)) {
                linkBidirectionalOneToMany(collection, associatedClass, key, otherSide);
            } else if ((otherSide instanceof ManyToMany) || Map.class.isAssignableFrom(property.getType())) {
                bindDependentKeyValue(property, key, mappings, sessionFactoryBeanName);
            }
        } else {
            if (propConfig.hasJoinKeyMapping()) {
                String columnName = propConfig.getJoinTable().getKey().getName();
                new SimpleValueColumnBinder().bindSimpleValue(key, "long", columnName, false);
            } else {
                bindDependentKeyValue(property, key, mappings, sessionFactoryBeanName);
            }
        }
        collection.setKey(key);

        // get cache config
        if (propConfig != null) {
            CacheConfig cacheConfig = propConfig.getCache();
            if (cacheConfig != null) {
                collection.setCacheConcurrencyStrategy(cacheConfig.getUsage());
            }
        }

        // if we have a many-to-many
        if (isManyToMany || isBidirectionalOneToManyMap(property)) {
            PersistentProperty otherSide = property.getInverseSide();

            if (property.isBidirectional()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("[GrailsDomainBinder] Mapping other side " + otherSide.getOwner().getName() + "." + otherSide.getName() + " -> " + collection.getCollectionTable().getName() + " as ManyToOne");
                ManyToOne element = new ManyToOne(metadataBuildingContext, collection.getCollectionTable());
                bindManyToMany((Association)otherSide, element, mappings, sessionFactoryBeanName);
                collection.setElement(element);
                new CollectionForPropertyConfigBinder().bindCollectionForPropertyConfig(collection, propConfig);
                if (property.isCircular()) {
                    collection.setInverse(false);
                }
            } else {
                // TODO support unidirectional many-to-many
            }
        } else if (new ShouldCollectionBindWithJoinColumn().apply(property)) {
            bindCollectionWithJoinTable(property, mappings, collection, propConfig, sessionFactoryBeanName);

        } else if (ofNullable(property).map(PersistentProperty::isUnidirectionalOneToMany).orElse(false)) {
            // for non-inverse one-to-many, with a not-null fk, add a backref!
            // there are problems with list and map mappings and join columns relating to duplicate key constraints
            // TODO change this when HHH-1268 is resolved
            bindUnidirectionalOneToMany((org.grails.datastore.mapping.model.types.OneToMany) property, mappings, collection);
        }
    }

    private String getMultiTenantFilterCondition(String sessionFactoryBeanName, PersistentEntity referenced) {
        TenantId tenantId = referenced.getTenantId();
        if(tenantId != null) {

            String defaultColumnName = new DefaultColumnNameFetcher(getNamingStrategy()).getDefaultColumnName(tenantId);
            return ":tenantId = " + defaultColumnName;
        }
        else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String buildOrderByClause(String hqlOrderBy, PersistentClass associatedClass, String role, String defaultOrder) {
        String orderByString = null;
        if (hqlOrderBy != null) {
            List<String> properties = new ArrayList<>();
            List<String> ordering = new ArrayList<>();
            StringBuilder orderByBuffer = new StringBuilder();
            if (hqlOrderBy.length() == 0) {
                //order by id
                Iterator<?> it = associatedClass.getIdentifier().getSelectables().iterator();
                while (it.hasNext()) {
                    Selectable col = (Selectable) it.next();
                    orderByBuffer.append(col.getText()).append(" asc").append(", ");
                }
            }
            else {
                StringTokenizer st = new StringTokenizer(hqlOrderBy, " ,", false);
                String currentOrdering = defaultOrder;
                //FIXME make this code decent
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    if (isNonPropertyToken(token)) {
                        if (currentOrdering != null) {
                            throw new DatastoreConfigurationException(
                                    "Error while parsing sort clause: " + hqlOrderBy
                                            + " (" + role + ")"
                            );
                        }
                        currentOrdering = token;
                    }
                    else {
                        //Add ordering of the previous
                        if (currentOrdering == null) {
                            //default ordering
                            ordering.add("asc");
                        }
                        else {
                            ordering.add(currentOrdering);
                            currentOrdering = null;
                        }
                        properties.add(token);
                    }
                }
                ordering.remove(0); //first one is the algorithm starter
                // add last one ordering
                if (currentOrdering == null) {
                    //default ordering
                    ordering.add(defaultOrder);
                }
                else {
                    ordering.add(currentOrdering);
                    currentOrdering = null;
                }
                int index = 0;

                for (String property : properties) {
                    Property p = BinderHelper.findPropertyByName(associatedClass, property);
                    if (p == null) {
                        throw new DatastoreConfigurationException(
                                "property from sort clause not found: "
                                        + associatedClass.getEntityName() + "." + property
                        );
                    }
                    PersistentClass pc = p.getPersistentClass();
                    String table;
                    if (pc == null) {
                        table = "";
                    }

                    else if (pc == associatedClass
                            || (associatedClass instanceof SingleTableSubclass &&
                            pc.getMappedClass().isAssignableFrom(associatedClass.getMappedClass()))) {
                        table = "";
                    } else {
                        table = pc.getTable().getQuotedName() + ".";
                    }

                    Iterator<?> propertyColumns = p.getSelectables().iterator();
                    while (propertyColumns.hasNext()) {
                        Selectable column = (Selectable) propertyColumns.next();
                        orderByBuffer.append(table)
                                .append(column.getText())
                                .append(" ")
                                .append(ordering.get(index))
                                .append(", ");
                    }
                    index++;
                }
            }
            orderByString = orderByBuffer.substring(0, orderByBuffer.length() - 2);
        }
        return orderByString;
    }

    private boolean isNonPropertyToken(String token) {
        if (" ".equals(token)) return true;
        if (",".equals(token)) return true;
        if (token.equalsIgnoreCase("desc")) return true;
        if (token.equalsIgnoreCase("asc")) return true;
        return false;
    }

    private Set<String> buildDiscriminatorSet(HibernatePersistentEntity domainClass) {
        Set<String> theSet = new HashSet<>();

        Mapping mapping = domainClass.getMapping().getMappedForm();
        String discriminator = domainClass.getName();
        if (mapping != null && mapping.getDiscriminator() != null) {
            DiscriminatorConfig discriminatorConfig = mapping.getDiscriminator();
            if(discriminatorConfig.getValue() != null) {
                discriminator = discriminatorConfig.getValue();
            }
        }
        Mapping rootMapping = getRootMapping(domainClass);
        String quote = "'";
        if (rootMapping != null && rootMapping.getDatasources() != null) {
            DiscriminatorConfig discriminatorConfig = rootMapping.getDiscriminator();
            if(discriminatorConfig != null && discriminatorConfig.getType() != null && !discriminatorConfig.getType().equals("string"))
                quote = "";
        }
        theSet.add(quote + discriminator + quote);

        final java.util.Collection<PersistentEntity> childEntities = domainClass.getMappingContext().getDirectChildEntities(domainClass);
        for (PersistentEntity subClass : childEntities) {
            theSet.addAll(buildDiscriminatorSet((HibernatePersistentEntity) subClass));
        }
        return theSet;
    }

    private Mapping getRootMapping(PersistentEntity referenced) {
        return Optional.of(referenced)
                .map(PersistentEntity::getRootEntity)
                .filter(HibernatePersistentEntity.class::isInstance)
                .map(HibernatePersistentEntity.class::cast)
                .map(HibernatePersistentEntity::getMappedForm)
                .orElse(null);
    }

    private boolean isBidirectionalOneToManyMap(Association property) {
        return Map.class.isAssignableFrom(property.getType()) && property.isBidirectional();
    }

    private void bindCollectionWithJoinTable(ToMany property,
                                               InFlightMetadataCollector mappings, Collection collection, PropertyConfig config, String sessionFactoryBeanName) {

//        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);

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
        collection.setInverse(false);

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
                var clazz = getNamingStrategy().resolveColumnName(className);
                var prop = getNamingStrategy().resolveTableName(property.getName());
                columnName = isEnum ? clazz : new BackticksRemover().apply(prop) + UNDERSCORE + new BackticksRemover().apply(clazz);
            }

            if (isEnum) {
                new EnumTypeBinder().bindEnumType(property, referencedType, element, columnName);
            }
            else {

                Mapping mapping =  new HibernateEntityWrapper().getMappedForm(property.getOwner());;
                String typeName = new TypeNameProvider().getTypeName(property, mapping);
                if (typeName == null) {
                    Type type = mappings.getTypeConfiguration().getBasicTypeRegistry().getRegisteredType(className);
                    if (type != null) {
                        typeName = type.getName();
                    }
                }
                if (typeName == null) {
                    String domainName = property.getOwner().getName();
                    throw new MappingException("Missing type or column for column["+columnName+"] on domain["+domainName+"] referencing["+className+"]");
                }

                new SimpleValueColumnBinder().bindSimpleValue(element, typeName, columnName, true);
                if (joinColumnMappingOptional.isPresent()) {
                    Column column = new SimpleValueColumnFetcher().getColumnForSimpleValue(element);
                    ColumnConfig columnConfig = joinColumnMappingOptional.get();
                    final PropertyConfig mappedForm = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
                    new ColumnConfigToColumnBinder().bindColumnConfigToColumn(column, columnConfig, mappedForm);
                }
            }
        } else {
            final PersistentEntity domainClass = property.getAssociatedEntity();

            Mapping m = new HibernateEntityWrapper().getMappedForm(domainClass);
            if (m.hasCompositeIdentifier()) {
                CompositeIdentity ci = (CompositeIdentity) m.getIdentity();
                new CompositeIdentifierToManyToOneBinder(namingStrategy).bindCompositeIdentifierToManyToOne(property, element, ci, domainClass, EMPTY_PATH);
            }
            else {
                if (joinColumnMappingOptional.isPresent()) {
                    columnName = joinColumnMappingOptional.get().getName();
                }
                else {
                    var decapitalize = domainClass.getName();
                    columnName = getNamingStrategy().resolveColumnName(decapitalize) + FOREIGN_KEY_SUFFIX;
                }

                new SimpleValueColumnBinder().bindSimpleValue(element, "long", columnName, true);
            }
        }

        collection.setElement(element);

        new CollectionForPropertyConfigBinder().bindCollectionForPropertyConfig(collection, config);
    }

    /**
     * @param property The property to bind
     * @param manyToOne The inverse side
     */
    private void bindUnidirectionalOneToManyInverseValues(ToMany property, ManyToOne manyToOne) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
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
        manyToOne.setReferencedEntityName(property.getAssociatedEntity().getName());
    }

    /**
     * Binds the primary key value column
     *
     * @param property The property
     * @param key      The key
     * @param mappings The mappings
     * @param sessionFactoryBeanName The name of the session factory
     */
    private void bindDependentKeyValue(PersistentProperty property, DependantValue key,
                                         InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsDomainBinder] binding  [" + property.getName() + "] with dependant key");
        }

        PersistentEntity refDomainClass = property.getOwner();
        final Mapping mapping = new HibernateEntityWrapper().getMappedForm(refDomainClass);
        boolean hasCompositeIdentifier = mapping.hasCompositeIdentifier();
        if ((new ShouldCollectionBindWithJoinColumn().apply((ToMany) property) && hasCompositeIdentifier) ||
                (hasCompositeIdentifier && ( property instanceof ManyToMany))) {
            CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
            new CompositeIdentifierToManyToOneBinder(namingStrategy).bindCompositeIdentifierToManyToOne((Association) property, key, ci, refDomainClass, EMPTY_PATH);
        }
        else {
            // set type
            new SimpleValueBinder(namingStrategy).bindSimpleValue(property, null, key, EMPTY_PATH);
        }
    }

    /**
     * Creates the DependentValue object that forms a primary key reference for the collection.
     *
     * @param mappings
     * @param property          The grails property
     * @param collection        The collection object
     * @param persistentClasses
     * @return The DependantValue (key)
     */
    private DependantValue createPrimaryKeyValue(InFlightMetadataCollector mappings, PersistentProperty property,
                                                   Collection collection, Map<?, ?> persistentClasses) {
        KeyValue keyValue;
        DependantValue key;
        String propertyRef = collection.getReferencedPropertyName();
        // this is to support mapping by a property
        if (propertyRef == null) {
            keyValue = collection.getOwner().getIdentifier();
        } else {
            keyValue = (KeyValue) collection.getOwner().getProperty(propertyRef).getValue();
        }

        if (LOG.isDebugEnabled())
            LOG.debug("[GrailsDomainBinder] creating dependant key value  to table [" + keyValue.getTable().getName() + "]");

        key = new DependantValue(metadataBuildingContext, collection.getCollectionTable(), keyValue);

        key.setTypeName(null);
        // make nullable and non-updateable
        key.setNullable(true);
        key.setUpdateable(false);
        //JPA now requires to check for sorting
        key.setSorted(collection.isSorted());
        return key;
    }

    /**
     * Binds a unidirectional one-to-many creating a psuedo back reference property in the process.
     *
     * @param property
     * @param mappings
     * @param collection
     */
    private void bindUnidirectionalOneToMany(org.grails.datastore.mapping.model.types.OneToMany property, InFlightMetadataCollector mappings, Collection collection) {
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
        PersistentEntity owner = property.getOwner();
        prop.setEntityName(owner.getName());
        String s2 = property.getName();
        prop.setName(UNDERSCORE + new BackticksRemover().apply(owner.getJavaClass().getSimpleName()) + UNDERSCORE + new BackticksRemover().apply(s2) + "Backref");
        prop.setUpdateable(false);
        prop.setInsertable(true);
        prop.setCollectionRole(collection.getRole());
        prop.setValue(collection.getKey());
        prop.setOptional(true);

        referenced.addProperty(prop);
    }

    private Property getProperty(PersistentClass associatedClass, String propertyName) throws MappingException {
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

    /**
     * Links a bidirectional one-to-many, configuring the inverse side and using a column copy to perform the link
     *
     * @param collection      The collection one-to-many
     * @param associatedClass The associated class
     * @param key             The key
     * @param otherSide       The other side of the relationship
     */
    private void linkBidirectionalOneToMany(Collection collection, PersistentClass associatedClass, DependantValue key, PersistentProperty otherSide) {
        collection.setInverse(true);

        // Iterator mappedByColumns = associatedClass.getProperty(otherSide.getName()).getValue().getColumnIterator();
        Iterator<?> mappedByColumns = getProperty(associatedClass, otherSide.getName()).getValue().getColumns().iterator();
        while (mappedByColumns.hasNext()) {
            Column column = (Column) mappedByColumns.next();
            linkValueUsingAColumnCopy(otherSide, column, key);
        }
    }

    /**
     * Establish whether a collection property is sorted
     *
     * @param property The property
     * @return true if sorted
     */
    private boolean isSorted(PersistentProperty property) {
        return SortedSet.class.isAssignableFrom(property.getType());
    }

    /**
     * Binds a many-to-many relationship. A many-to-many consists of
     * - a key (a DependentValue)
     * - an element
     *
     * The element is a ManyToOne from the association table to the target entity
     *
     * @param property The grails property
     * @param element  The ManyToOne element
     * @param mappings The mappings
     */
    private void bindManyToMany(Association property, ManyToOne element,
                                  InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        new ManyToOneBinder(namingStrategy).bindManyToOne(property, element, EMPTY_PATH);
        element.setReferencedEntityName(property.getOwner().getName());
    }

    private void linkValueUsingAColumnCopy(PersistentProperty prop, Column column, DependantValue key) {
        Column mappingColumn = new Column();
        mappingColumn.setName(column.getName());
        mappingColumn.setLength(column.getLength());
        mappingColumn.setNullable(prop.isNullable());
        mappingColumn.setSqlType(column.getSqlType());

        mappingColumn.setValue(key);
        key.addColumn(mappingColumn);
        key.getTable().addColumn(mappingColumn);
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
    public void bindCollection(ToMany property, Collection collection,
                               PersistentClass owner, InFlightMetadataCollector mappings, String path, String sessionFactoryBeanName) {

        // set role
        String propertyName = getNameForPropertyAndPath(property, path);
        collection.setRole(qualify(property.getOwner().getName(), propertyName));

        PropertyConfig pc = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
        // configure eager fetching
        final FetchMode fetchMode = pc.getFetchMode();
        if (fetchMode == FetchMode.JOIN) {
            collection.setFetchMode(FetchMode.JOIN);
        }
        else if (pc.getFetchMode() != null) {
            collection.setFetchMode(pc.getFetchMode());
        }
        else {
            collection.setFetchMode(FetchMode.DEFAULT);
        }

        if (pc.getCascade() != null) {
            collection.setOrphanDelete(pc.getCascade().equals(CASCADE_ALL_DELETE_ORPHAN));
        }
        // if it's a one-to-many mapping
        if (shouldBindCollectionWithForeignKey(property)) {
            OneToMany oneToMany = new OneToMany(metadataBuildingContext, collection.getOwner());
            collection.setElement(oneToMany);
            bindOneToMany((org.grails.datastore.mapping.model.types.OneToMany) property, oneToMany, mappings);
        } else {
            bindCollectionTable(property, mappings, collection, owner.getTable(), sessionFactoryBeanName);

            if (!property.isOwningSide()) {
                collection.setInverse(true);
            }
        }

        if (pc.getBatchSize() != null) {
            collection.setBatchSize(pc.getBatchSize());
        }

        // set up second pass
        if (collection instanceof org.hibernate.mapping.Set) {
            mappings.addSecondPass(new GrailsCollectionSecondPass(property, mappings, collection, sessionFactoryBeanName));
        }
        else if (collection instanceof org.hibernate.mapping.List) {
            mappings.addSecondPass(new ListSecondPass(property, mappings, collection, sessionFactoryBeanName));
        }
        else if (collection instanceof org.hibernate.mapping.Map) {
            mappings.addSecondPass(new MapSecondPass(property, mappings, collection, sessionFactoryBeanName));
        }
        else { // Collection -> Bag
            mappings.addSecondPass(new GrailsCollectionSecondPass(property, mappings, collection, sessionFactoryBeanName));
        }
    }

    /*
     * We bind collections with foreign keys if specified in the mapping and only if
     * it is a unidirectional one-to-many that is.
     */
    private boolean shouldBindCollectionWithForeignKey(ToMany property) {
        return ((property instanceof org.grails.datastore.mapping.model.types.OneToMany) && property.isBidirectional() ||
                !(boolean) new ShouldCollectionBindWithJoinColumn().apply(property)) &&
                !Map.class.isAssignableFrom(property.getType()) &&
                !(property instanceof ManyToMany) &&
                !(property instanceof Basic);
    }

    private String getNameForPropertyAndPath(PersistentProperty property, String path) {
        if (GrailsHibernateUtil.isNotEmpty(path)) {
            return qualify(path, property.getName());
        }
        return property.getName();
    }

    private void bindCollectionTable(ToMany property, InFlightMetadataCollector mappings,
                                       Collection collection, Table ownerTable, String sessionFactoryBeanName) {

        String owningTableSchema = ownerTable.getSchema();
        PropertyConfig config = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
        JoinTable jt = config.getJoinTable();

        String s = new TableForManyCalculator(namingStrategy).calculateTableForMany(property, sessionFactoryBeanName);
        String tableName = (jt != null && jt.getName() != null ? jt.getName() : getNamingStrategy().resolveTableName(s));

        String schemaName = new NamespaceNameExtractor().getSchemaName(mappings);
        String catalogName = new NamespaceNameExtractor().getCatalogName(mappings);
        if(jt != null) {
            if(jt.getSchema() != null) {
                schemaName = jt.getSchema();
            }
            if(jt.getCatalog() != null) {
                catalogName = jt.getCatalog();
            }
        }

        if(schemaName == null && owningTableSchema != null) {
            schemaName = owningTableSchema;
        }

        collection.setCollectionTable(mappings.addTable(
                schemaName, catalogName,
                tableName, null, false, metadataBuildingContext));
    }


    private PhysicalNamingStrategy getPhysicalNamingStrategy(String sessionFactoryBeanName) {
        return NAMING_STRATEGY_PROVIDER.getPhysicalNamingStrategy(sessionFactoryBeanName);
    }




    /**
     * Binds a Grails domain class to the Hibernate runtime meta model
     *
     * @param entity The domain class to bind
     * @param mappings    The existing mappings
     * @param sessionFactoryBeanName  the session factory bean name
     * @throws MappingException Thrown if the domain class uses inheritance which is not supported
     */
    public void bindClass(PersistentEntity entity, InFlightMetadataCollector mappings, String sessionFactoryBeanName)
            throws MappingException {
        //if (domainClass.getClazz().getSuperclass() == Object.class) {
        if (entity.isRoot()) {
            bindRoot((HibernatePersistentEntity) entity, mappings, sessionFactoryBeanName);
        }
    }

    public void evaluateMapping(PersistentEntity persistentEntity) {
        Optional.ofNullable(persistentEntity).ifPresent(domainClass -> {
            try {
                final Mapping m = new HibernateEntityWrapper().getMappedForm(domainClass);
                trackCustomCascadingSaves(m, domainClass.getPersistentProperties());
                AbstractGrailsDomainBinder.cacheMapping(domainClass.getJavaClass(), m);
            } catch (Exception e) {
                throw new DatastoreConfigurationException("Error evaluating ORM mappings block for domain [" +
                        domainClass.getName() + "]:  " + e.getMessage(), e);
            }
        });

    }

    /**
     * Checks for any custom cascading saves set up via the mapping DSL and records them within the persistent property.
     * @param mapping The Mapping.
     * @param persistentProperties The persistent properties of the domain class.
     */
    private void trackCustomCascadingSaves(Mapping mapping, Iterable<PersistentProperty> persistentProperties) {
        for (PersistentProperty property : persistentProperties) {
            PropertyConfig propConf = mapping.getPropertyConfig(property.getName());

            if (propConf != null && propConf.getCascade() != null) {
                propConf.setExplicitSaveUpdateCascade(isSaveUpdateCascade(propConf.getCascade()));
            }
        }
    }

    /**
     * Check if a save-update cascade is defined within the Hibernate cascade properties string.
     * @param cascade The string containing the cascade properties.
     * @return True if save-update or any other cascade property that encompasses those is present.
     */
    private boolean isSaveUpdateCascade(String cascade) {
        String[] cascades = cascade.split(",");

        for (String cascadeProp : cascades) {
            String trimmedProp = cascadeProp.trim();

            if (CASCADE_SAVE_UPDATE.equals(trimmedProp) || CASCADE_ALL.equals(trimmedProp) || CASCADE_ALL_DELETE_ORPHAN.equals(trimmedProp)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Obtains a mapping object for the given domain class nam
     *
     * @param theClass The domain class in question
     * @return A Mapping object or null
     */
    private static Mapping getMapping(Class<?> theClass) {
        return Optional.ofNullable(AbstractGrailsDomainBinder.getMapping(theClass)).orElseThrow();
    }


    public static void clearMappingCache() {
        AbstractGrailsDomainBinder.clearMappingCache();
    }

    public static void clearMappingCache(Class<?> theClass) {
        // no-op, here for compatibility
    }

    /**
     * Binds a root class (one with no super classes) to the runtime meta model
     * based on the supplied Grails domain class
     *
     * @param entity The Grails domain class
     * @param mappings    The Hibernate Mappings object
     * @param sessionFactoryBeanName  the session factory bean name
     */
    protected void bindRoot(HibernatePersistentEntity entity, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        if (mappings.getEntityBinding(entity.getName()) != null) {
            LOG.info("[GrailsDomainBinder] Class [" + entity.getName() + "] is already mapped, skipping.. ");
            return;
        }
        RootClass root = new RootClass(this.metadataBuildingContext);
        root.setAbstract(entity.isAbstract());
        classBinding.bindClass(entity, root, mappings);
        bindRootPersistentClassCommonValues(entity, root, mappings, sessionFactoryBeanName);

        var children = entity.getMappingContext()
                .getDirectChildEntities(entity);

        if (children.isEmpty()) {
            root.setPolymorphic(false);
        } else {
            root.setPolymorphic(true);
            Mapping m = new HibernateEntityWrapper().getMappedForm(entity);
            boolean tablePerSubclass = !m.getTablePerHierarchy();
            if (!tablePerSubclass) {
                // if the root class has children create a discriminator property

                bindDiscriminatorProperty(root.getTable(), root, m);
            }
            // bind the sub classes
            bindSubClasses(entity, root, mappings, sessionFactoryBeanName, m);
        }

        addMultiTenantFilterIfNecessary(entity, root, mappings, sessionFactoryBeanName);

        mappings.addEntityBinding(root);
    }

    public PersistentEntityNamingStrategy getNamingStrategy() {
        if (namingStrategy == null) {
            namingStrategy = new NamingStrategyWrapper(getPhysicalNamingStrategy(sessionFactoryName), getJdbcEnvironment());
        }
        return namingStrategy;
    }

    /**
     * Add a Hibernate filter for multitenancy if the persistent class is multitenant
     *
     * @param entity target persistent entity for get tenant information
     * @param persistentClass persistent class for add the filter and get tenant property info
     * @param mappings mappings to add the filter
     * @param sessionFactoryBeanName the session factory bean name
     */
    private void addMultiTenantFilterIfNecessary(
            HibernatePersistentEntity entity, PersistentClass persistentClass,
            InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        if (entity.isMultiTenant()) {
            TenantId tenantId = entity.getTenantId();

            if (tenantId != null) {
                String filterCondition = getMultiTenantFilterCondition(sessionFactoryBeanName, entity);

                persistentClass.addFilter(
                        GormProperties.TENANT_IDENTITY,
                        filterCondition,
                        true,
                        Collections.emptyMap(),
                        Collections.emptyMap()
                );

                Property property = getProperty(persistentClass, tenantId.getName());
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
     * Binds the sub classes of a root class using table-per-heirarchy inheritance mapping
     *
     * @param domainClass            The root domain class to bind
     * @param parent                 The parent class instance
     * @param mappings               The mappings instance
     * @param sessionFactoryBeanName the session factory bean name
     * @param m
     */
    private void bindSubClasses(HibernatePersistentEntity domainClass, PersistentClass parent,
                                InFlightMetadataCollector mappings, String sessionFactoryBeanName, Mapping m) {
        domainClass.getMappingContext()
                .getDirectChildEntities(domainClass)
                .stream()
                .filter(HibernatePersistentEntity.class::isInstance)
                .map(HibernatePersistentEntity.class::cast)
                .filter(this::usesConnectionSource)
                .filter(sub -> isChildEntity(sub, domainClass))
                .forEach( sub -> bindSubClass(sub, parent, mappings, sessionFactoryBeanName, m));

    }

    private boolean isChildEntity(HibernatePersistentEntity sub, HibernatePersistentEntity parent) {
        return sub.getJavaClass().getSuperclass().equals(parent.getJavaClass());
    }

    /**
     * Binds a sub class.
     *
     * @param sub      The sub domain class instance
     * @param parent   The parent persistent class instance
     * @param mappings The mappings instance
     * @param sessionFactoryBeanName  the session factory bean name
     */
    private void bindSubClass(HibernatePersistentEntity sub, PersistentClass parent,
                                InFlightMetadataCollector mappings, String sessionFactoryBeanName,Mapping m) {
        evaluateMapping(sub);
        Subclass subClass;
        boolean tablePerSubclass = m != null && !m.getTablePerHierarchy() && !m.isTablePerConcreteClass();
        boolean tablePerConcreteClass = m != null && m.isTablePerConcreteClass();
        final String fullName = sub.getName();
        if (tablePerSubclass) {
            subClass = new JoinedSubclass( parent, this.metadataBuildingContext);
        }
        else if(tablePerConcreteClass) {
            subClass = new UnionSubclass(parent, this.metadataBuildingContext);
        }
        else {
            subClass = new SingleTableSubclass(parent, this.metadataBuildingContext);
            // set the descriminator value as the name of the class. This is the
            // value used by Hibernate to decide what the type of the class is
            // to perform polymorphic queries
            Mapping subMapping = new HibernateEntityWrapper().getMappedForm(sub);
            DiscriminatorConfig discriminatorConfig = subMapping.getDiscriminator();

            subClass.setDiscriminatorValue(discriminatorConfig != null && discriminatorConfig.getValue() != null ? discriminatorConfig.getValue() : fullName);
            configureDerivedProperties(sub, subMapping);

        }
        Integer bs = (m == null) ? null : m.getBatchSize();
        if (bs != null) {
            subClass.setBatchSize(bs);
        }

        if (m != null && m.getDynamicUpdate()) {
            subClass.setDynamicUpdate(true);
        }
        if (m != null && m.getDynamicInsert()) {
            subClass.setDynamicInsert(true);
        }

        subClass.setCached(parent.isCached());

        subClass.setAbstract(sub.isAbstract());
        subClass.setEntityName(fullName);
        subClass.setJpaEntityName(unqualify(fullName));

        parent.addSubclass(subClass);
        mappings.addEntityBinding(subClass);

        if (tablePerSubclass) {
            bindJoinedSubClass(sub, (JoinedSubclass) subClass, mappings, m, sessionFactoryBeanName);
        }
        else if( tablePerConcreteClass) {
            bindUnionSubclass(sub, (UnionSubclass) subClass, mappings, sessionFactoryBeanName);
        }
        else {
            bindSubClass(sub, subClass, mappings, sessionFactoryBeanName);
        }

        addMultiTenantFilterIfNecessary(sub, subClass, mappings, sessionFactoryBeanName);

        final java.util.Collection<PersistentEntity> childEntities = sub.getMappingContext().getDirectChildEntities(sub);
        if (!childEntities.isEmpty()) {
            // bind the sub classes
            bindSubClasses(sub, subClass, mappings, sessionFactoryBeanName, m);
        }
    }


    private void bindUnionSubclass(HibernatePersistentEntity subClass, UnionSubclass unionSubclass,
                                  InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
        classBinding.bindClass(subClass, unionSubclass, mappings);

        Mapping subMapping = new HibernateEntityWrapper().getMappedForm(subClass);

        if ( unionSubclass.getEntityPersisterClass() == null ) {
            unionSubclass.getRootClass().setEntityPersisterClass(
                    UnionSubclassEntityPersister.class );
        }

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
    private void bindJoinedSubClass(HibernatePersistentEntity sub, JoinedSubclass joinedSubclass,
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
            HibernatePersistentEntity sub, PersistentClass model, Table denormalizedSuperTable,
            InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        String logicalTableName = unqualify(model.getEntityName());
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
    private void bindSubClass(HibernatePersistentEntity sub, Subclass subClass, InFlightMetadataCollector mappings,
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
        DiscriminatorConfig discriminatorConfig = someMapping != null ? someMapping.getDiscriminator() : null;

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
            new SimpleValueColumnBinder().bindSimpleValue(d, STRING_TYPE, RootClass.DEFAULT_DISCRIMINATOR_COLUMN_NAME, false);

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

    private void configureDerivedProperties(PersistentEntity domainClass, Mapping m) {
        domainClass.getPersistentProperties().forEach(new ConfigureDerivedPropertiesConsumer( m));
    }

    /*
     * Binds a persistent classes to the table representation and binds the class properties
     */
    private void bindRootPersistentClassCommonValues(HibernatePersistentEntity domainClass,
                                                       RootClass root, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        // get the schema and catalog names from the configuration
        Mapping m = new HibernateEntityWrapper().getMappedForm(domainClass);

        configureDerivedProperties(domainClass, m);
        CacheConfig cc = m.getCache();
        if (cc != null && cc.getEnabled()) {
            root.setCacheConcurrencyStrategy(cc.getUsage());
            root.setCached(true);
            if ("read-only".equals(cc.getUsage())) {
                root.setMutable(false);
            }
            root.setLazyPropertiesCacheable(!"non-lazy".equals(cc.getInclude()));
        }
        root.setBatchSize(ofNullable(m.getBatchSize()).orElse(0));
        root.setDynamicUpdate(m.getDynamicUpdate());
        root.setDynamicInsert(m.getDynamicInsert());


        var schema = ofNullable(m.getTable())
                .map(org.grails.orm.hibernate.cfg.Table::getSchema)
                .orElse(new NamespaceNameExtractor().getSchemaName(mappings));

        var catalog = ofNullable(m.getTable())
                .map(org.grails.orm.hibernate.cfg.Table::getCatalog)
                .orElse(new NamespaceNameExtractor().getCatalogName(mappings));


        var isAbstract = !m.getTablePerHierarchy() && m.isTablePerConcreteClass() && root.isAbstract();
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
        bindIdentity(domainClass, root, mappings, m, sessionFactoryBeanName);
        bindVersion(domainClass.getVersion(), root, mappings, sessionFactoryBeanName);
        root.createPrimaryKey();
        createClassProperties(domainClass, root, mappings, sessionFactoryBeanName);
    }



    private void bindIdentity(
            HibernatePersistentEntity domainClass,
            RootClass root,
            InFlightMetadataCollector mappings,
            Mapping gormMapping,
            String sessionFactoryBeanName) {

        PersistentProperty identifierProp = domainClass.getIdentity();
        if (gormMapping == null) {
            if(identifierProp != null) {
                bindSimpleId(identifierProp, root, mappings, null, sessionFactoryBeanName);
            }
            return;
        }

        Object id = gormMapping.getIdentity();
        if (id instanceof CompositeIdentity) {
            bindCompositeId(domainClass, root, (CompositeIdentity) id, mappings, sessionFactoryBeanName);
        } else {
            final Identity identity = (Identity) id;
            String propertyName = identity.getName();
            if (propertyName != null) {
                PersistentProperty namedIdentityProp = domainClass.getPropertyByName(propertyName);
                if (namedIdentityProp == null) {
                    throw new MappingException("Mapping specifies an identifier property name that doesn't exist ["+propertyName+"]");
                }
                if (!namedIdentityProp.equals(identifierProp)) {
                    identifierProp = namedIdentityProp;
                }
            }
            bindSimpleId(identifierProp, root, mappings, identity, sessionFactoryBeanName);
        }
    }

    private void bindCompositeId(PersistentEntity domainClass, RootClass root,
                                   CompositeIdentity compositeIdentity, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        HibernatePersistentEntity hibernatePersistentEntity = (HibernatePersistentEntity) domainClass;
        Component id = new Component(metadataBuildingContext, root);
        id.setNullValue("undefined");
        root.setIdentifier(id);
        root.setIdentifierMapper(id);
        root.setEmbeddedIdentifier(true);
        id.setComponentClassName(domainClass.getName());
        id.setKey(true);
        id.setEmbedded(true);

        String path = qualify(root.getEntityName(), "id");

        id.setRoleName(path);

        final PersistentProperty[] composite = hibernatePersistentEntity.getCompositeIdentity();
        for (PersistentProperty property : composite) {
            if (property == null) {
                throw new MappingException("Property referenced in composite-id mapping of class [" + domainClass.getName() +
                        "] is not a valid property!");
            }

            bindComponentProperty(id, null, property, root, "", root.getTable(), mappings, sessionFactoryBeanName);
        }
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
    private void createClassProperties(HibernatePersistentEntity domainClass, PersistentClass persistentClass,
                                         InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        Mapping gormMapping = new HibernateEntityWrapper().getMappedForm(domainClass);
        Table table = persistentClass.getTable();
        table.setComment(gormMapping.getComment());
        final List<PersistentProperty> persistentProperties = domainClass.getPersistentProperties()
                .stream()
                .filter(persistentProperty -> persistentProperty.getMappedForm() != null)
                .filter(persistentProperty -> !gormMapping.isCompositeIdProperty(persistentProperty))
                .filter(persistentProperty -> !gormMapping.isIdentityProperty(persistentProperty))
                .filter(persistentProperty -> !persistentProperty.getName().equals(GormProperties.VERSION) )
                .filter(persistentProperty -> !persistentProperty.isInherited())
                .toList();


        List<Embedded> embedded = new ArrayList<>();

        for (PersistentProperty<?> currentGrailsProp : persistentProperties) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("[GrailsDomainBinder] Binding persistent property [" + currentGrailsProp.getName() + "]");
            }

            Value value = null;

            // see if it's a collection type
            CollectionType collectionType = CT.collectionTypeForClass(currentGrailsProp.getType());

            Class<?> userType = getUserType(currentGrailsProp);

            if (userType != null && !UserCollectionType.class.isAssignableFrom(userType)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as SimpleValue");
                }
                value = new BasicValue(metadataBuildingContext, table);
                // set type
                new SimpleValueBinder(namingStrategy).bindSimpleValue(currentGrailsProp, null, (SimpleValue) value, EMPTY_PATH);
            }
            else if (collectionType != null) {
                String typeName = new TypeNameProvider().getTypeName(currentGrailsProp, gormMapping);
                if ("serializable".equals(typeName)) {
                    value = new BasicValue(metadataBuildingContext, table);
                    boolean nullable = currentGrailsProp.isNullable();
                    String columnName = new ColumnNameForPropertyAndPathFetcher(namingStrategy).getColumnNameForPropertyAndPath(currentGrailsProp, EMPTY_PATH, null);
                    new SimpleValueColumnBinder().bindSimpleValue((SimpleValue) value, typeName, columnName, nullable);
                }
                else {
                    // create collection
                    Collection collection = collectionType.create((ToMany) currentGrailsProp, persistentClass,
                            EMPTY_PATH, mappings, sessionFactoryBeanName);
                    mappings.addCollectionBinding(collection);
                    value = collection;
                }
            }
            else if (currentGrailsProp.getType().isEnum()) {
                value = new BasicValue(metadataBuildingContext, table);
                bindEnumType(currentGrailsProp, (SimpleValue) value, EMPTY_PATH, sessionFactoryBeanName);
            }
            else if(currentGrailsProp instanceof Association) {
                Association association = (Association) currentGrailsProp;
                if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.ManyToOne) {
                    if (LOG.isDebugEnabled())
                        LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as ManyToOne");

                    value = new ManyToOne(metadataBuildingContext, table);
                    new ManyToOneBinder(namingStrategy).bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, EMPTY_PATH);
                }
                else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.OneToOne && userType == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as OneToOne");
                    }

                    final boolean isHasOne = association.isHasOne();
                    if (isHasOne && !association.isBidirectional()) {
                        throw new MappingException("hasOne property [" + currentGrailsProp.getOwner().getName() +
                                "." + currentGrailsProp.getName() + "] is not bidirectional. Specify the other side of the relationship!");
                    }
                    else if (((Association) currentGrailsProp).canBindOneToOneWithSingleColumnAndForeignKey()) {
                        value = new OneToOne(metadataBuildingContext, table, persistentClass);
                        bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp, (OneToOne) value, EMPTY_PATH, sessionFactoryBeanName);
                    }
                    else {
                        if (isHasOne && association.isBidirectional()) {
                            value = new OneToOne(metadataBuildingContext, table, persistentClass);
                            bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp, (OneToOne) value, EMPTY_PATH, sessionFactoryBeanName);
                        }
                        else {
                            value = new ManyToOne(metadataBuildingContext, table);
                            new ManyToOneBinder(namingStrategy).bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, EMPTY_PATH);
                        }
                    }
                }
                else if (currentGrailsProp instanceof Embedded) {
                    embedded.add((Embedded)currentGrailsProp);
                    continue;
                }
            }
            // work out what type of relationship it is and bind value
            else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as SimpleValue");
                }
                value = new BasicValue(metadataBuildingContext, table);
                // set type
                new SimpleValueBinder(namingStrategy).bindSimpleValue(currentGrailsProp, null, (SimpleValue) value, EMPTY_PATH);
            }

            if (value != null) {
                Property property = createProperty(value, persistentClass, currentGrailsProp, mappings);
                persistentClass.addProperty(property);
            }
        }

        for (Embedded association : embedded) {
            Value value = new Component(metadataBuildingContext, persistentClass);

            bindComponent((Component) value, association, true, mappings, sessionFactoryBeanName);
            Property property = createProperty(value, persistentClass, association, mappings);
            persistentClass.addProperty(property);
        }
        new NaturalIdentifierBinder().bindNaturalIdentifier(gormMapping, persistentClass);
    }



    private void bindEnumType(PersistentProperty property, SimpleValue simpleValue,
                                String path, String sessionFactoryBeanName) {
        Class<?> propertyType = property.getType();
        String columnName = new ColumnNameForPropertyAndPathFetcher(namingStrategy).getColumnNameForPropertyAndPath(property, path, null);
        new EnumTypeBinder().bindEnumType(property, propertyType, simpleValue, columnName);
    }

    private Class<?> getUserType(PersistentProperty currentGrailsProp) {
        Class<?> userType = null;
        PropertyConfig config = new PersistentPropertyToPropertyConfig().toPropertyConfig(currentGrailsProp);
        Object typeObj = config.getType();
        if (typeObj instanceof Class<?>) {
            userType = (Class<?>)typeObj;
        } else if (typeObj != null) {
            String typeName = typeObj.toString();
            try {
                userType = Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                // only print a warning if the user type is in a package this excludes basic
                // types like string, int etc.
                if (typeName.indexOf(".")>-1) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("UserType not found ", e);
                    }
                }
            }
        }
        return userType;
    }

    /**
     * Binds a Hibernate component type using the given GrailsDomainClassProperty instance
     *
     * @param component  The component to bind
     * @param property   The property
     * @param isNullable Whether it is nullable or not
     * @param mappings   The Hibernate Mappings object
     * @param sessionFactoryBeanName  the session factory bean name
     */
    private void bindComponent(Component component, Embedded property,
                                 boolean isNullable, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        component.setEmbedded(true);
        Class<?> type = property.getType();
        String role = qualify(type.getName(), property.getName());
        component.setRoleName(role);
        component.setComponentClassName(type.getName());

        PersistentEntity domainClass = property.getAssociatedEntity();
        evaluateMapping(domainClass);
        final List<PersistentProperty> properties = domainClass.getPersistentProperties();
        Table table = component.getOwner().getTable();
        PersistentClass persistentClass = component.getOwner();
        String path = property.getName();
        Class<?> propertyType = property.getOwner().getJavaClass();

        for (PersistentProperty currentGrailsProp : properties) {
            if (currentGrailsProp.equals(domainClass.getIdentity())) continue;
            if (currentGrailsProp.getName().equals(GormProperties.VERSION)) continue;

            if (currentGrailsProp.getType().equals(propertyType)) {
                component.setParentProperty(currentGrailsProp.getName());
                continue;
            }

            bindComponentProperty(component, property, currentGrailsProp, persistentClass, path,
                    table, mappings, sessionFactoryBeanName);
        }
    }

    private void bindComponentProperty(Component component, PersistentProperty componentProperty,
                                         PersistentProperty currentGrailsProp, PersistentClass persistentClass,
                                         String path, Table table, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        Value value;
        // see if it's a collection type
        CollectionType collectionType = CT.collectionTypeForClass(currentGrailsProp.getType());
        if (collectionType != null) {
            // create collection
            Collection collection = collectionType.create((ToMany) currentGrailsProp, persistentClass,
                    path, mappings, sessionFactoryBeanName);
            mappings.addCollectionBinding(collection);
            value = collection;
        }
        // work out what type of relationship it is and bind value
        else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.ManyToOne) {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as ManyToOne");

            value = new ManyToOne(metadataBuildingContext, table);
            new ManyToOneBinder(namingStrategy).bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, path);
        } else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.OneToOne) {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as OneToOne");

            if (((Association) currentGrailsProp).canBindOneToOneWithSingleColumnAndForeignKey()) {
                value = new OneToOne(metadataBuildingContext, table, persistentClass);
                bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp, (OneToOne) value, path, sessionFactoryBeanName);
            }
            else {
                value = new ManyToOne(metadataBuildingContext, table);
                new ManyToOneBinder(namingStrategy).bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, path);
            }
        }
        else if (currentGrailsProp instanceof Embedded) {
            value = new Component(metadataBuildingContext, persistentClass);
            bindComponent((Component) value, (Embedded) currentGrailsProp, true, mappings, sessionFactoryBeanName);
        }
        else {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as SimpleValue");

            value = new BasicValue(metadataBuildingContext, table);
            if (currentGrailsProp.getType().isEnum()) {
                bindEnumType(currentGrailsProp, (SimpleValue) value, path, sessionFactoryBeanName);
            }
            else {
                // set type
                new SimpleValueBinder(namingStrategy).bindSimpleValue(currentGrailsProp, componentProperty, (SimpleValue) value, path);
            }
        }

        if (value != null) {
            Property persistentProperty = createProperty(value, persistentClass, currentGrailsProp, mappings);
            component.addProperty(persistentProperty);
            if (isComponentPropertyNullable(componentProperty)) {
                final Iterator<?> columnIterator = value.getColumns().iterator();
                while (columnIterator.hasNext()) {
                    Column c = (Column) columnIterator.next();
                    c.setNullable(true);
                }
            }
        }
    }

    private boolean isComponentPropertyNullable(PersistentProperty componentProperty) {
        if (componentProperty == null) return false;
        final HibernatePersistentEntity domainClass = (HibernatePersistentEntity) componentProperty.getOwner();
        final Mapping mapping = new HibernateEntityWrapper().getMappedForm(domainClass);
        return !domainClass.isRoot() && (mapping == null || mapping.isTablePerHierarchy()) || componentProperty.isNullable();
    }

    /*
     * Creates a persistent class property based on the GrailDomainClassProperty instance.
     */
    private Property createProperty(Value value, PersistentClass persistentClass, PersistentProperty grailsProperty, InFlightMetadataCollector mappings) {
        return this.grailsPropertyCreator.createProperty(value, persistentClass, grailsProperty);
    }

    private void bindOneToMany(org.grails.datastore.mapping.model.types.OneToMany currentGrailsProp, OneToMany one, InFlightMetadataCollector mappings) {
        one.setReferencedEntityName(currentGrailsProp.getAssociatedEntity().getName());
        one.setIgnoreNotFound(true);
    }

    // each property may consist of one or many columns (due to composite ids) so in order to get the
    // number of columns required for a column key we have to perform the calculation here

    private void bindOneToOne(final org.grails.datastore.mapping.model.types.OneToOne property, OneToOne oneToOne,
                                String path, String sessionFactoryBeanName) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
        final Association otherSide = property.getInverseSide();

        final boolean hasOne = otherSide.isHasOne();
        oneToOne.setConstrained(hasOne);
        oneToOne.setForeignKeyType(oneToOne.isConstrained() ?
                ForeignKeyDirection.FROM_PARENT :
                ForeignKeyDirection.TO_PARENT);
        oneToOne.setAlternateUniqueKey(true);

        if (config != null && config.getFetchMode() != null) {
            oneToOne.setFetchMode(config.getFetchMode());
        }
        else {
            oneToOne.setFetchMode(FetchMode.DEFAULT);
        }

        oneToOne.setReferencedEntityName(otherSide.getOwner().getName());
        oneToOne.setPropertyName(property.getName());
        oneToOne.setReferenceToPrimaryKey(false);

        //no-op, for subclasses to extend

        if (hasOne) {
            //TODO NOT TESTED
            // set type
            new SimpleValueBinder(namingStrategy).bindSimpleValue(property, null, oneToOne, path);
        }
        else {
            oneToOne.setReferencedPropertyName(otherSide.getName());
        }
    }

    private void bindVersion(PersistentProperty version, RootClass entity,
                               InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        if (version != null) {

            BasicValue val = new BasicValue(metadataBuildingContext, entity.getTable());

            // set type
            new SimpleValueBinder(namingStrategy).bindSimpleValue(version, null, val, EMPTY_PATH);

            if (val.isTypeSpecified()) {
//                if (!(val.getType() instanceof IntegerType ||
//                        val.getType() instanceof LongType ||
//                        val.getType() instanceof TimestampType)) {
//                    LOG.warn("Invalid version class specified in " + version.getOwner().getName() +
//                            "; must be one of [int, Integer, long, Long, Timestamp, Date]. Not mapping the version.");
//                    return;
//                }
            }
            else {
                val.setTypeName("version".equals(version.getName()) ? "integer" : "timestamp");
            }
            Property prop = new Property();
            prop.setValue(val);
            new PropertyBinder().bindProperty(version, prop);
            prop.setLazy(false);
            val.setNullValue("undefined");
            entity.setVersion(prop);
            entity.setDeclaredVersion(prop);
            entity.setOptimisticLockStyle(OptimisticLockStyle.VERSION);
            entity.addProperty(prop);
        }
        else {
            entity.setOptimisticLockStyle(OptimisticLockStyle.NONE);
        }
    }

    @SuppressWarnings("unchecked")
    private void bindSimpleId(PersistentProperty identifier, RootClass entity,
                                InFlightMetadataCollector mappings, Identity mappedId, String sessionFactoryBeanName) {

        Mapping mapping = new HibernateEntityWrapper().getMappedForm(identifier.getOwner());
        boolean useSequence = mapping.isTablePerConcreteClass();

        // create the id value
        BasicValue id = new BasicValue(metadataBuildingContext, entity.getTable());
        Property idProperty  = new Property();
        idProperty.setName(identifier.getName());
        idProperty.setValue(id);
        entity.setDeclaredIdentifierProperty(idProperty);
        // set identifier on entity

        Properties params = new Properties();
        entity.setIdentifier(id);

        if (mappedId == null) {
            // configure generator strategy
            id.setIdentifierGeneratorStrategy(useSequence ? "sequence-identity" : "native");
        } else {
            String generator = mappedId.getGenerator();
            if("native".equals(generator) && useSequence) {
                generator = "sequence-identity";
            }
            id.setIdentifierGeneratorStrategy(generator);
            params.putAll(mappedId.getParams());
            if(params.containsKey(SEQUENCE_KEY)) {
                params.put(SequenceStyleGenerator.SEQUENCE_PARAM,  params.getProperty(SEQUENCE_KEY));
            }
            if ("assigned".equals(generator)) {
                id.setNullValue("undefined");
            }
        }

        String schemaName = new NamespaceNameExtractor().getSchemaName(mappings);
        String catalogName = new NamespaceNameExtractor().getCatalogName(mappings);

        params.put(PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER, this.metadataBuildingContext.getObjectNameNormalizer());

        if (schemaName != null) {
            params.setProperty(PersistentIdentifierGenerator.SCHEMA, schemaName);
        }
        if (catalogName != null) {
            params.setProperty(PersistentIdentifierGenerator.CATALOG, catalogName);
        }
        id.setIdentifierGeneratorProperties(params);

        // bind value
        // set type
        new SimpleValueBinder(namingStrategy).bindSimpleValue(identifier, null, id, EMPTY_PATH);

        // create property
        Property prop = new Property();
        prop.setValue(id);

        // bind property
        new PropertyBinder().bindProperty(identifier, prop);
        // set identifier property
        entity.setIdentifierProperty(prop);

        Table table = id.getTable();
        table.setPrimaryKey(new PrimaryKey(table));
    }


    private String getIndexColumnName(PersistentProperty property, String sessionFactoryBeanName) {
        PropertyConfig pc = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
        if (pc.getIndexColumn() != null && pc.getIndexColumn().getColumn() != null) {
            return pc.getIndexColumn().getColumn();
        }
        return getNamingStrategy().resolveColumnName(property.getName()) + UNDERSCORE + IndexedCollection.DEFAULT_INDEX_COLUMN_NAME;
    }

    private String getIndexColumnType(PersistentProperty property, String defaultType) {
        PropertyConfig pc = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);
        if (pc.getIndexColumn() != null && pc.getIndexColumn().getType() != null) {
            Mapping mapping =new HibernateEntityWrapper().getMappedForm(property.getOwner());
            return new TypeNameProvider().getTypeName(property, mapping);
        }
        return defaultType;
    }

    private String getMapElementName(PersistentProperty property, String sessionFactoryBeanName) {
        PropertyConfig pc = new PersistentPropertyToPropertyConfig().toPropertyConfig(property);

        if (hasJoinTableColumnNameMapping(pc)) {
            return pc.getJoinTable().getColumn().getName();
        }
        return getNamingStrategy().resolveColumnName(property.getName()) + UNDERSCORE + IndexedCollection.DEFAULT_ELEMENT_COLUMN_NAME;
    }

    private boolean hasJoinTableColumnNameMapping(PropertyConfig pc) {
        return pc != null && pc.getJoinTable() != null && pc.getJoinTable().getColumn() != null && pc.getJoinTable().getColumn().getName() != null;
    }


    private String qualify(String prefix, String name) {
        return GrailsHibernateUtil.qualify(prefix, name);
    }

    private String unqualify(String qualifiedName) {
        return GrailsHibernateUtil.unqualify(qualifiedName);
    }

    public MetadataBuildingContext getMetadataBuildingContext() {
        return metadataBuildingContext;
    }

    /**
     * Second pass class for grails relationships. This is required as all
     * persistent classes need to be loaded in the first pass and then relationships
     * established in the second pass compile
     *
     * @author Graeme
     */
    class GrailsCollectionSecondPass implements org.hibernate.boot.spi.SecondPass {

        private static final long serialVersionUID = -5540526942092611348L;

        ToMany property;
        InFlightMetadataCollector mappings;
        Collection collection;
        String sessionFactoryBeanName;

        public GrailsCollectionSecondPass(ToMany property, InFlightMetadataCollector mappings,
                                          Collection coll,  String sessionFactoryBeanName) {
            this.property = property;
            this.mappings = mappings;
            this.collection = coll;
            this.sessionFactoryBeanName = sessionFactoryBeanName;
        }

        public void doSecondPass(Map<?, ?> persistentClasses, Map<?, ?> inheritedMetas) throws MappingException {
            bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
            createCollectionKeys();
        }

        private void createCollectionKeys() {
            collection.createAllKeys();

            if (LOG.isDebugEnabled()) {
                String msg = "Mapped collection key: " + columns(collection.getKey());
                if (collection.isIndexed())
                    msg += ", index: " + columns(((IndexedCollection) collection).getIndex());
                if (collection.isOneToMany()) {
                    msg += ", one-to-many: "
                            + ((OneToMany) collection.getElement()).getReferencedEntityName();
                } else {
                    msg += ", element: " + columns(collection.getElement());
                }
                LOG.debug(msg);
            }
        }

        private String columns(Value val) {
            StringBuilder columns = new StringBuilder();
            Iterator<?> iter = val.getColumns().iterator();
            while (iter.hasNext()) {
                columns.append(((Selectable) iter.next()).getText());
                if (iter.hasNext()) columns.append(", ");
            }
            return columns.toString();
        }

        @SuppressWarnings("rawtypes")
        public void doSecondPass(Map persistentClasses) throws MappingException {
            bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
            createCollectionKeys();
        }
    }


    class ListSecondPass extends GrailsCollectionSecondPass {
        private static final long serialVersionUID = -3024674993774205193L;

        public ListSecondPass(ToMany property, InFlightMetadataCollector mappings,
                              Collection coll, String sessionFactoryBeanName) {
            super(property, mappings, coll, sessionFactoryBeanName);
        }

        @Override
        public void doSecondPass(Map<?, ?> persistentClasses, Map<?, ?> inheritedMetas) throws MappingException {
            bindListSecondPass(property, mappings, persistentClasses,
                    (org.hibernate.mapping.List) collection, sessionFactoryBeanName);
        }

        @SuppressWarnings("rawtypes")
        @Override
        public void doSecondPass(Map persistentClasses) throws MappingException {
            bindListSecondPass(property, mappings, persistentClasses,
                    (org.hibernate.mapping.List) collection, sessionFactoryBeanName);
        }
    }

    class MapSecondPass extends GrailsCollectionSecondPass {
        private static final long serialVersionUID = -3244991685626409031L;

        public MapSecondPass(ToMany property, InFlightMetadataCollector mappings,
                             Collection coll, String sessionFactoryBeanName) {
            super(property, mappings, coll, sessionFactoryBeanName);
        }

        @Override
        public void doSecondPass(Map<?, ?> persistentClasses, Map<?, ?> inheritedMetas) throws MappingException {
            bindMapSecondPass(property, mappings, persistentClasses,
                    (org.hibernate.mapping.Map)collection, sessionFactoryBeanName);
        }

        @SuppressWarnings("rawtypes")
        @Override
        public void doSecondPass(Map persistentClasses) throws MappingException {
            bindMapSecondPass(property, mappings, persistentClasses,
                    (org.hibernate.mapping.Map) collection, sessionFactoryBeanName);
        }
    }

}
