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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.hibernate.FetchMode
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.KeyValue
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.SimpleValue

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.orm.hibernate.cfg.DiscriminatorConfig
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.util.ConfigureDerivedPropertiesConsumer
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.NamespaceNameExtractor

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE

/** Common interface for Hibernate persistent entities */
@CompileStatic
interface GrailsHibernatePersistentEntity extends PersistentEntity {

    private static String resolveDiscriminatorValue(DiscriminatorConfig discriminatorConfig) {
        return discriminatorConfig.column != null ? discriminatorConfig.column.name : discriminatorConfig.formula
    }

    @Override
    Mapping getMappedForm()

    @Nonnull
    default GrailsHibernatePersistentEntity getHibernateRootEntity() {
        return (GrailsHibernatePersistentEntity) rootEntity
    }

    default GrailsHibernatePersistentEntity getStrategyOwner() {
        List<HibernatePersistentProperty> props = getHibernatePersistentProperties()
        return (props != null && !props.isEmpty()) ? props.get(0).hibernateOwner : this
    }

    default Mapping getStrategyMapping() {
        return strategyOwner.mappedForm
    }

    default Mapping getRootMapping() {
        return hibernateRootEntity.mappedForm
    }

    default boolean isTablePerHierarchy() {
        Mapping mapping = strategyMapping
        return mapping == null || mapping.isTablePerHierarchy()
    }

    default boolean isJoinedSubclass() {
        Mapping mapping = strategyMapping
        return mapping != null && mapping.isJoinedSubclass()
    }

    default boolean isUnionSubclass() {
        Mapping mapping = strategyMapping
        return mapping != null && mapping.isUnionSubclass()
    }

    default boolean isTableAbstract() {
        return isUnionSubclass() && isAbstract()
    }

    default boolean isTablePerHierarchySubclass() {
        return !this.isRoot() && isTablePerHierarchy()
    }

    default Set<String> buildDiscriminatorSet() {
        Mapping rootMapping = rootMapping
        DiscriminatorConfig discriminatorConfig = rootMapping?.datasources != null ? rootMapping.discriminator : null
        String quote = (discriminatorConfig?.type != null && discriminatorConfig.type != 'string') ? '' : "'"

        String quotedDiscriminator = quote + discriminatorValue + quote

        Set<String> result = new LinkedHashSet<>()
        result.add(quotedDiscriminator)
        for (GrailsHibernatePersistentEntity child : childEntities) {
            result.addAll(child.buildDiscriminatorSet())
        }
        return result
    }

    default HibernatePropertyIdentity getHibernateIdentity() {
        Mapping mappedForm = mappedForm
        HibernatePropertyIdentity fromMapping = mappedForm?.identity
        if (fromMapping != null) {
            return fromMapping
        }
        HibernatePropertyIdentity composite = resolveCompositeIdentity()
        if (composite != null) {
            return composite
        }
        return defaultIdentity
    }

    private HibernatePropertyIdentity resolveCompositeIdentity() {
        HibernatePersistentProperty[] compositeId = compositeIdentity
        if (compositeId == null || compositeId.length <= 1) {
            return null
        }
        HibernateCompositeIdentity ci = new HibernateCompositeIdentity()
        ci.propertyNames = compositeId.collect { PersistentProperty<?> p -> p.name } as String[]
        return ci
    }

    private @Nonnull HibernateSimpleIdentity getDefaultIdentity() {
        HibernateSimpleIdentity defaultIdentity = new HibernateSimpleIdentity()
        PersistentProperty<?> existingIdentity = identity
        String existingIdentityName = existingIdentity?.name
        defaultIdentity.name = existingIdentityName != null ? existingIdentityName : name
        return defaultIdentity
    }

    @Override
    HibernatePersistentProperty getIdentity()

    @Override
    HibernatePersistentProperty[] getCompositeIdentity()

    default Optional<HibernateCompositeIdentity> getHibernateCompositeIdentity() {
        Mapping mappedForm = mappedForm
        if (mappedForm == null || !mappedForm.hasCompositeIdentifier()) {
            return Optional.empty()
        }
        HibernatePropertyIdentity identity = mappedForm.identity
        return identity instanceof HibernateCompositeIdentity ?
                Optional.of((HibernateCompositeIdentity) identity) : Optional.empty()
    }

    default String getDiscriminatorValue() {
        DiscriminatorConfig discriminatorConfig = mappedForm?.discriminator
        return discriminatorConfig?.value ?: javaClass.simpleName
    }

    String getDataSourceName()

    void setDataSourceName(String dataSourceName)

    boolean forGrailsDomainMapping(String dataSourceName)

    boolean usesConnectionSource(String dataSourceName)

    boolean isAbstract()

    default List<HibernatePersistentProperty> getPersistentPropertiesToBind() {
        List<HibernatePersistentProperty> properties = getHibernatePersistentProperties()
        if (properties == null) {
            return Collections.emptyList()
        }
        return properties.findAll { HibernatePersistentProperty p ->
            p != null &&
                    p.mappedForm != null &&
                    !p.isIdentityProperty() &&
                    GormProperties.VERSION != p.name &&
                    !p.isInherited()
        }
    }

    @Override
    HibernatePersistentProperty getVersion()

    /**
     * Returns the persistent property with the given name cast to {@link HibernatePersistentProperty},
     * or {@code null} if no such property exists.
     */
    default HibernatePersistentProperty getHibernatePropertyByName(String name) {
        return (HibernatePersistentProperty) getPropertyByName(name)
    }

    /**
     * Returns the persistent property with the given path (e.g. "author.name") cast to {@link HibernatePersistentProperty},
     * or {@code null} if no such property exists.
     *
     * @param path The path to the property
     * @return The property or null
     */
    default HibernatePersistentProperty getHibernatePropertyByPath(String path) {
        if (path == null) {
            return null
        }
        if (path.contains('.')) {
            String[] parts = path.split('\\.', 2)
            HibernatePersistentProperty prop = getHibernatePropertyByName(parts[0])
            if (prop != null) {
                GrailsHibernatePersistentEntity associated = prop.hibernateAssociatedEntity
                if (associated != null) {
                    return associated.getHibernatePropertyByPath(parts[1])
                }
            }
            return null
        }
        return getHibernatePropertyByName(path)
    }

    /**
     * @param parentType The type of the parent entity
     * @return The parent property if it exists
     */
    default Optional<HibernatePersistentProperty> getHibernateParentProperty(Class<?> parentType) {
        List<HibernatePersistentProperty> properties = getHibernatePersistentProperties()
        if (properties == null) {
            return Optional.empty()
        }
        for (HibernatePersistentProperty p : properties) {
            if (p != null && p.type == parentType) {
                return Optional.of(p)
            }
        }
        return Optional.empty()
    }

    /**
     * @param parentType The type of the parent entity to exclude from the results
     * @return The properties that should be bound to the Hibernate meta model
     */
    default List<HibernatePersistentProperty> getHibernatePersistentProperties(Class<?> parentType) {
        List<HibernatePersistentProperty> properties = getHibernatePersistentProperties()
        if (properties == null) {
            return Collections.emptyList()
        }
        return properties.findAll { HibernatePersistentProperty p ->
            p != null &&
                    p.mappedForm != null &&
                    p != identity &&
                    GormProperties.VERSION != p.name &&
                    p.type != parentType
        }
    }

    default List<HibernatePersistentEntity> getChildEntities() {
        return getChildEntities(dataSourceName)
    }

    default List<HibernatePersistentEntity> getChildEntities(String dataSourceName) {
        List<HibernatePersistentEntity> result = []
        for (PersistentEntity entity : mappingContext.getDirectChildEntities(this)) {
            if (entity instanceof HibernatePersistentEntity) {
                HibernatePersistentEntity persistentEntity = (HibernatePersistentEntity) entity
                if (persistentEntity.usesConnectionSource(dataSourceName) &&
                        persistentEntity.javaClass.superclass == this.javaClass) {
                    result.add(persistentEntity)
                }
            }
        }
        return result
    }

    default boolean isComponentPropertyNullable(PersistentProperty<?> embeddedProperty) {
        if (embeddedProperty == null) {
            return false
        }
        final Mapping mapping = mappedForm
        return !isRoot() && (mapping == null || mapping.isTablePerHierarchy()) || embeddedProperty.isNullable()
    }

    default void configureDerivedProperties() {
        hibernatePersistentProperties.forEach(new ConfigureDerivedPropertiesConsumer(mappedForm))
    }

    default HibernatePersistentProperty getHibernateTenantId() {
        return (HibernatePersistentProperty) tenantId
    }

    default String getMultiTenantFilterCondition(DefaultColumnNameFetcher fetcher) {
        HibernatePersistentProperty tenantId = hibernateTenantId
        return tenantId != null ? ':tenantId = ' + fetcher.getDefaultColumnName(tenantId) : null
    }

    default String getSchema(@Nonnull InFlightMetadataCollector mappings) {
        org.grails.orm.hibernate.cfg.Table table = mappedForm?.table
        return table?.schema ?: NamespaceNameExtractor.getSchemaName(mappings)
    }

    default String getCatalog(@Nonnull InFlightMetadataCollector mappings) {
        org.grails.orm.hibernate.cfg.Table table = mappedForm?.table
        return table?.catalog ?: NamespaceNameExtractor.getCatalogName(mappings)
    }

    /**
     * Evaluates the table name for the given entity
     *
     * @param persistentEntityNamingStrategy The naming strategy
     * @return The table name
     */
    default String getTableName(PersistentEntityNamingStrategy persistentEntityNamingStrategy) {
        String tableName = mappedForm?.tableName
        if (tableName != null) {
            return tableName
        }
        Mapping rootMapping = rootMapping
        if (rootMapping != null && rootMapping.isTablePerHierarchy()) {
            String rootTableName = rootMapping.tableName
            if (rootTableName != null) {
                return rootTableName
            }
        }
        return persistentEntityNamingStrategy.resolveTableName(this)
    }

    default String getDiscriminatorColumnName() {
        DiscriminatorConfig discriminatorConfig = rootMapping?.discriminator
        return discriminatorConfig != null ? resolveDiscriminatorValue(discriminatorConfig) : JPA_DEFAULT_DISCRIMINATOR_TYPE
    }

    default List<HibernatePersistentProperty> getHibernatePersistentProperties() {
        List<HibernatePersistentProperty> result = []
        for (PersistentProperty<?> p : persistentProperties) {
            if (p instanceof HibernatePersistentProperty) {
                HibernatePersistentProperty hp = (HibernatePersistentProperty) p
                result.add(hp.validateProperty())
            }
        }
        return result
    }

    default String getComment() {
        return mappedForm?.comment
    }

    default Mapping getHibernateMappedForm() {
        return mappedForm
    }

    PersistentClass getPersistentClass()

    void setPersistentClass(PersistentClass persistentClass)

    /**
     * Determines if the given property should be lazy.
     *
     * @param property The property
     * @return True if it should be lazy
     */
    default boolean isLazy(HibernatePersistentProperty property) {
        if (GormProperties.VERSION == property.name) {
            return false
        }

        PropertyConfig config = property.mappedForm
        if (config != null) {
            if (property instanceof HibernateAssociation && FetchMode.JOIN == config.fetchMode) {
                return false
            }
            Boolean lazy = config.getLazy()
            if (lazy != null) {
                return lazy
            }
        }
        return property instanceof HibernateAssociation
    }

    /**
     * Sorts or indexes the columns of {@code value} to align with this entity's composite
     * identifier order. When the identifier is a {@link Component} with an established sort order,
     * delegates to {@link SimpleValue#sortColumns(int[])}. Otherwise assigns sequential
     * {@link Column#setTypeIndex(int)} values so Hibernate can correlate them.
     *
     * @param value the foreign-key {@link SimpleValue} whose columns should be aligned
     */
    default void sortOrIndexForeignKeyColumns(SimpleValue value) {
        PersistentClass pc = persistentClass
        KeyValue identifier = pc != null ? pc.identifier : null
        int[] originalOrder = identifier instanceof Component ? ((Component) identifier).sortProperties() : null
        if (originalOrder != null) {
            value.sortColumns(originalOrder)
        }
        else {
            List<Column> cols = value.columns
            for (int i = 0; i < cols.size(); i++) {
                cols.get(i).typeIndex = i
            }
        }
    }

    /**
     * Returns the identifier columns for the given {@code propertyNames} in the order that aligns
     * with the sorted foreign-key layout produced by {@link #sortOrIndexForeignKeyColumns}.
     * <p>
     * When the identifier is a {@link Component}, columns are gathered per property name and then
     * reordered according to the same permutation used during {@link Component#sortProperties()}.
     * When the identifier is a plain {@link KeyValue}, its columns are returned directly.
     *
     * @param propertyNames composite identity property names in the caller's declared order
     * @return identifier columns aligned with the foreign-key column layout, or an empty list if
     *         this entity has no persistent class or identifier
     */
    default List<Column> getReferencedIdentifierColumns(String[] propertyNames) {
        PersistentClass pc = persistentClass
        KeyValue identifier = pc != null ? pc.identifier : null
        if (identifier == null) {
            return []
        }
        if (!(identifier instanceof Component)) {
            return identifier.columns
        }
        Component component = (Component) identifier
        int[] originalOrder = component.sortProperties()
        List<Column> referencedColumns = []
        for (String name : propertyNames) {
            referencedColumns.addAll(component.getProperty(name).value.columns)
        }
        return originalOrder != null ? sortedByPermutation(referencedColumns, originalOrder) : referencedColumns
    }

    private static List<Column> sortedByPermutation(List<Column> columns, int[] permutation) {
        List<Column> result = new ArrayList<>(columns)
        for (int i = 0; i < permutation.length; i++) {
            result.set(permutation[i], columns.get(i))
        }
        return result
    }

}
