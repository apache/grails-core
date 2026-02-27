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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class GrailsPropertyBinder {

  private static final Logger LOG = LoggerFactory.getLogger(GrailsPropertyBinder.class);

  private final EnumTypeBinder enumTypeBinder;
  private final ComponentBinder componentBinder;
  private final CollectionBinder collectionBinder;
  private final SimpleValueBinder simpleValueBinder;
  private final OneToOneBinder oneToOneBinder;
  private final ManyToOneBinder manyToOneBinder;

  public GrailsPropertyBinder(
      EnumTypeBinder enumTypeBinder,
      ComponentBinder componentBinder,
      CollectionBinder collectionBinder,
      SimpleValueBinder simpleValueBinder,
      OneToOneBinder oneToOneBinder,
      ManyToOneBinder manyToOneBinder) {
    this.enumTypeBinder = enumTypeBinder;
    this.componentBinder = componentBinder;
    this.collectionBinder = collectionBinder;
    this.simpleValueBinder = simpleValueBinder;
    this.oneToOneBinder = oneToOneBinder;
    this.manyToOneBinder = manyToOneBinder;
  }

  public Value bindProperty(
      PersistentClass persistentClass,
      Table table,
      String path,
      HibernatePersistentProperty parentProperty,
      @Nonnull HibernatePersistentProperty currentGrailsProp,
      @Nonnull InFlightMetadataCollector mappings) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "[GrailsPropertyBinder] Binding persistent property ["
              + currentGrailsProp.getName()
              + "]");
    }

    Value value = null;

    // 1. Create Value and apply binders (consolidated block)
    if (currentGrailsProp.isEnumType()) {
      // HibernateEnumTypeProperty
      value =
          enumTypeBinder.bindEnumType(currentGrailsProp, currentGrailsProp.getType(), table, path);
    } else if (currentGrailsProp instanceof HibernateOneToOneProperty oneToOne) {
      // HibernateOneToOneProperty
      if (oneToOne.isHibernateOneToOne()) {
        value =
            oneToOneBinder.bindOneToOne(
                (org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp,
                persistentClass,
                table,
                path);
      } else {
        value =
            manyToOneBinder.bindManyToOne((HibernateToOneProperty) currentGrailsProp, table, path);
      }
    } else if (currentGrailsProp instanceof HibernateManyToOneProperty manyToOne) {
      value = manyToOneBinder.bindManyToOne(manyToOne, table, path);
    } else if (currentGrailsProp instanceof HibernateToManyProperty toMany
        && !currentGrailsProp.isSerializableType()) {
      // HibernateToManyProperty
      value = collectionBinder.bindCollection(toMany, persistentClass, mappings, path);
    } else if (currentGrailsProp instanceof HibernateEmbeddedProperty embedded) {
      value = componentBinder.bindComponent(persistentClass, embedded, mappings, path);
    } else {
      // HibernateSimpleProperty
      value = simpleValueBinder.bindSimpleValue(currentGrailsProp, parentProperty, table, path);
    }

    return value;
  }
}
