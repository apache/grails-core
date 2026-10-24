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

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/**
 * Makes the columns of a collection key nullable and the key updatable.
 *
 * <p>The key is always updatable. The Hibernate 5 binder made every collection key not updatable, which Hibernate 5
 * tolerated, and the Hibernate 7 binder kept that for an owner with several unidirectional to-many properties, after
 * the rule of issue 10811. Hibernate 7's collection persister writes no rows for a collection whose key is not
 * updatable, so such an owner silently lost the elements of every one of those collections, even with
 * {@code failOnError}. When two of them share a join table, the uniqueness of its rows is the business of
 * {@link SharedJoinTableUniqueKeyBinder}.</p>
 */
public class CollectionKeyColumnUpdater {

    private final CollectionKeyBinder collectionKeyBinder;

    /** Creates a new {@link CollectionKeyColumnUpdater} instance. */
    public CollectionKeyColumnUpdater(CollectionKeyBinder collectionKeyBinder) {
        this.collectionKeyBinder = collectionKeyBinder;
    }

    /** Creates the key, sets it on the collection, and makes its columns nullable and the key updatable. */
    public void bind(HibernateToManyProperty property) {
        DependantValue key = collectionKeyBinder.bind(property);
        key.getColumns().stream().filter(Objects::nonNull).forEach(column -> column.setNullable(true));
        key.setUpdateable(true);
    }

}
