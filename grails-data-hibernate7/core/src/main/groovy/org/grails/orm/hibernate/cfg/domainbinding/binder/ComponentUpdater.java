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

import java.util.Iterator;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

public class ComponentUpdater {

  private final PropertyFromValueCreator propertyFromValueCreator;

  public ComponentUpdater(PropertyFromValueCreator propertyFromValueCreator) {
    this.propertyFromValueCreator = propertyFromValueCreator;
  }

  public void updateComponent(
      Component component,
      HibernatePersistentProperty componentProperty,
      HibernatePersistentProperty currentGrailsProp,
      Value value) {
    Property persistentProperty = propertyFromValueCreator.createProperty(value, currentGrailsProp);
    component.addProperty(persistentProperty);
    if (componentProperty != null
        && componentProperty.getOwner() instanceof GrailsHibernatePersistentEntity ghpe
        && ghpe.isComponentPropertyNullable(componentProperty)) {
      final Iterator<?> columnIterator = value.getColumns().iterator();
      while (columnIterator.hasNext()) {
        Column c = (Column) columnIterator.next();
        c.setNullable(true);
      }
    }
  }
}
