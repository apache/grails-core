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

import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.mapping.PropertyWithMapping;
import org.grails.orm.hibernate.cfg.CacheConfig;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder;

/** Marker interface for Hibernate to-many associations */
public interface HibernateToManyProperty extends PropertyWithMapping<PropertyConfig>, HibernateAssociation {

    default boolean hasSort() {
        return StringUtils.hasText(getMappedForm().getSort());
    }

    default String getSort() {
        return getMappedForm().getSort();
    }

    default String getOrder() {
        return getMappedForm().getOrder();
    }

    default boolean getIgnoreNotFound() {
        return getMappedForm().getIgnoreNotFound();
    }

    default FetchMode getFetchMode() {
        return getMappedForm().getFetchMode();
    }

    default Boolean getLazy() {
        return getMappedForm().getLazy();
    }

  default String getCacheUSage() {
    return Optional.ofNullable(getMappedForm())
            .map(PropertyConfig::getCache)
            .map(CacheConfig::getUsage)
            .orElse(null);
  }

  default boolean isBasic() {
    return this instanceof Basic;
  }

  /**
   * @return Whether the collection should be bound with a foreign key
   */
  default boolean shouldBindWithForeignKey() {
    return ((this instanceof HibernateOneToManyProperty) && isBidirectional()
            || !isUnidirectionalOneToMany())
        && !Map.class.isAssignableFrom(getType())
        && !(this instanceof HibernateManyToManyProperty)
        && !(this instanceof Basic);
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

  default String getMapElementName(PersistentEntityNamingStrategy namingStrategy) {
    return java.util.Optional.ofNullable(getMappedForm())
        .map(PropertyConfig::getJoinTable)
        .map(JoinTable::getColumn)
        .map(ColumnConfig::getName)
        .orElseGet(
            () ->
                namingStrategy.resolveColumnName(getName())
                    + GrailsDomainBinder.UNDERSCORE
                    + IndexedCollection.DEFAULT_ELEMENT_COLUMN_NAME);
  }

  default String resolveJoinTableForeignKeyColumnName(PersistentEntityNamingStrategy namingStrategy) {
    return java.util.Optional.ofNullable(getMappedForm())
        .map(PropertyConfig::getJoinTableColumnConfig)
        .map(ColumnConfig::getName)
        .orElseGet(
            () ->
                namingStrategy.resolveColumnName(
                        getHibernateAssociatedEntity().getHibernateRootEntity().getJavaClass().getSimpleName())
                    + GrailsDomainBinder.FOREIGN_KEY_SUFFIX);
  }
}
