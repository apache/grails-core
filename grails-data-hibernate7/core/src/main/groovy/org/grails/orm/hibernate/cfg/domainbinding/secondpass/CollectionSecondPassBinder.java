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

import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCollectionProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/**
 * Refactored from CollectionBinder to handle collection second pass binding.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CollectionSecondPassBinder {

    private final CollectionOrderByBinder collectionOrderByBinder;
    private final CollectionMultiTenantFilterBinder collectionMultiTenantFilterBinder;
    private final CollectionKeyColumnUpdater collectionKeyColumnUpdater;
    private final BidirectionalMapElementBinder bidirectionalMapElementBinder;
    private final ManyToManyElementBinder manyToManyElementBinder;
    private final UnidirectionalOneToManyBinder unidirectionalOneToManyBinder;
    private final CollectionWithJoinTableBinder collectionWithJoinTableBinder;
    private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder;

    public CollectionSecondPassBinder(
            CollectionKeyColumnUpdater collectionKeyColumnUpdater,
            UnidirectionalOneToManyBinder unidirectionalOneToManyBinder,
            CollectionWithJoinTableBinder collectionWithJoinTableBinder,
            CollectionForPropertyConfigBinder collectionForPropertyConfigBinder,
            BidirectionalMapElementBinder bidirectionalMapElementBinder,
            ManyToManyElementBinder manyToManyElementBinder,
            CollectionOrderByBinder collectionOrderByBinder,
            CollectionMultiTenantFilterBinder collectionMultiTenantFilterBinder) {
        this.collectionKeyColumnUpdater = collectionKeyColumnUpdater;
        this.unidirectionalOneToManyBinder = unidirectionalOneToManyBinder;
        this.collectionWithJoinTableBinder = collectionWithJoinTableBinder;
        this.collectionForPropertyConfigBinder = collectionForPropertyConfigBinder;
        this.bidirectionalMapElementBinder = bidirectionalMapElementBinder;
        this.manyToManyElementBinder = manyToManyElementBinder;
        this.collectionOrderByBinder = collectionOrderByBinder;
        this.collectionMultiTenantFilterBinder = collectionMultiTenantFilterBinder;
    }

    public void bindCollectionSecondPass(@Nonnull HibernateToManyProperty property, Map<?, ?> persistentClasses) {

        PersistentClass ownerClass = property.getHibernateOwner().getPersistentClass();

        if (property instanceof HibernateCollectionProperty collectionProperty) {
            collectionOrderByBinder.bind(collectionProperty);
        }
        collectionMultiTenantFilterBinder.bind(property);
        collectionKeyColumnUpdater.bind(property, ownerClass);
        Collection collection = property.getCollection();
        collection.setSorted(property.isSorted());
        collection.setCacheConcurrencyStrategy(property.getCacheUsage());
        bindCollectionElement(property);
    }

    private void bindCollectionElement(HibernateToManyProperty property) {
        if (property instanceof HibernateManyToManyProperty manyToMany && manyToMany.isBidirectional()) {
            manyToManyElementBinder.bind(manyToMany);
        } else if (property.isBidirectionalOneToManyMap() && property.isBidirectional()) {
            bidirectionalMapElementBinder.bind(property);
        } else if (property instanceof HibernateOneToManyProperty oneToManyProperty &&
                oneToManyProperty.isUnidirectionalOneToMany()) {
            unidirectionalOneToManyBinder.bind(oneToManyProperty);
        } else if (property.supportsJoinColumnMapping()) {
            collectionWithJoinTableBinder.bindCollectionWithJoinTable(property);
        }
    }
}
