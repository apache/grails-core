package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;

/**
 * Marker interface for Hibernate associations
 */
public interface HibernateToManyProperty extends GrailsHibernatePersistentProperty {

    default boolean isOwningSide() {
        return ((Association) this).isOwningSide();
    }

    default boolean isBidirectional() {
        return ((Association) this).isBidirectional();
    }

    default Association getInverseSide() {
        return ((Association) this).getInverseSide();
    }

    default boolean isCircular() {
        return ((Association) this).isCircular();
    }

    default PersistentEntity getAssociatedEntity() {
        return ((Association) this).getAssociatedEntity();
    }

    default boolean isBidirectionalOneToManyMap() {
        return ((Association) this).isBidirectionalOneToManyMap();
    }
}
