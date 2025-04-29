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
package org.grails.datastore.mapping.keyvalue.engine;

import org.springframework.context.ApplicationEventPublisher;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.NativeEntryEntityPersister;
import org.grails.datastore.mapping.keyvalue.mapping.config.Family;
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValue;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.PropertyMapping;

/**
 * Abstract implementation of the EntityPersister abstract class
 * for key/value style stores.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class AbstractKeyValueEntityPersister<T,K> extends NativeEntryEntityPersister<T,K> {
    protected String entityFamily;

    protected AbstractKeyValueEntityPersister(MappingContext context, PersistentEntity entity,
               Session session, ApplicationEventPublisher publisher) {
        super(context, entity, session, publisher);
        entityFamily = getFamily(entity, classMapping);
    }

    @Override
    public String getEntityFamily() {
        return entityFamily;
    }

    @Override
    public ClassMapping getClassMapping() {
        return classMapping;
    }

    @Override
    protected String getNativePropertyKey(PersistentProperty prop) {
        PropertyMapping<KeyValue> pm = prop.getMapping();
        String propKey = null;
        if (pm.getMappedForm()!=null) {
            propKey = pm.getMappedForm().getKey();
        }
        if (propKey == null) {
            propKey = prop.getName();
        }
        return propKey;
    }

    protected String getFamily(PersistentEntity persistentEntity, ClassMapping<Family> cm) {
        String table = null;
        if (cm.getMappedForm() != null) {
            table = cm.getMappedForm().getFamily();
        }
        if (table == null) table = persistentEntity.getJavaClass().getName();
        return table;
    }

    protected String getKeyspace(ClassMapping<Family> cm, String defaultValue) {
        String keyspace = null;
        if (cm.getMappedForm() != null) {
            keyspace = cm.getMappedForm().getKeyspace();
        }
        if (keyspace == null) keyspace = defaultValue;
        return keyspace;
    }
}
