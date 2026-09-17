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
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor
import org.hibernate.type.CompositeType

import java.lang.reflect.Method

/**
 * A ByteBuddy interceptor that avoids initializing the proxy for Groovy-specific methods.
 *
 * @author Graeme Rocher
 * @since 7.0
 */
@CompileStatic
class ByteBuddyGroovyInterceptor extends ByteBuddyInterceptor {

    private static final String GET_ID_METHOD = 'getId'
    private static final String GET_IDENTIFIER_METHOD = 'getIdentifier'

    protected final Method getIdentifierMethod

    private final boolean lazyToString

    ByteBuddyGroovyInterceptor(
            String entityName,
            Class<?> persistentClass,
            Class<?>[] interfaces,
            Object id,
            Method getIdentifierMethod,
            Method setIdentifierMethod,
            CompositeType componentIdType,
            SharedSessionContractImplementor session,
            boolean overridesEquals,
            boolean lazyToString) {
        super(
                entityName,
                persistentClass,
                interfaces,
                id,
                getIdentifierMethod,
                setIdentifierMethod,
                componentIdType,
                session,
                overridesEquals)
        this.getIdentifierMethod = getIdentifierMethod
        this.lazyToString = lazyToString
    }

    @Override
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName()

        // Check these BEFORE calling this.invoke() to avoid premature initialization in Hibernate 7
        if ((getIdentifierMethod != null && methodName == getIdentifierMethod.getName()) ||
                GET_ID_METHOD == methodName ||
                GET_IDENTIFIER_METHOD == methodName) {
            return getIdentifier()
        }

        GroovyProxyInterceptorLogic.InterceptorState state = new GroovyProxyInterceptorLogic.InterceptorState(
                getEntityName(), getPersistentClass(), getIdentifier(), lazyToString)

        if (isUninitialized()) {
            Object result = GroovyProxyInterceptorLogic.handleUninitialized(state, methodName, args)
            if (!GroovyProxyInterceptorLogic.INVOKE_IMPLEMENTATION.is(result)) { // NOPMD: sentinel comparison
                return result
            }
        }

        return this.invoke(method, args, proxy)
    }

}
