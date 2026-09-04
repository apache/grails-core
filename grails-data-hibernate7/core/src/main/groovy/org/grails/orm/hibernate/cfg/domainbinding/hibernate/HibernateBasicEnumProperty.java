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

import java.beans.PropertyDescriptor;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;

/**
 * Hibernate basic collection element property whose element type is an enum. Created by {@link
 * HibernateMappingFactory#createBasicCollection} when the collection's element type is an enum.
 */
public class HibernateBasicEnumProperty extends HibernateBasicProperty implements HibernateEnumProperty {

    public HibernateBasicEnumProperty(
            GrailsHibernatePersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }

    @Override
    public Class<?> getEnumType() {
        return getComponentType();
    }

    @Override
    public String resolveEnumColumnName(
            PersistentEntityNamingStrategy namingStrategy,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            String path) {
        return joinTableColumName(namingStrategy);
    }

    /** A hasMany element column is always nullable, matching the non-enum sibling binding path. */
    @Override
    public boolean isEnumColumnNullable() {
        return true;
    }

    @Override
    public boolean isCollectionElement() {
        return true;
    }

    /**
     * For an enum collection element, the table to bind the element column against is the
     * collection's join table rather than the owning entity's table. Before the collection
     * table has been assigned (e.g. while it is itself being computed), falls back to the
     * owning entity's table, matching the pre-collection-binding default. Scoped to the enum
     * subclass because only {@link org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder}
     * binds through {@code getTable()}; the non-enum element binding reads the collection
     * table directly.
     */
    @Override
    public Table getTable() {
        Collection collection = getHibernateCollection();
        Table collectionTable = collection != null ? collection.getCollectionTable() : null;
        return collectionTable != null ? collectionTable : getPersistentClass().getTable();
    }
}
