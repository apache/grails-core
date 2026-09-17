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
import org.hibernate.FetchMode
import org.hibernate.MappingException
import org.hibernate.mapping.Collection
import org.hibernate.mapping.IndexedCollection

import org.springframework.util.StringUtils

import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.mapping.PropertyWithMapping
import org.grails.orm.hibernate.cfg.CacheConfig
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior

import static org.grails.orm.hibernate.cfg.GrailsHibernateUtil.qualify
import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.ALL
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.ALL_DELETE_ORPHAN
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.NONE
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.SAVE_UPDATE

/** Marker interface for Hibernate to-many associations */
@CompileStatic
interface HibernateToManyProperty extends PropertyWithMapping<PropertyConfig>, HibernateAssociation {

    default boolean hasSort() {
        return StringUtils.hasText(hibernateMappedForm.sort)
    }

    default String getSort() {
        return hibernateMappedForm.sort
    }

    default String getOrder() {
        return hibernateMappedForm.order
    }

    default boolean getIgnoreNotFound() {
        return hibernateMappedForm.ignoreNotFound
    }

    default FetchMode getFetchMode() {
        return hibernateMappedForm.fetchMode
    }

    default Boolean getLazy() {
        return hibernateMappedForm.lazy
    }

    default String getCacheUsage() {
        PropertyConfig mapped = hibernateMappedForm
        CacheConfig cache = mapped?.cache
        Object usage = cache?.usage
        return usage != null ? usage.toString() : null
    }

    default boolean isBasic() {
        return this instanceof Basic
    }

    default boolean isManyToMany() {
        return this instanceof HibernateManyToManyProperty
    }

    default boolean isOneToMany() {
        return this instanceof HibernateOneToManyProperty
    }

    /**
     * The cascade behavior implied by this to-many property's shape, absent an explicit {@code
     * cascade} mapping. Self-contained: every fact this needs (basic-ness, Map-typedness, embedded
     * collection-ness, ownership, circularity) is already exposed by this interface or inherited
     * from the GORM {@code Association} hierarchy, so no external dispatch is required.
     */
    default CascadeBehavior getImpliedCascadeBehavior() {
        if (!(this instanceof Association)) {
            throw new MappingException("Unrecognized to-many association type ${type}")
        }
        Association association = (Association) this
        if (isBasic()) {
            return ALL
        }
        if (Map.isAssignableFrom(type)) {
            return association.correctlyOwned ? ALL : SAVE_UPDATE
        }
        if (this instanceof EmbeddedCollection) {
            return ALL
        }
        // Fail-fast only for entity relationships that are truly missing an association
        if (associatedEntity == null) {
            throw new MappingException("Relationship ${this} has no associated entity")
        }
        if (isOneToMany()) {
            return association.correctlyOwned ? ALL : SAVE_UPDATE
        }
        if (isManyToMany()) {
            return (association.correctlyOwned || circular) ? SAVE_UPDATE : NONE
        }
        throw new MappingException("Unrecognized to-many association type ${type}")
    }

    /**
     * Returns the component type for this to-many collection, or {@code null} if it cannot be
     * determined.
     */
    default Class<?> getComponentType() {
        if (this instanceof Basic) {
            return ((Basic) this).componentType
        }
        if (this instanceof Association) {
            def associatedEntity = ((Association) this).associatedEntity
            if (associatedEntity != null) {
                return associatedEntity.javaClass
            }
        }
        return null
    }

    /**
     * @return Whether the collection should be bound with a foreign key
     */
    default boolean shouldBindWithForeignKey() {
        return false
    }

    default String getIndexColumnName(PersistentEntityNamingStrategy namingStrategy) {
        PropertyConfig mapped = hibernateMappedForm

        if (mapped != null && mapped.indexColumn != null) {
            PropertyConfig indexColConfig = mapped.indexColumn
            if (!indexColConfig.columns.isEmpty()) {
                String name = indexColConfig.columns.get(0).name
                if (StringUtils.hasText(name)) {
                    return name
                }
            }
        }

        if (mapped == null || mapped.columns.isEmpty()) {
            return namingStrategy.resolveColumnName(name) +
                    UNDERSCORE +
                    IndexedCollection.DEFAULT_INDEX_COLUMN_NAME
        }

        ColumnConfig primaryCol = mapped.columns.get(0)
        Object rawIndex = primaryCol.index
        if (rawIndex instanceof groovy.lang.Closure) {
            PropertyConfig indexColConfig = PropertyConfig.configureNew((groovy.lang.Closure<?>) rawIndex)
            if (!indexColConfig.columns.isEmpty()) {
                String name = indexColConfig.columns.get(0).name
                if (StringUtils.hasText(name)) {
                    return name
                }
            }
        }

        try {
            Map<String, String> indexMap = primaryCol.indexAsMap
            String colName = indexMap.get('column')

            if (StringUtils.hasText(colName)) {
                return colName
            }
        }
        catch (Exception ignored) {
            // ignored
        }

        return namingStrategy.resolveColumnName(name) + UNDERSCORE + IndexedCollection.DEFAULT_INDEX_COLUMN_NAME
    }

    default String getIndexColumnType(String defaultType) {
        PropertyConfig mapped = hibernateMappedForm

        if (mapped != null && mapped.indexColumn != null) {
            PropertyConfig indexColConfig = mapped.indexColumn
            if (StringUtils.hasText(indexColConfig.typeName)) {
                return indexColConfig.typeName
            }
        }

        if (mapped == null || mapped.columns.isEmpty()) {
            return defaultType
        }

        ColumnConfig primaryCol = mapped.columns.get(0)
        Object rawIndex = primaryCol.index
        if (rawIndex instanceof groovy.lang.Closure) {
            PropertyConfig indexColConfig = PropertyConfig.configureNew((groovy.lang.Closure<?>) rawIndex)
            if (StringUtils.hasText(indexColConfig.typeName)) {
                return indexColConfig.typeName
            }
        }

        try {
            Map<String, String> indexMap = primaryCol.indexAsMap
            String typeName = indexMap.get('type')

            if (StringUtils.hasText(typeName)) {
                return typeName
            }
        }
        catch (Exception ignored) {
            // ignored
        }

        return defaultType
    }

    default String getMapElementName(PersistentEntityNamingStrategy namingStrategy) {
        PropertyConfig mapped = hibernateMappedForm
        JoinTable joinTable = mapped?.joinTable
        ColumnConfig column = joinTable?.column
        String columnName = column?.name
        return columnName != null ? columnName : namingStrategy.resolveColumnName(name) +
                GrailsDomainBinder.UNDERSCORE +
                IndexedCollection.DEFAULT_ELEMENT_COLUMN_NAME
    }

    /**
     * Only reached for a unidirectional {@code hasMany} join table (via {@code CollectionWithJoinTableBinder}).
     * A bidirectional many-to-many join table's foreign-key columns instead go through
     * {@code DefaultColumnNameFetcher#resolveForeignKeyForPropertyDomainClass}, unaffected by this method.
     */
    default String resolveJoinTableForeignKeyColumnName(PersistentEntityNamingStrategy namingStrategy) {
        PropertyConfig mapped = hibernateMappedForm
        ColumnConfig columnConfig = mapped?.joinTableColumnConfig
        String columnName = columnConfig?.name
        return columnName != null ? columnName : resolveAssociatedEntityTableName(namingStrategy) +
                GrailsDomainBinder.FOREIGN_KEY_SUFFIX
    }

    /**
     * Resolves the associated root entity's table name for use as a join-table foreign-key column
     * prefix. The result is a column-identifier fragment, never a literal, quotable SQL identifier -
     * so the Groovy backtick-quoting convention is always invalid there and must be stripped once
     * here, rather than trusted to the caller (a prior bug left the foreign key malformed as
     * {@code `quoted_table`_id}).
     */
    default String resolveAssociatedEntityTableName(PersistentEntityNamingStrategy namingStrategy) {
        return new BackticksRemover().apply(
                hibernateAssociatedEntity.hibernateRootEntity.getTableName(namingStrategy))
    }

    default String joinTableColumName(PersistentEntityNamingStrategy namingStrategy) {
        final Class<?> referencedType = componentType
        Optional<ColumnConfig> joinColumnMappingOptional = columnConfigOptional
        boolean present = joinColumnMappingOptional.present
        String columnName
        if (present) {
            columnName = joinColumnMappingOptional.get().name
        }
        else if (referencedType.isEnum()) {
            // Use the enum's simple name, not its fully-qualified name, so the column
            // isn't named after the enum's package.
            columnName = namingStrategy.resolveColumnName(referencedType.simpleName)
        }
        else {
            // Both callers of joinTableColumName (BasicCollectionElementBinder, EnumTypeBinder) operate on
            // a HibernateBasicProperty, so referencedType is always the collection's basic element type here,
            // never an associated entity - resolveAssociatedEntityTableName does not apply to this path.
            String clazz = namingStrategy.resolveColumnName(referencedType.name)
            String prop = namingStrategy.resolveColumnName(name)
            columnName = new BackticksRemover().apply(prop) + UNDERSCORE + new BackticksRemover().apply(clazz)
        }
        return columnName
    }

    default Optional<ColumnConfig> getColumnConfigOptional() {
        PropertyConfig mapped = hibernateMappedForm
        return Optional.ofNullable(mapped != null ? mapped.joinTableColumnConfig : null)
    }

    @Override
    default boolean isEnum() {
        Class<?> compType = componentType
        return compType != null && compType.isEnum()
    }

    /**
     * @return Whether the association column is nullable. ManyToMany is never nullable.
     */
    @Override
    default boolean isAssociationColumnNullable() {
        if (this instanceof HibernateManyToManyProperty) {
            return false
        }
        return nullable
    }

    default void validateOwningSide() {
        if (!(hibernateCollection instanceof org.hibernate.mapping.List)) {
            throw new MappingException("Collection must be of type List for property [${name}]")
        }
    }

    default Collection getCollection() {
        Collection collection = hibernateCollection
        if (collection == null) {
            throw new MappingException(
                    "Hibernate Collection has not been initialized for property [${name}]. Call setCollection() first.")
        }
        return collection
    }

    default void setCollection(Collection collection) {
        setCollection(collection, '')
    }

    default void setCollection(Collection collection, String path) {
        if (collection != null) {
            collection.role = getRole(path)
            collection.fetchMode = fetchMode
            collection.orphanDelete = ALL_DELETE_ORPHAN.value == cascade
            collection.batchSize = batchSize
        }
        hibernateCollection = collection
    }

    Collection getHibernateCollection()

    void setHibernateCollection(Collection collection)

    default String getCascade() {
        return hibernateMappedForm.cascade
    }

    default Integer getBatchSize() {
        PropertyConfig mapped = hibernateMappedForm
        return (mapped != null && mapped.batchSize != null) ? mapped.batchSize : -1
    }

    default String getRole(String path) {
        return qualify(hibernateOwner.name, getNameForPropertyAndPath(path))
    }

}
