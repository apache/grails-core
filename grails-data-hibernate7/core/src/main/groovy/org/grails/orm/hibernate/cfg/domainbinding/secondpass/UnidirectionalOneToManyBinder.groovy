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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.Backref
import org.hibernate.mapping.Collection
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.Value

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE

/** Binds unidirectional one-to-many associations. */
@CompileStatic
class UnidirectionalOneToManyBinder {

    private final CollectionWithJoinTableBinder collectionWithJoinTableBinder
    private final BackticksRemover backticksRemover = new BackticksRemover()
    private final InFlightMetadataCollector mappings

    UnidirectionalOneToManyBinder(
            CollectionWithJoinTableBinder collectionWithJoinTableBinder, InFlightMetadataCollector mappings) {
        this.collectionWithJoinTableBinder = collectionWithJoinTableBinder
        this.mappings = mappings
    }

    void bind(@Nonnull HibernateOneToManyProperty property) {
        Collection collection = property.collection
        if (!property.shouldBindWithForeignKey()) {
            collectionWithJoinTableBinder.bindCollectionWithJoinTable(property)
        }
        else {
            bindUnidirectionalOneToMany(property, collection)
        }
    }

    private void bindUnidirectionalOneToMany(
            @Nonnull HibernateOneToManyProperty property, @Nonnull Collection collection) {
        Value element = collection.element
        element.createForeignKey()

        String entityName = (element instanceof ManyToOne) ?
                ((ManyToOne) element).referencedEntityName :
                ((OneToMany) element).referencedEntityName

        collection.inverse = false

        mappings.getEntityBinding(entityName).addProperty(createBackref(property, collection))
    }

    private Backref createBackref(HibernateOneToManyProperty property, Collection collection) {
        GrailsHibernatePersistentEntity owner = (GrailsHibernatePersistentEntity) property.owner
        Backref backref = new Backref()
        backref.entityName = owner.name
        backref.name = UNDERSCORE.toString() + backticksRemover.apply(owner.javaClass.simpleName) +
                UNDERSCORE +
                backticksRemover.apply(property.name) +
                'Backref'
        backref.updatable = false
        backref.insertable = true
        backref.collectionRole = collection.role
        backref.value = collection.key
        backref.optional = true
        return backref
    }

}
