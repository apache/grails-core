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

import java.util.Collections;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.JoinedSubclass;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SingleTableSubclass;
import org.hibernate.mapping.UnionSubclass;

import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

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
     * Binds a multi-tenant filter to the given root class if necessary.
     *
     * @param entity The target persistent entity
     * @param rootClass The root class to add the filter to
     * @return The filter definition applied, or null if none
     */
    @Nullable
    public FilterDefinition bind(@Nonnull HibernatePersistentEntity entity, @Nonnull RootClass rootClass) {
        return doBind(entity, rootClass);
    }

    /**
     * Binds a multi-tenant filter to the given single table subclass if necessary.
     *
     * @param entity The target persistent entity
     * @param subclass The single table subclass
     * @return null as it's redundant for single table subclasses
     */
    @Nullable
    public FilterDefinition bind(
            @Nonnull HibernatePersistentEntity entity, @Nonnull SingleTableSubclass subclass) {
        return null; // Redundant for SingleTableSubclass
    }

    /**
     * Binds a multi-tenant filter to the given joined subclass if necessary.
     *
     * @param entity The target persistent entity
     * @param subclass The joined subclass
     * @return The filter definition applied, or null if none
     */
    @Nullable
    public FilterDefinition bind(@Nonnull HibernatePersistentEntity entity, @Nonnull JoinedSubclass subclass) {
        return doBind(entity, subclass);
    }

    /**
     * Binds a multi-tenant filter to the given union subclass if necessary.
     *
     * @param entity The target persistent entity
     * @param subclass The union subclass
     * @return The filter definition applied, or null if none
     */
    @Nullable
    public FilterDefinition bind(@Nonnull HibernatePersistentEntity entity, @Nonnull UnionSubclass subclass) {
        return doBind(entity, subclass);
    }

    @Nullable
    private FilterDefinition doBind(
            @Nonnull HibernatePersistentEntity entity, @Nonnull PersistentClass persistentClass) {

        if (!entity.isMultiTenant()) {
            return null;
        }

        return Optional.ofNullable(entity.getHibernateTenantId())
                .map(HibernatePersistentProperty::getName)
                .map(name -> grailsPropertyResolver.getProperty(persistentClass, name))
                .filter(property -> shouldApplyFilter(entity, persistentClass, property))
                .map(property -> {
                    var filterName = GormProperties.TENANT_IDENTITY;
                    FilterDefinition filterDefinition = mappings.getFilterDefinition(filterName);
                    if (filterDefinition == null) {
                        filterDefinition = filterDefinitionBinder
                                .create(filterName, property)
                                .orElse(null);
                        if (filterDefinition != null) {
                            mappings.addFilterDefinition(filterDefinition);
                        }
                    }

                    if (filterDefinition != null) {
                        persistentClass.addFilter(
                                filterName,
                                entity.getMultiTenantFilterCondition(fetcher),
                                true, // autoAliasInjection
                                Collections.emptyMap(),
                                Collections.emptyMap());
                    }
                    return filterDefinition;
                })
                .orElse(null);
    }

    private boolean shouldApplyFilter(
            HibernatePersistentEntity entity, PersistentClass persistentClass, Property property) {
        if (!(property.getValue() instanceof BasicValue)) {
            return false;
        }

        boolean isRoot = persistentClass instanceof RootClass;

        var table = persistentClass.getTable();
        var propertyValue = property.getValue();
        var propertyTable = propertyValue != null ? propertyValue.getTable() : null;

        boolean isInherited = table != null && propertyTable != null && !table.equals(propertyTable);

        if (isRoot || !isInherited) {
            return isRoot || !entity.isTablePerHierarchySubclass();
        }
        return false;
    }
}
