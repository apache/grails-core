package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import java.util.Optional;
import java.util.function.Function;

public class PersistentPropertyToPropertyConfig implements Function<PersistentProperty, PropertyConfig> {
    @Override
    public PropertyConfig apply(PersistentProperty persistentProperty) {
        return Optional.ofNullable(persistentProperty)
                .map(PersistentProperty::getMappedForm)
                .map(PropertyConfig.class::cast)
                .orElse(null);
    }
}
