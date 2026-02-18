package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.mapping.PropertyWithMapping;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Marker interface for Hibernate to-many associations
 */
public interface HibernateToManyProperty extends PropertyWithMapping<PropertyConfig>, GrailsHibernatePersistentProperty {

    /**
     * Binds the order by clause for the collection if configured.
     *
     * @param collection The Hibernate collection
     * @param persistentClasses The map of persistent classes
     * @param orderByClauseBuilder The order by clause builder
     * @return The associated persistent class.
     * @throws MappingException if the association references an unmapped class
     */
    default PersistentClass bindOrderBy(Collection collection, Map<?, ?> persistentClasses, OrderByClauseBuilder orderByClauseBuilder) {
        return Optional.ofNullable(getHibernateAssociatedEntity())
                .map(referenced -> {
                    if (referenced.isTablePerHierarchySubclass()) {
                        String discriminatorColumnName = referenced.getDiscriminatorColumnName();
                        Set<String> discSet = referenced.buildDiscriminatorSet();
                        String inclause = String.join(",", discSet);

                        collection.setWhere(discriminatorColumnName + " in (" + inclause + ")");
                    }

                    PersistentClass associatedClass = (PersistentClass) persistentClasses.get(referenced.getName());
                    if (associatedClass == null) {
                        throw new MappingException("Association references unmapped class: " + referenced.getName());
                    }

                    if (hasSort()) {
                        if (!isBidirectional() && this instanceof HibernateOneToManyProperty) {
                            throw new DatastoreConfigurationException("Default sort for associations [" + getHibernateOwner().getName() + "->" + getName() +
                                    "] are not supported with unidirectional one to many relationships.");
                        }
                        GrailsHibernatePersistentProperty sortBy = (GrailsHibernatePersistentProperty) referenced.getPropertyByName(getSort());
                        String order = Optional.ofNullable(getOrder()).orElse("asc");
                        collection.setOrderBy(orderByClauseBuilder.buildOrderByClause(sortBy.getName(), associatedClass, collection.getRole(), order));
                    }
                    return associatedClass;
                })
                .orElse(null);
    }

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
