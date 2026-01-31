package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.IdentityWithMapping;

import java.beans.PropertyDescriptor;

/**
 * Hibernate implementation of {@link org.grails.datastore.mapping.model.types.Identity}
 */
public class HibernateIdentityProperty extends IdentityWithMapping<PropertyConfig> implements GrailsHibernatePersistentProperty {
    public HibernateIdentityProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }
}
