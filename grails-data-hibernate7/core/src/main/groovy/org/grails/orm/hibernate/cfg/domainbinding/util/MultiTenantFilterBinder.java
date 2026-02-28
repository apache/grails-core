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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Optional;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;

/**
 * Utility class for binding multi-tenant filters to the Hibernate meta model.
 *
 * @since 7.0
 */
public class MultiTenantFilterBinder {

  private final GrailsPropertyResolver grailsPropertyResolver;
  private final MultiTenantFilterDefinitionBinder filterDefinitionBinder;
  private final InFlightMetadataCollector mappings;
  private final DefaultColumnNameFetcher fetcher;

  public MultiTenantFilterBinder(
      @Nonnull GrailsPropertyResolver grailsPropertyResolver,
      @Nonnull MultiTenantFilterDefinitionBinder filterDefinitionBinder,
      @Nonnull InFlightMetadataCollector mappings,
      @Nonnull DefaultColumnNameFetcher fetcher) {
    this.grailsPropertyResolver = grailsPropertyResolver;
    this.filterDefinitionBinder = filterDefinitionBinder;
    this.mappings = mappings;
    this.fetcher = fetcher;
  }

  /**
   * Adds a multi-tenant filter to the given persistent class if necessary.
   *
   * @param entity The target persistent entity
   * @param persistentClass The persistent class to add the filter to
   */
  public void addMultiTenantFilterIfNecessary(
      @Nonnull GrailsHibernatePersistentEntity entity, @Nonnull PersistentClass persistentClass) {

    if (!entity.isMultiTenant()) {
      return;
    }

    Optional.ofNullable(entity.getTenantId())
        .map(TenantId::getName)
        .map(name -> grailsPropertyResolver.getProperty(persistentClass, name))
        .ifPresent(
            property -> {
              var filterName = GormProperties.TENANT_IDENTITY;
              filterDefinitionBinder.ensureGlobalFilterDefinition(mappings, filterName, property);
              applyFilterToPersistentClass(entity, persistentClass, filterName, property);
            });
  }

  private void applyFilterToPersistentClass(
      GrailsHibernatePersistentEntity entity,
      PersistentClass persistentClass,
      String filterName,
      Property property) {

    if (shouldApplyFilter(entity, persistentClass, property)) {
      persistentClass.addFilter(
          filterName,
          entity.getMultiTenantFilterCondition(fetcher),
          true, // autoAliasInjection
          Collections.emptyMap(),
          Collections.emptyMap());
    }
  }

  private boolean shouldApplyFilter(
      GrailsHibernatePersistentEntity entity, PersistentClass persistentClass, Property property) {
    boolean isRoot =
        persistentClass instanceof RootClass
            || persistentClass.equals(persistentClass.getRootClass());

    var table = persistentClass.getTable();
    var propertyValue = property.getValue();
    var propertyTable = propertyValue != null ? propertyValue.getTable() : null;

    boolean isInherited = table != null && propertyTable != null && !table.equals(propertyTable);

    // Apply if it's the root or if the subclass has its own table containing the column
    // (UnionSubclass).
    // Skip if it's a SingleTable subclass (redundant) or JoinedSubclass where column is in root
    // (alias safety).
    if (isRoot || !isInherited) {
      return isRoot || !entity.isTablePerHierarchySubclass();
    }
    return false;
  }
}
