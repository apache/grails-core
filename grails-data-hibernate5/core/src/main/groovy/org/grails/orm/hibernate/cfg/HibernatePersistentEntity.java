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
package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.AbstractClassMapping;
import org.grails.datastore.mapping.model.AbstractPersistentEntity;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Persistent entity implementation for Hibernate
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class HibernatePersistentEntity extends AbstractPersistentEntity<Mapping> {

    private final AbstractClassMapping<Mapping> classMapping;

    public HibernatePersistentEntity(Class javaClass, final MappingContext context) {
        super(javaClass, context);


        this.classMapping = new AbstractClassMapping<Mapping>(this, context) {
            Mapping mappedForm = (Mapping) context.getMappingFactory().createMappedForm(HibernatePersistentEntity.this);

            @Override
            public PersistentEntity getEntity() {
                return HibernatePersistentEntity.this;
            }

            @Override
            public Mapping getMappedForm() {
                return mappedForm;
            }
        };

    }

    @Override
    protected boolean includeIdentifiers() {
        return true;
    }

    @Override
    public ClassMapping<Mapping> getMapping() {
        return this.classMapping;
    }
}
