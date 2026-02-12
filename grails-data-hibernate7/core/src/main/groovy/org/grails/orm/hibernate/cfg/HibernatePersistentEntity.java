/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.*;

import java.util.Optional;

import jakarta.persistence.Entity;

/**
 * Persistent entity implementation for Hibernate
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class HibernatePersistentEntity extends AbstractPersistentEntity<Mapping> implements GrailsHibernatePersistentEntity {
    private final AbstractClassMapping<Mapping> classMapping;
    private String dataSourceName;


    public HibernatePersistentEntity(Class javaClass, final MappingContext context) {
        super(javaClass, context);

        this.classMapping = new HibernateClassMapping(this, context);
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
    protected boolean includeIdentifiers() {
        return true;
    }


    @Override
    public ClassMapping<Mapping> getMapping() {
        return this.classMapping;
    }

    public Mapping getMappedForm() {
        return Optional.ofNullable(getMapping()).map(ClassMapping::getMappedForm).orElse(null);
    }

    @Override
    public GrailsHibernatePersistentProperty getIdentity() {
        return identity instanceof GrailsHibernatePersistentProperty ghpp ? ghpp : null;
    }

    @Override
    public GrailsHibernatePersistentProperty[] getCompositeIdentity() {
        PersistentProperty[] compositeIdentity = super.getCompositeIdentity();
        if (compositeIdentity == null) {
            return null;
        }
        GrailsHibernatePersistentProperty[] result = new GrailsHibernatePersistentProperty[compositeIdentity.length];
        for (int i = 0; i < compositeIdentity.length; i++) {
            result[i] = compositeIdentity[i] instanceof GrailsHibernatePersistentProperty ghpp ? ghpp : null;
        }
        return result;
    }

    private boolean isAnnotatedEntity() {
        return getJavaClass().isAnnotationPresent(Entity.class);
    }

    public boolean usesConnectionSource(String dataSourceName) {
        return ConnectionSourcesSupport.usesConnectionSource(this, dataSourceName);
    }

    public boolean forGrailsDomainMapping(String dataSourceName) {
        return !isAnnotatedEntity()
                && usesConnectionSource(dataSourceName)
                && isRoot();
    }

    @Override
    public GrailsHibernatePersistentProperty getVersion() {
        return version instanceof GrailsHibernatePersistentProperty ghpp ? ghpp : null;
    }


}
