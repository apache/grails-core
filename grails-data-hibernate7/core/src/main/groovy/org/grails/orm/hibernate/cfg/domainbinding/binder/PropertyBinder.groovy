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
import org.codehaus.groovy.transform.trait.Traits
import org.hibernate.boot.spi.AccessType
import org.hibernate.mapping.Property
import org.hibernate.mapping.Value

import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.access.TraitPropertyAccessStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehaviorFetcher

@CompileStatic
class PropertyBinder {

    private final CascadeBehaviorFetcher cascadeBehaviorFetcher

    PropertyBinder(CascadeBehaviorFetcher cascadeBehaviorFetcher) {
        this.cascadeBehaviorFetcher = cascadeBehaviorFetcher
    }

    PropertyBinder() {
        this(new CascadeBehaviorFetcher())
    }

    /**
     * Binds a property to Hibernate runtime meta model. Deals with cascade strategy based on the
     * Grails domain model
     *
     * @param persistentProperty The grails property instance
     * @param value The Hibernate value
     * @return The Hibernate property
     */
    Property bindProperty(HibernatePersistentProperty persistentProperty, Value value) {
        Property prop = new Property()
        prop.value = value
        // set the property name
        prop.name = persistentProperty.name
        PropertyConfig config = persistentProperty.hibernateMappedForm
        if (config == null) {
            config = new PropertyConfig()
        }

        if (persistentProperty instanceof HibernateAssociation &&
                ((HibernateAssociation) persistentProperty).isBidirectionalManyToOneWithListMapping(prop)) {
            prop.insertable = false
            prop.updatable = false
        } else {
            prop.insertable = config.insertable
            prop.updatable = config.updatable
        }

        AccessType accessType = AccessType.getAccessStrategy(config.accessType)

        String accessorName = accessType == AccessType.FIELD ?
                Optional.ofNullable(persistentProperty.reader)
                        .map { EntityReflector.PropertyReader reader -> reader.getter() }
                        .map { getter -> getter.getAnnotation(Traits.Implemented) }
                        .map { annotation -> TraitPropertyAccessStrategy.name }
                        .orElse(accessType.type) :
                accessType.type
        prop.propertyAccessorName = accessorName

        prop.optional = persistentProperty.isNullable()
        // No enum type is excluded here on its own account: a plain scalar enum property is never an
        // Association, so instanceof Association<?> already excludes it. A hasMany-of-enum collection IS
        // an Association (Basic), and CascadeBehaviorFetcher already dispatches Basic -> ALL correctly,
        // so it must go through the same path as every other collection type.
        if (persistentProperty instanceof Association) {
            prop.cascade = cascadeBehaviorFetcher.getCascadeBehaviour((Association) persistentProperty)
        }

        // Use centralized laziness determination
        prop.lazy = persistentProperty.isLazy()

        prop.insertable = value.hasAnyInsertableColumns()
        prop.updatable = value.hasAnyUpdatableColumns()

        return prop
    }

}
