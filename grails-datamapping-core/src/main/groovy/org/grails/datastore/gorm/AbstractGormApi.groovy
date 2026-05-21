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
package org.grails.datastore.gorm

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.grails.datastore.gorm.utils.ReflectionUtils
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.EntityReflector

/**
 * Abstract base class for GORM API objects
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class AbstractGormApi<D> extends AbstractDatastoreApi {

    protected static final List<String> EXCLUDES = [
        'wait', 'notify', 'notifyAll', 'toString', 'hashCode', 'equals', 'getClass',
        'getMetaClass', 'setMetaClass', 'getProperty', 'setProperty', 'invokeMethod'
    ]

    private static final Map<Class, List<Method>> METHODS_CACHE = new ConcurrentHashMap<>()
    private static final Map<Class, List<Method>> EXTENDED_METHODS_CACHE = new ConcurrentHashMap<>()

    protected Class<D> persistentClass
    private List<Method> methods
    private List<Method> extendedMethods

    AbstractGormApi(Class<D> persistentClass, Datastore datastore) {
        super(datastore)
        this.persistentClass = persistentClass
    }

    AbstractGormApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver) {
        super(datastoreResolver)
        this.persistentClass = persistentClass
    }

    /**
     * @return The persistent entity
     */
    PersistentEntity getGormPersistentEntity() {
        getDatastore()?.mappingContext?.getPersistentEntity(persistentClass.name)
    }

    @CompileDynamic
    protected synchronized void initializeMethods(Class apiClass) {
        if (methods == null) {
            if (!METHODS_CACHE.containsKey(apiClass)) {
                List<Method> methodList = []
                List<Method> extendedMethodList = []
                Class cls = apiClass
                while (cls != Object) {
                    final methodsToAdd = cls.declaredMethods.findAll { Method m ->
                        def mods = m.getModifiers()
                        !m.isSynthetic() && !Modifier.isStatic(mods) && Modifier.isPublic(mods) &&
                                !AbstractGormApi.EXCLUDES.contains(m.name)
                    }
                    methodList.addAll(methodsToAdd)
                    if (cls != GormStaticApi && cls != GormInstanceApi && cls != GormValidationApi && cls != AbstractGormApi) {
                        def extendedMethodsToAdd = methodsToAdd.findAll { Method m -> !ReflectionUtils.isMethodOverriddenFromParent(m) }
                        extendedMethodList.addAll(extendedMethodsToAdd)
                    }
                    cls = cls.getSuperclass()
                }
                METHODS_CACHE.put(apiClass, Collections.unmodifiableList(methodList))
                EXTENDED_METHODS_CACHE.put(apiClass, Collections.unmodifiableList(extendedMethodList))
            }
            this.methods = METHODS_CACHE.get(apiClass)
            this.extendedMethods = EXTENDED_METHODS_CACHE.get(apiClass)
        }
    }

    List<Method> getMethods() {
        if (methods == null) {
            initializeMethods(getClass())
        }
        return methods
    }

    List<Method> getExtendedMethods() {
        if (extendedMethods == null) {
            initializeMethods(getClass())
        }
        return extendedMethods
    }

    abstract org.springframework.transaction.PlatformTransactionManager getTransactionManager()

    static class ConstantDatastoreResolver implements DatastoreResolver {
        private final Datastore datastore

        ConstantDatastoreResolver(Datastore datastore) {
            this.datastore = datastore
        }

        @Override
        Datastore resolve() {
            return datastore
        }
    }
}
