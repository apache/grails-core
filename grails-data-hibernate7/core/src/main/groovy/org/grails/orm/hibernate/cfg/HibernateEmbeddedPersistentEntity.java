package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.*;

public class HibernateEmbeddedPersistentEntity extends EmbeddedPersistentEntity<Mapping> implements GrailsHibernatePersistentEntity {
    private final ClassMapping<Mapping> classMapping;
    private String dataSourceName;

    public Mapping getMappedForm() {
        return classMapping.getMappedForm();
    }

    @Override
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    @Override
    public String getDataSourceName() {
        return dataSourceName;
    }

    @Override
    public GrailsHibernatePersistentProperty getIdentity() {
        return super.getIdentity() instanceof GrailsHibernatePersistentProperty ghpp ? ghpp : null;
    }

    @Override
    public GrailsHibernatePersistentProperty[] getCompositeIdentity() {
        return null;
    }

    @Override
    public GrailsHibernatePersistentProperty getVersion() {
        return super.getVersion() instanceof GrailsHibernatePersistentProperty ghpp ? ghpp : null;
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