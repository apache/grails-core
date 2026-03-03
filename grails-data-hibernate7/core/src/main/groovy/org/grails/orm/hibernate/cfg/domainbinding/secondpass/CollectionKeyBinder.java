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
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.PersistentClass;

/** Binds the collection key value for a to-many association. */
public class CollectionKeyBinder {

  private final BidirectionalOneToManyLinker bidirectionalOneToManyLinker;
  private final DependentKeyValueBinder dependentKeyValueBinder;
  private final SimpleValueColumnBinder simpleValueColumnBinder;

  /** Creates a new {@link CollectionKeyBinder} instance. */
  public CollectionKeyBinder(
      BidirectionalOneToManyLinker bidirectionalOneToManyLinker,
      DependentKeyValueBinder dependentKeyValueBinder,
      SimpleValueColumnBinder simpleValueColumnBinder) {
    this.bidirectionalOneToManyLinker = bidirectionalOneToManyLinker;
    this.dependentKeyValueBinder = dependentKeyValueBinder;
    this.simpleValueColumnBinder = simpleValueColumnBinder;
  }

  /** Binds the collection key for the given property and collection. */
  public void bind(
      HibernateToManyProperty property,
      DependantValue key,
      PersistentClass associatedClass,
      Collection collection) {
    if (property.isBidirectional()) {
      var inverseSide = property.getHibernateInverseSide();
      if (inverseSide instanceof ToOne && property.shouldBindWithForeignKey()) {
        bidirectionalOneToManyLinker.link(collection, associatedClass, key, inverseSide);
      } else if (inverseSide instanceof HibernateManyToManyProperty
          || Map.class.isAssignableFrom(property.getType())) {
        dependentKeyValueBinder.bind(property, key);
      }
    } else {
      if (property.getMappedForm().hasJoinKeyMapping()) {
        simpleValueColumnBinder.bindSimpleValue(
            key, "long", property.getMappedForm().getJoinTable().getKey().getName(), true);
      } else {
        dependentKeyValueBinder.bind(property, key);
      }
    }
  }
}
