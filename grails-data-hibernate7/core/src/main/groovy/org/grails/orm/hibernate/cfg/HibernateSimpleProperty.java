package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.SimpleWithMapping;

import java.beans.PropertyDescriptor;

/**
 * Hibernate implementation of {@link org.grails.datastore.mapping.model.types.Simple}
 */
public class HibernateSimpleProperty extends SimpleWithMapping<PropertyConfig> implements GrailsHibernatePersistentProperty {
    public HibernateSimpleProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }
}
