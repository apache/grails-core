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
package org.grails.datastore.mapping.engine;

import org.grails.datastore.mapping.cache.TPCacheAdapter;
import org.grails.datastore.mapping.cache.TPCacheAdapterRepository;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.springframework.context.ApplicationEventPublisher;

import java.io.Serializable;

/**
 *
 * An {@link org.grails.datastore.mapping.engine.EntityPersister} that supports third party cache adapters
 *
 * @author Graeme Rocher
 * @since 4.1
 */
public abstract class ThirdPartyCacheEntityPersister<T> extends LockableEntityPersister {

    protected TPCacheAdapterRepository<T> cacheAdapterRepository;

    public ThirdPartyCacheEntityPersister(MappingContext mappingContext, PersistentEntity entity, Session session, ApplicationEventPublisher publisher) {
        super(mappingContext, entity, session, publisher);
    }

    public ThirdPartyCacheEntityPersister(MappingContext mappingContext, PersistentEntity entity, Session session, ApplicationEventPublisher publisher, TPCacheAdapterRepository<T> cacheAdapterRepository) {
        super(mappingContext, entity, session, publisher);
        this.cacheAdapterRepository = cacheAdapterRepository;
    }

    protected void updateTPCache(PersistentEntity persistentEntity, T e, Serializable id) {
        if (cacheAdapterRepository == null) {
            return;
        }

        TPCacheAdapter<T> cacheAdapter = cacheAdapterRepository.getTPCacheAdapter(persistentEntity);
        if (cacheAdapter != null) {
            cacheAdapter.cacheEntry(id, e);
        }
    }

    protected T getFromTPCache(PersistentEntity persistentEntity, Serializable id) {
        if (cacheAdapterRepository == null) {
            return null;
        }

        TPCacheAdapter<T> cacheAdapter = cacheAdapterRepository.getTPCacheAdapter(persistentEntity);
        if (cacheAdapter != null) {
            return cacheAdapter.getCachedEntry(id);
        }
        return null;
    }
}
