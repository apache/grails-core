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
package org.grails.datastore.gorm.proxy

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.HandleMetaClass
import org.codehaus.groovy.runtime.InvokerHelper

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
    Class<?> getProxiedClass(Object o) {
        if (isProxy(o)) {
            return o.getClass()
        }
        return o.getClass()
    }

    @Override
    void initialize(Object o) {
        unwrap(o)
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
        if (obj.respondsTo('getId')) {
            return (Serializable)obj.invokeMethod('getId', null)
        }
        return null
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
    <T> T createProxy(Session session, Class<T> type, Serializable key) {
        EntityPersister persister = (EntityPersister) session.getPersister(type)
        T proxy = type.newInstance()
        if (persister != null) {
            persister.setObjectIdentifier(proxy, key)
        } else {
            // Fallback: try to set identifier using MappingContext's EntityReflector if available
            try {
                def mappingContext = session.getMappingContext()
                if (mappingContext != null) {
                    def pe = mappingContext.getPersistentEntity(type.name)
                    if (pe != null) {
                        mappingContext.getEntityReflector(pe).setIdentifier(proxy, key)
                    } else {
                        // Last resort: set 'id' property directly
                        try {
                            proxy.metaClass.setProperty(proxy, 'id', key)
                        } catch (Throwable ignore) {
                            // ignore - proxy may not be a Groovy object
                        }
                    }
                }
            } catch (Throwable ignore) {
                // ignore
            }
        }

        MetaClass delegateMetaClass = InvokerHelper.getMetaClass(proxy.getClass())
        ProxyInstanceMetaClass proxyMc = new ProxyInstanceMetaClass(delegateMetaClass, session, key)
        setMetaClassDynamic(proxy, proxyMc)
        return proxy
    }

    @groovy.transform.CompileDynamic
    protected void setMetaClassDynamic(Object proxy, MetaClass proxyMc) {
        proxy.setMetaClass(proxyMc)
    }

    @Override
    <T, K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        throw new UnsupportedOperationException('Association proxies are not supported by GroovyProxyFactory')
    }

    @Override
    boolean isInitialized(Object object) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(object)
        return proxyMc == null || proxyMc.isProxyInitiated()
    }

    protected ProxyInstanceMetaClass getProxyInstanceMetaClass(object) {
        if (object == null) {
            return null
        }
        MetaClass mc = object instanceof GroovyObject ? ((GroovyObject) object).getMetaClass() : object.metaClass
        mc = unwrapHandleMetaClass(mc)
        mc instanceof ProxyInstanceMetaClass ? (ProxyInstanceMetaClass) mc : null
    }

    @Override
    boolean isInitialized(Object object, String associationName) {
        Object value = ClassPropertyFetcher.getInstancePropertyValue(object, associationName)
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

    protected MetaClass unwrapHandleMetaClass(MetaClass mc) {
        if (mc instanceof HandleMetaClass) {
            return ((HandleMetaClass) mc).getAdaptee()
        }
        return mc
    }
}
