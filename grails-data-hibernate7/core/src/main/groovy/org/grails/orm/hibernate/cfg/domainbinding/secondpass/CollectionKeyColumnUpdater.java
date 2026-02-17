package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

import org.hibernate.mapping.DependantValue;

import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * Forces columns to be nullable and checks if the key is updateable.
 */
public class CollectionKeyColumnUpdater {

    public void forceNullableAndCheckUpdatable(DependantValue key, HibernateToManyProperty property) {
        StreamSupport.stream(key.getColumns().spliterator(), false)
                .filter(Objects::nonNull)
                .forEach(column -> column.setNullable(true));

        long unidirectionalCount = property.getHibernateOwner()
                .getPersistentPropertiesToBind()
                .stream()
                .filter(p -> p instanceof HibernateToManyProperty association && !association.isBidirectional())
                .count();

        key.setUpdateable(unidirectionalCount <= 1);
    }
}
