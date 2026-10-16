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
import org.hibernate.mapping.Collection

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.mapping.ManyToManyWithMapping
import org.grails.orm.hibernate.cfg.PropertyConfig

import java.beans.PropertyDescriptor

/** Hibernate implementation of {@link org.grails.datastore.mapping.model.types.ManyToMany} */
@CompileStatic
class HibernateManyToManyProperty extends ManyToManyWithMapping<PropertyConfig>
        implements HibernateToManyEntityProperty {

    private Collection collection

    HibernateManyToManyProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property)
    }

    @Override
    HibernatePersistentEntity getHibernateAssociatedEntity() {
        return (HibernatePersistentEntity) super.getAssociatedEntity()
    }

    @Override
    String getReferencedEntityName() {
        return hibernateAssociatedEntity.name
    }

    @Override
    Collection getHibernateCollection() {
        return collection
    }

    @Override
    void setHibernateCollection(Collection collection) {
        this.collection = collection
    }

    @Override
    void validateOwningSide() {
        HibernateToManyEntityProperty.super.validateOwningSide()
        if (!owningSide) {
            throw new org.hibernate.MappingException("Invalid association [${this}]. " +
                    'List collection types only supported on the owning side of a many-to-many relationship.')
        }
    }

    @Override
    boolean isLazy() {
        return hibernateOwner.isLazy(this)
    }

}
