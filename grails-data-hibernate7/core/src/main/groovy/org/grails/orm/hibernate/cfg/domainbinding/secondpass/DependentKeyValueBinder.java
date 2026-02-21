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

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH;

import java.util.Optional;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.mapping.DependantValue;

/** Binds a dependent key value for collection associations. */
public class DependentKeyValueBinder {

  private final SimpleValueBinder simpleValueBinder;
  private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;

  public DependentKeyValueBinder(
      SimpleValueBinder simpleValueBinder,
      CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder) {
    this.simpleValueBinder = simpleValueBinder;
    this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
  }

  public void bind(HibernateToManyProperty property, DependantValue key) {
    GrailsHibernatePersistentEntity refDomainClass = property.getHibernateOwner();

    Optional<CompositeIdentity> compositeIdentity =
        property.supportsJoinColumnMapping()
            ? refDomainClass.getHibernateCompositeIdentity()
            : Optional.empty();

    compositeIdentity.ifPresentOrElse(
        ci -> {
          compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(
              property, key, ci, refDomainClass, EMPTY_PATH);
        },
        () -> {
          simpleValueBinder.bindSimpleValue(property, null, key, EMPTY_PATH);
        });
  }
}
