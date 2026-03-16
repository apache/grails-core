package org.grails.datastore.mapping.model.types.mapping;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.PropertyMapping;

public interface PropertyWithMapping<T extends Property> extends PersistentProperty<T> {

    PropertyMapping<T> getMapping();
}
