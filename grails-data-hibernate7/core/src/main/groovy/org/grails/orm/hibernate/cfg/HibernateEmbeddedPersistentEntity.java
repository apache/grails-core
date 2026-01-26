package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.*;

public class HibernateEmbeddedPersistentEntity extends EmbeddedPersistentEntity<Mapping> implements GrailsHibernatePersistentEntity {
    private final ClassMapping<Mapping> classMapping;

    public Mapping getMappedForm() {
        return classMapping.getMappedForm();
    }

    @Override
    public boolean forGrailsDomainMapping(String dataSourceName) {
        return false;
    }

    @Override
    public boolean usesConnectionSource(String dataSourceName) {
        return ConnectionSourcesSupport.usesConnectionSource(this, dataSourceName);
    }

    @Override
    public PersistentProperty[] getCompositeIdentity() {
        return null;
    }

    @Override
    public boolean isAbstract() {
        return false;
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
