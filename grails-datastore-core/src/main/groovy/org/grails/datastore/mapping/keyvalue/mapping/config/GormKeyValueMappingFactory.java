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

import org.grails.datastore.mapping.config.AbstractGormMappingFactory;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.IdentityMapping;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.PropertyMapping;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class GormKeyValueMappingFactory extends AbstractGormMappingFactory<Family, KeyValue> {

    private String keyspace;

    public GormKeyValueMappingFactory(String keyspace) {
        this.keyspace = keyspace;
    }

    @Override
    public Family createMappedForm(PersistentEntity entity) {
        Family family = super.createMappedForm(entity);
        if (family.getKeyspace() == null) {
            family.setKeyspace(keyspace);
        }
        if (family.getFamily() == null) {
            family.setFamily(entity.getName());
        }
        return family;
    }

    @Override
    public KeyValue createMappedForm(PersistentProperty mpp) {
        KeyValue kv = super.createMappedForm(mpp);
        if (kv.getKey() == null) {
            kv.setKey(mpp.getName());
        }
        return kv;
    }

    @Override
    protected PropertyMapping<KeyValue> createPropertyMapping(final PersistentProperty<KeyValue> property, final PersistentEntity owner) {
        PropertyMapping<KeyValue> mapping = super.createPropertyMapping(property, owner);
        if (mapping.getMappedForm().getFormula() != null) {
            mapping.getMappedForm().setDerived(true);
        }

        return mapping;
    }

    @Override
    protected Class<KeyValue> getPropertyMappedFormType() {
        return KeyValue.class;
    }

    @Override
    protected Class<Family> getEntityMappedFormType() {
        return Family.class;
    }

    @Override
    protected IdentityMapping getIdentityMappedForm(final ClassMapping classMapping, KeyValue property) {
        if (property != null) {
            return createDefaultIdentityMapping(classMapping, property);
        } else {
            return super.getIdentityMappedForm(classMapping, null);
        }
    }
}
