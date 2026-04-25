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
package org.grails.datastore.mapping.simple;

import org.grails.datastore.mapping.core.AbstractSession;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.EntityPersister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister;
import org.grails.datastore.mapping.transactions.Transaction;
import org.springframework.context.ApplicationEventPublisher;

import java.io.Serializable;
import java.util.Map;

/**
 * A {@link org.grails.datastore.mapping.core.Session} implementation that backs onto an in-memory map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class SimpleMapSession extends AbstractSession {

    public SimpleMapSession(Datastore datastore, MappingContext mappingContext,
               ApplicationEventPublisher publisher) {
        super(datastore, mappingContext, publisher);
    }

    @Override
    public boolean isPendingAlready(Object obj) {
        return false;
    }

    public Map<Serializable, Map> getBackingMap() {
        return ((SimpleMapDatastore)getDatastore()).getBackingMap();
    }

    public Map getIndices() {
        return ((SimpleMapDatastore)getDatastore()).getIndices();
    }

    @Override
    protected EntityPersister createPersister(Class cls, MappingContext mappingContext) {
        PersistentEntity entity = mappingContext.getPersistentEntity(cls.getName());
        if (entity == null) {
            return null;
        }
        return new SimpleMapEntityPersister(mappingContext, entity, this,
                publisher);
    }

    @Override
    protected Transaction beginTransactionInternal() {
        return new MockTransaction(this);
    }

    @Override
    public Map<Serializable, Map> getNativeInterface() {
        return getBackingMap();
    }

    private class MockTransaction implements Transaction {
        public MockTransaction(SimpleMapSession simpleMapSession) {
        }

        public void commit() {
            flush();
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
