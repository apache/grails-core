package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.OneToOneWithMapping;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import java.beans.PropertyDescriptor;

/**
 * Hibernate implementation of {@link org.grails.datastore.mapping.model.types.OneToOne}
 */
public class HibernateOneToOneProperty extends OneToOneWithMapping<PropertyConfig> implements HibernateToManyProperty {
    public HibernateOneToOneProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }

    @Override
    public GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
        return (GrailsHibernatePersistentEntity) super.getAssociatedEntity();
    }
}
