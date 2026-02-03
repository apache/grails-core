package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

public class GrailsPropertyCreator {

    private final InFlightMetadataCollector mappings;
    private final PropertyBinder propertyBinder;

    public GrailsPropertyCreator(InFlightMetadataCollector mappings, PropertyBinder propertyBinder) {
        this.mappings = mappings;
        this.propertyBinder = propertyBinder;
    }

    public Property createProperty(Value value, PersistentClass persistentClass, PersistentProperty grailsProperty) {
        // set type
        value.setTypeUsingReflection(persistentClass.getClassName(), grailsProperty.getName());

        if (value.getTable() != null) {
            value.createForeignKey();
        }

        Property prop = new Property();
        prop.setValue(value);
        propertyBinder.bindProperty(grailsProperty, prop);
        return prop;
    }
}
