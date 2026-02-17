package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.util.Collections;
import java.util.Optional;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;

import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;

/**
 * Utility class for binding multi-tenant filters to the Hibernate meta model.
 *
 * @since 7.0
 */
public class MultiTenantFilterBinder {

    private final GrailsPropertyResolver grailsPropertyResolver;

    public MultiTenantFilterBinder(GrailsPropertyResolver grailsPropertyResolver) {
        this.grailsPropertyResolver = grailsPropertyResolver;
    }

    /**
     * Adds a multi-tenant filter to the given persistent class if necessary.
     *
     * @param entity          The target persistent entity
     * @param persistentClass The persistent class to add the filter to
     * @param mappings        The in-flight metadata collector
     * @param fetcher         The column name fetcher
     */
    public void addMultiTenantFilterIfNecessary(
            @Nonnull GrailsHibernatePersistentEntity entity,
            @Nonnull PersistentClass persistentClass,
            @Nonnull InFlightMetadataCollector mappings,
            @Nonnull DefaultColumnNameFetcher fetcher) {

        if (!entity.isMultiTenant()) {
            return;
        }

        Optional.ofNullable(entity.getTenantId())
                .map(TenantId::getName)
                .map(name -> grailsPropertyResolver.getProperty(persistentClass, name))
                .ifPresent(property -> {
                    var filterName = GormProperties.TENANT_IDENTITY;
                    ensureGlobalFilterDefinition(mappings, filterName, property);
                    applyFilterToPersistentClass(entity, persistentClass, fetcher, filterName, property);
                });
    }

    private void ensureGlobalFilterDefinition(InFlightMetadataCollector mappings, String filterName, Property property) {
        if (mappings.getFilterDefinition(filterName) == null && property.getValue() instanceof BasicValue basicValue) {
            JdbcMapping jdbcMapping = basicValue.resolve().getJdbcMapping();
            mappings.addFilterDefinition(new FilterDefinition(
                    filterName,
                    null, // No default condition; let classes specify their own
                    Collections.singletonMap(filterName, jdbcMapping)
            ));
        }
    }

    private void applyFilterToPersistentClass(
            GrailsHibernatePersistentEntity entity,
            PersistentClass persistentClass,
            DefaultColumnNameFetcher fetcher,
            String filterName,
            Property property) {

        if (shouldApplyFilter(entity, persistentClass, property)) {
            persistentClass.addFilter(
                    filterName,
                    entity.getMultiTenantFilterCondition(fetcher),
                    true, // autoAliasInjection
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );
        }
    }

    private boolean shouldApplyFilter(GrailsHibernatePersistentEntity entity, PersistentClass persistentClass, Property property) {
        boolean isRoot = persistentClass instanceof RootClass || persistentClass.equals(persistentClass.getRootClass());
        
        var table = persistentClass.getTable();
        var propertyValue = property.getValue();
        var propertyTable = propertyValue != null ? propertyValue.getTable() : null;

        boolean isInherited = table != null && propertyTable != null && !table.equals(propertyTable);

        // Apply if it's the root or if the subclass has its own table containing the column (UnionSubclass).
        // Skip if it's a SingleTable subclass (redundant) or JoinedSubclass where column is in root (alias safety).
        if (isRoot || !isInherited) {
            return isRoot || !entity.isTablePerHierarchySubclass();
        }
        return false;
    }


}
