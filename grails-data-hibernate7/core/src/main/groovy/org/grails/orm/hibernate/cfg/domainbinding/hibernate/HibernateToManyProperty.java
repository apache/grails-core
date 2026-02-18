package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.mapping.PropertyWithMapping;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import java.util.Map;

/**
 * Marker interface for Hibernate to-many associations
 */
public interface HibernateToManyProperty extends PropertyWithMapping<PropertyConfig>, GrailsHibernatePersistentProperty {

    /**
     * @return Whether the collection should be bound with a foreign key
     */
    default boolean shouldBindWithForeignKey() {
        return ((this instanceof HibernateOneToManyProperty) && isBidirectional() ||
                !isUnidirectionalOneToMany()) &&
                !Map.class.isAssignableFrom(getType()) &&
                !(this instanceof HibernateManyToManyProperty) &&
                !(this instanceof Basic);
    }
}
