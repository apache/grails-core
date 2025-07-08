package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.Mapping;

import java.util.Objects;
import java.util.function.Consumer;
import static java.util.Optional.ofNullable;


public class ConfigureDerivedPropertiesConsumer implements Consumer<PersistentProperty> {

    private final Mapping m;

    public ConfigureDerivedPropertiesConsumer(Mapping m) {
        this.m = m;
    }
    @Override
    public void accept(PersistentProperty persistentProperty) {
        ofNullable(m.getPropertyConfig(persistentProperty.getName()))
            .ifPresent(propertyConfig ->
                    propertyConfig.setDerived(Objects.nonNull(propertyConfig.getFormula()))
            );
    }
}
