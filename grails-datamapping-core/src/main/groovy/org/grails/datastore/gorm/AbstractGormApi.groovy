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

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenantHolder
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.gorm.utils.ReflectionUtils
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.VoidSessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore

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
    protected final GormRegistry registry
    protected final String qualifier
    protected MappingContext mappingContext
    @Deprecated
    protected PersistentEntity persistentEntity
    private List<Method> methods
    private List<Method> extendedMethods

    AbstractGormApi(Class<D> persistentClass, Datastore datastore) {
        this(persistentClass, datastore, (GormRegistry) null)
    }

    AbstractGormApi(Class<D> persistentClass, Datastore datastore, GormRegistry registry) {
        super(datastore)
        this.persistentClass = persistentClass
        this.registry = registry ?: GormRegistry.instance
        this.qualifier = ConnectionSource.DEFAULT
        this.mappingContext = datastore?.mappingContext
        this.persistentEntity = datastore?.mappingContext?.getPersistentEntity(persistentClass?.name)
    }

    AbstractGormApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver) {
        this(persistentClass, mappingContext, datastoreResolver, (String) null, (GormRegistry) null)
    }

    AbstractGormApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver, String qualifier, GormRegistry registry) {
        super(datastoreResolver)
        this.persistentClass = persistentClass
        this.registry = registry ?: GormRegistry.instance
        this.qualifier = qualifier ?: ConnectionSource.DEFAULT
        this.mappingContext = mappingContext
        this.persistentEntity = mappingContext?.getPersistentEntity(persistentClass?.name)
    }

    @Override
    protected <T1> T1 execute(SessionCallback<T1> callback) {
        Datastore ds = getDatastore()
        if (ds == null) {
            throw new IllegalStateException('Cannot execute session callback with null datastore')
        }

        String currentQualifier = getQualifier()
        boolean isMultiTenantCapable = ds instanceof MultiTenantCapableDatastore
        boolean isMultiTenantEntity = MultiTenant.isAssignableFrom(persistentClass)

        // Check if we have a non-default qualifier
        if (currentQualifier != null && !ConnectionSource.DEFAULT.equals(currentQualifier) && !ConnectionSource.OLD_DEFAULT.equalsIgnoreCase(currentQualifier)) {
            if (isMultiTenantEntity && isMultiTenantCapable) {
                // Determine whether the qualifier names a datasource connection or is a tenant ID.
                // A datasource connection qualifier resolves via getDatastoreForConnection(); a tenant ID
                // (e.g. from withTenant("t1")) does not. When it IS a connection qualifier we must not
                // bind it as the tenant ID — doing so overwrites the tenant context set by the
                // TenantResolver (e.g. SystemPropertyTenantResolver) and causes discriminator filters to
                // match the connection name instead of the real tenant.
                boolean isConnectionQualifier = false
                if (ds instanceof MultipleConnectionSourceCapableDatastore) {
                    try {
                        Datastore resolved = ((MultipleConnectionSourceCapableDatastore) ds)
                                .getDatastoreForConnection(currentQualifier)
                        if (resolved != null) {
                            isConnectionQualifier = true
                        }
                    } catch (Exception ignored) {
                        // qualifier is not a known datasource name; treat it as a tenant ID below
                    }
                }
                if (!isConnectionQualifier) {
                    // Qualifier is a tenant ID — bind it so the session and any discriminator filter
                    // both see the correct tenant for this operation.
                    return (T1) Tenants.withId((MultiTenantCapableDatastore)ds, (Serializable)currentQualifier) {
                        DatastoreUtils.execute(ds, callback)
                    }
                }
            }
            return executeQualified(currentQualifier, callback)
        }

        // DEFAULT qualifier path: check if a tenant is already bound
        if (isMultiTenantCapable) {
            Serializable tenantId = CurrentTenantHolder.get((MultiTenantCapableDatastore) ds)
            if (tenantId != null) {
                // If a tenant is already bound, use executeQualified to delegate to a potentially specialized API
                return executeQualified(tenantId.toString(), callback)
            }
        }

        return DatastoreUtils.execute(ds, callback)
    }

    /**
     * @return The qualifier for this API instance
     */
    String getQualifier() {
        return this.qualifier
    }

    protected abstract <T1> T1 executeQualified(String qualifier, SessionCallback<T1> callback)

    @Override
    protected void execute(VoidSessionCallback callback) {
        execute(new SessionCallback<Object>() {
            @Override
            Object doInSession(Session session) {
                callback.doInSession(session)
                return null
            }
        })
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
