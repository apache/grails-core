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
import org.hibernate.mapping.Property
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

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.DEFAULT_DISCRIMINATOR_COLUMN_NAME

/** Common interface for Hibernate persistent entities */
@CompileStatic
interface GrailsHibernatePersistentEntity extends PersistentEntity {

    private static String resolveDiscriminatorValue(DiscriminatorConfig discriminatorConfig) {
        return discriminatorConfig.getColumn() != null ?
                discriminatorConfig.getColumn().getName() :
                discriminatorConfig.getFormula()
    }

    @Override
    Mapping getMappedForm()

    @Nonnull
    default GrailsHibernatePersistentEntity getHibernateRootEntity() {
        return (GrailsHibernatePersistentEntity) getRootEntity()
    }

    default GrailsHibernatePersistentEntity getStrategyOwner() {
        List<HibernatePersistentProperty> props = getHibernatePersistentProperties()
        return (props != null && !props.isEmpty()) ? props.get(0).getHibernateOwner() : this
    }

    default Mapping getStrategyMapping() {
        return getStrategyOwner().getMappedForm()
    }

    default Mapping getRootMapping() {
        return getHibernateRootEntity().getMappedForm()
    }

    default boolean isTablePerHierarchy() {
        Mapping mapping = getStrategyMapping()
        return mapping == null || mapping.isTablePerHierarchy()
    }

    default boolean isJoinedSubclass() {
        Mapping mapping = getStrategyMapping()
        return mapping != null && mapping.isJoinedSubclass()
    }

    default boolean isUnionSubclass() {
        Mapping mapping = getStrategyMapping()
        return mapping != null && mapping.isUnionSubclass()
    }

    default boolean isTableAbstract() {
        return isUnionSubclass() && isAbstract()
    }

    default boolean isTablePerHierarchySubclass() {
        return !this.isRoot() && isTablePerHierarchy()
    }

    default Set<String> buildDiscriminatorSet() {
        Mapping rootMapping = getRootMapping()
        DiscriminatorConfig discriminatorConfig = rootMapping?.getDatasources() != null ? rootMapping.getDiscriminator() : null
        String quote = (discriminatorConfig?.getType() != null && !discriminatorConfig.getType().equals('string')) ? '' : "'"

        String quotedDiscriminator = quote + getDiscriminatorValue() + quote

        Set<String> result = new LinkedHashSet<>()
        result.add(quotedDiscriminator)
        for (GrailsHibernatePersistentEntity child : getChildEntities()) {
            result.addAll(child.buildDiscriminatorSet())
        }
        return result
    }

    default HibernatePropertyIdentity getHibernateIdentity() {
        Mapping mappedForm = getMappedForm()
        HibernatePropertyIdentity fromMapping = mappedForm?.getIdentity()
        if (fromMapping != null) {
            return fromMapping
        }
        HibernatePropertyIdentity composite = resolveCompositeIdentity()
        if (composite != null) {
            return composite
        }
        return getDefaultIdentity()
    }

    private HibernatePropertyIdentity resolveCompositeIdentity() {
        HibernatePersistentProperty[] compositeId = getCompositeIdentity()
        if (compositeId == null || compositeId.length <= 1) {
            return null
        }
        HibernateCompositeIdentity ci = new HibernateCompositeIdentity()
        ci.setPropertyNames(compositeId.collect { PersistentProperty<?> p -> p.getName() } as String[])
        return ci
    }

    private @Nonnull HibernateSimpleIdentity getDefaultIdentity() {
        HibernateSimpleIdentity defaultIdentity = new HibernateSimpleIdentity()
        PersistentProperty<?> existingIdentity = getIdentity()
        String existingIdentityName = existingIdentity?.getName()
        defaultIdentity.setName(existingIdentityName != null ? existingIdentityName : getName())
        return defaultIdentity
    }

    @Override
    HibernatePersistentProperty getIdentity()

    @Override
    HibernatePersistentProperty[] getCompositeIdentity()

    default Optional<HibernateCompositeIdentity> getHibernateCompositeIdentity() {
        Mapping mappedForm = getMappedForm()
        if (mappedForm == null || !mappedForm.hasCompositeIdentifier()) {
            return Optional.empty()
        }
        HibernatePropertyIdentity identity = mappedForm.getIdentity()
        return identity instanceof HibernateCompositeIdentity ?
                Optional.of((HibernateCompositeIdentity) identity) : Optional.empty()
    }

    default String getDiscriminatorValue() {
        String value = getMappedForm()?.getDiscriminator()?.getValue()
        return value != null ? value : getJavaClass().getName()
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
                    p.getMappedForm() != null &&
                    !p.isIdentityProperty() &&
                    !GormProperties.VERSION.equals(p.getName()) &&
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
                GrailsHibernatePersistentEntity associated = prop.getHibernateAssociatedEntity()
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
            if (p != null && p.getType().equals(parentType)) {
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
                    p.getMappedForm() != null &&
                    !p.equals(getIdentity()) &&
                    !GormProperties.VERSION.equals(p.getName()) &&
                    !p.getType().equals(parentType)
        }
    }

    default List<HibernatePersistentEntity> getChildEntities() {
        return getChildEntities(getDataSourceName())
    }

    default List<HibernatePersistentEntity> getChildEntities(String dataSourceName) {
        List<HibernatePersistentEntity> result = []
        for (PersistentEntity entity : getMappingContext().getDirectChildEntities(this)) {
            if (entity instanceof HibernatePersistentEntity) {
                HibernatePersistentEntity persistentEntity = (HibernatePersistentEntity) entity
                if (persistentEntity.usesConnectionSource(dataSourceName) &&
                        persistentEntity.getJavaClass().getSuperclass().equals(this.getJavaClass())) {
                    result.add(persistentEntity)
                }
            }
        }
        return Collections.unmodifiableList(result)
    }

    default boolean isComponentPropertyNullable(PersistentProperty<?> embeddedProperty) {
        if (embeddedProperty == null) {
            return false
        }
        final Mapping mapping = getMappedForm()
        return !isRoot() && (mapping == null || mapping.isTablePerHierarchy()) || embeddedProperty.isNullable()
    }

    default void configureDerivedProperties() {
        getHibernatePersistentProperties().forEach(new ConfigureDerivedPropertiesConsumer(getMappedForm()))
    }

    default HibernatePersistentProperty getHibernateTenantId() {
        return (HibernatePersistentProperty) getTenantId()
    }

    default String getMultiTenantFilterCondition(DefaultColumnNameFetcher fetcher) {
        HibernatePersistentProperty tenantId = getHibernateTenantId()
        return tenantId != null ? ':tenantId = ' + fetcher.getDefaultColumnName(tenantId) : null
    }

    default String getSchema(@Nonnull InFlightMetadataCollector mappings) {
        org.grails.orm.hibernate.cfg.Table table = getMappedForm()?.getTable()
        String schema = table?.getSchema()
        return schema != null ? schema : NamespaceNameExtractor.getSchemaName(mappings)
    }

    default String getCatalog(@Nonnull InFlightMetadataCollector mappings) {
        org.grails.orm.hibernate.cfg.Table table = getMappedForm()?.getTable()
        String catalog = table?.getCatalog()
        return catalog != null ? catalog : NamespaceNameExtractor.getCatalogName(mappings)
    }

    /**
     * Evaluates the table name for the given entity
     *
     * @param persistentEntityNamingStrategy The naming strategy
     * @return The table name
     */
    default String getTableName(PersistentEntityNamingStrategy persistentEntityNamingStrategy) {
        String tableName = getMappedForm()?.getTableName()
        if (tableName != null) {
            return tableName
        }
        Mapping rootMapping = getRootMapping()
        if (rootMapping != null && rootMapping.isTablePerHierarchy()) {
            String rootTableName = rootMapping.getTableName()
            if (rootTableName != null) {
                return rootTableName
            }
        }
        return persistentEntityNamingStrategy.resolveTableName(this)
    }

    default String getDiscriminatorColumnName() {
        DiscriminatorConfig discriminatorConfig = getRootMapping()?.getDiscriminator()
        String column = discriminatorConfig != null ? resolveDiscriminatorValue(discriminatorConfig) : null
        return column != null ? column : DEFAULT_DISCRIMINATOR_COLUMN_NAME
    }

    default List<HibernatePersistentProperty> getHibernatePersistentProperties() {
        List<HibernatePersistentProperty> result = []
        for (PersistentProperty<?> p : getPersistentProperties()) {
            if (p instanceof HibernatePersistentProperty) {
                HibernatePersistentProperty hp = (HibernatePersistentProperty) p
                result.add(hp.validateProperty())
            }
        }
        return Collections.unmodifiableList(result)
    }

    default String getComment() {
        return getMappedForm()?.getComment()
    }

    default Mapping getHibernateMappedForm() {
        return getMappedForm()
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
        if (GormProperties.VERSION.equals(property.getName())) {
            return false
        }

        PropertyConfig config = property.getMappedForm()
        if (config != null) {
            if (property instanceof HibernateAssociation && FetchMode.JOIN.equals(config.getFetchMode())) {
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
     * {@link Column#setTypeIndex(int)} values so Hibernate can correlate them. The binder calls this
     * once the identifier is bound, deferring the call to a second pass when this entity is bound
     * later than the entity holding the key.
     *
     * @param value the foreign-key {@link SimpleValue} whose columns should be aligned
     */
    default void sortOrIndexForeignKeyColumns(SimpleValue value) {
        PersistentClass pc = getPersistentClass()
        KeyValue identifier = pc != null ? pc.getIdentifier() : null
        Component component = identifier instanceof Component ? (Component) identifier : null
        int[] originalOrder = component != null ? component.sortProperties() : null
        if (originalOrder != null) {
            value.sortColumns(toColumnPermutation(originalOrder, partColumnSpans(component, originalOrder)))
        } else {
            List<Column> cols = value.getColumns()
            for (int i = 0; i < cols.size(); i++) {
                cols.get(i).setTypeIndex(i)
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
        PersistentClass pc = getPersistentClass()
        KeyValue identifier = pc != null ? pc.getIdentifier() : null
        if (identifier == null) {
            return []
        }
        if (!(identifier instanceof Component)) {
            return identifier.getColumns()
        }
        Component component = (Component) identifier
        int[] originalOrder = component.sortProperties()
        List<Column> referencedColumns = []
        for (String name : propertyNames) {
            referencedColumns.addAll(component.getProperty(name).getValue().getColumns())
        }
        if (originalOrder == null) {
            return referencedColumns
        }
        int[] spans = new int[propertyNames.length]
        for (int i = 0; i < spans.length; i++) {
            spans[i] = component.getProperty(propertyNames[i]).getValue().getColumnSpan()
        }
        return sortedByPermutation(referencedColumns, toColumnPermutation(originalOrder, spans))
    }

    private static List<Column> sortedByPermutation(List<Column> columns, int[] permutation) {
        List<Column> result = new ArrayList<>(columns)
        for (int i = 0; i < permutation.length; i++) {
            result.set(permutation[i], columns.get(i))
        }
        return result
    }

    /**
     * Returns the number of columns each part of the composite identifier spans, in declared order.
     * {@code propertyPermutation[i]} is the position of declared part {@code i} in the sorted property list.
     */
    private static int[] partColumnSpans(Component component, int[] propertyPermutation) {
        List<Property> sorted = component.getProperties()
        int[] spans = new int[propertyPermutation.length]
        for (int i = 0; i < spans.length; i++) {
            spans[i] = sorted.get(propertyPermutation[i]).getValue().getColumnSpan()
        }
        return spans
    }

    /**
     * Expands a permutation of identifier parts into a permutation of columns. A to-one part that
     * references a composite identifier spans several columns, so moving it to its sorted position
     * has to move its whole column span. {@code result[c]} is the sorted position of declared column
     * {@code c}. With one column per part the result equals {@code propertyPermutation}.
     */
    private static int[] toColumnPermutation(int[] propertyPermutation, int[] spans) {
        int[] sortedStart = new int[propertyPermutation.length]
        int[] declaredAtSortedPosition = new int[propertyPermutation.length]
        for (int i = 0; i < propertyPermutation.length; i++) {
            declaredAtSortedPosition[propertyPermutation[i]] = i
        }
        int offset = 0
        for (int position in declaredAtSortedPosition) {
            sortedStart[position] = offset
            offset += spans[position]
        }
        int[] result = new int[offset]
        int declaredStart = 0
        for (int i = 0; i < spans.length; i++) {
            for (int k = 0; k < spans[i]; k++) {
                result[declaredStart + k] = sortedStart[i] + k
            }
            declaredStart += spans[i]
        }
        return result
    }
}
