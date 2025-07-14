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
package org.grails.datastore.mapping.cache;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * A repository of {@link TPCacheAdapter}s.
 *
 * @author Roman Stepanenko
 */
public interface TPCacheAdapterRepository<T> {

    /**
     * Returns {@link TPCacheAdapter} for the specified {@link PersistentEntity}.
     *
     * @param entity the entity
     * @return null if no {@link TPCacheAdapter} is found for the specified entity
     */
    TPCacheAdapter<T> getTPCacheAdapter(PersistentEntity entity);

    /**
     * Sets {@link TPCacheAdapter} for the specified {@link PersistentEntity}.
     * If the specified entity had another cache adapter before, the old one is ignored after this call.
     *
     * @param entity       the entity
     * @param cacheAdapter the adapter
     */
    void setTPCacheAdapter(PersistentEntity entity, TPCacheAdapter<T> cacheAdapter);

    /**
     * Sets {@link TPCacheAdapter} for the specified java class of {@link PersistentEntity}.
     * If the specified entity had another cache adapter before, the old one is ignored after this call.
     *
     * @param entityJavaClass equivalent to {@link PersistentEntity#getJavaClass()}
     * @param cacheAdapter    the adapter
     */
    void setTPCacheAdapter(@SuppressWarnings("rawtypes") Class entityJavaClass, TPCacheAdapter<T> cacheAdapter);

    /**
     * Sets {@link TPCacheAdapter} for the specified FQN java class of {@link PersistentEntity}.
     * If the specified entity had another cache adapter before, the old one is ignored after this call.
     *
     * @param entityJavaClassFQN equivalent to {@link PersistentEntity#getJavaClass()}.getName()
     * @param cacheAdapter       the adapter
     */
    void setTPCacheAdapter(String entityJavaClassFQN, TPCacheAdapter<T> cacheAdapter);
}
