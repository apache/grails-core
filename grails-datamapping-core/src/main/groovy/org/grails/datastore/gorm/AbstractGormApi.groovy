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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.grails.datastore.gorm.utils.ReflectionUtils
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Abstract GORM API provider.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 * @since 1.0
 */
@CompileStatic
abstract class AbstractGormApi<D> extends AbstractDatastoreApi {

    static final List<String> EXCLUDES = [
        'setProperty',
        'getProperty',
        'getMetaClass',
        'setMetaClass',
        'invokeMethod',
        'getMethods',
        'getExtendedMethods',
        'wait',
        'equals',
        'toString',
        'hashCode',
        'getClass',
        'notify',
        'notifyAll',
        'setTransactionManager'
    ]

    protected Class<D> persistentClass
    protected PersistentEntity persistentEntity
    private List<Method> methods
    private List<Method> extendedMethods

    private static final ThreadLocal<String> VALIDATION_CONNECTION = new ThreadLocal<>()

    /**
     * The connection the save or validation running on this thread names, such as {@code book.third.save()}, or
     * {@code null} when it names none. For the internal use of the {@code unique} constraint, which has to check the
     * connection that the instance is saved on.
     */
    static String validationConnection() {
        return VALIDATION_CONNECTION.get()
    }

    /**
     * Runs the callable with the connection this API names as that of the validation, so that the {@code unique}
     * constraint checks the connection a save or a validation names and not the default connection. An API of the
     * default connection names none: a save through it, made while a save on another connection runs, is checked on
     * the default connection and not on the one the outer save named.
     */
    protected <T1> T1 inConnection(Closure<T1> callable) {
        String connection = connectionName()
        String named = connection == null || ConnectionSource.DEFAULT == connection ? null : connection
        String previous = VALIDATION_CONNECTION.get()
        if (named == previous) {
            return callable.call()
        }
        if (named == null) {
            VALIDATION_CONNECTION.remove()
        } else {
            VALIDATION_CONNECTION.set(named)
        }
        try {
            return callable.call()
        } finally {
            if (previous == null) {
                VALIDATION_CONNECTION.remove()
            } else {
                VALIDATION_CONNECTION.set(previous)
            }
        }
    }

    private String connectionName() {
        // an API built from a mapping context has no datastore, and validating through it names no connection: it is
        // the default one, so the field is read directly: the property would call getDatastore(), which throws for it
        Datastore current = this.@datastore
        if (current instanceof ConnectionSourcesProvider) {
            return ((ConnectionSourcesProvider) current).getConnectionSources()?.getDefaultConnectionSource()?.getName()
        }
        return null
    }

    AbstractGormApi(Class<D> persistentClass, Datastore datastore) {
        super(datastore)
        this.persistentClass = persistentClass
        this.persistentEntity = datastore.getMappingContext().getPersistentEntity(persistentClass.name)
    }

    AbstractGormApi(Class<D> persistentClass, MappingContext mappingContext) {
        super(null)
        this.persistentClass = persistentClass
        this.persistentEntity = mappingContext.getPersistentEntity(persistentClass.name)
    }

    @CompileDynamic
    protected initializeMethods(clazz) {
        while (clazz != Object) {
            final methodsToAdd = clazz.declaredMethods.findAll { Method m ->
                def mods = m.getModifiers()
                !m.isSynthetic() && !Modifier.isStatic(mods) && Modifier.isPublic(mods) &&
                        !AbstractGormApi.EXCLUDES.contains(m.name)
            }
            methods.addAll(methodsToAdd)
            if (clazz != GormStaticApi && clazz != GormInstanceApi && clazz != GormValidationApi && clazz != AbstractGormApi) {
                def extendedMethodsToAdd = methodsToAdd.findAll { Method m -> !ReflectionUtils.isMethodOverriddenFromParent(m) }
                extendedMethods.addAll(extendedMethodsToAdd)
            }
            clazz = clazz.getSuperclass()
        }
        return clazz
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
}
