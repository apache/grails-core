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
import jakarta.persistence.Entity;
import org.codehaus.groovy.transform.trait.Traits;
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
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.grails.orm.hibernate.access.TraitPropertyAccessStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.ClassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.ConfigureDerivedPropertiesConsumer;
import org.grails.orm.hibernate.cfg.domainbinding.IndexBinder;
import org.grails.orm.hibernate.cfg.domainbinding.NamingStrategyProvider;
import org.grails.orm.hibernate.cfg.domainbinding.NumericColumnConstraintsBinder;
import org.grails.orm.hibernate.cfg.domainbinding.PersistentPropertyToPropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.SimpleValueBinder;
import org.grails.orm.hibernate.cfg.domainbinding.StringColumnConstraintsBinder;
import org.grails.orm.hibernate.cfg.domainbinding.TypeNameProvider;
import org.grails.orm.hibernate.cfg.domainbinding.UniqueNameGenerator;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.boot.internal.MetadataBuildingContextRootImpl;
import org.hibernate.boot.model.internal.BinderHelper;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.spi.AccessType;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataContributor;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
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
import org.hibernate.mapping.UniqueKey;
import org.hibernate.mapping.Value;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.persister.entity.UnionSubclassEntityPersister;
import org.hibernate.type.EnumType;
import org.hibernate.type.ForeignKeyDirection;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;
import org.hibernate.usertype.UserCollectionType;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
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

;

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
    private static final String FOREIGN_KEY_SUFFIX = "_id";
    private static final String STRING_TYPE = "string";
    private static final String EMPTY_PATH = "";
    private static final char UNDERSCORE = '_';
    private static final String CASCADE_ALL = "all";
    private static final String CASCADE_SAVE_UPDATE = "save-update";
    private static final String CASCADE_NONE = "none";
    private static final String BACKTICK = "`";

    private static final String ENUM_TYPE_CLASS = "org.hibernate.type.EnumType";
    private static final String ENUM_CLASS_PROP = "enumClass";
    private static final String ENUM_TYPE_PROP = "type";
    private static final String DEFAULT_ENUM_TYPE = "default";
    private static final Logger LOG = LoggerFactory.getLogger(GrailsDomainBinder.class);
    public static final String SEQUENCE_KEY = "sequence";



    /**
     * Provider for naming strategies
     */
    private static final NamingStrategyProvider NAMING_STRATEGY_PROVIDER = new NamingStrategyProvider();

    private final CollectionType CT = new CollectionType(null, this) {
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
        new SimpleValueBinder().bindSimpleValue(value, type, columnName1, true);
        PropertyConfig mappedForm = new PersistentPropertyToPropertyConfig().apply(property);
        if (mappedForm != null && mappedForm.getIndexColumn() != null) {
            Column column = getColumnForSimpleValue(value);
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

            PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
            Mapping mapping = getMapping(property.getOwner());
            String typeName = new TypeNameProvider().getTypeName(property, config, mapping);
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
            new SimpleValueBinder().bindSimpleValue(elt, typeName, columnName, false);

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
        new SimpleValueBinder().bindSimpleValue(iv, "integer", columnName, true);
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

            boolean compositeIdProperty = isCompositeIdProperty(m, property.getInverseSide());
            if (!compositeIdProperty) {
                Backref prop = new Backref();
                final PersistentEntity owner = property.getOwner();
                prop.setEntityName(owner.getName());
                prop.setName(UNDERSCORE + addUnderscore(owner.getJavaClass().getSimpleName(), property.getName()) + "Backref");
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

        PropertyConfig propConfig = new PersistentPropertyToPropertyConfig().apply(property);

        PersistentEntity referenced = property.getAssociatedEntity();
        if (propConfig != null && StringUtils.hasText(propConfig.getSort())) {
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

            bindCollectionForPropertyConfig(collection, propConfig);
        }

        final boolean isManyToMany = property instanceof ManyToMany;
        if(referenced != null && !isManyToMany && referenced.isMultiTenant()) {
            String filterCondition = getMultiTenantFilterCondition(sessionFactoryBeanName, referenced);
            if(filterCondition != null) {
                if (isUnidirectionalOneToMany(property)) {
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
            if (hasJoinKeyMapping(propConfig)) {
                String columnName = propConfig.getJoinTable().getKey().getName();
                new SimpleValueBinder().bindSimpleValue(key, "long", columnName, false);
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
                bindCollectionForPropertyConfig(collection, propConfig);
                if (property.isCircular()) {
                    collection.setInverse(false);
                }
            } else {
                // TODO support unidirectional many-to-many
            }
        } else if (shouldCollectionBindWithJoinColumn(property)) {
            bindCollectionWithJoinTable(property, mappings, collection, propConfig, sessionFactoryBeanName);

        } else if (isUnidirectionalOneToMany(property)) {
            // for non-inverse one-to-many, with a not-null fk, add a backref!
            // there are problems with list and map mappings and join columns relating to duplicate key constraints
            // TODO change this when HHH-1268 is resolved
            bindUnidirectionalOneToMany((org.grails.datastore.mapping.model.types.OneToMany) property, mappings, collection);
        }
    }

    private String getMultiTenantFilterCondition(String sessionFactoryBeanName, PersistentEntity referenced) {
        TenantId tenantId = referenced.getTenantId();
        if(tenantId != null) {
            String defaultColumnName = getDefaultColumnName(tenantId, sessionFactoryBeanName);
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
        if (referenced == null) return null;
        Class<?> current = referenced.getJavaClass();
        while (true) {
            Class<?> superClass = current.getSuperclass();
            if (Object.class.equals(superClass)) break;
            current = superClass;
        }

        return getMapping(current);
    }

    private boolean isBidirectionalOneToManyMap(Association property) {
        return Map.class.isAssignableFrom(property.getType()) && property.isBidirectional();
    }

    private void bindCollectionWithJoinTable(ToMany property,
                                               InFlightMetadataCollector mappings, Collection collection, PropertyConfig config, String sessionFactoryBeanName) {

        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);

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

        final boolean hasJoinColumnMapping = hasJoinColumnMapping(config);
        if (isBasicCollectionType) {
            final Class<?> referencedType = ((Basic)property).getComponentType();
            String className = referencedType.getName();
            final boolean isEnum = referencedType.isEnum();
            if (hasJoinColumnMapping) {
                columnName = config.getJoinTable().getColumn().getName();
            }
            else {
                var clazz = namingStrategy.toPhysicalColumnName(toIdentifier(className),getJdbcEnvironment());
                var prop = namingStrategy.toPhysicalTableName(toIdentifier(property.getName()),getJdbcEnvironment());
                columnName = isEnum ? clazz.toString() : addUnderscore(prop.toString(), clazz.toString());
            }

            if (isEnum) {
                bindEnumType(property, referencedType,element,columnName);
            }
            else {

                Mapping mapping = getMapping(property.getOwner());
                String typeName = new TypeNameProvider().getTypeName(property,config, mapping);
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

                new SimpleValueBinder().bindSimpleValue(element, typeName, columnName, true);
                if (hasJoinColumnMapping) {
                    Column column = getColumnForSimpleValue(element);
                    ColumnConfig columnConfig = config.getJoinTable().getColumn();
                    final PropertyConfig mappedForm = new PersistentPropertyToPropertyConfig().apply(property);
                    new ColumnConfigToColumnBinder().bindColumnConfigToColumn(column, columnConfig, mappedForm);
                }
            }
        } else {
            final PersistentEntity domainClass = property.getAssociatedEntity();

            Mapping m = getMapping(domainClass);
            if (hasCompositeIdentifier(m)) {
                CompositeIdentity ci = (CompositeIdentity) m.getIdentity();
                bindCompositeIdentifierToManyToOne(property, element, ci, domainClass,
                        EMPTY_PATH, sessionFactoryBeanName);
            }
            else {
                if (hasJoinColumnMapping) {
                    columnName = config.getJoinTable().getColumn().getName();
                }
                else {
                    Identifier decapitalize = toIdentifier(NameUtils.decapitalize(domainClass.getName()));
                    columnName = namingStrategy.toPhysicalColumnName(decapitalize,getJdbcEnvironment()).toString() + FOREIGN_KEY_SUFFIX;
                }

                new SimpleValueBinder().bindSimpleValue(element, "long", columnName, true);
            }
        }

        collection.setElement(element);

        bindCollectionForPropertyConfig(collection, config);
    }

    private String addUnderscore(String s1, String s2) {
        return removeBackticks(s1) + UNDERSCORE + removeBackticks(s2);
    }

    private String removeBackticks(String s) {
        return s.startsWith("`") && s.endsWith("`") ? s.substring(1, s.length() - 1) : s;
    }

    private Column getColumnForSimpleValue(SimpleValue element) {
        return element.getColumns().iterator().next();
    }

    private boolean hasJoinColumnMapping(PropertyConfig config) {
        return config != null && config.getJoinTable() != null && config.getJoinTable().getColumn() != null;
    }

    private boolean shouldCollectionBindWithJoinColumn(ToMany property) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
        JoinTable jt = config != null ? config.getJoinTable() : new JoinTable();

        return (isUnidirectionalOneToMany(property) || (property instanceof Basic)) && jt != null;
    }

    /**
     * @param property The property to bind
     * @param manyToOne The inverse side
     */
    private void bindUnidirectionalOneToManyInverseValues(ToMany property, ManyToOne manyToOne) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
        if (config == null) {
            manyToOne.setLazy(true);
        } else {
            manyToOne.setIgnoreNotFound(config.getIgnoreNotFound());
            final FetchMode fetch = config.getFetchMode();
            if(!fetch.equals(FetchMode.JOIN)) {
                manyToOne.setLazy(true);
            }

            final Boolean lazy = config.getLazy();
            if(lazy != null) {
                manyToOne.setLazy(lazy);
            }
        }

        // set referenced entity
        manyToOne.setReferencedEntityName(property.getAssociatedEntity().getName());
    }

    private void bindCollectionForPropertyConfig(Collection collection, PropertyConfig config) {
        if (config == null) {
            collection.setLazy(true);
            collection.setExtraLazy(false);
        } else {
            final FetchMode fetch = config.getFetchMode();
            if(!fetch.equals(FetchMode.JOIN)) {
                collection.setLazy(true);
            }
            final Boolean lazy = config.getLazy();
            if(lazy != null) {
                collection.setExtraLazy(lazy);
            }
        }
    }

    /**
     * Checks whether a property is a unidirectional non-circular one-to-many
     *
     * @param property The property to check
     * @return true if it is unidirectional and a one-to-many
     */
    private boolean isUnidirectionalOneToMany(PersistentProperty property) {
        return ((property instanceof org.grails.datastore.mapping.model.types.OneToMany) && !((Association)property).isBidirectional());
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
        final Mapping mapping = getMapping(refDomainClass.getJavaClass());
        boolean hasCompositeIdentifier = hasCompositeIdentifier(mapping);
        if ((shouldCollectionBindWithJoinColumn((ToMany) property) && hasCompositeIdentifier) ||
                (hasCompositeIdentifier && ( property instanceof ManyToMany))) {
            CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
            bindCompositeIdentifierToManyToOne((Association) property, key, ci, refDomainClass, EMPTY_PATH, sessionFactoryBeanName);
        }
        else {
            bindSimpleValue(property, null, key, EMPTY_PATH, mappings, sessionFactoryBeanName);
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
        prop.setName(UNDERSCORE + addUnderscore(owner.getJavaClass().getSimpleName(), property.getName()) + "Backref");
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
        bindManyToOne(property, element, EMPTY_PATH, mappings, sessionFactoryBeanName);
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
    private void bindCollection(ToMany property, Collection collection,
                                  PersistentClass owner, InFlightMetadataCollector mappings, String path, String sessionFactoryBeanName) {

        // set role
        String propertyName = getNameForPropertyAndPath(property, path);
        collection.setRole(qualify(property.getOwner().getName(), propertyName));

        PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(property);
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
                !shouldCollectionBindWithJoinColumn(property)) &&
                !Map.class.isAssignableFrom(property.getType()) &&
                !(property instanceof ManyToMany) &&
                !(property instanceof Basic);
    }

    private String getNameForPropertyAndPath(PersistentProperty property, String path) {
        if (isNotEmpty(path)) {
            return qualify(path, property.getName());
        }
        return property.getName();
    }

    private void bindCollectionTable(ToMany property, InFlightMetadataCollector mappings,
                                       Collection collection, Table ownerTable, String sessionFactoryBeanName) {

        String owningTableSchema = ownerTable.getSchema();
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
        JoinTable jt = config != null ? config.getJoinTable() : null;

        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);
        String s = calculateTableForMany(property, sessionFactoryBeanName);
        String tableName = (jt != null && jt.getName() != null ? jt.getName() : namingStrategy.toPhysicalTableName(toIdentifier(s),getJdbcEnvironment()).toString());
        String schemaName = getSchemaName(mappings);
        String catalogName = getCatalogName(mappings);
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

    /**
     * Calculates the mapping table for a many-to-many. One side of
     * the relationship has to "own" the relationship so that there is not a situation
     * where you have two mapping tables for left_right and right_left
     */
    private String calculateTableForMany(ToMany property, String sessionFactoryBeanName) {
        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);

        String propertyColumnName = namingStrategy.toPhysicalColumnName(toIdentifier(property.getName()), getJdbcEnvironment()).toString();
        //fix for GRAILS-5895
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
        JoinTable jt = config != null ? config.getJoinTable() : null;
        boolean hasJoinTableMapping = jt != null && jt.getName() != null;
        String left = getTableName(property.getOwner(), sessionFactoryBeanName);

        if (Map.class.isAssignableFrom(property.getType())) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            return addUnderscore(left, propertyColumnName);
        }

        if (property instanceof Basic) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            return addUnderscore(left, propertyColumnName);
        }

        if (property.getAssociatedEntity() == null) {
            throw new MappingException("Expected an entity to be associated with the association ("  + property + ") and none was found. ");
        }

        String right = getTableName(property.getAssociatedEntity(), sessionFactoryBeanName);

        if (property instanceof ManyToMany property1) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            if (property.isOwningSide()) {
                return addUnderscore(left, propertyColumnName);
            }
            Identifier inversePropertyName = toIdentifier(property1.getInversePropertyName());
            return addUnderscore(right, namingStrategy.toPhysicalColumnName(inversePropertyName,getJdbcEnvironment()).toString());
        }

        if (shouldCollectionBindWithJoinColumn(property)) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            left = trimBackTigs(left);
            right = trimBackTigs(right);
            return addUnderscore(left, right);
        }

        if (property.isOwningSide()) {
            return addUnderscore(left, right);
        }
        return addUnderscore(right, left);
    }

    private String trimBackTigs(String tableName) {
        if (tableName.startsWith(BACKTICK)) {
            return tableName.substring(1, tableName.length() - 1);
        }
        return tableName;
    }

    /**
     * Evaluates the table name for the given property
     *
     * @param domainClass The domain class to evaluate
     * @return The table name
     */
    private String getTableName(PersistentEntity domainClass, String sessionFactoryBeanName) {
        Mapping m = getMapping(domainClass);
        String tableName = null;
        if (m != null && m.getTableName() != null) {
            tableName = m.getTableName();
        }
        if (tableName == null) {
            String shortName = domainClass.getJavaClass().getSimpleName();
            PersistentEntityNamingStrategy namingStrategy = this.namingStrategy;

            if(namingStrategy != null) {
                tableName = namingStrategy.resolveTableName(domainClass);
            }
            if(tableName == null) {
                tableName = getPhysicalNamingStrategy(sessionFactoryBeanName).toPhysicalTableName(toIdentifier(shortName),getJdbcEnvironment()).toString();
            }
        }
        return tableName;
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

    public void evaluateMapping(PersistentEntity domainClass) {
        try {
            final Mapping m = (Mapping) domainClass.getMapping().getMappedForm();
            trackCustomCascadingSaves(m, domainClass.getPersistentProperties());
            AbstractGrailsDomainBinder.cacheMapping(domainClass.getJavaClass(), m);
        } catch (Exception e) {
            throw new DatastoreConfigurationException("Error evaluating ORM mappings block for domain [" +
                    domainClass.getName() + "]:  " + e.getMessage(), e);
        }
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

    /**
     * Obtains a mapping object for the given domain class nam
     *
     * @param domainClass The domain class in question
     * @return A Mapping object or null
     */
    private static Mapping getMapping(PersistentEntity domainClass) {
        return getMapping(domainClass.getJavaClass());
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
            Mapping m = getMapping(entity);
            boolean tablePerSubclass = m != null && !m.getTablePerHierarchy();
            if (!tablePerSubclass) {
                // if the root class has children create a discriminator property
                bindDiscriminatorProperty(root.getTable(), root, mappings);
            }
            // bind the sub classes
            bindSubClasses(entity, root, mappings, sessionFactoryBeanName);
        }

        addMultiTenantFilterIfNecessary(entity, root, mappings, sessionFactoryBeanName);

        mappings.addEntityBinding(root);
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
     * @param domainClass The root domain class to bind
     * @param parent      The parent class instance
     * @param mappings    The mappings instance
     * @param sessionFactoryBeanName  the session factory bean name
     */
    private void bindSubClasses(HibernatePersistentEntity domainClass, PersistentClass parent,
                                  InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        domainClass.getMappingContext()
                .getDirectChildEntities(domainClass)
                .stream()
                .filter(HibernatePersistentEntity.class::isInstance)
                .map(HibernatePersistentEntity.class::cast)
                .filter(this::usesConnectionSource)
                .filter(sub -> isChildEntity(sub, domainClass))
                .forEach( sub -> bindSubClass(sub, parent, mappings, sessionFactoryBeanName));

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
                                InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        evaluateMapping(sub);
        Mapping m = getMapping(parent.getMappedClass());
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
            Mapping subMapping = getMapping(sub);
            DiscriminatorConfig discriminatorConfig = subMapping != null ? subMapping.getDiscriminator() : null;

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
            bindSubClasses(sub, subClass, mappings, sessionFactoryBeanName);
        }
    }


    private void bindUnionSubclass(HibernatePersistentEntity subClass, UnionSubclass unionSubclass,
                                  InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
        classBinding.bindClass(subClass, unionSubclass, mappings);

        Mapping subMapping = getMapping(subClass.getJavaClass());

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
                getTableName(subClass, sessionFactoryBeanName),
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

        String schemaName = getSchemaName(mappings);
        String catalogName = getCatalogName(mappings);

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
        String columnName = getColumnNameForPropertyAndPath(identifier, EMPTY_PATH, null, sessionFactoryBeanName);
        new SimpleValueBinder().bindSimpleValue(key, identifier.getType().getName(), columnName, false);

        joinedSubclass.createPrimaryKey();
        joinedSubclass.createForeignKey();

        // properties
        createClassProperties(sub, joinedSubclass, mappings, sessionFactoryBeanName);
    }

    private String getJoinedSubClassTableName(
            HibernatePersistentEntity sub, PersistentClass model, Table denormalizedSuperTable,
            InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        String logicalTableName = unqualify(model.getEntityName());
        String physicalTableName = getTableName(sub, sessionFactoryBeanName);

        String schemaName = getSchemaName(mappings);
        String catalogName = getCatalogName(mappings);

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
     * @param table    The table to bind onto
     * @param entity   The root class entity
     * @param mappings The mappings instance
     */
    private void bindDiscriminatorProperty(Table table, RootClass entity, InFlightMetadataCollector mappings) {
        Mapping m = getMapping(entity.getMappedClass());
        SimpleValue d = new BasicValue(metadataBuildingContext, table);
        entity.setDiscriminator(d);
        DiscriminatorConfig discriminatorConfig = m != null ? m.getDiscriminator() : null;

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
            new SimpleValueBinder().bindSimpleValue(d, STRING_TYPE, RootClass.DEFAULT_DISCRIMINATOR_COLUMN_NAME, false);

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
        Mapping m = ofNullable(getMapping(domainClass.getJavaClass())).orElseThrow();

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
                .orElse(getSchemaName(mappings));

        var catalog = ofNullable(m.getTable())
                .map(org.grails.orm.hibernate.cfg.Table::getCatalog)
                .orElse( getCatalogName(mappings));


        var isAbstract = !m.getTablePerHierarchy() && m.isTablePerConcreteClass() && root.isAbstract();
        // create the table
        var table = mappings.addTable(schema
                , catalog
                , getTableName(domainClass, sessionFactoryBeanName)
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

        final List<PersistentProperty> persistentProperties = domainClass.getPersistentProperties();
        Table table = persistentClass.getTable();

        Mapping gormMapping = domainClass.getMapping().getMappedForm();

        if (gormMapping != null) {
            table.setComment(gormMapping.getComment());
        }

        List<Embedded> embedded = new ArrayList<>();

        for (PersistentProperty currentGrailsProp : persistentProperties) {

            // if its inherited skip
            if (currentGrailsProp.isInherited()) {
                continue;
            }
            if(currentGrailsProp.getName().equals(GormProperties.VERSION) ) continue;
            if (isCompositeIdProperty(gormMapping, currentGrailsProp)) continue;
            if (isIdentityProperty(gormMapping, currentGrailsProp)) continue;

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
                bindSimpleValue(currentGrailsProp, null, (SimpleValue) value, EMPTY_PATH, mappings, sessionFactoryBeanName);
            }
            else if (collectionType != null) {
                PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(currentGrailsProp);
                String typeName = new TypeNameProvider().getTypeName(currentGrailsProp,config, gormMapping);
                if ("serializable".equals(typeName)) {
                    value = new BasicValue(metadataBuildingContext, table);
                    boolean nullable = currentGrailsProp.isNullable();
                    String columnName = getColumnNameForPropertyAndPath(currentGrailsProp, EMPTY_PATH, null, sessionFactoryBeanName);
                    new SimpleValueBinder().bindSimpleValue((SimpleValue) value, typeName, columnName, nullable);
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
                    bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, EMPTY_PATH, mappings, sessionFactoryBeanName);
                }
                else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.OneToOne && userType == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as OneToOne");
                    }

                    final boolean isHasOne = isHasOne(association);
                    if (isHasOne && !association.isBidirectional()) {
                        throw new MappingException("hasOne property [" + currentGrailsProp.getOwner().getName() +
                                "." + currentGrailsProp.getName() + "] is not bidirectional. Specify the other side of the relationship!");
                    }
                    else if (canBindOneToOneWithSingleColumnAndForeignKey((Association) currentGrailsProp)) {
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
                            bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, EMPTY_PATH, mappings, sessionFactoryBeanName);
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
                bindSimpleValue(currentGrailsProp, null, (SimpleValue) value, EMPTY_PATH, mappings, sessionFactoryBeanName);
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
        bindNaturalIdentifier(table, gormMapping, persistentClass);
    }

    private boolean isHasOne(Association association) {
        return association instanceof org.grails.datastore.mapping.model.types.OneToOne && ((org.grails.datastore.mapping.model.types.OneToOne)association).isForeignKeyInChild();
    }

    private void bindNaturalIdentifier(Table table, Mapping mapping, PersistentClass persistentClass) {
        Object o = mapping != null ? mapping.getIdentity() : null;
        if (!(o instanceof Identity)) {
            return;
        }

        Identity identity = (Identity) o;
        final NaturalId naturalId = identity.getNatural();
        if (naturalId == null || naturalId.getPropertyNames().isEmpty()) {
            return;
        }

        UniqueKey uk = new UniqueKey();
        uk.setTable(table);

        boolean mutable = naturalId.isMutable();

        for (String propertyName : naturalId.getPropertyNames()) {
            Property property = persistentClass.getProperty(propertyName);

            property.setNaturalIdentifier(true);
            if (!mutable) property.setUpdateable(false);

            uk.addColumns(property.getValue());
        }

        new UniqueNameGenerator().setGeneratedUniqueName(uk);

        table.addUniqueKey(uk);
    }

    private boolean canBindOneToOneWithSingleColumnAndForeignKey(Association currentGrailsProp) {
        if (currentGrailsProp.isBidirectional()) {
            final Association otherSide = currentGrailsProp.getInverseSide();
            if(otherSide != null) {
                if (isHasOne(otherSide)) {
                    return false;
                }
                if (!currentGrailsProp.isOwningSide() && (otherSide.isOwningSide())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isIdentityProperty(Mapping gormMapping, PersistentProperty currentGrailsProp) {
        if (gormMapping == null) {
            return false;
        }

        Object identityMapping = gormMapping.getIdentity();
        if (!(identityMapping instanceof Identity)) {
            return false;
        }

        String identityName = ((Identity)identityMapping).getName();
        return identityName != null && identityName.equals(currentGrailsProp.getName());
    }

    private void bindEnumType(PersistentProperty property, SimpleValue simpleValue,
                                String path, String sessionFactoryBeanName) {
        bindEnumType(property, property.getType(), simpleValue,
                getColumnNameForPropertyAndPath(property, path, null, sessionFactoryBeanName));
    }

    private void bindEnumType(PersistentProperty property, Class<?> propertyType, SimpleValue simpleValue, String columnName) {

        PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(property);
        final PersistentEntity owner = property.getOwner();
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
        Mapping mapping1 = getMapping(owner);
        String typeName = new TypeNameProvider().getTypeName(property,config, mapping1);
        if (typeName == null) {
            Properties enumProperties = new Properties();
            enumProperties.put(ENUM_CLASS_PROP, propertyType.getName());

            String enumType = pc == null ? DEFAULT_ENUM_TYPE : pc.getEnumType();
            boolean isDefaultEnumType = enumType.equals(DEFAULT_ENUM_TYPE);
            simpleValue.setTypeName(ENUM_TYPE_CLASS);
            if (isDefaultEnumType || "string".equalsIgnoreCase(enumType)) {
                enumProperties.put(EnumType.TYPE, String.valueOf(Types.VARCHAR));
                enumProperties.put(EnumType.NAMED, Boolean.TRUE.toString());
            }
            else if("identity".equals(enumType)) {
                simpleValue.setTypeName(IdentityEnumType.class.getName());
            }
            else if (!"ordinal".equalsIgnoreCase(enumType)) {
                simpleValue.setTypeName(enumType);
            }
            simpleValue.setTypeParameters(enumProperties);
        }
        else {
            simpleValue.setTypeName(typeName);
        }

        Table t = simpleValue.getTable();
        Column column = new Column();

        if (owner.isRoot()) {
            column.setNullable(property.isNullable());
        } else {
            Mapping mapping = getMapping(owner);
            if (mapping == null || mapping.getTablePerHierarchy()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[GrailsDomainBinder] Sub class property [" + property.getName() +
                            "] for column name [" + column.getName() + "] set to nullable");
                }
                column.setNullable(true);
            } else {
                column.setNullable(property.isNullable());
            }
        }
        column.setValue(simpleValue);
        column.setName(columnName);
        if (t != null) t.addColumn(column);

        simpleValue.addColumn(column);

        PropertyConfig propertyConfig = new PersistentPropertyToPropertyConfig().apply(property);
        if (propertyConfig != null && !propertyConfig.getColumns().isEmpty()) {
            ColumnConfig columnConfig = propertyConfig.getColumns().get(0);
            new IndexBinder().bindIndex(columnName, column, columnConfig, t);
            final PropertyConfig mappedForm = new PersistentPropertyToPropertyConfig().apply(property);
            new ColumnConfigToColumnBinder().bindColumnConfigToColumn(column, columnConfig, mappedForm);
        }
    }

    private Class<?> getUserType(PersistentProperty currentGrailsProp) {
        Class<?> userType = null;
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(currentGrailsProp);
        Object typeObj = config == null ? null : config.getType();
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

    private boolean isCompositeIdProperty(Mapping gormMapping, PersistentProperty currentGrailsProp) {
        if (gormMapping != null && gormMapping.getIdentity() != null) {
            Object id = gormMapping.getIdentity();
            if (id instanceof CompositeIdentity) {
                String[] propertyNames = ((CompositeIdentity) id).getPropertyNames();
                String property = currentGrailsProp.getName();
                for (String currentName : propertyNames) {
                    if(currentName != null && currentName.equals(property)) return true;
                }
            }
        }
        return false;
    }

    private boolean isBidirectionalManyToOne(PersistentProperty currentGrailsProp) {
        return ((currentGrailsProp instanceof org.grails.datastore.mapping.model.types.ManyToOne) && ((Association)currentGrailsProp).isBidirectional());
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
            bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, path, mappings, sessionFactoryBeanName);
        } else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.OneToOne) {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as OneToOne");

            if (canBindOneToOneWithSingleColumnAndForeignKey((Association) currentGrailsProp)) {
                value = new OneToOne(metadataBuildingContext, table, persistentClass);
                bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp, (OneToOne) value, path, sessionFactoryBeanName);
            }
            else {
                value = new ManyToOne(metadataBuildingContext, table);
                bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, path, mappings, sessionFactoryBeanName);
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
                bindSimpleValue(currentGrailsProp, componentProperty, (SimpleValue) value, path,
                        mappings, sessionFactoryBeanName);
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
        final PersistentEntity domainClass = componentProperty.getOwner();
        final Mapping mapping = getMapping(domainClass.getJavaClass());
        return !domainClass.isRoot() && (mapping == null || mapping.isTablePerHierarchy()) || componentProperty.isNullable();
    }

    /*
     * Creates a persistent class property based on the GrailDomainClassProperty instance.
     */
    private Property createProperty(Value value, PersistentClass persistentClass, PersistentProperty grailsProperty, InFlightMetadataCollector mappings) {
        // set type
        value.setTypeUsingReflection(persistentClass.getClassName(), grailsProperty.getName());

        if (value.getTable() != null) {
            value.createForeignKey();
        }

        Property prop = new Property();
        prop.setValue(value);
        bindProperty(grailsProperty, prop, mappings);
        return prop;
    }

    private void bindOneToMany(org.grails.datastore.mapping.model.types.OneToMany currentGrailsProp, OneToMany one, InFlightMetadataCollector mappings) {
        one.setReferencedEntityName(currentGrailsProp.getAssociatedEntity().getName());
        one.setIgnoreNotFound(true);
    }

    /**
     * Binds a many-to-one relationship to the
     *
     */
    @SuppressWarnings("unchecked")
    private void bindManyToOne(Association property, ManyToOne manyToOne,
                                 String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);


        bindManyToOneValues(property, manyToOne);
        PersistentEntity refDomainClass = property instanceof ManyToMany ? property.getOwner() : property.getAssociatedEntity();
        Mapping mapping = getMapping(refDomainClass);
        boolean isComposite = hasCompositeIdentifier(mapping);
        if (isComposite) {
            CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
            bindCompositeIdentifierToManyToOne(property, manyToOne, ci, refDomainClass, path, sessionFactoryBeanName);
        }
        else {
            if (property.isCircular() && (property instanceof ManyToMany)) {
                PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(property);

                if (pc.getColumns().isEmpty()) {
                    mapping.getColumns().put(property.getName(), pc);
                }
                if (!hasJoinKeyMapping(pc) ) {
                    JoinTable jt = new JoinTable();
                    final ColumnConfig columnConfig = new ColumnConfig();
                    columnConfig.setName(namingStrategy.toPhysicalColumnName(toIdentifier(property.getName()), getJdbcEnvironment()).toString() + UNDERSCORE + FOREIGN_KEY_SUFFIX);
                    jt.setKey(columnConfig);
                    pc.setJoinTable(jt);
                }
                bindSimpleValue(property, manyToOne, path, pc, sessionFactoryBeanName);
            }
            else {
                // bind column
                bindSimpleValue(property, null, manyToOne, path, mappings, sessionFactoryBeanName);
            }
        }

        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
        if ((property instanceof org.grails.datastore.mapping.model.types.OneToOne) && !isComposite) {
            manyToOne.setAlternateUniqueKey(true);
            Column c = getColumnForSimpleValue(manyToOne);
            if (config != null && !config.isUniqueWithinGroup()) {
                c.setUnique(config.isUnique());
            }
            else if (property.isBidirectional() && isHasOne(property.getInverseSide())) {
                c.setUnique(true);
            }
        }
    }

    private void bindCompositeIdentifierToManyToOne(Association property,
                                                      SimpleValue value, CompositeIdentity compositeId, PersistentEntity refDomainClass,
                                                      String path, String sessionFactoryBeanName) {

        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);

        String[] propertyNames = compositeId.getPropertyNames();
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);

        List<ColumnConfig> columns = config.getColumns();
        int i = columns.size();
        int expectedForeignKeyColumnLength = calculateForeignKeyColumnCount(refDomainClass, propertyNames);
        if (i != expectedForeignKeyColumnLength) {
            int j = 0;
            for (String propertyName : propertyNames) {
                ColumnConfig cc;
                // if a column configuration exists in the mapping use it
                if(j < i) {
                    cc = columns.get(j++);
                }
                // otherwise create a new one to represent the composite column
                else {
                    cc = new ColumnConfig();
                }
                // if the name is null then configure the name by convention
                if(cc.getName() == null) {
                    // use the referenced table name as a prefix
                    String prefix = getTableName(refDomainClass, sessionFactoryBeanName);
                    PersistentProperty referencedProperty = refDomainClass.getPropertyByName(propertyName);

                    // if the referenced property is a ToOne and it has a composite id
                    // then a column is needed for each property that forms the composite id
                    if(referencedProperty instanceof ToOne) {
                        ToOne toOne = (ToOne) referencedProperty;
                        PersistentProperty[] compositeIdentity = toOne.getAssociatedEntity().getCompositeIdentity();
                        if(compositeIdentity != null) {
                            for (PersistentProperty cip : compositeIdentity) {
                                // for each property of a composite id by default we use the table name and the property name as a prefix
                                String compositeIdPrefix = addUnderscore(prefix, namingStrategy.toPhysicalColumnName(toIdentifier(referencedProperty.getName()), getJdbcEnvironment()).toString());
                                String suffix = getDefaultColumnName(cip, sessionFactoryBeanName);
                                String finalColumnName = addUnderscore(compositeIdPrefix, suffix);
                                cc = new ColumnConfig();
                                cc.setName(finalColumnName);
                                columns.add(cc);
                            }
                            continue;
                        }
                    }

                    String suffix = getDefaultColumnName(referencedProperty, sessionFactoryBeanName);
                    String finalColumnName = addUnderscore(prefix, suffix);
                    cc.setName(finalColumnName);
                    columns.add(cc);
                }
            }
        }
        bindSimpleValue(property, value, path, config, sessionFactoryBeanName);
    }

    // each property may consist of one or many columns (due to composite ids) so in order to get the
    // number of columns required for a column key we have to perform the calculation here
    private int calculateForeignKeyColumnCount(PersistentEntity refDomainClass, String[] propertyNames) {
        int expectedForeignKeyColumnLength = 0;
        for (String propertyName : propertyNames) {
            PersistentProperty referencedProperty = refDomainClass.getPropertyByName(propertyName);
            if(referencedProperty instanceof ToOne) {
                ToOne toOne = (ToOne) referencedProperty;
                PersistentProperty[] compositeIdentity = toOne.getAssociatedEntity().getCompositeIdentity();
                if(compositeIdentity != null) {
                    expectedForeignKeyColumnLength += compositeIdentity.length;
                }
                else {
                    expectedForeignKeyColumnLength++;
                }
            }
            else {
                expectedForeignKeyColumnLength++;
            }
        }
        return expectedForeignKeyColumnLength;
    }

    private boolean hasCompositeIdentifier(Mapping mapping) {
        return mapping != null && (mapping.getIdentity() instanceof CompositeIdentity);
    }

    private void bindOneToOne(final org.grails.datastore.mapping.model.types.OneToOne property, OneToOne oneToOne,
                                String path, String sessionFactoryBeanName) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
        final Association otherSide = property.getInverseSide();

        final boolean hasOne = isHasOne(otherSide);
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

        bindOneToOneInternal(property, oneToOne, path);

        if (hasOne) {
            PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(property);
            bindSimpleValue(property, oneToOne, path, pc, sessionFactoryBeanName);
        }
        else {
            oneToOne.setReferencedPropertyName(otherSide.getName());
        }
    }

    private void bindOneToOneInternal(org.grails.datastore.mapping.model.types.OneToOne property, OneToOne oneToOne, String path) {
        //no-op, for subclasses to extend
    }

    /**
     */
    private void bindManyToOneValues(org.grails.datastore.mapping.model.types.Association property, ManyToOne manyToOne) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);

        if (config != null && config.getFetchMode() != null) {
            manyToOne.setFetchMode(config.getFetchMode());
        }
        else {
            manyToOne.setFetchMode(FetchMode.DEFAULT);
        }

        manyToOne.setLazy(getLaziness(property));

        if (config != null) {
            manyToOne.setIgnoreNotFound(config.getIgnoreNotFound());
        }

        // set referenced entity
        manyToOne.setReferencedEntityName(property.getAssociatedEntity().getName());
    }

    private void bindVersion(PersistentProperty version, RootClass entity,
                               InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        if (version != null) {

            BasicValue val = new BasicValue(metadataBuildingContext, entity.getTable());

            bindSimpleValue(version, null, val, EMPTY_PATH, mappings, sessionFactoryBeanName);

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
            bindProperty(version, prop, mappings);
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

        Mapping mapping = getMapping(identifier.getOwner());
        boolean useSequence = mapping != null && mapping.isTablePerConcreteClass();

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

        String schemaName = getSchemaName(mappings);
        String catalogName = getCatalogName(mappings);

        params.put(PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER, this.metadataBuildingContext.getObjectNameNormalizer());

        if (schemaName != null) {
            params.setProperty(PersistentIdentifierGenerator.SCHEMA, schemaName);
        }
        if (catalogName != null) {
            params.setProperty(PersistentIdentifierGenerator.CATALOG, catalogName);
        }
        id.setIdentifierGeneratorProperties(params);

        // bind value
        bindSimpleValue(identifier, null, id, EMPTY_PATH, mappings, sessionFactoryBeanName);

        // create property
        Property prop = new Property();
        prop.setValue(id);

        // bind property
        bindProperty(identifier, prop, mappings);
        // set identifier property
        entity.setIdentifierProperty(prop);

        Table table = id.getTable();
        table.setPrimaryKey(new PrimaryKey(table));
    }

    private String getSchemaName(InFlightMetadataCollector mappings) {
        Identifier schema = mappings.getDatabase().getDefaultNamespace().getName().getSchema();
        if(schema != null) {
            return schema.getCanonicalName();
        }
        return null;
    }

    private String getCatalogName(InFlightMetadataCollector mappings) {
        Identifier catalog = mappings.getDatabase().getDefaultNamespace().getName().getCatalog();
        if(catalog != null) {
            return catalog.getCanonicalName();
        }
        return null;
    }

    /**
     * Binds a property to Hibernate runtime meta model. Deals with cascade strategy based on the Grails domain model
     *
     * @param grailsProperty The grails property instance
     * @param prop           The Hibernate property
     * @param mappings       The Hibernate mappings
     */
    private void bindProperty(PersistentProperty grailsProperty, Property prop, InFlightMetadataCollector mappings) {
        // set the property name
        prop.setName(grailsProperty.getName());
        if (isBidirectionalManyToOneWithListMapping(grailsProperty, prop)) {
            prop.setInsertable(false);
            prop.setUpdateable(false);
        } else {
            prop.setInsertable(getInsertableness(grailsProperty));
            prop.setUpdateable(getUpdateableness(grailsProperty));
        }

        AccessType accessType = AccessType.getAccessStrategy(
               new PersistentPropertyToPropertyConfig().apply(grailsProperty).getAccessType()
        );

        if(accessType == AccessType.FIELD) {
            EntityReflector.PropertyReader reader = grailsProperty.getReader();
            Method getter  = reader != null ? reader.getter() : null;
            if(getter != null && getter.getAnnotation(Traits.Implemented.class) != null) {
                prop.setPropertyAccessorName(TraitPropertyAccessStrategy.class.getName());
            }
            else {
                prop.setPropertyAccessorName( accessType.getType() );
            }
        }
        else {
            prop.setPropertyAccessorName( accessType.getType() );
        }


        prop.setOptional(grailsProperty.isNullable());

        setCascadeBehaviour(grailsProperty, prop);

        // lazy to true
        final boolean isToOne = grailsProperty instanceof ToOne && !(grailsProperty instanceof Embedded);
        PersistentEntity propertyOwner = grailsProperty.getOwner();
        boolean isLazyable = isToOne ||
                !(grailsProperty instanceof Association) && !grailsProperty.equals(propertyOwner.getIdentity());

        if (isLazyable) {
            final boolean isLazy = getLaziness(grailsProperty);
            prop.setLazy(isLazy);

            if (isLazy && isToOne && !(PersistentAttributeInterceptable.class.isAssignableFrom(propertyOwner.getJavaClass()))) {
//                handleLazyProxy(propertyOwner, grailsProperty);
            }
        }
    }

    private boolean getLaziness(PersistentProperty grailsProperty) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(grailsProperty);
        final Boolean lazy = config.getLazy();
        if(lazy == null && grailsProperty instanceof Association) {
            return true;
        }
        else if(lazy != null) {
            return lazy;
        }
        return false;
    }

    private boolean getInsertableness(PersistentProperty grailsProperty) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(grailsProperty);
        return config == null || config.getInsertable();
    }

    private boolean getUpdateableness(PersistentProperty grailsProperty) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(grailsProperty);
        return config == null || config.getUpdatable();
    }

    private boolean isBidirectionalManyToOneWithListMapping(PersistentProperty grailsProperty, Property prop) {
        if(grailsProperty instanceof Association) {

            Association association = (Association) grailsProperty;
            Association otherSide = association.getInverseSide();
            return association.isBidirectional() && otherSide != null &&
                    prop.getValue() instanceof ManyToOne &&
                    List.class.isAssignableFrom(otherSide.getType());
        }
        return false;
    }

    private void setCascadeBehaviour(PersistentProperty grailsProperty, Property prop) {
        String cascadeStrategy = "none";
        // set to cascade all for the moment
        PersistentEntity domainClass = grailsProperty.getOwner();
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(grailsProperty);
        if (config != null && config.getCascade() != null) {
            cascadeStrategy = config.getCascade();
            LOG.debug("Cascade strategy for property ${grailsProperty.getName()} is ${cascadeStrategy}");
        } else if (grailsProperty instanceof Association) {
            Association association = (Association) grailsProperty;
            PersistentEntity referenced = association.getAssociatedEntity();
            if (isHasOne(association)) {
                cascadeStrategy = CASCADE_ALL;
            }
            else if (association instanceof org.grails.datastore.mapping.model.types.OneToOne) {
                if (referenced != null && association.isOwningSide()) {
                    cascadeStrategy = CASCADE_ALL;
                }
                else {
                    cascadeStrategy = CASCADE_SAVE_UPDATE;
                }
            } else if (association instanceof org.grails.datastore.mapping.model.types.OneToMany) {
                if (referenced != null && association.isOwningSide()) {
                    cascadeStrategy = CASCADE_ALL;
                }
                else {
                    cascadeStrategy = CASCADE_SAVE_UPDATE;
                }
            } else if (grailsProperty instanceof ManyToMany) {
                if ((referenced != null && referenced.isOwningEntity(domainClass)) || association.isCircular()) {
                    cascadeStrategy = CASCADE_SAVE_UPDATE;
                }
            } else if (grailsProperty instanceof org.grails.datastore.mapping.model.types.ManyToOne) {
                if (referenced != null && referenced.isOwningEntity(domainClass) && !isCircularAssociation(grailsProperty)) {
                    cascadeStrategy = CASCADE_ALL;
                }
                else if(isCompositeIdProperty((Mapping) domainClass.getMapping().getMappedForm(), grailsProperty)) {
                    cascadeStrategy = CASCADE_ALL;
                }
                else {
                    cascadeStrategy = CASCADE_NONE;
                }
            }
            else if (grailsProperty instanceof Basic) {
                cascadeStrategy = CASCADE_ALL;
            }
            else if (Map.class.isAssignableFrom(grailsProperty.getType())) {
                referenced = association.getAssociatedEntity();
                if (referenced != null && referenced.isOwningEntity(domainClass)) {
                    cascadeStrategy = CASCADE_ALL;
                } else {
                    cascadeStrategy = CASCADE_SAVE_UPDATE;
                }
            }
            logCascadeMapping(association, cascadeStrategy, referenced);
        } else {
            LOG.debug("No cascade strategy for property: " + grailsProperty);
        }
        prop.setCascade(cascadeStrategy);
    }

    private boolean isCircularAssociation(PersistentProperty grailsProperty) {
        return grailsProperty.getType().equals(grailsProperty.getOwner().getJavaClass());
    }

    private void logCascadeMapping(Association grailsProperty, String cascadeStrategy, PersistentEntity referenced) {
        if (LOG.isDebugEnabled() & referenced != null) {
            String assType = getAssociationDescription(grailsProperty);
            LOG.debug("Mapping cascade strategy for " + assType + " property " + grailsProperty.getOwner().getName() + "." + grailsProperty.getName() + " referencing type [" + referenced.getJavaClass().getName() + "] -> [CASCADE: " + cascadeStrategy + "]");
        }
    }

    private String getAssociationDescription(Association grailsProperty) {
        String assType = "unknown";
        if (grailsProperty instanceof ManyToMany) {
            assType = "many-to-many";
        } else if (grailsProperty instanceof org.grails.datastore.mapping.model.types.OneToMany) {
            assType = "one-to-many";
        } else if (grailsProperty instanceof org.grails.datastore.mapping.model.types.OneToOne) {
            assType = "one-to-one";
        } else if (grailsProperty instanceof org.grails.datastore.mapping.model.types.ManyToOne) {
            assType = "many-to-one";
        } else if (grailsProperty.isEmbedded()) {
            assType = "embedded";
        }
        return assType;
    }

    /**
     * Binds a simple value to the Hibernate metamodel. A simple value is
     * any type within the Hibernate type system
     *
     * @param property
     * @param parentProperty
     * @param simpleValue The simple value to bind
     * @param path
     * @param mappings    The Hibernate mappings instance
     * @param sessionFactoryBeanName  the session factory bean name
     */
    private void bindSimpleValue(PersistentProperty property, PersistentProperty parentProperty,
                                   SimpleValue simpleValue, String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        // set type
        bindSimpleValue(property,parentProperty, simpleValue, path, new PersistentPropertyToPropertyConfig().apply(property), sessionFactoryBeanName);
    }

    private void bindSimpleValue(PersistentProperty grailsProp, SimpleValue simpleValue,
                                   String path, PropertyConfig propertyConfig, String sessionFactoryBeanName) {
        bindSimpleValue(grailsProp, null, simpleValue, path, propertyConfig, sessionFactoryBeanName);
    }

    private void bindSimpleValue(
            PersistentProperty grailsProp
            , PersistentProperty parentProperty
            , SimpleValue simpleValue
            , String path
            , PropertyConfig propertyConfig
            , String sessionFactoryBeanName
    ) {
        setTypeForPropertyConfig(grailsProp, simpleValue, propertyConfig);
        final PropertyConfig mappedForm = (PropertyConfig) grailsProp.getMappedForm();
        if (mappedForm != null && mappedForm.isDerived() && !(grailsProp instanceof TenantId)) {
            Formula formula = new Formula();
            formula.setFormula(propertyConfig.getFormula());
            simpleValue.addFormula(formula);
        } else {
            Table table = simpleValue.getTable();
            boolean hasConfig = propertyConfig != null;

            String generator = hasConfig ? propertyConfig.getGenerator() : null;
            if(generator != null) {
                simpleValue.setIdentifierGeneratorStrategy(generator);
                Properties params = propertyConfig.getTypeParams();
                if(params != null) {
                    Properties generatorProps = new Properties();
                    generatorProps.putAll(params);

                    if(generatorProps.containsKey(SEQUENCE_KEY)) {
                        generatorProps.put(SequenceStyleGenerator.SEQUENCE_PARAM,  generatorProps.getProperty(SEQUENCE_KEY));
                    }
                    simpleValue.setIdentifierGeneratorProperties( generatorProps );
                }
            }

            // Add the column definitions for this value/property. Note that
            // not all custom mapped properties will have column definitions,
            // in which case we still need to create a Hibernate column for
            // this value.
            var columnConfigToColumnBinder = new ColumnConfigToColumnBinder();
            ofNullable(propertyConfig)
                    .map(PropertyConfig::getColumns)
                    .filter(columns -> !columns.isEmpty())
                    .orElse(Arrays.asList(new ColumnConfig[] { null }))
                    .forEach( cc -> {
                        Column column = new Column();
                        columnConfigToColumnBinder.bindColumnConfigToColumn(column,cc,mappedForm);
                        bindColumn(grailsProp, parentProperty, column, cc, path, table, sessionFactoryBeanName);
                        if (table != null) {
                            table.addColumn(column);
                        }
                        simpleValue.addColumn(column);
                    });
        }
    }

    private void setTypeForPropertyConfig(PersistentProperty grailsProp, SimpleValue simpleValue, PropertyConfig config) {
        Mapping mapping = getMapping(grailsProp.getOwner());
        final String typeName = new TypeNameProvider().getTypeName(grailsProp, new PersistentPropertyToPropertyConfig().apply(grailsProp), mapping);
        if (typeName == null) {
            simpleValue.setTypeName(grailsProp.getType().getName());
        }
        else {
            simpleValue.setTypeName(typeName);
            if (config != null) {
                simpleValue.setTypeParameters(config.getTypeParams());
            }
        }
    }

    /**
     * Binds a Column instance to the Hibernate meta model
     *
     * @param property The Grails domain class property
     * @param parentProperty
     * @param column     The column to bind
     * @param path
     * @param table      The table name
     * @param sessionFactoryBeanName  the session factory bean name
     */
    private void bindColumn(PersistentProperty property, PersistentProperty parentProperty,
                              Column column, ColumnConfig cc, String path, Table table, String sessionFactoryBeanName) {

        if (cc != null) {
            column.setComment(cc.getComment());
            column.setDefaultValue(cc.getDefaultValue());
            column.setCustomRead(cc.getRead());
            column.setCustomWrite(cc.getWrite());
        }

        Class<?> userType = getUserType(property);
        String columnName = getColumnNameForPropertyAndPath(property, path, cc, sessionFactoryBeanName);
        if ((property instanceof Association) && userType == null) {
            Association association = (Association) property;
            // Only use conventional naming when the column has not been explicitly mapped.
            if (column.getName() == null) {
                column.setName(columnName);
            }
            if (property instanceof ManyToMany) {
                column.setNullable(false);
            }
            else if (property instanceof org.grails.datastore.mapping.model.types.OneToOne && association.isBidirectional() && !association.isOwningSide()) {
                if (isHasOne(((Association) property).getInverseSide())) {
                    column.setNullable(false);
                }
                else {
                    column.setNullable(true);
                }
            }
            else if ((property instanceof ToOne) && association.isCircular()) {
                column.setNullable(true);
            }
            else {
                column.setNullable(property.isNullable());
            }
        }
        else {
            column.setName(columnName);
            column.setNullable(property.isNullable() || (parentProperty != null && parentProperty.isNullable()));
            PropertyConfig propertyConfig = new PersistentPropertyToPropertyConfig().apply(property);
            // Use the constraints for this property to more accurately define
            // the column's length, precision, and scale
            if (String.class.isAssignableFrom(property.getType()) || byte[].class.isAssignableFrom(property.getType())) {
                new StringColumnConstraintsBinder().bindStringColumnConstraints(column, propertyConfig);
            }

            if (Number.class.isAssignableFrom(property.getType())) {

                new NumericColumnConstraintsBinder().bindNumericColumnConstraints(column, cc, propertyConfig);
            }
        }

        handleUniqueConstraint(property, column, path, table, columnName, sessionFactoryBeanName);

        new IndexBinder().bindIndex(columnName, column, cc, table);

        final PersistentEntity owner = property.getOwner();
        if (!owner.isRoot()) {
            Mapping mapping = getMapping(owner);
            if (mapping == null || mapping.getTablePerHierarchy()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("[GrailsDomainBinder] Sub class property [" + property.getName() + "] for column name ["+column.getName()+"] set to nullable");
                column.setNullable(true);
            } else {
                column.setNullable(property.isNullable());
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("[GrailsDomainBinder] bound property [" + property.getName() + "] to column name ["+column.getName()+"] in table ["+table.getName()+"]");
    }


    private void createKeyForProps(PersistentProperty grailsProp, String path, Table table,
                                     String columnName, List<?> propertyNames, String sessionFactoryBeanName) {
        List<Column> keyList = new ArrayList<>();
        keyList.add(new Column(columnName));
        for (Iterator<?> i = propertyNames.iterator(); i.hasNext();) {
            String propertyName = (String) i.next();
            PersistentProperty otherProp = grailsProp.getOwner().getPropertyByName(propertyName);
            if (otherProp == null) {
                throw new MappingException(grailsProp.getOwner().getJavaClass().getName() + " references an unknown property " + propertyName);
            }
            String otherColumnName = getColumnNameForPropertyAndPath(otherProp, path, null, sessionFactoryBeanName);
            keyList.add(new Column(otherColumnName));
        }
        createUniqueKeyForColumns(table, columnName, keyList);
    }

    private void createUniqueKeyForColumns(Table table, String columnName, List<Column> columns) {
        Collections.reverse(columns);

        UniqueKey uk = new UniqueKey();
        uk.setTable(table);
        for(Column column : columns) {
            uk.addColumn(column);
        }

        if(LOG.isDebugEnabled()) {
            LOG.debug("create unique key for " + table.getName() + " columns = " + columns);
        }
        new UniqueNameGenerator().setGeneratedUniqueName(uk);
        table.addUniqueKey(uk);
    }

    private String getColumnNameForPropertyAndPath(PersistentProperty grailsProp,
                                                     String path, ColumnConfig cc, String sessionFactoryBeanName) {

        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);

        // First try the column config.
        String columnName = null;
        if (cc == null) {
            // No column config given, so try to fetch it from the mapping
            PersistentEntity domainClass = grailsProp.getOwner();
            Mapping m = getMapping(domainClass);
            if (m != null) {
                PropertyConfig c = m.getPropertyConfig(grailsProp.getName());

                if (supportsJoinColumnMapping(grailsProp) && hasJoinKeyMapping(c)) {
                    columnName = c.getJoinTable().getKey().getName();
                }
                else if (c != null && c.getColumn() != null) {
                    columnName = c.getColumn();
                }
            }
        }
        else {
            if (supportsJoinColumnMapping(grailsProp)) {
                PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(grailsProp);
                if (hasJoinKeyMapping(pc)) {
                    columnName = pc.getJoinTable().getKey().getName();
                }
                else {
                    columnName = cc.getName();
                }
            }
            else {
                columnName = cc.getName();
            }
        }

        if (columnName == null) {
            if (isNotEmpty(path)) {
                columnName = addUnderscore(namingStrategy.toPhysicalColumnName(toIdentifier(path), getJdbcEnvironment()).toString(),
                        getDefaultColumnName(grailsProp, sessionFactoryBeanName));
            } else {
                columnName = getDefaultColumnName(grailsProp, sessionFactoryBeanName);
            }
        }
        return columnName;
    }

    private boolean hasJoinKeyMapping(PropertyConfig c) {
        return c != null && c.getJoinTable() != null && c.getJoinTable().getKey() != null;
    }

    private boolean supportsJoinColumnMapping(PersistentProperty grailsProp) {
        return grailsProp instanceof ManyToMany || isUnidirectionalOneToMany(grailsProp) || grailsProp instanceof Basic;
    }

    private String getDefaultColumnName(PersistentProperty property, String sessionFactoryBeanName) {

        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);

        String columnName = namingStrategy.toPhysicalColumnName(toIdentifier(property.getName()), getJdbcEnvironment()).toString();
        if (property instanceof Association) {
            Association association = (Association) property;
            boolean isBasic = property instanceof Basic;
            if (isBasic && (new PersistentPropertyToPropertyConfig().apply(property)).getType() != null) {
                return columnName;
            }

            if (isBasic) {
                return getForeignKeyForPropertyDomainClass(property, sessionFactoryBeanName);
            }

            if (property instanceof ManyToMany) {
                return getForeignKeyForPropertyDomainClass(property, sessionFactoryBeanName);
            }

            if (!association.isBidirectional() && association instanceof org.grails.datastore.mapping.model.types.OneToMany) {
                String prefix = namingStrategy.toPhysicalTableName(toIdentifier(property.getOwner().getName()), getJdbcEnvironment()).toString();
                return addUnderscore(prefix, columnName) + FOREIGN_KEY_SUFFIX;
            }

            if (property.isInherited() && isBidirectionalManyToOne(property)) {
                return namingStrategy.toPhysicalColumnName(toIdentifier(property.getOwner().getName()), getJdbcEnvironment()).toString() + '_'+ columnName + FOREIGN_KEY_SUFFIX;
            }

            return columnName + FOREIGN_KEY_SUFFIX;
        }


        return columnName;
    }

    private String getForeignKeyForPropertyDomainClass(PersistentProperty property,
                                                         String sessionFactoryBeanName) {
        final String propertyName = NameUtils.decapitalize( property.getOwner().getName() );
        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);
        return namingStrategy.toPhysicalColumnName(toIdentifier(propertyName), getJdbcEnvironment()).toString() + FOREIGN_KEY_SUFFIX;
    }

    private String getIndexColumnName(PersistentProperty property, String sessionFactoryBeanName) {
        PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(property);
        if (pc != null && pc.getIndexColumn() != null && pc.getIndexColumn().getColumn() != null) {
            return pc.getIndexColumn().getColumn();
        }
        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);
        return namingStrategy.toPhysicalColumnName(toIdentifier(property.getName()), getJdbcEnvironment()).toString() + UNDERSCORE + IndexedCollection.DEFAULT_INDEX_COLUMN_NAME;
    }

    private String getIndexColumnType(PersistentProperty property, String defaultType) {
        PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(property);
        if (pc != null && pc.getIndexColumn() != null && pc.getIndexColumn().getType() != null) {
            PropertyConfig config = pc.getIndexColumn();
            Mapping mapping = getMapping(property.getOwner());
            return new TypeNameProvider().getTypeName(property,config, mapping);
        }
        return defaultType;
    }

    private String getMapElementName(PersistentProperty property, String sessionFactoryBeanName) {
        PropertyConfig pc = new PersistentPropertyToPropertyConfig().apply(property);

        if (hasJoinTableColumnNameMapping(pc)) {
            return pc.getJoinTable().getColumn().getName();
        }

        PhysicalNamingStrategy namingStrategy = getPhysicalNamingStrategy(sessionFactoryBeanName);
        return namingStrategy.toPhysicalColumnName(toIdentifier(property.getName()), getJdbcEnvironment()).toString() + UNDERSCORE + IndexedCollection.DEFAULT_ELEMENT_COLUMN_NAME;
    }

    private boolean hasJoinTableColumnNameMapping(PropertyConfig pc) {
        return pc != null && pc.getJoinTable() != null && pc.getJoinTable().getColumn() != null && pc.getJoinTable().getColumn().getName() != null;
    }


    private void handleUniqueConstraint(PersistentProperty property, Column column, String path, Table table, String columnName, String sessionFactoryBeanName) {
        final PropertyConfig mappedForm = new PersistentPropertyToPropertyConfig().apply(property);
        if (mappedForm.isUnique()) {
            if (!mappedForm.isUniqueWithinGroup()) {
                column.setUnique(true);
            }
            else {
                createKeyForProps(property, path, table, columnName, mappedForm.getUniquenessGroup(), sessionFactoryBeanName);
            }
        }

    }


    private boolean isNotEmpty(String s) {
        return GrailsHibernateUtil.isNotEmpty(s);
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
    /**
     * A Collection type, for the moment only Set is supported
     *
     * @author Graeme
     */
    static abstract class CollectionType {

        private final Class<?> clazz;
        private final GrailsDomainBinder binder;
        private final MetadataBuildingContext buildingContext;

        private CollectionType SET;
        private CollectionType LIST;
        private CollectionType BAG;
        private CollectionType MAP;
        private boolean initialized;

        private final Map<Class<?>, CollectionType> INSTANCES = new HashMap<>();

        public abstract Collection create(ToMany property, PersistentClass owner,
                                          String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException;

        private CollectionType(Class<?> clazz, GrailsDomainBinder binder) {
            this.clazz = clazz;
            this.binder = binder;
            this.buildingContext = binder.getMetadataBuildingContext();
        }

        @Override
        public String toString() {
            return clazz.getName();
        }

        private void createInstances() {

            if (initialized) {
                return;
            }

            initialized = true;

            SET = new CollectionType(Set.class, binder) {
                @Override
                public Collection create(ToMany property, PersistentClass owner,
                                         String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
                    org.hibernate.mapping.Set coll = new org.hibernate.mapping.Set(buildingContext, owner);
                    coll.setCollectionTable(owner.getTable());
                    coll.setTypeName(getTypeName(property));
                    binder.bindCollection(property, coll, owner, mappings, path, sessionFactoryBeanName);
                    return coll;
                }
            };
            INSTANCES.put(Set.class, SET);
            INSTANCES.put(SortedSet.class, SET);

            LIST = new CollectionType(List.class, binder) {
                @Override
                public Collection create(ToMany property, PersistentClass owner,
                                         String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
                    org.hibernate.mapping.List coll = new org.hibernate.mapping.List(buildingContext, owner);
                    coll.setCollectionTable(owner.getTable());
                    coll.setTypeName(getTypeName(property));
                    binder.bindCollection(property, coll, owner, mappings, path, sessionFactoryBeanName);
                    return coll;
                }
            };
            INSTANCES.put(List.class, LIST);

            BAG = new CollectionType(java.util.Collection.class, binder) {
                @Override
                public Collection create(ToMany property, PersistentClass owner,
                                         String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
                    Bag coll = new Bag(buildingContext, owner);
                    coll.setCollectionTable(owner.getTable());
                    coll.setTypeName(getTypeName(property));
                    binder.bindCollection(property, coll, owner, mappings, path, sessionFactoryBeanName);
                    return coll;
                }
            };
            INSTANCES.put(java.util.Collection.class, BAG);

            MAP = new CollectionType(Map.class, binder) {
                @Override
                public Collection create(ToMany property, PersistentClass owner,
                                         String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
                    org.hibernate.mapping.Map map = new org.hibernate.mapping.Map(buildingContext, owner);
                    map.setTypeName(getTypeName(property));
                    binder.bindCollection(property, map, owner, mappings, path, sessionFactoryBeanName);
                    return map;
                }
            };
            INSTANCES.put(Map.class, MAP);
        }

        public CollectionType collectionTypeForClass(Class<?> clazz) {
            createInstances();
            return INSTANCES.get(clazz);
        }

        public String getTypeName(ToMany property) {
            PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(property);
            Mapping mapping = getMapping(property.getOwner());
            return new TypeNameProvider().getTypeName(property,config, mapping);
        }

    }

}
