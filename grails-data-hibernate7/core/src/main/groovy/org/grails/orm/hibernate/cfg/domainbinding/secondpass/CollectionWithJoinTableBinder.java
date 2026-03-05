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
import org.grails.orm.hibernate.cfg.*;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

import org.hibernate.mapping.*;

/** Binds a collection with a join table. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CollectionWithJoinTableBinder {

  private final PersistentEntityNamingStrategy namingStrategy;
  private final UnidirectionalOneToManyInverseValuesBinder
      unidirectionalOneToManyInverseValuesBinder;
  private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;
  private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder;
  private final SimpleValueColumnBinder simpleValueColumnBinder;
  private final BasicCollectionElementBinder basicCollectionElementBinder;

  /** Creates a new {@link CollectionWithJoinTableBinder} instance. */
  public CollectionWithJoinTableBinder(
      PersistentEntityNamingStrategy namingStrategy,
      UnidirectionalOneToManyInverseValuesBinder unidirectionalOneToManyInverseValuesBinder,
      CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder,
      CollectionForPropertyConfigBinder collectionForPropertyConfigBinder,
      SimpleValueColumnBinder simpleValueColumnBinder,
      BasicCollectionElementBinder basicCollectionElementBinder) {
    this.namingStrategy = namingStrategy;
    this.unidirectionalOneToManyInverseValuesBinder = unidirectionalOneToManyInverseValuesBinder;
    this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
    this.collectionForPropertyConfigBinder = collectionForPropertyConfigBinder;
    this.simpleValueColumnBinder = simpleValueColumnBinder;
    this.basicCollectionElementBinder = basicCollectionElementBinder;
  }

  /** Bind collection with join table. */
  public void bindCollectionWithJoinTable(
      @Nonnull HibernateToManyProperty property,
      @Nonnull Collection collection) {

    collection.setInverse(false);
    SimpleValue element;
    if (property.isBasic()) {
      element = basicCollectionElementBinder.bind(property, collection);
    } else {
      element = unidirectionalOneToManyInverseValuesBinder.bind(property, collection);
      final var domainClass = property.getHibernateAssociatedEntity();
      if (domainClass != null) {
          if (domainClass.getHibernateCompositeIdentity().isPresent()) {
          CompositeIdentity ci = domainClass.getHibernateCompositeIdentity().get();
          compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(
              property, element, ci, domainClass, EMPTY_PATH);
        } else {
          simpleValueColumnBinder.bindSimpleValue(
              element, "long", property.resolveJoinTableForeignKeyColumnName(namingStrategy), true);
        }

        collection.setElement(element);
        collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property);
    }

    collection.setElement(element);
    collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property);
  }
}
