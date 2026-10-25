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

import java.util.Objects;

import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyCollectionProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/** Forces columns to be nullable and checks if the key is updatable. */
public class CollectionKeyColumnUpdater {

    private final CollectionKeyBinder collectionKeyBinder;

    /** Creates a new {@link CollectionKeyColumnUpdater} instance. */
    public CollectionKeyColumnUpdater(CollectionKeyBinder collectionKeyBinder) {
        this.collectionKeyBinder = collectionKeyBinder;
    }

    /** Creates the key, sets it on the collection, and updates its columns. */
    public void bind(HibernateToManyProperty property) {
        DependantValue key = collectionKeyBinder.bind(property);
        key.getColumns().stream().filter(Objects::nonNull).forEach(column -> column.setNullable(true));
        Table collectionTable = property.getCollection().getCollectionTable();
        long unidirectionalCount = property.getHibernateOwner()
                .getPersistentPropertiesToBind()
                .stream()
                .filter(HibernateToManyProperty::isBoundAsCollection)
                .map(HibernateToManyProperty.class::cast)
                .filter(p -> !p.isBidirectional())
                .filter(p -> Objects.equals(p.getCollection().getCollectionTable(), collectionTable))
                .count();

        // Collections of basic values or enums (HibernateToManyCollectionProperty) always keep an updatable key: they
        // have no inverse side, and Hibernate writes no rows for a collection whose key is not updatable.
        // For entity collections (HibernateToManyEntityProperty) the key is non-updatable only when the owner has
        // two or more unidirectional to-many properties that share this collection's join table, the rule for issue
        // 10811. Unidirectional collections with join tables of their own keep an updatable key, as they have no
        // second key column to leave null. A to-many that is not bound as a collection (a serializable or user type)
        // has no join table and no Hibernate collection, so it is neither counted nor asked for one.
        key.setUpdateable(property instanceof HibernateToManyCollectionProperty || unidirectionalCount <= 1);
    }

}
