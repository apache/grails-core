package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.Iterator;

import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;

public class ComponentUpdater {

    private final PropertyFromValueCreator propertyFromValueCreator;

    public ComponentUpdater(PropertyFromValueCreator propertyFromValueCreator) {
        this.propertyFromValueCreator = propertyFromValueCreator;
    }

    public void updateComponent(Component component,
                                GrailsHibernatePersistentProperty componentProperty,
                                GrailsHibernatePersistentProperty currentGrailsProp,
                                Value value) {
        Property persistentProperty = propertyFromValueCreator.createProperty(value, currentGrailsProp);
        component.addProperty(persistentProperty);
        if (componentProperty != null && componentProperty.getOwner() instanceof GrailsHibernatePersistentEntity ghpe && ghpe.isComponentPropertyNullable(componentProperty)) {
            final Iterator<?> columnIterator = value.getColumns().iterator();
            while (columnIterator.hasNext()) {
                Column c = (Column) columnIterator.next();
                c.setNullable(true);
            }
        }
    }
}
