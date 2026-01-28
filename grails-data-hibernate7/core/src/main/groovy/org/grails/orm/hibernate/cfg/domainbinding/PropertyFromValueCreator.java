package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

import org.grails.datastore.mapping.model.PersistentProperty;

public class PropertyFromValueCreator {

    private final PropertyBinder propertyBinder;

    public PropertyFromValueCreator() {
        this.propertyBinder = new PropertyBinder();
    }

    protected PropertyFromValueCreator(PropertyBinder propertyBinder) {
        this.propertyBinder = propertyBinder;
    }

    public Property createProperty(Value value, PersistentProperty grailsProperty) {
        // set type
        value.setTypeUsingReflection(grailsProperty.getOwnerClassName(), grailsProperty.getName());

        if (value.getTable() != null) {
            value.createForeignKey();
        }

        Property prop = new Property();
        prop.setValue(value);
        propertyBinder.bindProperty(grailsProperty, prop);
        return prop;
    }
}