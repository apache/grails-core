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
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.RootClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Binder for root classes. */
public class RootBinder {

  private static final Logger LOG = LoggerFactory.getLogger(RootBinder.class);

  private final String dataSourceName;
  private final MultiTenantFilterBinder multiTenantFilterBinder;
  private final SubClassBinder subClassBinder;
  private final DefaultColumnNameFetcher defaultColumnNameFetcher;
  private final RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder;
  private final DiscriminatorPropertyBinder discriminatorPropertyBinder;

  public RootBinder(
      String dataSourceName,
      MultiTenantFilterBinder multiTenantFilterBinder,
      SubClassBinder subClassBinder,
      DefaultColumnNameFetcher defaultColumnNameFetcher,
      RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder,
      DiscriminatorPropertyBinder discriminatorPropertyBinder) {
    this.dataSourceName = dataSourceName;
    this.multiTenantFilterBinder = multiTenantFilterBinder;
    this.subClassBinder = subClassBinder;
    this.defaultColumnNameFetcher = defaultColumnNameFetcher;
    this.rootPersistentClassCommonValuesBinder = rootPersistentClassCommonValuesBinder;
    this.discriminatorPropertyBinder = discriminatorPropertyBinder;
  }

  /**
   * Binds a root class (one with no super classes) to the runtime meta model based on the supplied
   * Grails domain class
   *
   * @param entity The Grails domain class
   * @param mappings The Hibernate Mappings object
   */
  public void bindRoot(
      @Nonnull GrailsHibernatePersistentEntity entity,
      @Nonnull InFlightMetadataCollector mappings) {
    if (mappings.getEntityBinding(entity.getName()) != null) {
      LOG.info("[RootBinder] Class [" + entity.getName() + "] is already mapped, skipping.. ");
      return;
    }

    Collection<GrailsHibernatePersistentEntity> children = entity.getChildEntities(dataSourceName);
    RootClass root =
        rootPersistentClassCommonValuesBinder.bindRootPersistentClassCommonValues(
            entity, children, mappings);
    Mapping m = entity.getMappedForm();

    if (!children.isEmpty() && entity.isTablePerHierarchy()) {
      discriminatorPropertyBinder.bindDiscriminatorProperty(root, m);
    }

    // bind the sub classes
    children.forEach(sub -> subClassBinder.bindSubClass(sub, root, mappings, m));

    multiTenantFilterBinder.addMultiTenantFilterIfNecessary(
        entity, root, mappings, defaultColumnNameFetcher);

    mappings.addEntityBinding(root);
  }
}
