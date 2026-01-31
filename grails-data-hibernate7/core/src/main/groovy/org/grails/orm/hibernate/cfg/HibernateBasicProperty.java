package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.BasicWithMapping;

import java.beans.PropertyDescriptor;

/**
 * Hibernate implementation of {@link org.grails.datastore.mapping.model.types.Basic}
 */
public class HibernateBasicProperty extends BasicWithMapping<PropertyConfig> implements GrailsHibernatePersistentProperty {
    public HibernateBasicProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }
}
