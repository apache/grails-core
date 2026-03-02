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

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ComponentBinder {

  private final MetadataBuildingContext metadataBuildingContext;
  private final MappingCacheHolder mappingCacheHolder;
  private final ComponentUpdater componentUpdater;
  private final InFlightMetadataCollector metadataCollector;
  private GrailsPropertyBinder grailsPropertyBinder;

  public ComponentBinder(
      MetadataBuildingContext metadataBuildingContext,
      MappingCacheHolder mappingCacheHolder,
      ComponentUpdater componentUpdater,
      InFlightMetadataCollector metadataCollector) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.mappingCacheHolder = mappingCacheHolder;
    this.componentUpdater = componentUpdater;
    this.metadataCollector = metadataCollector;
  }

  public void setGrailsPropertyBinder(GrailsPropertyBinder grailsPropertyBinder) {
    this.grailsPropertyBinder = grailsPropertyBinder;
  }

  public Component bindComponent(
      PersistentClass owner,
      HibernateEmbeddedProperty embeddedProperty,
      String path) {
    Component component = new Component(metadataBuildingContext, owner);
    Class<?> type = embeddedProperty.getType();
    String role = GrailsHibernateUtil.qualify(type.getName(), embeddedProperty.getName());
    component.setRoleName(role);
    component.setComponentClassName(type.getName());

    GrailsHibernatePersistentEntity domainClass =
        (GrailsHibernatePersistentEntity) embeddedProperty.getAssociatedEntity();
    mappingCacheHolder.cacheMapping(domainClass);

    Table table = component.getOwner().getTable();
    PersistentClass persistentClass = component.getOwner();
    String currentPath =
        path.isEmpty() ? embeddedProperty.getName() : path + "." + embeddedProperty.getName();
    Class<?> propertyType = embeddedProperty.getOwner().getJavaClass();

    domainClass
        .getHibernateParentProperty(propertyType)
        .ifPresent(p -> component.setParentProperty(p.getName()));

    for (HibernatePersistentProperty peerProperty :
        domainClass.getHibernatePersistentProperties(propertyType)) {
      var value =
          grailsPropertyBinder.bindProperty(
              persistentClass, table, currentPath, embeddedProperty, peerProperty);
      componentUpdater.updateComponent(component, embeddedProperty, peerProperty, value);
    }
}
