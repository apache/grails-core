package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.function.Function;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;

import static java.util.Optional.ofNullable;

public class ShouldCollectionBindWithJoinColumn implements Function<HibernateToManyProperty,Boolean> {

    @Override
    public Boolean apply(HibernateToManyProperty property) {
        return (ofNullable(property).map(PersistentProperty::isUnidirectionalOneToMany).orElse(false) || (property instanceof Basic));
    }
}
