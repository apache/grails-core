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
package org.grails.datastore.gorm.neo4j.proxy

import java.lang.reflect.Method

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO
import javassist.util.proxy.MethodHandler
import javassist.util.proxy.ProxyObject
import org.springframework.beans.BeanUtils
import org.springframework.util.ReflectionUtils

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.proxy.GroovyObjectMethodHandler
import org.grails.datastore.mapping.proxy.JavassistProxyFactory

/**
 * Extends the default proxy factory to make proxies hashCode/equals aware
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class HashcodeEqualsAwareProxyFactory extends JavassistProxyFactory {

    @Override
    protected Object createProxiedInstance(final Session session, final Class cls, Class proxyClass, final Serializable id) {
        MethodHandler mi = new HashcodeEqualsAwareMethodHandler(proxyClass, session, cls, id)
        Object proxy = BeanUtils.instantiateClass(proxyClass)
        ((ProxyObject) proxy).setHandler(mi)
        return proxy
    }

    @CompileStatic
    @POJO
    private static class HashcodeEqualsAwareMethodHandler extends GroovyObjectMethodHandler {

        private final Session session
        private final Class cls
        private final Serializable id
        private Object target

        HashcodeEqualsAwareMethodHandler(Class proxyClass, Session session, Class cls, Serializable id) {
            super(proxyClass)
            this.session = session
            this.cls = cls
            this.id = id
        }

        @Override
        protected Object resolveDelegate(Object self) {
            return target
        }

        @Override
        Object invoke(Object proxy, Method method, Method proceed, Object[] args) throws Throwable {
            final String methodName = method.getName()
            if (args.length == 0) {
                if (methodName.equals('getId') || methodName.equals('getProxyKey')) {
                    return id
                }
                if (methodName.equals('initialize')) {
                    initialize()
                    return null
                }
                if (methodName.equals('isInitialized')) {
                    return target != null
                }
                if (methodName.equals('getTarget')) {
                    initialize()
                    return target
                }
                if (methodName.equals('hashCode')) {
                    return ((Long) id).intValue()
                }
            } else if (args.length == 1) {
                if (methodName.equals('equals')) {
                    final Method getIdMethod = ReflectionUtils.findMethod(cls, 'getId')
                    Object other = args[0]
                    if (other == null) {
                        return false
                    }
                    if (other.is(proxy)) {
                        return true
                    }
                    if (other.getClass() == proxyClass && other instanceof EntityProxy && id.equals(((EntityProxy) other).getProxyKey())) {
                        return true
                    }
                    if (other.getClass() == cls && id.equals(ReflectionUtils.invokeMethod(getIdMethod, other))) {
                        return true
                    }
                    return false
                }
            }
            if (target == null) {
                initialize()

                // This tends to happen during unit testing if the proxy class is not properly mocked
                // and therefore can't be found in the session.
                if (target == null) {
                    throw new IllegalStateException('Proxy for [' + cls.getName() + ':' + id + '] could not be initialized')
                }
            }

            Object result = handleInvocation(target, method, args)
            if (!wasHandled(result)) {
                return ReflectionUtils.invokeMethod(method, target, args)
            } else {
                return result
            }
        }

        void initialize() {
            target = session.retrieve(cls, id)
        }

    }

}
