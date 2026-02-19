package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Subclass;

import java.util.Collection;

/**
 * Binder for subclasses.
 */
public class SubClassBinder {

    private final MappingCacheHolder mappingCacheHolder;
    private final SubclassMappingBinder subclassMappingBinder;
    private final MultiTenantFilterBinder multiTenantFilterBinder;
    private final DefaultColumnNameFetcher defaultColumnNameFetcher;
    private final String dataSourceName;

    public SubClassBinder(
            MappingCacheHolder mappingCacheHolder,
            SubclassMappingBinder subclassMappingBinder,
            MultiTenantFilterBinder multiTenantFilterBinder,
            DefaultColumnNameFetcher defaultColumnNameFetcher,
            String dataSourceName) {
        this.mappingCacheHolder = mappingCacheHolder;
        this.subclassMappingBinder = subclassMappingBinder;
        this.multiTenantFilterBinder = multiTenantFilterBinder;
        this.defaultColumnNameFetcher = defaultColumnNameFetcher;
        this.dataSourceName = dataSourceName;
    }

    /**
     * Binds a sub class.
     *
     * @param sub                The sub domain class instance
     * @param parent             The parent persistent class instance
     * @param mappings           The mappings instance
     * @param m                  The mapping config
     */
    public void bindSubClass(@Nonnull GrailsHibernatePersistentEntity sub,
                              PersistentClass parent,
                              @Nonnull InFlightMetadataCollector mappings,
                              Mapping m) {
        mappingCacheHolder.cacheMapping(sub);
        Subclass subClass = subclassMappingBinder.createSubclassMapping(sub, parent, mappings, m);

        parent.addSubclass(subClass);
        mappings.addEntityBinding(subClass);

        multiTenantFilterBinder.addMultiTenantFilterIfNecessary(sub, subClass, mappings, defaultColumnNameFetcher);

        Collection<GrailsHibernatePersistentEntity> children = sub.getChildEntities(dataSourceName);
        if (!children.isEmpty()) {
            // bind the sub classes
            children.forEach(sub1 -> bindSubClass(sub1, subClass, mappings, m));
        }
    }
}
