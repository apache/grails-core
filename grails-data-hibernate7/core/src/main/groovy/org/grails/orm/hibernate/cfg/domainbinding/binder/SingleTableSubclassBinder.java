package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.SingleTableSubclass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds a sub-class using table-per-hierarchy inheritance mapping
 *
 * @since 7.0
 */
public class SingleTableSubclassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(SingleTableSubclassBinder.class);

    private final ClassBinder classBinder;

    public SingleTableSubclassBinder(ClassBinder classBinder) {
        this.classBinder = classBinder;
    }

    /**
     * Binds a sub-class using table-per-hierarchy inheritance mapping
     *
     * @param sub      The Grails domain class instance representing the sub-class
     * @param subClass The Hibernate SubClass instance
     * @param mappings The mappings instance
     */
    public void bindSubClass(@Nonnull GrailsHibernatePersistentEntity sub, SingleTableSubclass subClass, @Nonnull InFlightMetadataCollector mappings) {
        classBinder.bindClass(sub, subClass, mappings);
        subClass.setDiscriminatorValue(sub.getDiscriminatorValue());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Mapping subclass: " + subClass.getEntityName() +
                    " -> " + subClass.getTable().getName());
        }
    }
}
