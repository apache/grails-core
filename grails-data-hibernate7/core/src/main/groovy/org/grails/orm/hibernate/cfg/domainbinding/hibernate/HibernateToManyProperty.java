package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.Mapping;

import java.util.Optional;
import java.util.Map;

/**
 * Marker interface for Hibernate associations
 */
public interface HibernateToManyProperty extends GrailsHibernatePersistentProperty {

    default Optional<CompositeIdentity> getCompositeIdentity(Mapping mapping) {
        return Optional.ofNullable(mapping)
                .filter(m -> m.hasCompositeIdentifier() && supportsJoinColumnMapping())
                .map(Mapping::getIdentity)
                .filter(CompositeIdentity.class::isInstance)
                .map(CompositeIdentity.class::cast);
    }

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
        return ((this instanceof HibernateOneToManyProperty) && isBidirectional() ||
                !isUnidirectionalOneToMany()) &&
                !Map.class.isAssignableFrom(getType()) &&
                !(this instanceof HibernateManyToManyProperty) &&
                !(this instanceof Basic);
    }
}
