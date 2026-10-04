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
package org.grails.orm.hibernate.proxy

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.HandleMetaClass
import org.codehaus.groovy.runtime.InvokerHelper

import org.grails.datastore.gorm.proxy.ProxyInstanceMetaClass

/**
 * Pure logic for Groovy proxy interception and handling, decoupled from Hibernate.
 *
 * @author Graeme Rocher
 * @since 7.0
 */
@CompileStatic
class GroovyProxyInterceptorLogic {

    public static final Object INVOKE_IMPLEMENTATION = new Object()

    /**
     * Setting that controls {@code toString()} on an uninitialized proxy. When {@code false}
     * (the default, matching Grails 7 and earlier), {@code toString()} initializes the proxy
     * and delegates to the entity's own implementation. When {@code true}, the proxy answers
     * {@code entityName:id} without initializing.
     */
    public static final String PROPERTY_LAZY_TO_STRING = 'hibernate.grails.proxy.lazy_to_string'

    private static final String GET_META_CLASS = 'getMetaClass'
    private static final String SET_META_CLASS = 'setMetaClass'
    private static final String META_CLASS_PROPERTY = 'metaClass'
    private static final String GET_PROPERTY = 'getProperty'
    private static final String ID_PROPERTY = 'id'
    private static final String IDENT_METHOD = 'ident'
    private static final String IS_DIRTY = 'isDirty'
    private static final String HAS_CHANGED = 'hasChanged'
    private static final String TO_STRING = 'toString'

    static Object handleUninitialized(InterceptorState state, String methodName, Object... args) {
        if ((GET_META_CLASS == methodName || methodName.endsWith('getStaticMetaClass')) &&
                (args == null || args.length == 0)) {
            return InvokerHelper.getMetaClass(state.persistentClass())
        }
        if (GET_PROPERTY == methodName && args.length == 1) {
            if (ID_PROPERTY == args[0]) {
                return state.identifier()
            }
            if (META_CLASS_PROPERTY == args[0]) {
                return InvokerHelper.getMetaClass(state.persistentClass())
            }
        }
        if (IDENT_METHOD == methodName && (args == null || args.length == 0)) {
            return state.identifier()
        }
        if ((IS_DIRTY == methodName || HAS_CHANGED == methodName) && (args == null || args.length == 0)) {
            return false
        }
        if (state.lazyToString() && TO_STRING == methodName && (args == null || args.length == 0)) {
            return state.entityName() + ':' + state.identifier()
        }
        return INVOKE_IMPLEMENTATION
    }

    static boolean isGroovyMethod(String methodName) {
        return 'getMetaClass' == methodName ||
                'setMetaClass' == methodName ||
                'getProperty' == methodName ||
                'setProperty' == methodName ||
                'invokeMethod' == methodName
    }

    static ProxyInstanceMetaClass getProxyInstanceMetaClass(Object o) {
        if (o instanceof GroovyObject) {
            MetaClass mc = o.getMetaClass()
            if (mc instanceof HandleMetaClass) {
                mc = ((HandleMetaClass) mc).getAdaptee()
            }
            if (mc instanceof ProxyInstanceMetaClass) {
                return (ProxyInstanceMetaClass) mc
            }
        }
        return null
    }

    static Object unwrap(Object object) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(object)
        if (proxyMc != null) {
            return proxyMc.getProxyTarget()
        }
        return null
    }

    static Serializable getIdentifier(Object o) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(o)
        if (proxyMc != null) {
            return proxyMc.getKey()
        }
        return null
    }

    /**
     * Check if a Groovy proxy is initialized.
     * @return {@code true} if initialized, {@code false} if not initialized, or {@code null} if the object is not a Groovy proxy
     */
    static Boolean isInitialized(Object o) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(o)
        if (proxyMc != null) {
            return proxyMc.isProxyInitiated()
        }
        return null
    }

    record InterceptorState(String entityName, Class<?> persistentClass, Object identifier, boolean lazyToString) {

        InterceptorState(String entityName, Class<?> persistentClass, Object identifier) {
            this(entityName, persistentClass, identifier, false)
        }
    }

}
