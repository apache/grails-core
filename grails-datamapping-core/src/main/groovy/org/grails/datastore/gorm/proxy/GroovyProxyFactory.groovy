/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.proxy

import groovy.transform.CompileStatic

import org.codehaus.groovy.runtime.HandleMetaClass
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

/**
 * Implements the proxy interface and creates a Groovy proxy by passing the need for javassist style proxies
 * and all the problems they bring.
 *
 * @author Graeme Rocher
 */
@CompileStatic
class GroovyProxyFactory implements ProxyFactory {
    /**
     * Check our object has the correct meta class to be a proxy of this type.
     * @param object The object.
     * @return true if it is.
     */
    @Override
    boolean isProxy(Object object) {
        getProxyInstanceMetaClass(object) != null
    }

    @Override
    @Override
    public Class<?> getProxiedClass(Object o) {
        if(isProxy(o)) {
            return o.getClass().getSuperclass()
        }
        return o.getClass()
    }

    @Override
    void initialize(Object o) {
        unwrap(o)
    }

    protected ProxyInstanceMetaClass getProxyInstanceMetaClass(object) {
        if(object == null) {
            return null
        }
        MetaClass mc = unwrapHandleMetaClass(object instanceof GroovyObject ? ((GroovyObject)object).getMetaClass() : object.metaClass)
        mc instanceof ProxyInstanceMetaClass ? (ProxyInstanceMetaClass)mc : null
    }

    @Override
    Serializable getIdentifier(Object obj) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(obj)
        if (proxyMc != null) {
            return proxyMc.getKey()
        } else {
            getIdDynamic(obj)
        }
    }

    @groovy.transform.CompileDynamic
    protected Serializable getIdDynamic(obj) {
        return obj.getId()
    }

    /**
     * Creates a proxy
     *
     * @param <T> The type of the proxy to create
     * @param session The session instance
     * @param type The type of the proxy to create
     * @param key The key to proxy
     * @return A proxy instance
     */
    @Override
    public <T> T createProxy(Session session, Class<T> type, Serializable key) {
        EntityPersister persister = (EntityPersister) session.getPersister(type)
        T proxy = type.newInstance()
        persister.setObjectIdentifier(proxy, key)

        MetaClass metaClass = new ProxyInstanceMetaClass(resolveTargetMetaClass(proxy, type), session, key)
        if(proxy instanceof GroovyObject) {
            // direct assignment of MetaClass to GroovyObject
            ((GroovyObject)proxy).setMetaClass(metaClass)
        } else {
            // call DefaultGroovyMethods.setMetaClass
            proxy.metaClass = metaClass
        }
        return proxy
    }

    @Override
    def <T, K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        throw new UnsupportedOperationException("Association proxies are not currently supported by the Groovy project factory")
    }

    protected <T> MetaClass resolveTargetMetaClass(T proxy, Class<T> type) {
        unwrapHandleMetaClass(proxy.getMetaClass())
    }
    
    private MetaClass unwrapHandleMetaClass(MetaClass metaClass) {
        (metaClass instanceof HandleMetaClass) ? ((HandleMetaClass)metaClass).getAdaptee() : metaClass
    }

    @Override
    boolean isInitialized(Object object) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(object)
        if (proxyMc != null) {
            return proxyMc.isProxyInitiated()
        }
        return true
    }

    @Override
    public boolean isInitialized(Object object, String associationName) {
        final Object value = ClassPropertyFetcher.getInstancePropertyValue(object, associationName)
        return value == null || isInitialized(value)
    }

    @Override
    Object unwrap(Object object) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(object)
        if (proxyMc != null) {
            return proxyMc.getProxyTarget()
        }
        return object
    }
}
