package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.TenantIdWithMapping;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import java.beans.PropertyDescriptor;

/**
 * Hibernate implementation of {@link org.grails.datastore.mapping.model.types.TenantId}
 */
public class HibernateTenantIdProperty extends TenantIdWithMapping<PropertyConfig> implements GrailsHibernatePersistentProperty {
    public HibernateTenantIdProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }
}
