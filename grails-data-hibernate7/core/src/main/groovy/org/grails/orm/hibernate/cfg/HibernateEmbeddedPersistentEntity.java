package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.EmbeddedPersistentEntity;
import org.grails.datastore.mapping.model.IdentityMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;

public class HibernateEmbeddedPersistentEntity extends EmbeddedPersistentEntity<Mapping> {
    private final ClassMapping<Mapping> classMapping;

    public Mapping getMappedForm() {
        return classMapping.getMappedForm();
    }

    public HibernateEmbeddedPersistentEntity(Class type, MappingContext ctx) {
        super(type, ctx);
        this.classMapping = new ClassMapping<Mapping>() {
            Mapping mappedForm = (Mapping) context.getMappingFactory().createMappedForm(HibernateEmbeddedPersistentEntity.this);

            @Override
            public PersistentEntity getEntity() {
                return HibernateEmbeddedPersistentEntity.this;
            }

            @Override
            public Mapping getMappedForm() {
                return mappedForm;
            }

            @Override
            public IdentityMapping getIdentifier() {
                return null;
            }


        };


    }

    @Override
    public ClassMapping getMapping() {
        return classMapping;
    }
}
