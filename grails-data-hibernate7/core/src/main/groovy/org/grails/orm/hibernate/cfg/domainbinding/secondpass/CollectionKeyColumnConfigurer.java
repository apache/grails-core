package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.DependantValue;

import java.util.Iterator;
import java.util.stream.StreamSupport;

/**
 * Configures nullability and updateability for collection key columns.
 */
public class CollectionKeyColumnConfigurer {

    public void configure(DependantValue key, GrailsHibernatePersistentProperty property) {
        // Force all columns in the key to be nullable
        StreamSupport.stream(key.getColumns().spliterator(), false)
                .filter(Column.class::isInstance)
                .map(Column.class::cast)
                .forEach(column -> column.setNullable(true));

        // Determine updateable status based on unidirectional associations
        long unidirectionalCount = property.getHibernateOwner()
                .getPersistentPropertiesToBind()
                .stream()
                .filter(p -> p instanceof Association association && !association.isBidirectional())
                .count();

        key.setUpdateable(unidirectionalCount <= 1);
    }
}
