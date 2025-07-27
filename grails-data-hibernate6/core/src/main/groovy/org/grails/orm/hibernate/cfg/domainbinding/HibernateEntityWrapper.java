package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.hibernate.MappingException;

import java.util.Optional;

public class HibernateEntityWrapper {

    private HibernatePersistentEntity hibernatePersistentEntity;
    private HibernateMappingContext.HibernateEmbeddedPersistentEntity  hibernateEmbeddedPersistentEntity;

    public HibernateEntityWrapper(PersistentEntity hibernatePersistentEntity) {
        if (hibernatePersistentEntity instanceof HibernatePersistentEntity) {
            this.hibernatePersistentEntity = (HibernatePersistentEntity) hibernatePersistentEntity;
        } else if (hibernatePersistentEntity instanceof HibernateMappingContext.HibernateEmbeddedPersistentEntity) {
            this.hibernateEmbeddedPersistentEntity = (HibernateMappingContext.HibernateEmbeddedPersistentEntity) hibernatePersistentEntity;
        } else {
            throw new MappingException("Not correct Persistent Entity "+  hibernatePersistentEntity.getClass());
        }
    }


    public Mapping getMappedForm() {
       if (hibernatePersistentEntity != null) {
           return hibernatePersistentEntity.getMappedForm();
       } else if (hibernateEmbeddedPersistentEntity != null) {
           return hibernateEmbeddedPersistentEntity.getMappedForm();
       } else {
           throw new MappingException("Not correct Persistent Entity");
       }
    }


}
