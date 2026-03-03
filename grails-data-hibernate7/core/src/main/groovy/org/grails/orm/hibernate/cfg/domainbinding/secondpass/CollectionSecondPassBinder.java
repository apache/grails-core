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

import java.util.*;
import java.util.Map;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.hibernate.MappingException;
import org.hibernate.mapping.*;
import org.hibernate.mapping.Collection;

/** Refactored from CollectionBinder to handle collection second pass binding. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CollectionSecondPassBinder {
  private final CollectionOrderByBinder collectionOrderByBinder;
  private final CollectionMultiTenantFilterBinder collectionMultiTenantFilterBinder;
  private final CollectionKeyBinder collectionKeyBinder;
  private final ManyToOneBinder manyToOneBinder;
  private final PrimaryKeyValueCreator primaryKeyValueCreator;
  private final CollectionKeyColumnUpdater collectionKeyColumnUpdater;
  private final UnidirectionalOneToManyBinder unidirectionalOneToManyBinder;
  private final CollectionWithJoinTableBinder collectionWithJoinTableBinder;
  private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder;

  /** Creates a new {@link CollectionSecondPassBinder} instance. */
  public CollectionSecondPassBinder(
      ManyToOneBinder manyToOneBinder,
      PrimaryKeyValueCreator primaryKeyValueCreator,
      CollectionKeyColumnUpdater collectionKeyColumnUpdater,
      UnidirectionalOneToManyBinder unidirectionalOneToManyBinder,
      CollectionWithJoinTableBinder collectionWithJoinTableBinder,
      CollectionForPropertyConfigBinder collectionForPropertyConfigBinder,
      CollectionKeyBinder collectionKeyBinder,
      CollectionOrderByBinder collectionOrderByBinder,
      CollectionMultiTenantFilterBinder collectionMultiTenantFilterBinder) {
    this.manyToOneBinder = manyToOneBinder;
    this.primaryKeyValueCreator = primaryKeyValueCreator;
    this.collectionKeyColumnUpdater = collectionKeyColumnUpdater;
    this.unidirectionalOneToManyBinder = unidirectionalOneToManyBinder;
    this.collectionWithJoinTableBinder = collectionWithJoinTableBinder;
    this.collectionForPropertyConfigBinder = collectionForPropertyConfigBinder;
    this.collectionKeyBinder = collectionKeyBinder;
    this.collectionOrderByBinder = collectionOrderByBinder;
    this.collectionMultiTenantFilterBinder = collectionMultiTenantFilterBinder;
  }

  /** Bind collection second pass. */
  public void bindCollectionSecondPass(
      @Nonnull HibernateToManyProperty property,
      @Nonnull InFlightMetadataCollector mappings,
      Map<?, ?> persistentClasses,
      @Nonnull Collection collection) {

    PersistentClass associatedClass = resolveAssociatedClass(property, persistentClasses);
    collectionOrderByBinder.bind(property, collection, associatedClass);
    bindOneToManyAssociation(property, associatedClass, collection);

    collectionMultiTenantFilterBinder.bind(property, collection);
    if (property.isSorted()) {
      collection.setSorted(true);
    }

    DependantValue key = primaryKeyValueCreator.createPrimaryKeyValue(collection);
    collection.setKey(key);
    collectionKeyBinder.bind(property, key, associatedClass, collection);

    Optional.ofNullable(property.getMappedForm().getCache())
        .ifPresent(cacheConfig -> collection.setCacheConcurrencyStrategy(cacheConfig.getUsage()));

    bindCollectionElement(property, mappings, collection);
    collectionKeyColumnUpdater.forceNullableAndCheckUpdatable(key, property);
  }

  private void bindOneToManyAssociation(
      HibernateToManyProperty property,
      PersistentClass associatedClass,
      Collection collection) {
    if (!collection.isOneToMany()) {
      return;
    }
    OneToMany oneToMany = (OneToMany) collection.getElement();
    oneToMany.setAssociatedClass(associatedClass);
    if (property.shouldBindWithForeignKey()) {
      collection.setCollectionTable(associatedClass.getTable());
    }
    collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property);
  }


  private void bindCollectionElement(
      HibernateToManyProperty property,
      InFlightMetadataCollector mappings,
      Collection collection) {
    if (property instanceof HibernateManyToManyProperty manyToMany && manyToMany.isBidirectional()) {
      bindManyToManyElement(manyToMany, collection);
    } else if (property.isBidirectionalOneToManyMap() && property.isBidirectional()) {
      bindBidirectionalMapElement(property, collection);
    } else if (property instanceof HibernateOneToManyProperty oneToManyProperty && oneToManyProperty.isUnidirectionalOneToMany()) {
      unidirectionalOneToManyBinder.bind(oneToManyProperty, collection);
    } else if (property.supportsJoinColumnMapping()) {
      collectionWithJoinTableBinder.bindCollectionWithJoinTable(property, mappings, collection);
    }
  }

  private void bindManyToManyElement(
      HibernateManyToManyProperty manyToMany, Collection collection) {
    HibernateManyToManyProperty otherSide = manyToMany.getHibernateInverseSide();
    ManyToOne element =
        manyToOneBinder.bindManyToOne(otherSide, collection.getCollectionTable(), EMPTY_PATH);
    element.setReferencedEntityName(otherSide.getOwner().getName());
    collection.setElement(element);
    collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, manyToMany);
    if (manyToMany.isCircular()) {
      collection.setInverse(false);
    }
  }

  private void bindBidirectionalMapElement(
      HibernateToManyProperty property, Collection collection) {
    HibernateManyToOneProperty otherSide =
        (HibernateManyToOneProperty) property.getHibernateInverseSide();
    ManyToOne element =
        manyToOneBinder.bindManyToOne(otherSide, collection.getCollectionTable(), EMPTY_PATH);
    element.setReferencedEntityName(otherSide.getOwner().getName());
    collection.setElement(element);
    collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property);
  }

  private @Nonnull PersistentClass resolveAssociatedClass(
      HibernateToManyProperty property, Map<?, ?> persistentClasses) {
    return Optional.ofNullable(property.getHibernateAssociatedEntity())
        .map(
            referenced -> (PersistentClass) persistentClasses.get(referenced.getName()))
        .orElseThrow(() -> new MappingException("Association [" + property.getName() + "] has no associated class"));
  }

}