package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.DependantValue;

import java.util.stream.StreamSupport;

/**
 * Forces columns to be nullable and checks if the key is updateable.
 */
public class CollectionKeyColumnUpdater {

    public void forceNullableAndCheckUpdateable(DependantValue key, GrailsHibernatePersistentProperty property) {
        StreamSupport.stream(key.getColumns().spliterator(), false)
                .filter(Column.class::isInstance)
                .map(Column.class::cast)
                .forEach(column -> column.setNullable(true));

        long unidirectionalCount = property.getHibernateOwner()
                .getPersistentPropertiesToBind()
                .stream()
                .filter(p -> p instanceof Association association && !association.isBidirectional())
                .count();

        key.setUpdateable(unidirectionalCount <= 1);
    }
}
