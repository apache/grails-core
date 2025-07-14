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
package org.grails.datastore.mapping.keyvalue.mapping.config;

import org.grails.datastore.mapping.model.AbstractClassMapping;
import org.grails.datastore.mapping.model.AbstractPersistentEntity;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Embedded;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class KeyValuePersistentEntity extends AbstractPersistentEntity<Family> {

    private Object mappedForm;
    private KeyValueClassMapping classMapping;

    public KeyValuePersistentEntity(@SuppressWarnings("rawtypes") Class javaClass, MappingContext context) {
        super(javaClass, context);
        this.mappedForm = context.getMappingFactory().createMappedForm(KeyValuePersistentEntity.this);
        this.classMapping = new KeyValueClassMapping(this, context);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ClassMapping<Family> getMapping() {
        return classMapping;
    }

    @Override
    public PersistentEntity getParentEntity() {
        Class superclass = this.javaClass.getSuperclass();
        if (superclass != null) {
            return context.getPersistentEntity(superclass.getName());
        }

        return null;
    }

    @Override
    public PersistentProperty getPropertyByName(String name) {
        if (name != null && name.contains(".")) {
            String[] props = name.split("\\.");

            // Get the embedded property type
            PersistentProperty embeddedProp = super.getPropertyByName(props[0]);
            if (embeddedProp instanceof Embedded) {
                PersistentEntity embeddedEntity = ((Embedded) embeddedProp).getAssociatedEntity();
                return embeddedEntity.getPropertyByName(props[1]);
            }

            return super.getPropertyByName(name);
        } else {
            return super.getPropertyByName(name);
        }
    }

    public class KeyValueClassMapping extends AbstractClassMapping<Family> {

        public KeyValueClassMapping(PersistentEntity entity, MappingContext context) {
            super(entity, context);

        }

        @Override
        public Family getMappedForm() {
            return (Family) mappedForm;
        }
    }
}
