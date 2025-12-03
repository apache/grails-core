package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.hibernate.MappingException;

import java.util.Optional;

import jakarta.annotation.Nonnull;

/**
 * This class exists because Embedded Entities do not inherit
 * from HibernatePersistentEntity but have similar functionality.
 */
public class HibernateEntityWrapper {

    @Nonnull
    public Mapping getMappedForm(PersistentEntity persistentEntity) {
        if (persistentEntity instanceof HibernatePersistentEntity _hibernatePersistentEntity) {
            return _hibernatePersistentEntity.getMappedForm();
        } else if (persistentEntity instanceof HibernateMappingContext.HibernateEmbeddedPersistentEntity embedded) {
            return embedded.getMappedForm();
        } else {
            throw new MappingException("Not correct Persistent Entity");
        }
    }

}
