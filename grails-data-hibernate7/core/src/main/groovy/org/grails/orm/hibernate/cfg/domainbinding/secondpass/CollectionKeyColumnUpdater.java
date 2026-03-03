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
import java.util.stream.StreamSupport;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.PersistentClass;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/** Forces columns to be nullable and checks if the key is updatable. */
public class CollectionKeyColumnUpdater {

  private final CollectionKeyBinder collectionKeyBinder;

  /** Creates a new {@link CollectionKeyColumnUpdater} instance. */
  public CollectionKeyColumnUpdater(CollectionKeyBinder collectionKeyBinder) {
    this.collectionKeyBinder = collectionKeyBinder;
  }

  /** Creates the key, sets it on the collection, and updates its columns. */
  public void bind(
      HibernateToManyProperty property,
      PersistentClass associatedClass,
      Collection collection) {
    DependantValue key = collectionKeyBinder.bind(property, associatedClass, collection);
    forceNullableAndCheckUpdatable(key, property);
  }

  /** Force nullable and check updatable. */
  public void forceNullableAndCheckUpdatable(DependantValue key, HibernateToManyProperty property) {
    key.getColumns().stream()
        .filter(Objects::nonNull)
        .forEach(column -> column.setNullable(true));

    /** Creates a new {@link CollectionKeyColumnUpdater} instance. */
    public CollectionKeyColumnUpdater(CollectionKeyBinder collectionKeyBinder) {
        this.collectionKeyBinder = collectionKeyBinder;
    }

    /** Creates the key, sets it on the collection, and updates its columns. */
    public void bind(HibernateToManyProperty property, PersistentClass associatedClass, Collection collection) {
        DependantValue key = collectionKeyBinder.bind(property, associatedClass, collection);
        forceNullableAndCheckUpdatable(key, property);
    }

    /** Force nullable and check updatable. */
    public void forceNullableAndCheckUpdatable(DependantValue key, HibernateToManyProperty property) {
        key.getColumns().stream().filter(Objects::nonNull).forEach(column -> column.setNullable(true));

        long unidirectionalCount = property.getHibernateOwner().getPersistentPropertiesToBind().stream()
                .filter(p -> p instanceof HibernateToManyProperty association && !association.isBidirectional())
                .count();

        key.setUpdateable(unidirectionalCount <= 1);
    }
}
