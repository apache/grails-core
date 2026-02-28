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
import java.util.Collection;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Subclass;

/** Binder for subclasses. */
public class SubClassBinder {

  private final MappingCacheHolder mappingCacheHolder;
  private final SubclassMappingBinder subclassMappingBinder;
  private final MultiTenantFilterBinder multiTenantFilterBinder;
  private final String dataSourceName;

  public SubClassBinder(
      MappingCacheHolder mappingCacheHolder,
      SubclassMappingBinder subclassMappingBinder,
      MultiTenantFilterBinder multiTenantFilterBinder,
      String dataSourceName) {
    this.mappingCacheHolder = mappingCacheHolder;
    this.subclassMappingBinder = subclassMappingBinder;
    this.multiTenantFilterBinder = multiTenantFilterBinder;
    this.dataSourceName = dataSourceName;
  }

  /**
   * Binds a sub class.
   *
   * @param sub The sub domain class instance
   * @param parent The parent persistent class instance
   * @param mappings The mappings instance
   * @param m The mapping config
   */
  public void bindSubClass(
      @Nonnull GrailsHibernatePersistentEntity sub,
      PersistentClass parent,
      @Nonnull InFlightMetadataCollector mappings,
      Mapping m) {
    mappingCacheHolder.cacheMapping(sub);
    Subclass subClass = subclassMappingBinder.createSubclassMapping(sub, parent, mappings, m);

    parent.addSubclass(subClass);
    mappings.addEntityBinding(subClass);

    multiTenantFilterBinder.addMultiTenantFilterIfNecessary(sub, subClass);

    Collection<GrailsHibernatePersistentEntity> children = sub.getChildEntities(dataSourceName);
    if (!children.isEmpty()) {
      // bind the sub classes
      children.forEach(sub1 -> bindSubClass(sub1, subClass, mappings, m));
    }
  }
}
