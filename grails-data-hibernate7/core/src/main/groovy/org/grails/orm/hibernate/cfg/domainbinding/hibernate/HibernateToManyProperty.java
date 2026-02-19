package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.mapping.PropertyWithMapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.FetchMode;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Marker interface for Hibernate to-many associations
 */
public interface HibernateToManyProperty extends PropertyWithMapping<PropertyConfig>, GrailsHibernatePersistentProperty {

    default boolean hasSort() {
        return StringUtils.hasText(getMappedForm().getSort());
    }

    default String getSort() {
        return getMappedForm().getSort();
    }

    default String getOrder() {
        return getMappedForm().getOrder();
    }

    default boolean getIgnoreNotFound() {
        return getMappedForm().getIgnoreNotFound();
    }

    default FetchMode getFetchMode() {
        return getMappedForm().getFetchMode();
    }

    default Boolean getLazy() {
        return getMappedForm().getLazy();
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
