package org.grails.orm.hibernate.cfg.domainbinding;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.*;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.boot.model.internal.BinderHelper;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.*;
import org.hibernate.mapping.Collection;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.*;

/**
 * Refactored from CollectionBinder to handle collection second pass binding.
 */
public class CollectionSecondPassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionSecondPassBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;

    public CollectionSecondPassBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
    }

    public PersistentEntityNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void bindCollectionSecondPass(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                         java.util.Map<?, ?> persistentClasses, Collection collection, String sessionFactoryBeanName) {
        PersistentClass associatedClass = null;

        if (LOG.isDebugEnabled())
            LOG.debug("Mapping collection: "
                    + collection.getRole()
                    + " -> "
                    + collection.getCollectionTable().getName());

        PropertyConfig propConfig = property.getMappedForm();

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

            Mapping m = new RootMappingFetcher().getRootMapping(referenced);
            boolean tablePerSubclass = m != null && !m.getTablePerHierarchy();

            if (referenced != null && !referenced.isRoot() && !tablePerSubclass) {
                Mapping rootMapping = new RootMappingFetcher().getRootMapping(referenced);
                //TODO FIXME
                String discriminatorColumnName = JPA_DEFAULT_DISCRIMINATOR_TYPE;

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
                java.util.Set<String> discSet = buildDiscriminatorSet((GrailsHibernatePersistentEntity) referenced);
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

        final boolean isManyToMany = property instanceof ManyToMany;
        if(referenced != null && !isManyToMany && referenced.isMultiTenant()) {
            String filterCondition = getMultiTenantFilterCondition(referenced);
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
        DependantValue key = createPrimaryKeyValue(mappings, (GrailsHibernatePersistentProperty) property, collection, persistentClasses);

        // link a bidirectional relationship
        if (property.isBidirectional()) {

            GrailsHibernatePersistentProperty otherSide = (GrailsHibernatePersistentProperty) property.getInverseSide();

            if ((otherSide instanceof org.grails.datastore.mapping.model.types.ToOne) && property.shouldBindWithForeignKey()) {

                linkBidirectionalOneToMany(collection, associatedClass, key, otherSide);

            } else if ((otherSide instanceof ManyToMany) || java.util.Map.class.isAssignableFrom(property.getType())) {

                bindDependentKeyValue((GrailsHibernatePersistentProperty) property, key, mappings, sessionFactoryBeanName);

            }

        } else {

            if (propConfig.hasJoinKeyMapping()) {

                String columnName = propConfig.getJoinTable().getKey().getName();

                new SimpleValueColumnBinder().bindSimpleValue(key, "long", columnName, false);

            } else {

                bindDependentKeyValue((GrailsHibernatePersistentProperty) property, key, mappings, sessionFactoryBeanName);

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
        if (isManyToMany || property.isBidirectionalOneToManyMap()) {
            PersistentProperty otherSide = property.getInverseSide();

            if (property.isBidirectional()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("[CollectionSecondPassBinder] Mapping other side " + otherSide.getOwner().getName() + "." + otherSide.getName() + " -> " + collection.getCollectionTable().getName() + " as ManyToOne");
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
        } else if (property.supportsJoinColumnMapping()) {
            bindCollectionWithJoinTable(property, mappings, collection, propConfig, sessionFactoryBeanName);

        } else if (property.isUnidirectionalOneToMany()) {
            // for non-inverse one-to-many, with a not-null fk, add a backref!
            // there are problems with list and map mappings and join columns relating to duplicate key constraints
            // TODO change this when HHH-1268 is resolved
            bindUnidirectionalOneToMany((org.grails.datastore.mapping.model.types.OneToMany) property, mappings, collection);
        }
    }

    private void bindUnidirectionalOneToMany(org.grails.datastore.mapping.model.types.OneToMany property, @Nonnull InFlightMetadataCollector mappings, Collection collection) {
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
        prop.setUpdatable(false);
        prop.setInsertable(true);
        prop.setCollectionRole(collection.getRole());
        prop.setValue(collection.getKey());
        prop.setOptional(true);

        referenced.addProperty(prop);
    }

    private void bindCollectionWithJoinTable(HibernateToManyProperty property,
                                             @Nonnull InFlightMetadataCollector mappings, Collection collection, PropertyConfig config, String sessionFactoryBeanName) {

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
                var clazz = namingStrategy.resolveColumnName(className);
                var prop = namingStrategy.resolveTableName(property.getName());
                columnName = isEnum ? clazz : new BackticksRemover().apply(prop) + UNDERSCORE + new BackticksRemover().apply(clazz);
            }

            if (isEnum) {
                new EnumTypeBinder().bindEnumType((GrailsHibernatePersistentProperty) property, referencedType, element, columnName);
            }
            else {

                Mapping mapping = null;
                GrailsHibernatePersistentEntity domainClass = (GrailsHibernatePersistentEntity) property.getOwner();
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
                    String domainName = property.getOwner().getName();
                    throw new MappingException("Missing type or column for column["+columnName+"] on domain["+domainName+"] referencing["+className+"]");
                }

                new SimpleValueColumnBinder().bindSimpleValue(element, typeName, columnName, true);
                if (joinColumnMappingOptional.isPresent()) {
                    Column column = new SimpleValueColumnFetcher().getColumnForSimpleValue(element);
                    ColumnConfig columnConfig = joinColumnMappingOptional.get();
                    final PropertyConfig mappedForm = property.getMappedForm();
                    new ColumnConfigToColumnBinder().bindColumnConfigToColumn(column, columnConfig, mappedForm);
                }
            }
        } else {
            final PersistentEntity domainClass = property.getAssociatedEntity();

            Mapping m = null;
            if (domainClass != null) {
                m = ((GrailsHibernatePersistentEntity) domainClass).getMappedForm();
            }
            if (m != null && m.hasCompositeIdentifier()) {
                CompositeIdentity ci = (CompositeIdentity) m.getIdentity();
                new CompositeIdentifierToManyToOneBinder(namingStrategy).bindCompositeIdentifierToManyToOne((GrailsHibernatePersistentProperty) property, element, ci, domainClass, EMPTY_PATH);
            }
            else {
                if (joinColumnMappingOptional.isPresent()) {
                    columnName = joinColumnMappingOptional.get().getName();
                }
                else {
                    var decapitalize = domainClass.getName();
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
        manyToOne.setReferencedEntityName(property.getAssociatedEntity().getName());
    }

    private void bindManyToMany(Association property, ManyToOne element,
                                @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        new ManyToOneBinder(namingStrategy).bindManyToOne(property, element, EMPTY_PATH);
        element.setReferencedEntityName(property.getOwner().getName());
    }

    private void bindDependentKeyValue(GrailsHibernatePersistentProperty property, DependantValue key,
                                       @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("[CollectionSecondPassBinder] binding  [" + property.getName() + "] with dependant key");
        }

        PersistentEntity refDomainClass = property.getOwner();
        Mapping mapping = null;
        if (refDomainClass instanceof GrailsHibernatePersistentEntity persistentEntity) {
            mapping = persistentEntity.getMappedForm();
        }
        boolean hasCompositeIdentifier = mapping != null && mapping.hasCompositeIdentifier();
        if (hasCompositeIdentifier && property.supportsJoinColumnMapping()) {
            CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
            new CompositeIdentifierToManyToOneBinder(namingStrategy).bindCompositeIdentifierToManyToOne((GrailsHibernatePersistentProperty) property, key, ci, refDomainClass, EMPTY_PATH);
        }
        else {
            // set type
            new SimpleValueBinder(namingStrategy).bindSimpleValue(property, null, key, EMPTY_PATH);
        }
    }

    private void linkBidirectionalOneToMany(Collection collection, PersistentClass associatedClass, DependantValue key, PersistentProperty otherSide) {
        collection.setInverse(true);

        // Iterator mappedByColumns = associatedClass.getProperty(otherSide.getName()).getValue().getColumnIterator();
        Iterator<?> mappedByColumns = getProperty(associatedClass, otherSide.getName()).getValue().getColumns().iterator();
        while (mappedByColumns.hasNext()) {
            Column column = (Column) mappedByColumns.next();
            linkValueUsingAColumnCopy(otherSide, column, key);
        }
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

    private DependantValue createPrimaryKeyValue(@Nonnull InFlightMetadataCollector mappings, PersistentProperty property,
                                                 Collection collection, java.util.Map<?, ?> persistentClasses) {
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
            LOG.debug("[CollectionSecondPassBinder] creating dependant key value  to table [" + keyValue.getTable().getName() + "]");

        key = new DependantValue(metadataBuildingContext, collection.getCollectionTable(), keyValue);

        key.setTypeName(null);
        // make nullable and non-updateable
        key.setNullable(true);
        key.setUpdateable(false);
        //JPA now requires to check for sorting
        key.setSorted(collection.isSorted());
        return key;
    }

    private java.util.Set<String> buildDiscriminatorSet(GrailsHibernatePersistentEntity domainClass) {
        java.util.Set<String> theSet = new java.util.HashSet<>();

        Mapping mapping = domainClass.getMappedForm();
        String discriminator = domainClass.getName();
        if (mapping != null && mapping.getDiscriminator() != null) {
            DiscriminatorConfig discriminatorConfig = mapping.getDiscriminator();
            if(discriminatorConfig.getValue() != null) {
                discriminator = discriminatorConfig.getValue();
            }
        }
        Mapping rootMapping = new RootMappingFetcher().getRootMapping(domainClass);
        String quote = "'";
        if (rootMapping != null && rootMapping.getDatasources() != null) {
            DiscriminatorConfig discriminatorConfig = rootMapping.getDiscriminator();
            if(discriminatorConfig != null && discriminatorConfig.getType() != null && !discriminatorConfig.getType().equals("string"))
                quote = "";
        }
        theSet.add(quote + discriminator + quote);

        final java.util.Collection<PersistentEntity> childEntities = domainClass.getMappingContext().getDirectChildEntities(domainClass);
        for (PersistentEntity subClass : childEntities) {
            theSet.addAll(buildDiscriminatorSet((GrailsHibernatePersistentEntity) subClass));
        }
        return theSet;
    }

    @SuppressWarnings("unchecked")
    private String buildOrderByClause(String hqlOrderBy, PersistentClass associatedClass, String role, String defaultOrder) {
        String orderByString = null;
        if (hqlOrderBy != null) {
            java.util.List<String> properties = new java.util.ArrayList<>();
            java.util.List<String> ordering = new java.util.ArrayList<>();
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

    public String getMultiTenantFilterCondition(PersistentEntity referenced) {
        TenantId tenantId = referenced.getTenantId();
        if(tenantId != null) {

            String defaultColumnName = new DefaultColumnNameFetcher(namingStrategy).getDefaultColumnName(tenantId);
            return ":tenantId = " + defaultColumnName;
        }
        else {
            return null;
        }
    }

    public boolean hasJoinTableColumnNameMapping(PropertyConfig pc) {
        return pc != null && pc.getJoinTable() != null && pc.getJoinTable().getColumn() != null && pc.getJoinTable().getColumn().getName() != null;
    }
}
