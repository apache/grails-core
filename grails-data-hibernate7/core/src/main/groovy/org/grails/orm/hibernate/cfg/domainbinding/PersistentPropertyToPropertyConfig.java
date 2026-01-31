package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.MappingException;

import java.util.Optional;

import jakarta.annotation.Nonnull;

public class PersistentPropertyToPropertyConfig  {
    @Nonnull public PropertyConfig toPropertyConfig(PersistentProperty persistentProperty) {
        return Optional.of(persistentProperty)
                .map(PersistentProperty::getMappedForm)
                .map(PropertyConfig.class::cast)
                .orElseThrow(() -> new MappingException("No PropertyConfig found for property"));
    }
}
