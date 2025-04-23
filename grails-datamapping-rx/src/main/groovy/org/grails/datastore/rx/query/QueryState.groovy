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

package org.grails.datastore.rx.query

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

/**
 *
 * Used to maintain query state and avoid hitting the database again when loading associations
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class QueryState {

    private final Map<Class, Map<Serializable, Object>> loadedEntities = new ConcurrentHashMap<>()

    QueryState() {
    }

    void addLoadedEntity(Class type, Serializable id, Object object) {
        def loadedByType = loadedEntities.get(type)
        if(loadedByType == null) {
            loadedByType = new ConcurrentHashMap<Serializable, Object>()
            loadedByType.put(id, object)
            loadedEntities.put(type, loadedByType)
        }
        else {
            loadedByType.put(id, object)
        }
    }

    public <T> T getLoadedEntity(Class<T> type, Serializable id) {
        def loadedByType = loadedEntities.get(type)
        if(loadedByType == null) {
            return null
        }
        else {
            return (T) loadedByType.get(id)
        }
    }
}
