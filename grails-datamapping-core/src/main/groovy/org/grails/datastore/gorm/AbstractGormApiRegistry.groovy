/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.reflect.NameUtils

import java.util.concurrent.ConcurrentHashMap

@CompileStatic
abstract class AbstractGormApiRegistry<T extends AbstractDatastoreApi> {

    private final Map<String, T> apis = new ConcurrentHashMap<>()
    protected final GormRegistry registry

    AbstractGormApiRegistry(GormRegistry registry) {
        this.registry = registry
    }

    void register(String className, T api) {
        if (className != null && api != null) {
            apis.put(className, api)
        }
    }

    T get(String className) {
        return apis.get(className)
    }

    T get(String className, String qualifier) {
        T api = apis.get(className)
        if (api != null && qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            Datastore ds = registry.getDatastore(className, qualifier)
            if (ds != null && ds != api.getDatastore()) {
                return qualify(api, qualifier)
            }
        }
        return api
    }

    boolean containsKey(String className) {
        return apis.containsKey(className)
    }

    int size() {
        return apis.size()
    }

    Set<String> keySet() {
        return apis.keySet()
    }

    void clear() {
        apis.clear()
    }

    protected String className(Class entity) {
        return NameUtils.getClassName(entity)
    }

    protected IllegalStateException stateException(Class entity) {
        return new IllegalStateException("No GORM implementation configured for class [${entity.name}]. Ensure GORM has been initialized correctly")
    }

    protected abstract T qualify(T api, String qualifier)
}
