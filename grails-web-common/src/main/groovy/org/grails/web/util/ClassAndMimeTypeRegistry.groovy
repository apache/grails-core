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
package org.grails.web.util

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import grails.util.Environment
import groovy.transform.CompileStatic
import grails.util.GrailsClassUtils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

import grails.web.mime.MimeType
import grails.web.mime.MimeTypeProvider

/**
 * Abstract class for class that maintains a registry of mappings MimeType,Class and a particular object type. Used by RendererRegistry and DataBindingSourceRegistry
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
abstract class ClassAndMimeTypeRegistry<R extends MimeTypeProvider, K> {

    private static final MimeTypeProvider NULL_RESOLVE = new MimeTypeProvider() {
        MimeType[] getMimeTypes() { null }
    }

    private Map<Class, Collection<R >> registeredObjectsByType = new ConcurrentHashMap<>()
    private Map<MimeType, R> defaultObjectsByMimeType = new ConcurrentHashMap<>()
    private Cache<K, R > resolvedObjectCache = Caffeine.newBuilder()
        .initialCapacity(500)
        .maximumSize(1000)
        .build()

    void registerDefault(MimeType mt, R object) {
        defaultObjectsByMimeType.put(mt, object)
    }

    void addToRegisteredObjects(Class targetType, R object) {
        Collection<R> rendererList = getRegisteredObjects(targetType)
        rendererList?.add(object)
    }

    Collection<R> getRegisteredObjects(Class targetType) {
        if(targetType == null) {
            return null
        }
        def registeredObjects = registeredObjectsByType.get(targetType)
        if (registeredObjects == null) {
            registeredObjects = new ConcurrentLinkedQueue<R>()
            registeredObjectsByType.put(targetType, registeredObjects)
        }
        return registeredObjects
    }

    R findMatchingObjectForMimeType(MimeType mimeType, object) {
        if(object == null) return null

        final clazz = object instanceof Class ? (Class)object : object.getClass()

        final K cacheKey = createCacheKey(clazz, mimeType)
        R registeredObject = (R)resolvedObjectCache.getIfPresent(cacheKey)
        if (registeredObject == null) {

            Class currentClass = clazz
            while(currentClass != null) {

                registeredObject = findRegisteredObjectForType(currentClass, mimeType)
                if (registeredObject) {
                    resolvedObjectCache.put(cacheKey, registeredObject)
                    return registeredObject
                }
                if (currentClass == Object) break
                currentClass = currentClass.getSuperclass()
            }

            final interfaces = GrailsClassUtils.getAllInterfaces(object)
            for(i in interfaces) {
                registeredObject = findRegisteredObjectForType(i, mimeType)
                if (registeredObject) break
            }

            if (registeredObject == null) {
                registeredObject = (R)defaultObjectsByMimeType.get(mimeType)
            }
            if (registeredObject != null) {
                resolvedObjectCache.put(cacheKey, registeredObject)
            }
        }

        if(registeredObject == null && !Environment.isDevelopmentMode()) {
            resolvedObjectCache.put(cacheKey, (R)NULL_RESOLVE)
        }
        else if(NULL_RESOLVE.is(registeredObject)) {
            return null
        }
        return registeredObject
    }

    protected R findRegisteredObjectForType(Class currentClass, MimeType mimeType) {
        R findObject = null
        final objectList = registeredObjectsByType.get(currentClass)
        if (objectList) {
            findObject = (R)objectList.find { 
                MimeTypeProvider r = (MimeTypeProvider)it
                r.mimeTypes.any { MimeType mt ->
                    mt  == mimeType
                }
            }
            if(findObject == null) {
                findObject = (R)objectList.find {
                    MimeTypeProvider r = (MimeTypeProvider)it
                    r.mimeTypes.any { MimeType mt ->
                        mt.name == mimeType.name
                    }
                }
            }
        }
        findObject
    }

    void removeFromCache(Class type, MimeType mimeType) {
        final key = createCacheKey(type, mimeType)
        resolvedObjectCache.invalidate(key)
    }

    abstract K createCacheKey(Class type, MimeType mimeType)
}
