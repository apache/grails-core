/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg;

import groovy.lang.Closure;
import groovy.transform.Trait;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.transform.trait.Traits;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.*;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.grails.orm.hibernate.access.TraitPropertyAccessStrategy;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.boot.internal.MetadataBuildingContextRootImpl;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.spi.*;
import org.hibernate.cfg.*;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.mapping.*;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.Table;
import org.hibernate.persister.entity.UnionSubclassEntityPersister;
import org.hibernate.type.*;
import org.hibernate.usertype.UserCollectionType;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import jakarta.persistence.Entity;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles the binding Grails domain classes and properties to the Hibernate runtime meta model.
 * Based on the HbmBinder code in Hibernate core and influenced by AnnotationsBinder.
 *
 * @author Graeme Rocher
 * @since 0.1
 */
@SuppressWarnings("WeakerAccess")
public class GrailsDomainBinder implements MetadataContributor {

    protected static final String CASCADE_ALL_DELETE_ORPHAN = "all-delete-orphan";
    protected static final String FOREIGN_KEY_SUFFIX = "_id";
    protected static final String STRING_TYPE = "string";
    protected static final String EMPTY_PATH = "";
    protected static final char UNDERSCORE = '_';
    protected static final String CASCADE_ALL = "all";
    protected static final String CASCADE_SAVE_UPDATE = "save-update";
    protected static final String CASCADE_NONE = "none";
    protected static final String BACKTICK = "`";

    protected static final String ENUM_TYPE_CLASS = "org.hibernate.type.EnumType";
    protected static final String ENUM_CLASS_PROP = "enumClass";
    protected static final String ENUM_TYPE_PROP = "type";
    protected static final String DEFAULT_ENUM_TYPE = "default";
    protected static final Logger LOG = LoggerFactory.getLogger(GrailsDomainBinder.class);
    public static final String SEQUENCE_KEY = "sequence";
    /**
     * Overrideable naming strategy. Defaults to <code>ImprovedNamingStrategy</code> but can
     * be configured in DataSource.groovy via <code>hibernate.naming_strategy = ...</code>.
     */
    public static Map<String, NamingStrategy> NAMING_STRATEGIES = new HashMap<>();
    static {
        NAMING_STRATEGIES.put(ConnectionSource.DEFAULT, ImprovedNamingStrategy.INSTANCE);
    }

    protected final CollectionType CT = new CollectionType(null, this) {
        public Collection create(ToMany property, PersistentClass owner, String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
            return null;
        }
    };

    protected final String sessionFactoryName;
    protected final String dataSourceName;
    protected final HibernateMappingContext hibernateMappingContext;
    protected Closure defaultMapping;
    protected PersistentEntityNamingStrategy namingStrategy;
    protected MetadataBuildingContext metadataBuildingContext;

    public GrailsDomainBinder(
            String dataSourceName,
            String sessionFactoryName,
            HibernateMappingContext hibernateMappingContext) {
        this.sessionFactoryName = sessionFactoryName;
        this.dataSourceName = dataSourceName;
        this.hibernateMappingContext = hibernateMappingContext;
        // pre-build mappings
        for (PersistentEntity persistentEntity : hibernateMappingContext.getPersistentEntities()) {
            evaluateMapping(persistentEntity);
        }
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
        MetadataBuildingOptions options = metadataCollector.getMetadataBuildingOptions();
        ClassLoaderService classLoaderService = options.getServiceRegistry().getService(ClassLoaderService.class);


        this.metadataBuildingContext = new MetadataBuildingContextRootImpl(
                metadataCollector.getBootstrapContext(),
                options,
                metadataCollector
        );

            java.util.Collection<PersistentEntity> persistentEntities = hibernateMappingContext.getPersistentEntities();
        for (PersistentEntity persistentEntity : persistentEntities) {
            if(!persistentEntity.getJavaClass().isAnnotationPresent(Entity.class)) {
                if(ConnectionSourcesSupport.usesConnectionSource(persistentEntity, dataSourceName) && persistentEntity.isRoot()) {
                    bindRoot((HibernatePersistentEntity) persistentEntity, metadataCollector, sessionFactoryName);
                }
            }
        }
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
     * or an instance of a NamingStrategy.
     *
     * @param datasourceName the datasource name
     * @param strategy  the class, name, or instance
     * @throws ClassNotFoundException When the class was not found for specified strategy
     * @throws InstantiationException When an error occurred instantiating the strategy
     * @throws IllegalAccessException When an error occurred instantiating the strategy
     */
    public static void configureNamingStrategy(final String datasourceName, final Object strategy) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Class<?> namingStrategyClass = null;
        NamingStrategy namingStrategy;
        if (strategy instanceof Class<?>) {
            namingStrategyClass = (Class<?>)strategy;
        }
        else if (strategy instanceof CharSequence) {
            namingStrategyClass = Thread.currentThread().getContextClassLoader().loadClass(strategy.toString());
        }

        if (namingStrategyClass == null) {
            namingStrategy = (NamingStrategy)strategy;
        }
        else {
            namingStrategy = (NamingStrategy)namingStrategyClass.newInstance();
        }

        NAMING_STRATEGIES.put(datasourceName, namingStrategy);
    }

    protected void bindMapSecondPass(ToMany property, InFlightMetadataCollector mappings,
                                     Map<?, ?> persistentClasses, org.hibernate.mapping.Map map, String sessionFactoryBeanName) {
        bindCollectionSecondPass(property, mappings, persistentClasses, map, sessionFactoryBeanName);

        SimpleValue value = new SimpleValue(metadataBuildingContext, map.getCollectionTable());

        String type = getIndexColumnType(property, STRING_TYPE);
        String columnName1 = getIndexColumnName(property, sessionFactoryBeanName);
        new SimpleValueBinder().bindSimpleValue(value, type, columnName1, true);
        PropertyConfig pc = getPropertyConfig(property);
        if (pc != null && pc.getIndexColumn() != null) {
            bindColumnConfigToColumn(property, getColumnForSimpleValue(value), getSingleColumnConfig(pc.getIndexColumn()));
        }

        if (!value.isTypeSpecified()) {
            throw new MappingException("map index element must specify a type: " + map.getRole());
        }
        map.setIndex(value);

        if(!(property instanceof org.grails.datastore.mapping.model.types.OneToMany) && !(property instanceof ManyToMany)) {

            SimpleValue elt = new SimpleValue(metadataBuildingContext, map.getCollectionTable());
            map.setElement(elt);

            String typeName = getTypeName(property,getPropertyConfig(property), getMapping(property.getOwner()));
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

    protected ColumnConfig getSingleColumnConfig(PropertyConfig propertyConfig) {
        if (propertyConfig != null) {
            List<ColumnConfig> columns = propertyConfig.getColumns();
            if (columns != null && !columns.isEmpty()) {
                return columns.get(0);
            }
        }
        return null;
    }

    protected void bindListSecondPass(ToMany property, InFlightMetadataCollector mappings,
                                      Map<?, ?> persistentClasses, org.hibernate.mapping.List list, String sessionFactoryBeanName) {

        bindCollectionSecondPass(property, mappings, persistentClasses, list, sessionFactoryBeanName);

        String columnName = getIndexColumnName(property, sessionFactoryBeanName);
        final boolean isManyToMany = property instanceof ManyToMany;

        if (isManyToMany && !property.isOwningSide()) {
            throw new MappingException("Invalid association [" + property +
                    "]. List collection types only supported on the owning side of a many-to-many relationship.");
        }

        Table collectionTable = list.getCollectionTable();
        SimpleValue iv = new SimpleValue(metadataBuildingContext, collectionTable);
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

    protected void bindCollectionSecondPass(ToMany property, InFlightMetadataCollector mappings,
                                            Map<?, ?> persistentClasses, Collection collection, String sessionFactoryBeanName) {

        PersistentClass associatedClass = null;

        if (LOG.isDebugEnabled())
            LOG.debug("Mapping collection: "
                    + collection.getRole()
                    + " -> "
                    + collection.getCollectionTable().getName());

        PropertyConfig propConfig = getPropertyConfig(property);

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
                bindSimpleValue("long", key,false, propConfig.getJoinTable().getKey().getName(), mappings);
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
    protected String buildOrderByClause(String hqlOrderBy, PersistentClass associatedClass, String role, String defaultOrder) {
        String orderByString = null;
        if (hqlOrderBy != null) {
            List<String> properties = new ArrayList<>();
            List<String> ordering = new ArrayList<>();
            StringBuilder orderByBuffer = new StringBuilder();
            if (hqlOrderBy.length() == 0) {
                //order by id
                Iterator<?> it = associatedClass.getIdentifier().getColumnIterator();
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

                    Iterator<?> propertyColumns = p.getColumnIterator();
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

    protected boolean isNonPropertyToken(String token) {
        if (" ".equals(token)) return true;
        if (",".equals(token)) return true;
        if (token.equalsIgnoreCase("desc")) return true;
        if (token.equalsIgnoreCase("asc")) return true;
        return false;
    }

    protected Set<String> buildDiscriminatorSet(HibernatePersistentEntity domainClass) {
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

    protected Mapping getRootMapping(PersistentEntity referenced) {
        if (referenced == null) return null;
        Class<?> current = referenced.getJavaClass();
        while (true) {
            Class<?> superClass = current.getSuperclass();
            if (Object.class.equals(superClass)) break;
            current = superClass;
        }

        return getMapping(current);
    }

    protected boolean isBidirectionalOneToManyMap(Association property) {
        return Map.class.isAssignableFrom(property.getType()) && property.isBidirectional();
    }

    protected void bindCollectionWithJoinTable(ToMany property,
                                               InFlightMetadataCollector mappings, Collection collection, PropertyConfig config, String sessionFactoryBeanName) {

        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);

        SimpleValue element;
        final boolean isBasicCollectionType = property instanceof Basic;
        if (isBasicCollectionType) {
            element = new SimpleValue(metadataBuildingContext, collection.getCollectionTable());
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
                columnName = isEnum ? namingStrategy.propertyToColumnName(className) :
                        addUnderscore(namingStrategy.propertyToColumnName(property.getName()),
                                namingStrategy.propertyToColumnName(className));
            }

            if (isEnum) {
                bindEnumType(property, referencedType,element,columnName);
            }
            else {

                String typeName = getTypeName(property, config, getMapping(property.getOwner()));
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
                    bindColumnConfigToColumn(property, getColumnForSimpleValue(element), config.getJoinTable().getColumn());
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
                    columnName = namingStrategy.propertyToColumnName(NameUtils.decapitalize(domainClass.getName())) + FOREIGN_KEY_SUFFIX;
                }

                new SimpleValueBinder().bindSimpleValue(element, "long", columnName, true);
            }
        }

        collection.setElement(element);

        bindCollectionForPropertyConfig(collection, config);
    }

    protected String addUnderscore(String s1, String s2) {
        return removeBackticks(s1) + UNDERSCORE + removeBackticks(s2);
    }

    protected String removeBackticks(String s) {
        return s.startsWith("`") && s.endsWith("`") ? s.substring(1, s.length() - 1) : s;
    }

    protected Column getColumnForSimpleValue(SimpleValue element) {
        return (Column)element.getColumnIterator().next();
    }

    protected String getTypeName(PersistentProperty property, PropertyConfig config, Mapping mapping) {
        if (config != null && config.getType() != null) {
            final Object typeObj = config.getType();
            if (typeObj instanceof Class<?>) {
                return ((Class<?>)typeObj).getName();
            }
            return typeObj.toString();
        }

        if (mapping != null) {
            return mapping.getTypeName(property.getType());
        }

        return null;
    }

    protected void bindColumnConfigToColumn(PersistentProperty property, Column column, ColumnConfig columnConfig) {
        final PropertyConfig mappedForm = property != null ? (PropertyConfig) property.getMapping().getMappedForm() : null;
        boolean allowUnique = mappedForm != null && !mappedForm.isUniqueWithinGroup();

        if (columnConfig == null) {
            return;
        }

        if (columnConfig.getLength() != -1) {
            column.setLength(columnConfig.getLength());
        }
        if (columnConfig.getPrecision() != -1) {
            column.setPrecision(columnConfig.getPrecision());
        }
        if (columnConfig.getScale() != -1) {
            column.setScale(columnConfig.getScale());
        }
        if (columnConfig.getSqlType() != null && !columnConfig.getSqlType().isEmpty()) {
            column.setSqlType(columnConfig.getSqlType());
        }
        if(allowUnique) {
            column.setUnique(columnConfig.getUnique());
        }
    }

    protected boolean hasJoinColumnMapping(PropertyConfig config) {
        return config != null && config.getJoinTable() != null && config.getJoinTable().getColumn() != null;
    }

    protected boolean shouldCollectionBindWithJoinColumn(ToMany property) {
        PropertyConfig config = getPropertyConfig(property);
        JoinTable jt = config != null ? config.getJoinTable() : new JoinTable();

        return (isUnidirectionalOneToMany(property) || (property instanceof Basic)) && jt != null;
    }

    /**
     * @param property The property to bind
     * @param manyToOne The inverse side
     */
    protected void bindUnidirectionalOneToManyInverseValues(ToMany property, ManyToOne manyToOne) {
        PropertyConfig config = getPropertyConfig(property);
        if (config == null) {
            manyToOne.setLazy(true);
        } else {
            manyToOne.setIgnoreNotFound(config.getIgnoreNotFound());
            final FetchMode fetch = config.getFetchMode();
            if(!fetch.equals(FetchMode.JOIN) && !fetch.equals(FetchMode.EAGER)) {
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

    protected void bindCollectionForPropertyConfig(Collection collection, PropertyConfig config) {
        if (config == null) {
            collection.setLazy(true);
            collection.setExtraLazy(false);
        } else {
            final FetchMode fetch = config.getFetchMode();
            if(!fetch.equals(FetchMode.JOIN) && !fetch.equals(FetchMode.EAGER)) {
                collection.setLazy(true);
            }
            final Boolean lazy = config.getLazy();
            if(lazy != null) {
                collection.setExtraLazy(lazy);
            }
        }
    }

    public PropertyConfig getPropertyConfig(PersistentProperty property) {
        return (PropertyConfig) property.getMapping().getMappedForm();
    }

    /**
     * Checks whether a property is a unidirectional non-circular one-to-many
     *
     * @param property The property to check
     * @return true if it is unidirectional and a one-to-many
     */
    protected boolean isUnidirectionalOneToMany(PersistentProperty property) {
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
    protected void bindDependentKeyValue(PersistentProperty property, DependantValue key,
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
    protected DependantValue createPrimaryKeyValue(InFlightMetadataCollector mappings, PersistentProperty property,
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
        return key;
    }

    /**
     * Binds a unidirectional one-to-many creating a psuedo back reference property in the process.
     *
     * @param property
     * @param mappings
     * @param collection
     */
    protected void bindUnidirectionalOneToMany(org.grails.datastore.mapping.model.types.OneToMany property, InFlightMetadataCollector mappings, Collection collection) {
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

    protected Property getProperty(PersistentClass associatedClass, String propertyName) throws MappingException {
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
    protected void linkBidirectionalOneToMany(Collection collection, PersistentClass associatedClass, DependantValue key, PersistentProperty otherSide) {
        collection.setInverse(true);

        // Iterator mappedByColumns = associatedClass.getProperty(otherSide.getName()).getValue().getColumnIterator();
        Iterator<?> mappedByColumns = getProperty(associatedClass, otherSide.getName()).getValue().getColumnIterator();
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
    protected boolean isSorted(PersistentProperty property) {
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
    protected void bindManyToMany(Association property, ManyToOne element,
                                  InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        bindManyToOne(property, element, EMPTY_PATH, mappings, sessionFactoryBeanName);
        element.setReferencedEntityName(property.getOwner().getName());
    }

    protected void linkValueUsingAColumnCopy(PersistentProperty prop, Column column, DependantValue key) {
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
    protected void bindCollection(ToMany property, Collection collection,
                                  PersistentClass owner, InFlightMetadataCollector mappings, String path, String sessionFactoryBeanName) {

        // set role
        String propertyName = getNameForPropertyAndPath(property, path);
        collection.setRole(qualify(property.getOwner().getName(), propertyName));

        PropertyConfig pc = getPropertyConfig(property);
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
    protected boolean shouldBindCollectionWithForeignKey(ToMany property) {
        return ((property instanceof org.grails.datastore.mapping.model.types.OneToMany) && property.isBidirectional() ||
                !shouldCollectionBindWithJoinColumn(property)) &&
                !Map.class.isAssignableFrom(property.getType()) &&
                !(property instanceof ManyToMany) &&
                !(property instanceof Basic);
    }

    protected String getNameForPropertyAndPath(PersistentProperty property, String path) {
        if (isNotEmpty(path)) {
            return qualify(path, property.getName());
        }
        return property.getName();
    }

    protected void bindCollectionTable(ToMany property, InFlightMetadataCollector mappings,
                                       Collection collection, Table ownerTable, String sessionFactoryBeanName) {

        String owningTableSchema = ownerTable.getSchema();
        PropertyConfig config = getPropertyConfig(property);
        JoinTable jt = config != null ? config.getJoinTable() : null;

        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);
        String tableName = (jt != null && jt.getName() != null ? jt.getName() : namingStrategy.tableName(calculateTableForMany(property, sessionFactoryBeanName)));
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
                tableName, null, false));
    }

    /**
     * Calculates the mapping table for a many-to-many. One side of
     * the relationship has to "own" the relationship so that there is not a situation
     * where you have two mapping tables for left_right and right_left
     */
    protected String calculateTableForMany(ToMany property, String sessionFactoryBeanName) {
        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);

        String propertyColumnName = namingStrategy.propertyToColumnName(property.getName());
        //fix for GRAILS-5895
        PropertyConfig config = getPropertyConfig(property);
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

        if (property instanceof ManyToMany) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            if (property.isOwningSide()) {
                return addUnderscore(left, propertyColumnName);
            }
            return addUnderscore(right, namingStrategy.propertyToColumnName(((ManyToMany) property).getInversePropertyName()));
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

    protected String trimBackTigs(String tableName) {
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
    protected String getTableName(PersistentEntity domainClass, String sessionFactoryBeanName) {
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
                tableName = getNamingStrategy(sessionFactoryBeanName).classToTableName(shortName);
            }
        }
        return tableName;
    }

    protected NamingStrategy getNamingStrategy(String sessionFactoryBeanName) {
        String key = "sessionFactory".equals(sessionFactoryBeanName) ?
                ConnectionSource.DEFAULT :
                sessionFactoryBeanName.substring("sessionFactory_".length());
        NamingStrategy namingStrategy = NAMING_STRATEGIES.get(key);
        return namingStrategy != null ? namingStrategy : new ImprovedNamingStrategy();
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

    /**
     * Evaluates a Mapping object from the domain class if it has a mapping closure
     *
     * @param domainClass The domain class
     * @return the mapping
     */
    public Mapping evaluateMapping(PersistentEntity domainClass) {
        return evaluateMapping(domainClass, null);
    }

    public Mapping evaluateMapping(PersistentEntity domainClass, Closure<?> defaultMapping) {
        return evaluateMapping(domainClass, defaultMapping, true);
    }

    public Mapping evaluateMapping(PersistentEntity domainClass, Closure<?> defaultMapping, boolean cache) {
        try {
            final Mapping m = (Mapping) domainClass.getMapping().getMappedForm();
            trackCustomCascadingSaves(m, domainClass.getPersistentProperties());
            if (cache) {
                AbstractGrailsDomainBinder.cacheMapping(domainClass.getJavaClass(), m);
            }
            return m;
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
    protected void trackCustomCascadingSaves(Mapping mapping, Iterable<PersistentProperty> persistentProperties) {
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
    protected boolean isSaveUpdateCascade(String cascade) {
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
    public static Mapping getMapping(Class<?> theClass) {
        return AbstractGrailsDomainBinder.getMapping(theClass);
    }

    /**
     * Obtains a mapping object for the given domain class nam
     *
     * @param domainClass The domain class in question
     * @return A Mapping object or null
     */
    public static Mapping getMapping(PersistentEntity domainClass) {
        return domainClass == null ? null : AbstractGrailsDomainBinder.getMapping(domainClass.getJavaClass());
    }

    public static void clearMappingCache() {
        AbstractGrailsDomainBinder.clearMappingCache();
    }

    public static void clearMappingCache(Class<?> theClass) {
        // no-op, here for compatibility
    }

    /**
     * Binds the specified persistant class to the runtime model based on the
     * properties defined in the domain class
     *
     * @param domainClass     The Grails domain class
     * @param persistentClass The persistant class
     * @param mappings        Existing mappings
     */
    protected void bindClass(PersistentEntity domainClass, PersistentClass persistentClass, InFlightMetadataCollector mappings) {

        boolean autoImport = mappings.getMetadataBuildingOptions().getMappingDefaults().isAutoImportEnabled();
        org.grails.datastore.mapping.config.Entity mappedForm = domainClass.getMapping().getMappedForm();
        if (mappedForm instanceof Mapping) {
            autoImport = ((Mapping) mappedForm).isAutoImport();
        }

        // set lazy loading for now
        persistentClass.setLazy(true);
        final String entityName = domainClass.getName();
        persistentClass.setEntityName(entityName);
        persistentClass.setJpaEntityName(autoImport ? unqualify(entityName) : entityName);
        persistentClass.setProxyInterfaceName(entityName);
        persistentClass.setClassName(entityName);

        // set dynamic insert to false
        persistentClass.setDynamicInsert(false);
        // set dynamic update to false
        persistentClass.setDynamicUpdate(false);
        // set select before update to false
        persistentClass.setSelectBeforeUpdate(false);

        // add import to mappings
        String en = persistentClass.getEntityName();

        if (autoImport && en.indexOf('.') > 0) {
            String unqualified = unqualify(en);
            mappings.addImport(unqualified, en);
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
    public void bindRoot(HibernatePersistentEntity entity, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        if (mappings.getEntityBinding(entity.getName()) != null) {
            LOG.info("[GrailsDomainBinder] Class [" + entity.getName() + "] is already mapped, skipping.. ");
            return;
        }

        RootClass root = new RootClass(this.metadataBuildingContext);
        root.setAbstract(entity.isAbstract());
        final MappingContext mappingContext = entity.getMappingContext();



        final java.util.Collection<PersistentEntity> children = mappingContext.getDirectChildEntities(entity);
        if (children.isEmpty()) {
            root.setPolymorphic(false);
        }
        bindClass(entity, root, mappings);

        Mapping m = getMapping(entity);

        bindRootPersistentClassCommonValues(entity, root, mappings, sessionFactoryBeanName);

        if (!children.isEmpty()) {
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
    protected void addMultiTenantFilterIfNecessary(
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

                mappings.addFilterDefinition(new FilterDefinition(
                        GormProperties.TENANT_IDENTITY,
                        filterCondition,
                        Collections.singletonMap(GormProperties.TENANT_IDENTITY, getProperty(persistentClass, tenantId.getName()).getType())
                ));
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
    protected void bindSubClasses(HibernatePersistentEntity domainClass, PersistentClass parent,
                                  InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        final java.util.Collection<PersistentEntity> subClasses = domainClass.getMappingContext().getDirectChildEntities(domainClass);

        for (PersistentEntity sub : subClasses) {
            final Class javaClass = sub.getJavaClass();
            if (javaClass.getSuperclass().equals(domainClass.getJavaClass()) && ConnectionSourcesSupport.usesConnectionSource(sub, dataSourceName)) {
                bindSubClass((HibernatePersistentEntity)sub, parent, mappings, sessionFactoryBeanName);
            }
        }
    }

    /**
     * Binds a sub class.
     *
     * @param sub      The sub domain class instance
     * @param parent   The parent persistent class instance
     * @param mappings The mappings instance
     * @param sessionFactoryBeanName  the session factory bean name
     */
    protected void bindSubClass(HibernatePersistentEntity sub, PersistentClass parent,
                                InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        evaluateMapping(sub, defaultMapping);
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

            if (subMapping != null) {
                configureDerivedProperties(sub, subMapping);
            }
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


    public void bindUnionSubclass(HibernatePersistentEntity subClass, UnionSubclass unionSubclass,
                                  InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
        bindClass(subClass, unionSubclass, mappings);

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
                denormalizedSuperTable
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
    protected void bindJoinedSubClass(HibernatePersistentEntity sub, JoinedSubclass joinedSubclass,
                                      InFlightMetadataCollector mappings, Mapping gormMapping, String sessionFactoryBeanName) {
        bindClass(sub, joinedSubclass, mappings);

        String schemaName = getSchemaName(mappings);
        String catalogName = getCatalogName(mappings);

        Table mytable = mappings.addTable(
                schemaName, catalogName,
                getJoinedSubClassTableName(sub, joinedSubclass, null, mappings, sessionFactoryBeanName),
                null, false);

        joinedSubclass.setTable(mytable);
        LOG.info("Mapping joined-subclass: " + joinedSubclass.getEntityName() +
                " -> " + joinedSubclass.getTable().getName());

        SimpleValue key = new DependantValue(metadataBuildingContext, mytable, joinedSubclass.getIdentifier());
        joinedSubclass.setKey(key);
        final PersistentProperty identifier = sub.getIdentity();
        String columnName = getColumnNameForPropertyAndPath(identifier, EMPTY_PATH, null, sessionFactoryBeanName);
        bindSimpleValue(identifier.getType().getName(), key, false, columnName, mappings);

        joinedSubclass.createPrimaryKey();
        joinedSubclass.createForeignKey();

        // properties
        createClassProperties(sub, joinedSubclass, mappings, sessionFactoryBeanName);
    }

    protected String getJoinedSubClassTableName(
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
    protected void bindSubClass(HibernatePersistentEntity sub, Subclass subClass, InFlightMetadataCollector mappings,
                                String sessionFactoryBeanName) {
        bindClass(sub, subClass, mappings);

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
    protected void bindDiscriminatorProperty(Table table, RootClass entity, InFlightMetadataCollector mappings) {
        Mapping m = getMapping(entity.getMappedClass());
        SimpleValue d = new SimpleValue(metadataBuildingContext, table);
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
            bindSimpleValue(STRING_TYPE, d, false, RootClass.DEFAULT_DISCRIMINATOR_COLUMN_NAME, mappings);

            ColumnConfig cc = !hasDiscriminatorConfig ? null : discriminatorConfig.getColumn();
            if (cc != null) {
                Column c = (Column) d.getColumnIterator().next();
                if (cc.getName() != null) {
                    c.setName(cc.getName());
                }
                bindColumnConfigToColumn(null, c, cc);
            }
        }

        entity.setPolymorphic(true);
    }

    protected void configureDerivedProperties(PersistentEntity domainClass, Mapping m) {
        for (PersistentProperty prop : domainClass.getPersistentProperties()) {
            PropertyConfig propertyConfig = m.getPropertyConfig(prop.getName());
            if (propertyConfig != null && propertyConfig.getFormula() != null) {
                propertyConfig.setDerived(true);
            }
        }
    }

    /*
     * Binds a persistent classes to the table representation and binds the class properties
     */
    protected void bindRootPersistentClassCommonValues(HibernatePersistentEntity domainClass,
                                                       RootClass root, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        // get the schema and catalog names from the configuration
        Mapping m = getMapping(domainClass.getJavaClass());

        String schema = getSchemaName(mappings);
        String catalog = getCatalogName(mappings);

        if (m != null) {
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

            Integer bs = m.getBatchSize();
            if (bs != null) {
                root.setBatchSize(bs);
            }

            if (m.getDynamicUpdate()) {
                root.setDynamicUpdate(true);
            }
            if (m.getDynamicInsert()) {
                root.setDynamicInsert(true);
            }
        }

        final boolean hasTableDefinition = m != null && m.getTable() != null;
        if (hasTableDefinition && m.getTable().getSchema() != null) {
            schema =  m.getTable().getSchema();
        }
        if (hasTableDefinition && m.getTable().getCatalog() != null) {
            catalog = m.getTable().getCatalog();
        }

        final boolean isAbstract = m != null && !m.getTablePerHierarchy() && m.isTablePerConcreteClass() && root.isAbstract();
        // create the table
        Table table = mappings.addTable(schema, catalog,
                getTableName(domainClass, sessionFactoryBeanName),
                null, isAbstract);
        root.setTable(table);

        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsDomainBinder] Mapping Grails domain class: " + domainClass.getName() + " -> " + root.getTable().getName());
        }

        bindIdentity(domainClass, root, mappings, m, sessionFactoryBeanName);

        if (m == null) {
            bindVersion(domainClass.getVersion(), root, mappings, sessionFactoryBeanName);
        }
        else {
            if (m.getVersioned()) {
                bindVersion(domainClass.getVersion(), root, mappings, sessionFactoryBeanName);
            }
            else {
                root.setOptimisticLockStyle(OptimisticLockStyle.NONE);
            }
        }

        root.createPrimaryKey();

        createClassProperties(domainClass, root, mappings, sessionFactoryBeanName);
    }



    protected void bindIdentity(
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

    protected void bindCompositeId(PersistentEntity domainClass, RootClass root,
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
    protected void createClassProperties(HibernatePersistentEntity domainClass, PersistentClass persistentClass,
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
                value = new SimpleValue(metadataBuildingContext, table);
                bindSimpleValue(currentGrailsProp, null, (SimpleValue) value, EMPTY_PATH, mappings, sessionFactoryBeanName);
            }
            else if (collectionType != null) {
                String typeName = getTypeName(currentGrailsProp, getPropertyConfig(currentGrailsProp),gormMapping);
                if ("serializable".equals(typeName)) {
                    value = new SimpleValue(metadataBuildingContext, table);
                    bindSimpleValue(typeName, (SimpleValue) value, currentGrailsProp.isNullable(),
                            getColumnNameForPropertyAndPath(currentGrailsProp, EMPTY_PATH, null, sessionFactoryBeanName), mappings);
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
                value = new SimpleValue(metadataBuildingContext, table);
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
                value = new SimpleValue(metadataBuildingContext, table);
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

    protected void bindNaturalIdentifier(Table table, Mapping mapping, PersistentClass persistentClass) {
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

            uk.addColumns(property.getColumnIterator());
        }

        setGeneratedUniqueName(uk);

        table.addUniqueKey(uk);
    }

    protected void setGeneratedUniqueName(UniqueKey uk) {
        StringBuilder sb = new StringBuilder(uk.getTable().getName()).append('_');
        for (Object col : uk.getColumns()) {
            sb.append(((Column) col).getName()).append('_');
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            md.update(sb.toString().getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        String name = "UK" + new BigInteger(1, md.digest()).toString(16);
        if (name.length() > 30) {
            // Oracle has a 30-char limit
            name = name.substring(0, 30);
        }

        uk.setName(name);
    }

    protected boolean canBindOneToOneWithSingleColumnAndForeignKey(Association currentGrailsProp) {
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

    protected boolean isIdentityProperty(Mapping gormMapping, PersistentProperty currentGrailsProp) {
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

    protected void bindEnumType(PersistentProperty property, SimpleValue simpleValue,
                                String path, String sessionFactoryBeanName) {
        bindEnumType(property, property.getType(), simpleValue,
                getColumnNameForPropertyAndPath(property, path, null, sessionFactoryBeanName));
    }

    protected void bindEnumType(PersistentProperty property, Class<?> propertyType, SimpleValue simpleValue, String columnName) {

        PropertyConfig pc = getPropertyConfig(property);
        final PersistentEntity owner = property.getOwner();
        String typeName = getTypeName(property, getPropertyConfig(property), getMapping(owner));
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

        PropertyConfig propertyConfig = getPropertyConfig(property);
        if (propertyConfig != null && !propertyConfig.getColumns().isEmpty()) {
            bindIndex(columnName, column, propertyConfig.getColumns().get(0), t);
            bindColumnConfigToColumn(property, column, propertyConfig.getColumns().get(0));
        }
    }

    protected Class<?> getUserType(PersistentProperty currentGrailsProp) {
        Class<?> userType = null;
        PropertyConfig config = getPropertyConfig(currentGrailsProp);
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

    protected boolean isCompositeIdProperty(Mapping gormMapping, PersistentProperty currentGrailsProp) {
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

    protected boolean isBidirectionalManyToOne(PersistentProperty currentGrailsProp) {
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
    protected void bindComponent(Component component, Embedded property,
                                 boolean isNullable, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        component.setEmbedded(true);
        Class<?> type = property.getType();
        String role = qualify(type.getName(), property.getName());
        component.setRoleName(role);
        component.setComponentClassName(type.getName());

        PersistentEntity domainClass = property.getAssociatedEntity();
        evaluateMapping(domainClass, defaultMapping);
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

    protected void bindComponentProperty(Component component, PersistentProperty componentProperty,
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

            value = new SimpleValue(metadataBuildingContext, table);
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
                final Iterator<?> columnIterator = value.getColumnIterator();
                while (columnIterator.hasNext()) {
                    Column c = (Column) columnIterator.next();
                    c.setNullable(true);
                }
            }
        }
    }

    protected boolean isComponentPropertyNullable(PersistentProperty componentProperty) {
        if (componentProperty == null) return false;
        final PersistentEntity domainClass = componentProperty.getOwner();
        final Mapping mapping = getMapping(domainClass.getJavaClass());
        return !domainClass.isRoot() && (mapping == null || mapping.isTablePerHierarchy()) || componentProperty.isNullable();
    }

    /*
     * Creates a persistent class property based on the GrailDomainClassProperty instance.
     */
    protected Property createProperty(Value value, PersistentClass persistentClass, PersistentProperty grailsProperty, InFlightMetadataCollector mappings) {
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

    protected void bindOneToMany(org.grails.datastore.mapping.model.types.OneToMany currentGrailsProp, OneToMany one, InFlightMetadataCollector mappings) {
        one.setReferencedEntityName(currentGrailsProp.getAssociatedEntity().getName());
        one.setIgnoreNotFound(true);
    }

    /**
     * Binds a many-to-one relationship to the
     *
     */
    @SuppressWarnings("unchecked")
    protected void bindManyToOne(Association property, ManyToOne manyToOne,
                                 String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);

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
                PropertyConfig pc = getPropertyConfig(property);

                if (pc.getColumns().isEmpty()) {
                    mapping.getColumns().put(property.getName(), pc);
                }
                if (!hasJoinKeyMapping(pc) ) {
                    JoinTable jt = new JoinTable();
                    final ColumnConfig columnConfig = new ColumnConfig();
                    columnConfig.setName(namingStrategy.propertyToColumnName(property.getName()) +
                            UNDERSCORE + FOREIGN_KEY_SUFFIX);
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

        PropertyConfig config = getPropertyConfig(property);
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

    protected void bindCompositeIdentifierToManyToOne(Association property,
                                                      SimpleValue value, CompositeIdentity compositeId, PersistentEntity refDomainClass,
                                                      String path, String sessionFactoryBeanName) {

        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);

        String[] propertyNames = compositeId.getPropertyNames();
        PropertyConfig config = getPropertyConfig(property);

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
                                String compositeIdPrefix = addUnderscore(prefix, namingStrategy.propertyToColumnName(referencedProperty.getName()));
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

    protected boolean hasCompositeIdentifier(Mapping mapping) {
        return mapping != null && (mapping.getIdentity() instanceof CompositeIdentity);
    }

    protected void bindOneToOne(final org.grails.datastore.mapping.model.types.OneToOne property, OneToOne oneToOne,
                                String path, String sessionFactoryBeanName) {
        PropertyConfig config = getPropertyConfig(property);
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
            PropertyConfig pc = getPropertyConfig(property);
            bindSimpleValue(property, oneToOne, path, pc, sessionFactoryBeanName);
        }
        else {
            oneToOne.setReferencedPropertyName(otherSide.getName());
        }
    }

    protected void bindOneToOneInternal(org.grails.datastore.mapping.model.types.OneToOne property, OneToOne oneToOne, String path) {
        //no-op, for subclasses to extend
    }

    /**
     */
    protected void bindManyToOneValues(org.grails.datastore.mapping.model.types.Association property, ManyToOne manyToOne) {
        PropertyConfig config = getPropertyConfig(property);

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

    protected void bindVersion(PersistentProperty version, RootClass entity,
                               InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        if (version != null) {

            SimpleValue val = new SimpleValue(metadataBuildingContext, entity.getTable());

            bindSimpleValue(version, null, val, EMPTY_PATH, mappings, sessionFactoryBeanName);

            if (val.isTypeSpecified()) {
                if (!(val.getType() instanceof IntegerType ||
                        val.getType() instanceof LongType ||
                        val.getType() instanceof TimestampType)) {
                    LOG.warn("Invalid version class specified in " + version.getOwner().getName() +
                            "; must be one of [int, Integer, long, Long, Timestamp, Date]. Not mapping the version.");
                    return;
                }
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
    protected void bindSimpleId(PersistentProperty identifier, RootClass entity,
                                InFlightMetadataCollector mappings, Identity mappedId, String sessionFactoryBeanName) {

        Mapping mapping = getMapping(identifier.getOwner());
        boolean useSequence = mapping != null && mapping.isTablePerConcreteClass();

        // create the id value
        SimpleValue id = new SimpleValue(metadataBuildingContext, entity.getTable());
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

        id.getTable().setIdentifierValue(id);
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
    protected void bindProperty(PersistentProperty grailsProperty, Property prop, InFlightMetadataCollector mappings) {
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
                grailsProperty.getMapping().getMappedForm().getAccessType()
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

    protected boolean getLaziness(PersistentProperty grailsProperty) {
        PropertyConfig config = getPropertyConfig(grailsProperty);
        final Boolean lazy = config.getLazy();
        if(lazy == null && grailsProperty instanceof Association) {
            return true;
        }
        else if(lazy != null) {
            return lazy;
        }
        return false;
    }

    protected boolean getInsertableness(PersistentProperty grailsProperty) {
        PropertyConfig config = getPropertyConfig(grailsProperty);
        return config == null || config.getInsertable();
    }

    protected boolean getUpdateableness(PersistentProperty grailsProperty) {
        PropertyConfig config = getPropertyConfig(grailsProperty);
        return config == null || config.getUpdatable();
    }

    protected boolean isBidirectionalManyToOneWithListMapping(PersistentProperty grailsProperty, Property prop) {
        if(grailsProperty instanceof Association) {

            Association association = (Association) grailsProperty;
            Association otherSide = association.getInverseSide();
            return association.isBidirectional() && otherSide != null &&
                    prop.getValue() instanceof ManyToOne &&
                    List.class.isAssignableFrom(otherSide.getType());
        }
        return false;
    }

    protected void setCascadeBehaviour(PersistentProperty grailsProperty, Property prop) {
        String cascadeStrategy = "none";
        // set to cascade all for the moment
        PersistentEntity domainClass = grailsProperty.getOwner();
        PropertyConfig config = getPropertyConfig(grailsProperty);
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

    protected boolean isCircularAssociation(PersistentProperty grailsProperty) {
        return grailsProperty.getType().equals(grailsProperty.getOwner().getJavaClass());
    }

    protected void logCascadeMapping(Association grailsProperty, String cascadeStrategy, PersistentEntity referenced) {
        if (LOG.isDebugEnabled() & referenced != null) {
            String assType = getAssociationDescription(grailsProperty);
            LOG.debug("Mapping cascade strategy for " + assType + " property " + grailsProperty.getOwner().getName() + "." + grailsProperty.getName() + " referencing type [" + referenced.getJavaClass().getName() + "] -> [CASCADE: " + cascadeStrategy + "]");
        }
    }

    protected String getAssociationDescription(Association grailsProperty) {
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
    protected void bindSimpleValue(PersistentProperty property, PersistentProperty parentProperty,
                                   SimpleValue simpleValue, String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        // set type
        bindSimpleValue(property,parentProperty, simpleValue, path, getPropertyConfig(property), sessionFactoryBeanName);
    }

    protected void bindSimpleValue(PersistentProperty grailsProp, SimpleValue simpleValue,
                                   String path, PropertyConfig propertyConfig, String sessionFactoryBeanName) {
        bindSimpleValue(grailsProp, null, simpleValue, path, propertyConfig, sessionFactoryBeanName);
    }

    protected void bindSimpleValue(PersistentProperty grailsProp,
                                   PersistentProperty parentProperty, SimpleValue simpleValue,
                                   String path, PropertyConfig propertyConfig, String sessionFactoryBeanName) {
        setTypeForPropertyConfig(grailsProp, simpleValue, propertyConfig);
        final PropertyConfig mappedForm = (PropertyConfig) grailsProp.getMapping().getMappedForm();
        if (mappedForm.isDerived() && !(grailsProp instanceof TenantId)) {
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
            List<?> columnDefinitions = hasConfig ? propertyConfig.getColumns()
                    : Arrays.asList(new Object[] { null });
            if (columnDefinitions.isEmpty()) {
                columnDefinitions = Arrays.asList(new Object[] { null });
            }

            for (Object columnDefinition : columnDefinitions) {
                ColumnConfig cc = (ColumnConfig) columnDefinition;
                Column column = new Column();

                // Check for explicitly mapped column name and SQL type.
                if (cc != null) {
                    if (cc.getName() != null) {
                        column.setName(cc.getName());
                    }
                    if (cc.getSqlType() != null) {
                        column.setSqlType(cc.getSqlType());
                    }
                }

                column.setValue(simpleValue);


                if (cc != null) {
                    if (cc.getLength() != -1) {
                        column.setLength(cc.getLength());
                    }
                    if (cc.getPrecision() != -1) {
                        column.setPrecision(cc.getPrecision());
                    }
                    if (cc.getScale() != -1) {
                        column.setScale(cc.getScale());
                    }
                    if(!mappedForm.isUniqueWithinGroup()) {
                        column.setUnique(cc.isUnique());
                    }
                }

                bindColumn(grailsProp, parentProperty, column, cc, path, table, sessionFactoryBeanName);

                if (table != null) {
                    table.addColumn(column);
                }

                simpleValue.addColumn(column);
            }
        }
    }

    protected void setTypeForPropertyConfig(PersistentProperty grailsProp, SimpleValue simpleValue, PropertyConfig config) {
        final String typeName = getTypeName(grailsProp, getPropertyConfig(grailsProp), getMapping(grailsProp.getOwner()));
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
    protected void bindColumn(PersistentProperty property, PersistentProperty parentProperty,
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

            // Use the constraints for this property to more accurately define
            // the column's length, precision, and scale
            if (String.class.isAssignableFrom(property.getType()) || byte[].class.isAssignableFrom(property.getType())) {
                bindStringColumnConstraints(column, property);
            }

            if (Number.class.isAssignableFrom(property.getType())) {
                bindNumericColumnConstraints(column, property, cc);
            }
        }

        handleUniqueConstraint(property, column, path, table, columnName, sessionFactoryBeanName);

        bindIndex(columnName, column, cc, table);

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


    protected void createKeyForProps(PersistentProperty grailsProp, String path, Table table,
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

    protected void createUniqueKeyForColumns(Table table, String columnName, List<Column> columns) {
        Collections.reverse(columns);

        UniqueKey uk = new UniqueKey();
        uk.setTable(table);
        uk.addColumns(columns.iterator());

        if(LOG.isDebugEnabled()) {
            LOG.debug("create unique key for " + table.getName() + " columns = " + columns);
        }
        setGeneratedUniqueName(uk);
        table.addUniqueKey(uk);
    }

    protected void bindIndex(String columnName, Column column, ColumnConfig cc, Table table) {
        if (cc == null) {
            return;
        }

        Object indexObj = cc.getIndex();
        String indexDefinition = null;
        if (indexObj instanceof Boolean) {
            Boolean b = (Boolean) indexObj;
            if (b) {
                indexDefinition = table.getName() + '_' + columnName + "_idx";
            }
        }
        else if (indexObj != null) {
            indexDefinition = indexObj.toString();
        }
        if (indexDefinition == null) {
            return;
        }

        String[] tokens = indexDefinition.split(",");
        for (String index : tokens) {
            table.getOrCreateIndex(index).addColumn(column);
        }
    }

    protected String getColumnNameForPropertyAndPath(PersistentProperty grailsProp,
                                                     String path, ColumnConfig cc, String sessionFactoryBeanName) {

        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);

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
                PropertyConfig pc = getPropertyConfig(grailsProp);
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
                columnName = addUnderscore(namingStrategy.propertyToColumnName(path),
                        getDefaultColumnName(grailsProp, sessionFactoryBeanName));
            } else {
                columnName = getDefaultColumnName(grailsProp, sessionFactoryBeanName);
            }
        }
        return columnName;
    }

    protected boolean hasJoinKeyMapping(PropertyConfig c) {
        return c != null && c.getJoinTable() != null && c.getJoinTable().getKey() != null;
    }

    protected boolean supportsJoinColumnMapping(PersistentProperty grailsProp) {
        return grailsProp instanceof ManyToMany || isUnidirectionalOneToMany(grailsProp) || grailsProp instanceof Basic;
    }

    protected String getDefaultColumnName(PersistentProperty property, String sessionFactoryBeanName) {

        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);

        String columnName = namingStrategy.propertyToColumnName(property.getName());
        if (property instanceof Association) {
            Association association = (Association) property;
            boolean isBasic = property instanceof Basic;
            if(isBasic && ((PropertyConfig)property.getMapping().getMappedForm()).getType() != null ) {
                return columnName;
            }

            if (isBasic) {
                return getForeignKeyForPropertyDomainClass(property, sessionFactoryBeanName);
            }

            if (property instanceof ManyToMany) {
                return getForeignKeyForPropertyDomainClass(property, sessionFactoryBeanName);
            }

            if (!association.isBidirectional() && association instanceof org.grails.datastore.mapping.model.types.OneToMany) {
                String prefix = namingStrategy.classToTableName(property.getOwner().getName());
                return addUnderscore(prefix, columnName) + FOREIGN_KEY_SUFFIX;
            }

            if (property.isInherited() && isBidirectionalManyToOne(property)) {
                return namingStrategy.propertyToColumnName(property.getOwner().getName()) + '_'+ columnName + FOREIGN_KEY_SUFFIX;
            }

            return columnName + FOREIGN_KEY_SUFFIX;
        }


        return columnName;
    }

    protected String getForeignKeyForPropertyDomainClass(PersistentProperty property,
                                                         String sessionFactoryBeanName) {
        final String propertyName = NameUtils.decapitalize( property.getOwner().getName() );
        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);
        return namingStrategy.propertyToColumnName(propertyName) + FOREIGN_KEY_SUFFIX;
    }

    protected String getIndexColumnName(PersistentProperty property, String sessionFactoryBeanName) {
        PropertyConfig pc = getPropertyConfig(property);
        if (pc != null && pc.getIndexColumn() != null && pc.getIndexColumn().getColumn() != null) {
            return pc.getIndexColumn().getColumn();
        }
        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);
        return namingStrategy.propertyToColumnName(property.getName()) + UNDERSCORE + IndexedCollection.DEFAULT_INDEX_COLUMN_NAME;
    }

    protected String getIndexColumnType(PersistentProperty property, String defaultType) {
        PropertyConfig pc = getPropertyConfig(property);
        if (pc != null && pc.getIndexColumn() != null && pc.getIndexColumn().getType() != null) {
            return getTypeName(property, pc.getIndexColumn(), getMapping(property.getOwner()));
        }
        return defaultType;
    }

    protected String getMapElementName(PersistentProperty property, String sessionFactoryBeanName) {
        PropertyConfig pc = getPropertyConfig(property);

        if (hasJoinTableColumnNameMapping(pc)) {
            return pc.getJoinTable().getColumn().getName();
        }

        NamingStrategy namingStrategy = getNamingStrategy(sessionFactoryBeanName);
        return namingStrategy.propertyToColumnName(property.getName()) + UNDERSCORE + IndexedCollection.DEFAULT_ELEMENT_COLUMN_NAME;
    }

    protected boolean hasJoinTableColumnNameMapping(PropertyConfig pc) {
        return pc != null && pc.getJoinTable() != null && pc.getJoinTable().getColumn() != null && pc.getJoinTable().getColumn().getName() != null;
    }



    /**
     * Interrogates the specified constraints looking for any constraints that would limit the
     * length of the property's value.  If such constraints exist, this method adjusts the length
     * of the column accordingly.
     *  @param column              the column that corresponds to the property
     * @param constrainedProperty the property's constraints
     */
    protected void bindStringColumnConstraints(Column column, PersistentProperty constrainedProperty) {
        final org.grails.datastore.mapping.config.Property mappedForm = constrainedProperty.getMapping().getMappedForm();
        Number columnLength = mappedForm.getMaxSize();
        List<?> inListValues = mappedForm.getInList();
        if (columnLength != null) {
            column.setLength(columnLength.intValue());
        } else if (inListValues != null) {
            column.setLength(getMaxSize(inListValues));
        }
    }

    protected void bindNumericColumnConstraints(Column column, PersistentProperty constrainedProperty) {
        bindNumericColumnConstraints(column, constrainedProperty, null);
    }

    /**
     * Interrogates the specified constraints looking for any constraints that would limit the
     * precision and/or scale of the property's value.  If such constraints exist, this method adjusts
     * the precision and/or scale of the column accordingly.
     *  @param column              the column that corresponds to the property
     * @param property the property's constraints
     * @param cc the column configuration
     */
    protected void bindNumericColumnConstraints(Column column, PersistentProperty property, ColumnConfig cc) {
        int scale = Column.DEFAULT_SCALE;
        int precision = Column.DEFAULT_PRECISION;


        PropertyConfig constrainedProperty = (PropertyConfig) property.getMapping().getMappedForm();
        if(  cc != null && cc.getScale() > - 1) {
            column.setScale(cc.getScale());
        } else if (constrainedProperty.getScale() > -1) {
            scale = constrainedProperty.getScale();
            column.setScale(scale);
        }


        if( cc != null && cc.getPrecision() > -1) {
            column.setPrecision(cc.getPrecision());
        }
        else {

            Comparable<?> minConstraintValue = constrainedProperty.getMin();
            Comparable<?> maxConstraintValue = constrainedProperty.getMax();

            int minConstraintValueLength = 0;
            if ((minConstraintValue != null) && (minConstraintValue instanceof Number)) {
                minConstraintValueLength = Math.max(
                        countDigits((Number) minConstraintValue),
                        countDigits(((Number) minConstraintValue).longValue()) + scale);
            }
            int maxConstraintValueLength = 0;
            if ((maxConstraintValue != null) && (maxConstraintValue instanceof Number)) {
                maxConstraintValueLength = Math.max(
                        countDigits((Number) maxConstraintValue),
                        countDigits(((Number) maxConstraintValue).longValue()) + scale);
            }

            if (minConstraintValueLength > 0 && maxConstraintValueLength > 0) {
                // If both of min and max constraints are setted we could use
                // maximum digits number in it as precision
                precision = Math.max(minConstraintValueLength, maxConstraintValueLength);
            } else {
                // Overwise we should also use default precision
                precision = DefaultGroovyMethods.max(new Integer[]{precision, minConstraintValueLength, maxConstraintValueLength});
            }

            column.setPrecision(precision);
        }
    }

    /**
     * @return a count of the digits in the specified number
     */
    protected int countDigits(Number number) {
        int numDigits = 0;

        if (number != null) {
            // Remove everything that's not a digit (e.g., decimal points or signs)
            String digitsOnly = number.toString().replaceAll("\\D", EMPTY_PATH);
            numDigits = digitsOnly.length();
        }

        return numDigits;
    }

    /**
     * @return the maximum length of the strings in the specified list
     */
    protected int getMaxSize(List<?> inListValues) {
        int maxSize = 0;

        for (Object inListValue : inListValues) {
            String value = (String) inListValue;
            maxSize = Math.max(value.length(), maxSize);
        }

        return maxSize;
    }

    protected void handleUniqueConstraint(PersistentProperty property, Column column, String path, Table table, String columnName, String sessionFactoryBeanName) {
        final PropertyConfig mappedForm = (PropertyConfig) property.getMapping().getMappedForm();
        if (mappedForm.isUnique()) {
            if (!mappedForm.isUniqueWithinGroup()) {
                column.setUnique(true);
            }
            else {
                createKeyForProps(property, path, table, columnName, mappedForm.getUniquenessGroup(), sessionFactoryBeanName);
            }
        }

    }


    protected boolean isNotEmpty(String s) {
        return GrailsHibernateUtil.isNotEmpty(s);
    }

    protected String qualify(String prefix, String name) {
        return GrailsHibernateUtil.qualify(prefix, name);
    }

    protected String unqualify(String qualifiedName) {
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
    class GrailsCollectionSecondPass implements SecondPass {

        private static final long serialVersionUID = -5540526942092611348L;

        protected ToMany property;
        protected InFlightMetadataCollector mappings;
        protected Collection collection;
        protected String sessionFactoryBeanName;

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

        protected void createCollectionKeys() {
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

        protected String columns(Value val) {
            StringBuilder columns = new StringBuilder();
            Iterator<?> iter = val.getColumnIterator();
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

        protected final Class<?> clazz;
        protected final GrailsDomainBinder binder;
        protected final MetadataBuildingContext buildingContext;

        protected CollectionType SET;
        protected CollectionType LIST;
        protected CollectionType BAG;
        protected CollectionType MAP;
        protected boolean initialized;

        protected final Map<Class<?>, CollectionType> INSTANCES = new HashMap<>();

        public abstract Collection create(ToMany property, PersistentClass owner,
                                          String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException;

        protected CollectionType(Class<?> clazz, GrailsDomainBinder binder) {
            this.clazz = clazz;
            this.binder = binder;
            this.buildingContext = binder.getMetadataBuildingContext();
        }

        @Override
        public String toString() {
            return clazz.getName();
        }

        protected void createInstances() {

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
            return binder.getTypeName(property, binder.getPropertyConfig(property), getMapping(property.getOwner()));
        }

    }

}


