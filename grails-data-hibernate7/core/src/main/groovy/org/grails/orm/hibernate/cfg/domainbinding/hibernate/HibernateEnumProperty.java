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

import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;

/**
 * Contract for Hibernate persistent properties that bind an enum value — either the property's
 * own type or a basic collection's element type.
 *
 * <p>Three concrete subtypes exist, corresponding to the three creation paths in {@link
 * HibernateMappingFactory}:
 *
 * <ul>
 *   <li>{@link HibernateSimpleEnumProperty} — plain enum with no custom type marshaller
 *   <li>{@link HibernateCustomEnumProperty} — enum backed by a custom type marshaller
 *   <li>{@link HibernateBasicEnumProperty} — enum element of a {@code hasMany} basic collection
 * </ul>
 *
 * <p>Use {@code instanceof HibernateEnumProperty} instead of {@code isEnumType()} to branch on
 * enum properties at binding time. Each implementation resolves its own enum class and column
 * name so {@link org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder} can bind any
 * of them through a single code path.
 */
public interface HibernateEnumProperty extends HibernatePersistentProperty {

    /** The enum class to bind: the property's own type, or a basic collection's element type. */
    default Class<?> getEnumType() {
        return getType();
    }

    /** Resolves the column name to bind the enum value under. */
    default String resolveEnumColumnName(
            PersistentEntityNamingStrategy namingStrategy,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            String path) {
        return columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(this, path, null);
    }

    /**
     * Whether the enum column should allow NULL. Subclass properties in a table-per-hierarchy
     * strategy must be nullable; otherwise this follows the property's own nullable constraint.
     */
    default boolean isEnumColumnNullable() {
        return getHibernateOwner().isTablePerHierarchySubclass() || isNullable();
    }

    /**
     * Whether this property is a {@code hasMany} basic-collection element rather than a scalar
     * enum-typed property. {@link org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder}
     * uses this to decide whether to bind it directly here, or let it fall through to the normal
     * to-many collection path (whose element is bound later, from within the collection binder).
     */
    default boolean isCollectionElement() {
        return false;
    }
}
