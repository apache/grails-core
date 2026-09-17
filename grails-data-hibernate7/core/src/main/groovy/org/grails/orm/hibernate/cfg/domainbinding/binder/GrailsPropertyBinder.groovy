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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCustomProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateTenantIdProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

@CompileStatic
class GrailsPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPropertyBinder)

    private final EnumTypeBinder enumTypeBinder
    private final ComponentBinder componentBinder
    private final CollectionBinder collectionBinder
    private final SimpleValueBinder simpleValueBinder
    private final OneToOneBinder oneToOneBinder
    private final ManyToOneBinder manyToOneBinder
    private final ForeignKeyOneToOneBinder foreignKeyOneToOneBinder

    GrailsPropertyBinder(
            EnumTypeBinder enumTypeBinder,
            ComponentBinder componentBinder,
            CollectionBinder collectionBinder,
            SimpleValueBinder simpleValueBinder,
            OneToOneBinder oneToOneBinder,
            ManyToOneBinder manyToOneBinder,
            ForeignKeyOneToOneBinder foreignKeyOneToOneBinder) {
        this.enumTypeBinder = enumTypeBinder
        this.componentBinder = componentBinder
        this.collectionBinder = collectionBinder
        this.simpleValueBinder = simpleValueBinder
        this.oneToOneBinder = oneToOneBinder
        this.manyToOneBinder = manyToOneBinder
        this.foreignKeyOneToOneBinder = foreignKeyOneToOneBinder
    }

    Value bindProperty(
            @Nonnull HibernatePersistentProperty currentGrailsProp,
            HibernatePersistentProperty parentProperty,
            String path) {
        Table table = currentGrailsProp.table
        if (LOG.isDebugEnabled()) {
            LOG.debug('[GrailsPropertyBinder] Binding persistent property [{}]', currentGrailsProp.name)
        }

        Value value

        if (currentGrailsProp instanceof HibernateEnumProperty &&
                !((HibernateEnumProperty) currentGrailsProp).isCollectionElement()) {
            // A hasMany-of-enum property is also a HibernateEnumProperty, but it must still go
            // through collectionBinder.bindCollection() below so its join table gets created;
            // EnumTypeBinder only binds its element column, from BasicCollectionElementBinder.
            value = enumTypeBinder.bindEnumType((HibernateEnumProperty) currentGrailsProp, path)
        } else if (currentGrailsProp.isUserButNotCollectionType()) {
            value = simpleValueBinder.bindBasicValue(currentGrailsProp, parentProperty, path)
        } else if (currentGrailsProp instanceof HibernateOneToOneProperty &&
                ((HibernateOneToOneProperty) currentGrailsProp).isValidHibernateOneToOne()) {
            value = oneToOneBinder.bindOneToOne((HibernateOneToOneProperty) currentGrailsProp, path)
        } else if (currentGrailsProp instanceof HibernateOneToOneProperty) {
            value = foreignKeyOneToOneBinder.bind((HibernateOneToOneProperty) currentGrailsProp, path)
        } else if (currentGrailsProp instanceof HibernateManyToOneProperty) {
            value = manyToOneBinder.bindManyToOne((HibernateManyToOneProperty) currentGrailsProp, table, path)
        } else if (currentGrailsProp instanceof HibernateToManyProperty &&
                !currentGrailsProp.isSerializableType()) {
            value = collectionBinder.bindCollection((HibernateToManyProperty) currentGrailsProp, path)
        } else if (currentGrailsProp instanceof HibernateEmbeddedProperty) {
            value = componentBinder.bindComponent((HibernateEmbeddedProperty) currentGrailsProp, path)
        } else if (currentGrailsProp instanceof HibernateSimpleProperty) {
            value = simpleValueBinder.bindBasicValue((HibernateSimpleProperty) currentGrailsProp, parentProperty, path)
        } else if (currentGrailsProp instanceof HibernateCustomProperty) {
            value = simpleValueBinder.bindBasicValue((HibernateCustomProperty) currentGrailsProp, parentProperty, path)
        } else if (currentGrailsProp instanceof HibernateTenantIdProperty) {
            value = simpleValueBinder.bindBasicValue((HibernateTenantIdProperty) currentGrailsProp, parentProperty, path)
        } else if (currentGrailsProp instanceof HibernateToManyProperty &&
                currentGrailsProp.isSerializableType()) {
            value = simpleValueBinder.bindBasicValue((HibernateToManyProperty) currentGrailsProp, parentProperty, path)
        } else {
            throw new RuntimeException(
                    "Unsupported property type: ${currentGrailsProp.class.name}".toString())
        }

        return value
    }

}
