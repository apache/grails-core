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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import java.util.Map;
import java.util.Optional;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.hibernate.FetchMode;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.IndexedCollection;

import org.springframework.util.StringUtils;

import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.mapping.PropertyWithMapping;
import org.grails.orm.hibernate.cfg.CacheConfig;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE;

/** Marker interface for Hibernate to-many associations */
public interface HibernateToManyProperty extends PropertyWithMapping<PropertyConfig>, HibernateAssociation {

    default boolean hasSort() {
        return StringUtils.hasText(getHibernateMappedForm().getSort());
    }

    default String getSort() {
        return getHibernateMappedForm().getSort();
    }

    default String getOrder() {
        return getHibernateMappedForm().getOrder();
    }

    default boolean getIgnoreNotFound() {
        return getHibernateMappedForm().getIgnoreNotFound();
    }

    default FetchMode getFetchMode() {
        return getHibernateMappedForm().getFetchMode();
    }

    default Boolean getLazy() {
        return getHibernateMappedForm().getLazy();
    }

    default String getCacheUsage() {
        return Optional.ofNullable(getHibernateMappedForm())
                .map(PropertyConfig::getCache)
                .map(CacheConfig::getUsage)
                .map(Object::toString)
                .orElse(null);
    }

    default boolean isBasic() {
        return this instanceof Basic;
    }

    /**
     * Returns the component type for basic (scalar/enum) collections, or {@code null} if this is not
     * a basic collection.
     */
    default Class<?> getComponentType() {
        return this instanceof Basic<?> basic ? basic.getComponentType() : null;
    }

    /**
     * @return Whether the collection should be bound with a foreign key
     */
    default boolean shouldBindWithForeignKey() {
        return ((this instanceof HibernateOneToManyProperty) && isBidirectional() || !isUnidirectionalOneToMany())
                && !Map.class.isAssignableFrom(getType())
                && !(this instanceof HibernateManyToManyProperty)
                && !(this instanceof Basic);
    }

    default String getIndexColumnName(PersistentEntityNamingStrategy namingStrategy) {
        PropertyConfig mapped = getHibernateMappedForm();

        if (mapped != null && mapped.getIndexColumn() != null) {
            PropertyConfig indexColConfig = mapped.getIndexColumn();
            if (!indexColConfig.getColumns().isEmpty()) {
                String name = indexColConfig.getColumns().get(0).getName();
                if (StringUtils.hasText(name)) {
                    return name;
                }
            }
        }

        if (mapped == null || mapped.getColumns().isEmpty()) {
            return namingStrategy.resolveColumnName(getName())
                    + UNDERSCORE
                    + IndexedCollection.DEFAULT_INDEX_COLUMN_NAME;
        }

        ColumnConfig primaryCol = mapped.getColumns().get(0);
        Object rawIndex = primaryCol.getIndex();
        if (rawIndex instanceof groovy.lang.Closure) {
            PropertyConfig indexColConfig = PropertyConfig.configureNew((groovy.lang.Closure<?>) rawIndex);
            if (!indexColConfig.getColumns().isEmpty()) {
                String name = indexColConfig.getColumns().get(0).getName();
                if (StringUtils.hasText(name)) {
                    return name;
                }
            }
        }

        try {
            Map<String, String> indexMap = primaryCol.getIndexAsMap();
            String colName = indexMap.get("column");

            if (StringUtils.hasText(colName)) {
                return colName;
            }
        } catch (Exception e) {
            // ignore
        }

        return namingStrategy.resolveColumnName(getName()) + UNDERSCORE + IndexedCollection.DEFAULT_INDEX_COLUMN_NAME;
    }

    default String getIndexColumnType(String defaultType) {
        PropertyConfig mapped = getHibernateMappedForm();

        if (mapped != null && mapped.getIndexColumn() != null) {
            PropertyConfig indexColConfig = mapped.getIndexColumn();
            if (StringUtils.hasText(indexColConfig.getTypeName())) {
                return indexColConfig.getTypeName();
            }
        }

        if (mapped == null || mapped.getColumns().isEmpty()) {
            return defaultType;
        }

        ColumnConfig primaryCol = mapped.getColumns().get(0);
        Object rawIndex = primaryCol.getIndex();
        if (rawIndex instanceof groovy.lang.Closure) {
            PropertyConfig indexColConfig = PropertyConfig.configureNew((groovy.lang.Closure<?>) rawIndex);
            if (StringUtils.hasText(indexColConfig.getTypeName())) {
                return indexColConfig.getTypeName();
            }
        }

        try {
            Map<String, String> indexMap = primaryCol.getIndexAsMap();
            String typeName = indexMap.get("type");

            if (StringUtils.hasText(typeName)) {
                return typeName;
            }
        } catch (Exception e) {
            // ignore
        }

        return defaultType;
    }

    default String getMapElementName(PersistentEntityNamingStrategy namingStrategy) {
        return java.util.Optional.ofNullable(getHibernateMappedForm())
                .map(PropertyConfig::getJoinTable)
                .map(JoinTable::getColumn)
                .map(ColumnConfig::getName)
                .orElseGet(() -> namingStrategy.resolveColumnName(getName())
                        + GrailsDomainBinder.UNDERSCORE
                        + IndexedCollection.DEFAULT_ELEMENT_COLUMN_NAME);
    }

    default String resolveJoinTableForeignKeyColumnName(PersistentEntityNamingStrategy namingStrategy) {
        return java.util.Optional.ofNullable(getHibernateMappedForm())
                .map(PropertyConfig::getJoinTableColumnConfig)
                .map(ColumnConfig::getName)
                .orElseGet(() -> namingStrategy.resolveColumnName(getHibernateAssociatedEntity()
                                .getHibernateRootEntity()
                                .getJavaClass()
                                .getSimpleName())
                        + GrailsDomainBinder.FOREIGN_KEY_SUFFIX);
    }

    default String joinTableColumName(PersistentEntityNamingStrategy namingStrategy) {
        final Class<?> referencedType = getComponentType();
        var joinColumnMappingOptional = getColumnConfigOptional();
        boolean present = joinColumnMappingOptional.isPresent();
        String columnName;
        if (present) {
            columnName = joinColumnMappingOptional.get().getName();
        } else {
            var clazz = namingStrategy.resolveColumnName(referencedType.getName());
            var prop = namingStrategy.resolveTableName(getName());
            columnName = referencedType.isEnum()
                    ? clazz
                    : new BackticksRemover().apply(prop) + UNDERSCORE + new BackticksRemover().apply(clazz);
        }
        return columnName;
    }

    @NonNull
    default Optional<ColumnConfig> getColumnConfigOptional() {
        return Optional.ofNullable(getHibernateMappedForm()).map(PropertyConfig::getJoinTableColumnConfig);
    }

    default boolean isEnum() {
        return getComponentType().isEnum();
    }

    /**
     * @return Whether the association column is nullable. ManyToMany is never nullable.
     */
    default boolean isAssociationColumnNullable() {
        if (this instanceof HibernateManyToManyProperty) {
            return false;
        }
        return isNullable();
    }

    void setCollection(Collection collection);

    Collection getCollection();
}
