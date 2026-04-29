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
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.engine.EntityPersister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import grails.gorm.multitenancy.Tenants;
import org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister;
import org.grails.datastore.mapping.transactions.Transaction;
import org.springframework.context.ApplicationEventPublisher;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
        SimpleMapDatastore datastore = (SimpleMapDatastore) getDatastore();
        MultiTenancySettings.MultiTenancyMode mode = datastore.getMultiTenancyMode();
        if (mode == MultiTenancySettings.MultiTenancyMode.DATABASE || mode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            Serializable tenantId = Tenants.currentId((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)datastore);
            if (tenantId != null) {
                return datastore.getBackingMap(tenantId.toString());
            }
        }
        return datastore.getBackingMap();
    }

    public Map getIndices() {
        SimpleMapDatastore datastore = (SimpleMapDatastore) getDatastore();
        MultiTenancySettings.MultiTenancyMode mode = datastore.getMultiTenancyMode();
        if (mode == MultiTenancySettings.MultiTenancyMode.DATABASE || mode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            Serializable tenantId = Tenants.currentId((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)datastore);
            if (tenantId != null) {
                return datastore.getIndices(tenantId.toString());
            }
        }
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
