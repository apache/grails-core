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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;

import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCustomProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateTenantIdProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

public class GrailsPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPropertyBinder.class);

    private final EnumTypeBinder enumTypeBinder;
    private final ComponentBinder componentBinder;
    private final CollectionBinder collectionBinder;
    private final SimpleValueBinder simpleValueBinder;
    private final OneToOneBinder oneToOneBinder;
    private final ManyToOneBinder manyToOneBinder;
    private final ForeignKeyOneToOneBinder foreignKeyOneToOneBinder;

    public GrailsPropertyBinder(
            EnumTypeBinder enumTypeBinder,
            ComponentBinder componentBinder,
            CollectionBinder collectionBinder,
            SimpleValueBinder simpleValueBinder,
            OneToOneBinder oneToOneBinder,
            ManyToOneBinder manyToOneBinder,
            ForeignKeyOneToOneBinder foreignKeyOneToOneBinder) {
        this.enumTypeBinder = enumTypeBinder;
        this.componentBinder = componentBinder;
        this.collectionBinder = collectionBinder;
        this.simpleValueBinder = simpleValueBinder;
        this.oneToOneBinder = oneToOneBinder;
        this.manyToOneBinder = manyToOneBinder;
        this.foreignKeyOneToOneBinder = foreignKeyOneToOneBinder;
    }

    public Value bindProperty(
            @Nonnull HibernatePersistentProperty currentGrailsProp, HibernatePersistentProperty parentProperty, String path) {
        Table table = currentGrailsProp.getTable();
        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsPropertyBinder] Binding persistent property [" + currentGrailsProp.getName() + "]");
        }

        Value value;

        if (currentGrailsProp instanceof HibernateEnumProperty hibernateEnumProperty) {
            value = enumTypeBinder.bindEnumType(hibernateEnumProperty, table, path);
        } else if (currentGrailsProp.isUserButNotCollectionType()) {
            value = simpleValueBinder.bindSimpleValue(currentGrailsProp, parentProperty, table, path);
        } else if (currentGrailsProp instanceof HibernateOneToOneProperty oneToOne
                && oneToOne.isValidHibernateOneToOne()) {
            value = oneToOneBinder.bindOneToOne(oneToOne, path);
        } else if (currentGrailsProp instanceof HibernateOneToOneProperty oneToOne) {
            value = foreignKeyOneToOneBinder.bind(oneToOne, path);
        } else if (currentGrailsProp instanceof HibernateManyToOneProperty manyToOne) {
            value = manyToOneBinder.bindManyToOne(manyToOne, table, path);
        } else if (currentGrailsProp instanceof HibernateToManyProperty toMany
                && !currentGrailsProp.isSerializableType()) {
            value = collectionBinder.bindCollection(toMany, path);
        } else if (currentGrailsProp instanceof HibernateEmbeddedProperty embedded) {
            value = componentBinder.bindComponent(embedded, path);
        } else if (currentGrailsProp instanceof HibernateSimpleProperty simple) {
            value = simpleValueBinder.bindSimpleValue(simple, parentProperty, table, path);
        } else if (currentGrailsProp instanceof HibernateCustomProperty custom) {
            value = simpleValueBinder.bindSimpleValue(custom, parentProperty, table, path);
        } else if (currentGrailsProp instanceof HibernateTenantIdProperty tenantId) {
            value = simpleValueBinder.bindSimpleValue(tenantId, parentProperty, table, path);
        } else if (currentGrailsProp instanceof HibernateToManyProperty toMany && currentGrailsProp.isSerializableType()) {
            value = simpleValueBinder.bindSimpleValue(toMany, parentProperty, table, path);
        } else {
            throw new RuntimeException("Unsupported property type: " + currentGrailsProp.getClass().getName());
        }

        return value;
    }
}
