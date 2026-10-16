/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import groovy.transform.CompileStatic
import jakarta.persistence.Entity
import org.hibernate.MappingException
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass

import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport
import org.grails.datastore.mapping.model.AbstractClassMapping
import org.grails.datastore.mapping.model.AbstractPersistentEntity
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.Mapping

/**
 * Persistent entity implementation for Hibernate
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class HibernatePersistentEntity extends AbstractPersistentEntity<Mapping> implements GrailsHibernatePersistentEntity {

    private final AbstractClassMapping<Mapping> classMapping
    private String dataSourceName
    private PersistentClass persistentClass

    HibernatePersistentEntity(Class<?> javaClass, final MappingContext context) {
        super(javaClass, context)

        this.classMapping = new HibernateClassMapping(this, context)
    }

    @Override
    String getDataSourceName() {
        return dataSourceName
    }

    @Override
    void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName
    }

    @Override
    ClassMapping<Mapping> getMapping() {
        return this.classMapping
    }

    @Override
    Mapping getMappedForm() {
        return mapping?.mappedForm
    }

    @Override
    HibernatePersistentProperty getIdentity() {
        PersistentProperty<?> id = super.getIdentity()
        return id instanceof HibernatePersistentProperty ? (HibernatePersistentProperty) id : null
    }

    @Override
    @SuppressWarnings(['PMD.DataflowAnomalyAnalysis', 'PMD.NullAssignment'])
    HibernatePersistentProperty[] getCompositeIdentity() {
        PersistentProperty<?>[] compositeIdentity = super.getCompositeIdentity()
        if (compositeIdentity == null) {
            return new HibernatePersistentProperty[0]
        }
        return compositeIdentity.collect { PersistentProperty<?> p -> (HibernatePersistentProperty) p } as HibernatePersistentProperty[]
    }

    HibernateIdentityProperty getIdentityProperty() {
        HibernatePersistentProperty[] compositeId = compositeIdentity
        if (compositeId != null && compositeId.length > 1) {
            return new HibernateCompositeIdentityProperty(this, mappingContext, name, Object, compositeId)
        }
        HibernatePersistentProperty id = identity
        if (id instanceof HibernateSimpleIdentityProperty) {
            return (HibernateSimpleIdentityProperty) id
        }
        throw new MappingException("Entity [${name}] has no identity property. " +
                'Only embedded entities are allowed to have no identity.')
    }

    private boolean isAnnotatedEntity() {
        return javaClass.isAnnotationPresent(Entity)
    }

    @Override
    boolean usesConnectionSource(String dataSourceName) {
        return ConnectionSourcesSupport.usesConnectionSource(this, dataSourceName)
    }

    @Override
    boolean forGrailsDomainMapping(String dataSourceName) {
        return !isAnnotatedEntity() && usesConnectionSource(dataSourceName) && isRoot()
    }

    @Override
    HibernatePersistentProperty getVersion() {
        return (HibernatePersistentProperty) super.getVersion()
    }

    @Override
    PersistentClass getPersistentClass() {
        return persistentClass
    }

    RootClass getRootClass() {
        return persistentClass.rootClass
    }

    @Override
    void setPersistentClass(PersistentClass persistentClass) {
        this.persistentClass = persistentClass
    }

    String getIdentityGeneratorName() {
        if (hibernateIdentity instanceof HibernateSimpleIdentity) {
            HibernateSimpleIdentity simpleIdentity = (HibernateSimpleIdentity) hibernateIdentity
            Mapping result = hibernateMappedForm
            boolean useSequence = result != null && result.isTablePerConcreteClass()
            return simpleIdentity.determineGeneratorName(useSequence)
        }
        throw new MappingException('Simple Identity expected')
    }

}
