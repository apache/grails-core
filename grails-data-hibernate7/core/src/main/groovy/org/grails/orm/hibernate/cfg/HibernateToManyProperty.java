package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.datastore.mapping.model.types.OneToMany;

import java.util.Map;

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

    default HibernateToManyProperty getHibernateInverseSide() {
        return (HibernateToManyProperty)((Association) this).getInverseSide();
    }

    default boolean isCircular() {
        return ((Association) this).isCircular();
    }

    default GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
        return (GrailsHibernatePersistentEntity) ((Association) this).getAssociatedEntity();
    }

    default boolean isBidirectionalOneToManyMap() {
        return ((Association) this).isBidirectionalOneToManyMap();
    }

    /**
     * @return Whether the collection should be bound with a foreign key
     */
    default boolean shouldBindWithForeignKey() {
        return ((this instanceof OneToMany) && isBidirectional() ||
                !isUnidirectionalOneToMany()) &&
                !Map.class.isAssignableFrom(getType()) &&
                !(this instanceof ManyToMany) &&
                !(this instanceof Basic);
    }
}
