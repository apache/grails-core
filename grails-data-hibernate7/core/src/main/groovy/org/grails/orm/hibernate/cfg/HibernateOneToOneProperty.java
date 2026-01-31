package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.OneToOneWithMapping;

import java.beans.PropertyDescriptor;

/**
 * Hibernate implementation of {@link org.grails.datastore.mapping.model.types.OneToOne}
 */
public class HibernateOneToOneProperty extends OneToOneWithMapping<PropertyConfig> implements GrailsHibernatePersistentProperty {
    public HibernateOneToOneProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }
}
