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
import org.hibernate.mapping.Collection
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.PersistentClass

import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

/** Binds the collection key value for a to-many association. */
@CompileStatic
class CollectionKeyBinder {

    private final BidirectionalOneToManyLinker bidirectionalOneToManyLinker
    private final DependentKeyValueBinder dependentKeyValueBinder
    private final SimpleValueColumnBinder simpleValueColumnBinder
    private final PrimaryKeyValueCreator primaryKeyValueCreator

    /** Creates a new {@link CollectionKeyBinder} instance. */
    CollectionKeyBinder(
            BidirectionalOneToManyLinker bidirectionalOneToManyLinker,
            DependentKeyValueBinder dependentKeyValueBinder,
            SimpleValueColumnBinder simpleValueColumnBinder,
            PrimaryKeyValueCreator primaryKeyValueCreator) {
        this.bidirectionalOneToManyLinker = bidirectionalOneToManyLinker
        this.dependentKeyValueBinder = dependentKeyValueBinder
        this.simpleValueColumnBinder = simpleValueColumnBinder
        this.primaryKeyValueCreator = primaryKeyValueCreator
    }

    /** Creates the {@link DependantValue} key, sets it on the collection, and binds it. */
    DependantValue bind(HibernateToManyProperty property) {
        Collection collection = property.collection
        DependantValue key = primaryKeyValueCreator.createPrimaryKeyValue(collection)
        collection.key = key
        if (property.isBidirectional()) {
            HibernateAssociation inverseSide = property.hibernateInverseSide
            if (inverseSide instanceof ToOne && property.shouldBindWithForeignKey()) {
                PersistentClass associatedClass = inverseSide.hibernateOwner.persistentClass
                bidirectionalOneToManyLinker.link(collection, associatedClass, key, inverseSide)
            }
            else if (inverseSide instanceof HibernateManyToManyProperty ||
                    Map.isAssignableFrom(property.type)) {
                dependentKeyValueBinder.bind(property, key)
            }
        }
        else {
            PropertyConfig mappedForm = property.hibernateMappedForm
            if (mappedForm.hasJoinKeyMapping()) {
                JoinTable joinTable = mappedForm.joinTable
                def keys = joinTable.keys
                if (keys != null && keys.size() > 1) {
                    // Composite key: delegate to DependentKeyValueBinder
                    dependentKeyValueBinder.bind(property, key)
                }
                else {
                    // Single key: use SimpleValueColumnBinder
                    simpleValueColumnBinder.bindSimpleValue(
                            key,
                            'long',
                            (joinTable.keys != null && !joinTable.keys.isEmpty()) ? joinTable.keys.get(0).name : null,
                            true)
                }
            }
            else if (property instanceof EmbeddedCollection) {
                // For embedded (value-type) collections the DependantValue already wraps the owner PK;
                // do not override the type name with the element class name.
                key.typeName = null
            }
            else {
                dependentKeyValueBinder.bind(property, key)
            }
        }
        return key
    }

}
