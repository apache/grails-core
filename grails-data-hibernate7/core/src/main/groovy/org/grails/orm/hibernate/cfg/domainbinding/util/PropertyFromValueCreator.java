package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder;

public class PropertyFromValueCreator {

    private final PropertyBinder propertyBinder;

    public PropertyFromValueCreator() {
        this.propertyBinder = new PropertyBinder();
    }

    protected PropertyFromValueCreator(PropertyBinder propertyBinder) {
        this.propertyBinder = propertyBinder;
    }

    public Property createProperty(Value value, GrailsHibernatePersistentProperty grailsProperty) {
        // set type
        if (!grailsProperty.isEnumType()) {
            value.setTypeUsingReflection(grailsProperty.getOwnerClassName(), grailsProperty.getName());
        }

        if (value.getTable() != null) {
            value.createForeignKey();
        }

        return propertyBinder.bindProperty(grailsProperty, value);
    }
}