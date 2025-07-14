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
package org.grails.datastore.mapping.document.config;

import org.grails.datastore.mapping.model.AbstractClassMapping;
import org.grails.datastore.mapping.model.AbstractPersistentEntity;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.IdentityMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;

public class DocumentPersistentEntity extends AbstractPersistentEntity<Collection> {

    private final DocumentCollectionMapping classMapping;

    public DocumentPersistentEntity(@SuppressWarnings("rawtypes") Class javaClass, MappingContext context, boolean external) {
        super(javaClass, context);
        setExternal(external);
        this.classMapping = new DocumentCollectionMapping(this, context);
    }

    public DocumentPersistentEntity(@SuppressWarnings("rawtypes") Class javaClass, MappingContext context) {
        this(javaClass, context, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ClassMapping<Collection> getMapping() {
        return classMapping;
    }

    public class DocumentCollectionMapping extends AbstractClassMapping<Collection> {

        private final Collection mappedForm;

        private IdentityMapping identityMapping;

        public DocumentCollectionMapping(PersistentEntity entity, MappingContext context) {
            super(entity, context);
            if (entity.isExternal()) {
                this.mappedForm = null;
            } else {
                this.mappedForm = (Collection) context.getMappingFactory().createMappedForm(DocumentPersistentEntity.this);
            }
        }

        @Override
        public Collection getMappedForm() {
            return mappedForm;
        }

        @Override
        public IdentityMapping getIdentifier() {
            if (identityMapping == null) {
                identityMapping = context.getMappingFactory().createIdentityMapping(this);
            }
            return identityMapping;
        }
    }
}
