/* Copyright (C) 2010 SpringSource
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
package org.grails.datastore.mapping.simple;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.grails.datastore.mapping.core.AbstractSession;
import org.grails.datastore.mapping.engine.Persister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister;
import org.grails.datastore.mapping.transactions.Transaction;

/**
 * A simple implementation of the {@link org.grails.datastore.mapping.core.Session} interface that backs onto an in-memory map.
 * Mainly used for mocking and testing scenarios
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class SimpleMapSession extends AbstractSession<Map> {
    private Map<String, Map> datastore;

    public SimpleMapSession(SimpleMapDatastore datastore, MappingContext mappingContext,
               ApplicationEventPublisher publisher) {
        super(datastore, mappingContext, publisher);
        this.datastore = datastore.getBackingMap();
    }

    @Override
    public boolean isPendingAlready(Object obj) {
        return false;
    }

    @Override
    protected Persister createPersister(Class cls, MappingContext mappingContext) {
        PersistentEntity entity = mappingContext.getPersistentEntity(cls.getName());
        if (entity == null) {
            return null;
        }
        return new SimpleMapEntityPersister(mappingContext, entity, this,
            (SimpleMapDatastore) getDatastore(), publisher);
    }

    public Map<String, Map> getBackingMap() {
        return datastore;
    }

    @Override
    protected Transaction beginTransactionInternal() {
        return new MockTransaction(this);
    }

    public Map getNativeInterface() {
        return datastore;
    }

    private class MockTransaction implements Transaction {
        public MockTransaction(SimpleMapSession simpleMapSession) {
        }

        public void commit() {
            // do nothing
        }

        public void rollback() {
            // do nothing
        }

        public Object getNativeTransaction() {
            return this;
        }

        public boolean isActive() {
            return true;
        }

        public void setTimeout(int timeout) {
            // do nothing
        }
    }
}
