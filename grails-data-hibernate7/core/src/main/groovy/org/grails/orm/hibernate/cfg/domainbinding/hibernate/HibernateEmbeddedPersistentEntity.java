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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.hibernate.mapping.PersistentClass;

import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.EmbeddedPersistentEntity;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.orm.hibernate.cfg.Mapping;

public class HibernateEmbeddedPersistentEntity extends EmbeddedPersistentEntity<Mapping>
        implements GrailsHibernatePersistentEntity {
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
    public HibernatePersistentProperty getIdentity() {
        return super.getIdentity() instanceof HibernatePersistentProperty ghpp ? ghpp : null;
    }

    @Override
    public HibernatePersistentProperty[] getCompositeIdentity() {
        return new HibernatePersistentProperty[0];
    }

    @Override
    public HibernatePersistentProperty getVersion() {
        return super.getVersion() instanceof HibernatePersistentProperty ghpp ? ghpp : null;
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

    public HibernateEmbeddedPersistentEntity(Class<?> type, MappingContext ctx) {
        super(type, ctx);
        this.classMapping = new HibernateEmbeddedClassMapping(this, ctx);
    }

    @Override
    public ClassMapping<Mapping> getMapping() {
        return classMapping;
    }
}
