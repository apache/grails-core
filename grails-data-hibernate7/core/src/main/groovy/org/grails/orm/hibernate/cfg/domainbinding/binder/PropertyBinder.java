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

import java.util.Optional;
import org.codehaus.groovy.transform.trait.Traits;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.orm.hibernate.access.TraitPropertyAccessStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation;
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehaviorFetcher;
import org.hibernate.boot.spi.AccessType;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

public class PropertyBinder {

  private final CascadeBehaviorFetcher cascadeBehaviorFetcher;

  public PropertyBinder(CascadeBehaviorFetcher cascadeBehaviorFetcher) {
    this.cascadeBehaviorFetcher = cascadeBehaviorFetcher;
  }

  public PropertyBinder() {
    this(new CascadeBehaviorFetcher());
  }

  /**
   * Binds a property to Hibernate runtime meta model. Deals with cascade strategy based on the
   * Grails domain model
   *
   * @param persistentProperty The grails property instance
   * @param value The Hibernate value
   * @return The Hibernate property
   */
  public Property bindProperty(GrailsHibernatePersistentProperty persistentProperty, Value value) {
    var prop = new Property();
    prop.setValue(value);
    // set the property name
    prop.setName(persistentProperty.getName());
    PropertyConfig config = persistentProperty.getMappedForm();
    if (config == null) {
      config = new PropertyConfig();
    }

    if (persistentProperty instanceof HibernateAssociation assoc
        && assoc.isBidirectionalManyToOneWithListMapping(prop)) {
      prop.setInsertable(false);
      prop.setUpdatable(false);
    } else {
      prop.setInsertable(config.getInsertable());
      prop.setUpdatable(config.getUpdatable());
    }

    var accessType = AccessType.getAccessStrategy(config.getAccessType());

    var accessorName =
        accessType == AccessType.FIELD
            ? Optional.ofNullable(persistentProperty.getReader())
                .map(EntityReflector.PropertyReader::getter)
                .map(getter -> getter.getAnnotation(Traits.Implemented.class))
                .map(annotation -> TraitPropertyAccessStrategy.class.getName())
                .orElse(accessType.getType())
            : accessType.getType();
    prop.setPropertyAccessorName(accessorName);

    prop.setOptional(persistentProperty.isNullable());
    if (persistentProperty instanceof Association<?> association
        && !persistentProperty.isEnumType()) {
      prop.setCascade(cascadeBehaviorFetcher.getCascadeBehaviour(association));
    }

    // lazy to true

    if (persistentProperty.isLazyAble()) {
      final boolean isLazy =
          Optional.ofNullable(config.getLazy()).orElse(persistentProperty instanceof Association);
      prop.setLazy(isLazy);
    }
    return prop;
  }
}
