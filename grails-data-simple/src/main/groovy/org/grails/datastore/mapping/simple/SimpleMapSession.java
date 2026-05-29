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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.ApplicationEventPublisher;

import org.grails.datastore.mapping.core.AbstractSession;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.EntityPersister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister;
import org.grails.datastore.mapping.transactions.Transaction;

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

    /**
     * In the in-memory test datastore the backing map can be emptied between unit-test feature
     * methods (see {@code SimpleMapDatastore.clearData()}), while {@code @Shared} domain instances
     * that were saved in a previous iteration retain their identifier and a "clean" dirty-checking
     * state. {@link org.grails.datastore.mapping.engine.NativeEntryEntityPersister} skips the write
     * for an identified instance that is not dirty, which would leave the cleared datastore empty on
     * re-save. Treat an identified instance that is absent from the backing map as dirty so that
     * {@code save()} re-inserts it. Hibernate (H5/H7) does not need this: {@code saveOrUpdate}
     * already re-inserts a detached instance that is missing from the database.
     */
    @Override
    public boolean isDirty(Object instance) {
        if (super.isDirty(instance)) {
            return true;
        }
        if (instance == null) {
            return false;
        }
        EntityPersister persister = (EntityPersister) getPersister(instance);
        if (persister == null) {
            return false;
        }
        Serializable id = persister.getObjectIdentifier(instance);
        if (id == null) {
            return false;
        }
        String family = ((SimpleMapEntityPersister) persister).getEntityFamily();
        Map familyMap = getBackingMap().get(family);
        Object key = id instanceof Number ? ((Number) id).longValue() : id;
        return familyMap == null || !familyMap.containsKey(key);
    }

    public Map<Serializable, Map> getBackingMap() {
        SimpleMapDatastore datastore = (SimpleMapDatastore) getDatastore();
        return datastore.getBackingMap();
    }

    public Map getIndices() {
        SimpleMapDatastore datastore = (SimpleMapDatastore) getDatastore();
        return datastore.getIndices();
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

    private boolean rollbackOnly = false;

    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    public boolean isRollbackOnly() {
        return this.rollbackOnly;
    }

    @Override
    public void flush() {
        if (!isRollbackOnly()) {
            super.flush();
        }
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
        private final SimpleMapSession session;
        private final Map<Serializable, Map> dataBackup;
        private final Map<String, List> indicesBackup;
        private final Map<String, Long> lastKeysBackup;

        public MockTransaction(SimpleMapSession simpleMapSession) {
            this.session = simpleMapSession;
            SimpleMapDatastore datastore = (SimpleMapDatastore) session.getDatastore();
            SimpleMapDatastore.SharedState state = datastore.getSharedState();
            
            this.dataBackup = new HashMap<>();
            for (Map.Entry<Serializable, Map> entry : state.inmemoryData.entrySet()) {
                Map familyMap = entry.getValue();
                Map familyBackup = new HashMap();
                for (Object key : familyMap.keySet()) {
                    Object val = familyMap.get(key);
                    if (val instanceof Map) {
                        familyBackup.put(key, new HashMap((Map) val));
                    } else {
                        familyBackup.put(key, val);
                    }
                }
                dataBackup.put(entry.getKey(), familyBackup);
            }
            
            this.indicesBackup = new HashMap<>();
            for (Map.Entry<String, List> entry : state.indices.entrySet()) {
                indicesBackup.put(entry.getKey(), new ArrayList(entry.getValue()));
            }
            
            this.lastKeysBackup = new HashMap<>();
            for (Map.Entry<String, AtomicLong> entry : state.lastKeys.entrySet()) {
                lastKeysBackup.put(entry.getKey(), entry.getValue().get());
            }
        }

        public void commit() {
            if (!session.isRollbackOnly()) {
                session.flush();
            }
        }

        public void rollback() {
            session.setRollbackOnly();
            SimpleMapDatastore datastore = (SimpleMapDatastore) session.getDatastore();
            SimpleMapDatastore.SharedState state = datastore.getSharedState();
            
            state.inmemoryData.clear();
            state.inmemoryData.putAll(dataBackup);
            
            state.indices.clear();
            state.indices.putAll(indicesBackup);
            
            for (Map.Entry<String, Long> entry : lastKeysBackup.entrySet()) {
                AtomicLong al = state.lastKeys.get(entry.getKey());
                if (al != null) {
                    al.set(entry.getValue());
                }
            }
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

        public void setRollbackOnly() {
            session.setRollbackOnly();
        }

        public boolean isRollbackOnly() {
            return session.isRollbackOnly();
        }
    }
}
