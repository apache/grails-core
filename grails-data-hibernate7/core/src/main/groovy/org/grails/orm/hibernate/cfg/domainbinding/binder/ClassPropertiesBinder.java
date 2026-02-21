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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;

/**
 * Binds the properties of a Grails domain class to the Hibernate meta-model.
 *
 * @author Graeme Rocher
 * @since 7.0
 */
public class ClassPropertiesBinder {

  private final GrailsPropertyBinder grailsPropertyBinder;
  private final PropertyFromValueCreator propertyFromValueCreator;
  private final NaturalIdentifierBinder naturalIdentifierBinder;

  public ClassPropertiesBinder(
      GrailsPropertyBinder grailsPropertyBinder,
      PropertyFromValueCreator propertyFromValueCreator,
      NaturalIdentifierBinder naturalIdentifierBinder) {
    this.grailsPropertyBinder = grailsPropertyBinder;
    this.propertyFromValueCreator = propertyFromValueCreator;
    this.naturalIdentifierBinder = naturalIdentifierBinder;
  }

  public ClassPropertiesBinder(
      GrailsPropertyBinder grailsPropertyBinder,
      PropertyFromValueCreator propertyFromValueCreator) {
    this(grailsPropertyBinder, propertyFromValueCreator, new NaturalIdentifierBinder());
  }

  /**
   * Binds the properties of the specified Grails domain class to the Hibernate meta-model.
   *
   * @param domainClass The Grails domain class
   * @param persistentClass The Hibernate PersistentClass instance
   * @param mappings The Hibernate InFlightMetadataCollector instance
   */
  public void bindClassProperties(
      @Nonnull GrailsHibernatePersistentEntity domainClass,
      PersistentClass persistentClass,
      @Nonnull InFlightMetadataCollector mappings) {

    persistentClass.getTable().setComment(domainClass.getMappedForm().getComment());
    Table table = persistentClass.getTable();

    for (GrailsHibernatePersistentProperty currentGrailsProp :
        domainClass.getPersistentPropertiesToBind()) {
      Value value =
          grailsPropertyBinder.bindProperty(
              persistentClass,
              table,
              GrailsDomainBinder.EMPTY_PATH,
              null,
              currentGrailsProp,
              mappings);
      persistentClass.addProperty(
          propertyFromValueCreator.createProperty(value, currentGrailsProp));
    }

    naturalIdentifierBinder.bindNaturalIdentifier(domainClass.getMappedForm(), persistentClass);
  }
}
