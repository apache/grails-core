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
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Component

import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyCollectionProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

/**
 * Refactored from CollectionBinder to handle collection second pass binding.
 */
// CollectionSecondPassBinder receives its ComponentBinder reference via setComponentBinder()
// post-construction, mirroring the GrailsPropertyBinder ↔ ComponentBinder circular dependency. A future
// major release can introduce a shared binding context or factory that all binders receive at construction
// time, eliminating the need for post-construction wiring.
@CompileStatic
class CollectionSecondPassBinder {

    private final HibernateToManyEntityOrderByBinder hibernateToManyEntityOrderByBinder
    private final ToManyEntityMultiTenantFilterBinder hibernateToManyEntityMultiTenantFilterBinder
    private final CollectionKeyColumnUpdater collectionKeyColumnUpdater
    private final BidirectionalMapElementBinder bidirectionalMapElementBinder
    private final ManyToOneElementBinder manyToManyElementBinder
    private final UnidirectionalOneToManyBinder unidirectionalOneToManyBinder
    private final CollectionWithJoinTableBinder collectionWithJoinTableBinder
    private final SharedJoinTableUniqueKeyBinder sharedJoinTableUniqueKeyBinder
    private ComponentBinder componentBinder

    /**
     * The constructor of 8.0.0, which has no binder for the join tables that collections share, so a table they share
     * is left as Hibernate binds it: its collections get no uniqueness of their own, and clearing one of them removes
     * the rows of the others that use the same key column.
     *
     * @deprecated since 8.0.1: pass a {@link SharedJoinTableUniqueKeyBinder}. Kept for one release.
     */
    @Deprecated
    CollectionSecondPassBinder(
            CollectionKeyColumnUpdater collectionKeyColumnUpdater,
            UnidirectionalOneToManyBinder unidirectionalOneToManyBinder,
            CollectionWithJoinTableBinder collectionWithJoinTableBinder,
            BidirectionalMapElementBinder bidirectionalMapElementBinder,
            ManyToOneElementBinder manyToManyElementBinder,
            HibernateToManyEntityOrderByBinder hibernateToManyEntityOrderByBinder,
            ToManyEntityMultiTenantFilterBinder hibernateToManyEntityMultiTenantFilterBinder) {
        this(collectionKeyColumnUpdater, unidirectionalOneToManyBinder, collectionWithJoinTableBinder,
                bidirectionalMapElementBinder, manyToManyElementBinder, hibernateToManyEntityOrderByBinder,
                hibernateToManyEntityMultiTenantFilterBinder, null)
    }

    CollectionSecondPassBinder(
            CollectionKeyColumnUpdater collectionKeyColumnUpdater,
            UnidirectionalOneToManyBinder unidirectionalOneToManyBinder,
            CollectionWithJoinTableBinder collectionWithJoinTableBinder,
            BidirectionalMapElementBinder bidirectionalMapElementBinder,
            ManyToOneElementBinder manyToManyElementBinder,
            HibernateToManyEntityOrderByBinder hibernateToManyEntityOrderByBinder,
            ToManyEntityMultiTenantFilterBinder hibernateToManyEntityMultiTenantFilterBinder,
            SharedJoinTableUniqueKeyBinder sharedJoinTableUniqueKeyBinder) {
        this.collectionKeyColumnUpdater = collectionKeyColumnUpdater
        this.unidirectionalOneToManyBinder = unidirectionalOneToManyBinder
        this.collectionWithJoinTableBinder = collectionWithJoinTableBinder
        this.bidirectionalMapElementBinder = bidirectionalMapElementBinder
        this.manyToManyElementBinder = manyToManyElementBinder
        this.hibernateToManyEntityOrderByBinder = hibernateToManyEntityOrderByBinder
        this.hibernateToManyEntityMultiTenantFilterBinder = hibernateToManyEntityMultiTenantFilterBinder
        this.sharedJoinTableUniqueKeyBinder = sharedJoinTableUniqueKeyBinder
    }

    void setComponentBinder(ComponentBinder componentBinder) {
        this.componentBinder = componentBinder
    }

    void bindCollectionSecondPass(@Nonnull HibernateToManyProperty property) {

        if (property instanceof HibernateEmbeddedCollectionProperty && componentBinder != null) {
            HibernateEmbeddedCollectionProperty embeddedCollectionProperty = (HibernateEmbeddedCollectionProperty) property
            Component component = componentBinder.bindEmbeddedCollectionComponent(embeddedCollectionProperty)
            embeddedCollectionProperty.collection.element = component
        }
        else if (property instanceof HibernateToManyEntityProperty) {
            HibernateToManyEntityProperty entityProperty = (HibernateToManyEntityProperty) property
            hibernateToManyEntityOrderByBinder.bind(entityProperty)
            if (entityProperty.isManyToMany() && entityProperty.isBidirectional()) {
                manyToManyElementBinder.bind((HibernateManyToManyProperty) entityProperty)
            }
            else if (entityProperty.isBidirectionalToManyMap() && entityProperty.isBidirectional()) {
                bidirectionalMapElementBinder.bind(entityProperty)
            }
            else if (entityProperty.isOneToMany() && entityProperty.isUnidirectionalOneToMany()) {
                unidirectionalOneToManyBinder.bind((HibernateOneToManyProperty) entityProperty)
            }
            hibernateToManyEntityMultiTenantFilterBinder.bind(entityProperty)
        }
        else if (property instanceof HibernateToManyCollectionProperty && ((HibernateToManyCollectionProperty) property).supportsJoinColumnMapping()) {
            collectionWithJoinTableBinder.bindCollectionWithJoinTable((HibernateToManyCollectionProperty) property)
        }

        collectionKeyColumnUpdater.bind(property)
        Collection collection = property.collection
        collection.sorted = property.isSorted()
        collection.cacheConcurrencyStrategy = property.cacheUsage
    }

    /** Binds a set in its second pass: the collection, then its keys. */
    void bindSetSecondPass(@Nonnull HibernateToManyProperty property) {
        bindCollectionSecondPass(property)
        createCollectionKeys(property)
    }

    /**
     * Creates the keys of the collection of the property and gives it a uniqueness constraint of its own on a join
     * table it shares. Runs last in a second pass, once the collection has its element and, for a list or a map, its
     * index.
     */
    void createCollectionKeys(@Nonnull HibernateToManyProperty property) {
        Collection collection = property.collection
        collection.createAllKeys()
        if (sharedJoinTableUniqueKeyBinder != null) {
            sharedJoinTableUniqueKeyBinder.bind(collection)
        }
    }
}
