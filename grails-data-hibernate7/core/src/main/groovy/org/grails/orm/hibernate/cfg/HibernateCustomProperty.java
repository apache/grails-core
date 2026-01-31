package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.CustomWithMapping;

import java.beans.PropertyDescriptor;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;

/**
 * Hibernate implementation of {@link org.grails.datastore.mapping.model.types.Custom}
 */
public class HibernateCustomProperty extends CustomWithMapping<PropertyConfig> implements GrailsHibernatePersistentProperty {
    public HibernateCustomProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property, CustomTypeMarshaller<?, ?, ?> customTypeMarshaller) {
        super(entity, context, property, customTypeMarshaller);
    }
}
