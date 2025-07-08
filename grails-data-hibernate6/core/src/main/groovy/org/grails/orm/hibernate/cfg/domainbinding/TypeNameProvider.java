package org.grails.orm.hibernate.cfg.domainbinding;

import jakarta.validation.constraints.NotNull;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import static java.util.Optional.ofNullable;

public class TypeNameProvider {

    public String getTypeName(@NotNull PersistentProperty property
                                ,@NotNull Mapping mapping) {
       return ofNullable(getPropertyConfig(property))
                .map(PropertyConfig::getType)
                .map(typeObj -> typeObj instanceof Class<?> clazz ?
                        clazz.getName() : typeObj.toString()
                )
               .orElse(mapping.getTypeName(property.getType()));
    }

    private PropertyConfig getPropertyConfig(PersistentProperty property) {
        return (PropertyConfig) property.getMapping().getMappedForm();
    }
}



