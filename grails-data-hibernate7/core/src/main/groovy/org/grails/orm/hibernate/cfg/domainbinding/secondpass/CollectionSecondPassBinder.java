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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import jakarta.annotation.Nonnull;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Component;

import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyCollectionProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/**
 * Refactored from CollectionBinder to handle collection second pass binding.
 */
// TODO (Hibernate 8 refactor): CollectionSecondPassBinder receives its ComponentBinder reference via
// setComponentBinder() post-construction (mirroring the GrailsPropertyBinder ↔ ComponentBinder circular
// dependency). This should be resolved by introducing a shared binding context or factory that all binders
// receive at construction time, eliminating the need for post-construction wiring.
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CollectionSecondPassBinder {

    private final HibernateToManyEntityOrderByBinder hibernateToManyEntityOrderByBinder;
    private final ToManyEntityMultiTenantFilterBinder hibernateToManyEntityMultiTenantFilterBinder;
    private final CollectionKeyColumnUpdater collectionKeyColumnUpdater;
    private final BidirectionalMapElementBinder bidirectionalMapElementBinder;
    private final ManyToOneElementBinder manyToManyElementBinder;
    private final UnidirectionalOneToManyBinder unidirectionalOneToManyBinder;
    private final CollectionWithJoinTableBinder collectionWithJoinTableBinder;
    private ComponentBinder componentBinder;

    public CollectionSecondPassBinder(
            CollectionKeyColumnUpdater collectionKeyColumnUpdater,
            UnidirectionalOneToManyBinder unidirectionalOneToManyBinder,
            CollectionWithJoinTableBinder collectionWithJoinTableBinder,
            BidirectionalMapElementBinder bidirectionalMapElementBinder,
            ManyToOneElementBinder manyToManyElementBinder,
            HibernateToManyEntityOrderByBinder hibernateToManyEntityOrderByBinder,
            ToManyEntityMultiTenantFilterBinder hibernateToManyEntityMultiTenantFilterBinder) {
        this.collectionKeyColumnUpdater = collectionKeyColumnUpdater;
        this.unidirectionalOneToManyBinder = unidirectionalOneToManyBinder;
        this.collectionWithJoinTableBinder = collectionWithJoinTableBinder;
        this.bidirectionalMapElementBinder = bidirectionalMapElementBinder;
        this.manyToManyElementBinder = manyToManyElementBinder;
        this.hibernateToManyEntityOrderByBinder = hibernateToManyEntityOrderByBinder;
        this.hibernateToManyEntityMultiTenantFilterBinder = hibernateToManyEntityMultiTenantFilterBinder;
    }

    public void setComponentBinder(ComponentBinder componentBinder) {
        this.componentBinder = componentBinder;
    }
    public void bindCollectionSecondPass(@Nonnull HibernateToManyProperty property) {

        if (property instanceof HibernateEmbeddedCollectionProperty embeddedCollectionProperty
                && componentBinder != null) {
            Component component = componentBinder.bindEmbeddedCollectionComponent(embeddedCollectionProperty);
            embeddedCollectionProperty.getCollection().setElement(component);
        } else if (property instanceof HibernateToManyEntityProperty entityProperty) {
            hibernateToManyEntityOrderByBinder.bind(entityProperty);
            if (entityProperty.isManyToMany() && entityProperty.isBidirectional()) {
                manyToManyElementBinder.bind((HibernateManyToManyProperty) entityProperty);
            } else if (entityProperty.isBidirectionalToManyMap() && entityProperty.isBidirectional()) {
                bidirectionalMapElementBinder.bind(entityProperty);
            } else if (entityProperty.isOneToMany() && entityProperty.isUnidirectionalOneToMany()) {
                unidirectionalOneToManyBinder.bind((HibernateOneToManyProperty) entityProperty);
            }
            hibernateToManyEntityMultiTenantFilterBinder.bind(entityProperty);
        } else if (property instanceof HibernateToManyCollectionProperty collectionProperty &&
                collectionProperty.supportsJoinColumnMapping()) {
            collectionWithJoinTableBinder.bindCollectionWithJoinTable(collectionProperty);
        }

        collectionKeyColumnUpdater.bind(property);
        Collection collection = property.getCollection();
        collection.setSorted(property.isSorted());
        collection.setCacheConcurrencyStrategy(property.getCacheUsage());
    }
}
