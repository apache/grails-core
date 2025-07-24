/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.keyvalue.mapping.config;

import java.beans.PropertyDescriptor;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.MappingFactory;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class KeyValueMappingFactory extends MappingFactory<Family, KeyValue> {

    protected String keyspace;

    public KeyValueMappingFactory(String keyspace) {
        this.keyspace = keyspace;
    }

    @Override
    public Family createMappedForm(PersistentEntity entity) {
        return new Family(keyspace, entity.getName());
    }

    @Override
    public KeyValue createMappedForm(@SuppressWarnings("rawtypes") PersistentProperty mpp) {
        return new KeyValue(mpp.getName());
    }

    @Override
    public boolean isTenantId(PersistentEntity entity, MappingContext context, PropertyDescriptor descriptor) {
        return false;
    }
}
