package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;

/**
 * Common interface for Hibernate persistent entities
 */
public interface GrailsHibernatePersistentEntity extends PersistentEntity {
    Mapping getMappedForm();

    boolean forGrailsDomainMapping(String dataSourceName);

    boolean usesConnectionSource(String dataSourceName);

    PersistentProperty[] getCompositeIdentity();

    boolean isAbstract();
}
