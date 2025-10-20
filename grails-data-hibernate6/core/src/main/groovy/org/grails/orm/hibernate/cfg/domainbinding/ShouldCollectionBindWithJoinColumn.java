package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.function.Function;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.ToMany;

import static java.util.Optional.ofNullable;

public class ShouldCollectionBindWithJoinColumn implements Function<ToMany,Boolean> {

    @Override
    public Boolean apply(ToMany property) {
        return (ofNullable(property).map(PersistentProperty::isUnidirectionalOneToMany).orElse(false) || (property instanceof Basic));
    }
}
