package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.*;

public class HibernateEmbeddedPersistentEntity extends EmbeddedPersistentEntity<Mapping> implements GrailsHibernatePersistentEntity {
    private final ClassMapping<Mapping> classMapping;

    public Mapping getMappedForm() {
        return classMapping.getMappedForm();
    }

    @Override
    public GrailsHibernatePersistentProperty getIdentity() {
        return (GrailsHibernatePersistentProperty) super.getIdentity();
    }

    @Override
    public GrailsHibernatePersistentProperty[] getCompositeIdentity() {
        return null;
    }

    @Override
    public GrailsHibernatePersistentProperty getVersion() {
        return (GrailsHibernatePersistentProperty) super.getVersion();
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
    public boolean isAbstract() {
        return false;
    }

    public HibernateEmbeddedPersistentEntity(Class type, MappingContext ctx) {
        super(type, ctx);
        this.classMapping = new HibernateEmbeddedClassMapping(this, ctx);
    }

    @Override
    public ClassMapping getMapping() {
        return classMapping;
    }
}