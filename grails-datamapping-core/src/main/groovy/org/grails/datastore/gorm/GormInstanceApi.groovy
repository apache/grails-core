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

import groovy.transform.CompileDynamic
import org.codehaus.groovy.runtime.InvokerHelper

import grails.gorm.api.GormInstanceOperations
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.VoidSessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.validation.ValidationException
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode
import org.grails.datastore.gorm.multitenancy.TenantDelegatingGormOperations
import org.springframework.transaction.PlatformTransactionManager
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable

/**
 * GORM instance API implementation.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileDynamic
class GormInstanceApi<D> extends AbstractGormApi<D> implements GormInstanceOperations<D> {

    Class<? extends Exception> validationException = ValidationException.VALIDATION_EXCEPTION_TYPE
    boolean failOnError = false
    boolean markDirty = true

    GormInstanceApi(Class<D> persistentClass, Datastore datastore) {
        super(persistentClass, datastore)
        if (datastore != null) {
            def mappingFactory = datastore.getMappingContext().getMappingFactory()
            try {
                def defaultSettings = mappingFactory.getClass().getMethod("getDefaultSettings").invoke(mappingFactory)
                this.failOnError = (boolean)defaultSettings.getClass().getMethod("isFailOnError").invoke(defaultSettings)
                this.markDirty = (boolean)defaultSettings.getClass().getMethod("isMarkDirty").invoke(defaultSettings)
            } catch (Throwable e) {
                this.failOnError = false
                this.markDirty = true
            }
        }
    }

    GormInstanceApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver) {
        super(persistentClass, mappingContext, datastoreResolver)
        def mappingFactory = mappingContext.getMappingFactory()
        try {
            def defaultSettings = mappingFactory.getClass().getMethod("getDefaultSettings").invoke(mappingFactory)
            this.failOnError = (boolean)defaultSettings.getClass().getMethod("isFailOnError").invoke(defaultSettings)
            this.markDirty = (boolean)defaultSettings.getClass().getMethod("isMarkDirty").invoke(defaultSettings)
        } catch (Throwable e) {
            this.failOnError = false
            this.markDirty = true
        }
    }

    @Override
    PlatformTransactionManager getTransactionManager() {
        Datastore ds = getDatastore()
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore)ds).getTransactionManager()
        }
        return null
    }

    GormInstanceApi<D> forQualifier(String qualifier) {
        DatastoreResolver resolver = new DatastoreResolver() {
            @Override Datastore resolve() { GormEnhancer.findDatastore(persistentClass, qualifier) }
        }
        GormInstanceApi<D> newApi = new GormInstanceApi<D>(persistentClass, mappingContext, resolver)
        newApi.failOnError = failOnError
        newApi.markDirty = markDirty
        return newApi
    }

    @Override
    Object propertyMissing(D instance, String name) {
        Datastore ds = getDatastore()
        if (ds instanceof ConnectionSourcesProvider) {
            ConnectionSources sources = ((ConnectionSourcesProvider) ds).connectionSources
            if (sources != null && sources.getConnectionSource(name) != null) {
                def instanceApi = GormEnhancer.findInstanceApi(persistentClass, name)
                return new DelegatingGormEntityApi(instanceApi, instance)
            }
        }
        throw new MissingPropertyException(name, persistentClass)
    }

    @Override
    boolean instanceOf(D instance, Class cls) {
        if (instance == null) return false
        if (instance instanceof EntityProxy) {
            return cls.isInstance(((EntityProxy) instance).getTarget())
        }
        return cls.isInstance(instance)
    }

    @Override
    D lock(D instance) {
        execute({ Session session ->
            session.lock(instance)
            return instance
        } as SessionCallback)
    }

    @Override
    def <T> T mutex(D instance, Closure<T> callable) {
        execute({ Session session ->
            session.lock(instance)
            callable?.call()
        } as SessionCallback)
    }

    @Override
    D refresh(D instance) {
        execute({ Session session ->
            session.refresh(instance)
            return instance
        } as SessionCallback)
    }

    @Override
    D merge(D instance, Map args) {
        save(instance, args)
    }

    @Override
    D merge(D instance) {
        save(instance, [:])
    }

    @Override
    D save(D instance) {
        save(instance, [:])
    }

    @Override
    D save(D instance, boolean validate) {
        save(instance, [validate: validate])
    }

    @Override
    D save(D instance, Map arguments) {
        boolean shouldFlush = arguments?.containsKey("flush") ? (boolean)arguments.get("flush") : false
        boolean validate = arguments?.containsKey("validate") ? (boolean)arguments.get("validate") : true

        if (validate) {
            if (!GormEnhancer.findValidationApi(persistentClass).validate(instance, arguments)) {
                if (shouldFail(arguments)) {
                    throw validationException.newInstance('Validation Error(s) occurred during save()', instance.errors)
                }
                return null
            }
        } else {
            GormEnhancer.findValidationApi(persistentClass).clearErrors(instance)
        }

        execute({ Session session ->
            session.persist(instance)
            if (shouldFlush) {
                session.flush()
            }
            return instance
        } as SessionCallback)
    }

    private boolean shouldFail(Map arguments) {
        if (arguments?.containsKey("failOnError")) {
            return (boolean)arguments.get("failOnError")
        }
        return failOnError
    }

    @Override
    D insert(D instance) {
        insert(instance, [:])
    }

    @Override
    D insert(D instance, Map arguments) {
        boolean shouldFlush = arguments?.containsKey("flush") ? (boolean)arguments.get("flush") : false
        execute({ Session session ->
            session.insert(instance)
            if (shouldFlush) {
                session.flush()
            }
            return instance
        } as SessionCallback)
    }

    @Override
    void delete(D instance) {
        delete(instance, [:])
    }

    @Override
    void delete(D instance, Map arguments) {
        boolean shouldFlush = arguments?.containsKey("flush") ? (boolean)arguments.get("flush") : false
        execute({ Session session ->
            session.delete(instance)
            if (shouldFlush) {
                session.flush()
            }
        } as VoidSessionCallback)
    }

    @Override
    Serializable ident(D instance) {
        (Serializable)InvokerHelper.getProperty(instance, "id")
    }

    @Override
    D attach(D instance) {
        execute({ Session session ->
            session.attach(instance)
            return instance
        } as SessionCallback)
    }

    @Override
    boolean isAttached(D instance) {
        execute({ Session session ->
            session.contains(instance)
        } as SessionCallback)
    }

    @Override
    void discard(D instance) {
        execute({ Session session ->
            session.clear(instance)
        } as SessionCallback)
    }

    boolean isDirty(D instance) {
        if (instance instanceof DirtyCheckable) {
            return ((DirtyCheckable)instance).hasChanged()
        }
        return false
    }

    boolean isDirty(D instance, String fieldName) {
        if (instance instanceof DirtyCheckable) {
            return ((DirtyCheckable)instance).hasChanged(fieldName)
        }
        return false
    }

    List<String> getDirtyPropertyNames(D instance) {
        if (instance instanceof DirtyCheckable) {
            return ((DirtyCheckable)instance).listDirtyPropertyNames()
        }
        return Collections.emptyList()
    }

    Object getPersistentValue(D instance, String fieldName) {
        if (instance instanceof DirtyCheckable) {
            return ((DirtyCheckable)instance).getOriginalValue(fieldName)
        }
        return null
    }
}
