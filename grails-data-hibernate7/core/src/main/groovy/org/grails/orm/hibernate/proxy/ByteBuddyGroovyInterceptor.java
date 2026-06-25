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
package org.grails.orm.hibernate.proxy;

import java.lang.reflect.Method;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor;
import org.hibernate.type.CompositeType;

/**
 * A ByteBuddy interceptor that avoids initializing the proxy for Groovy-specific methods.
 *
 * @author Graeme Rocher
 * @since 7.0
 */
public class ByteBuddyGroovyInterceptor extends ByteBuddyInterceptor {

    private static final String GET_ID_METHOD = "getId";
    private static final String GET_IDENTIFIER_METHOD = "getIdentifier";

    protected final Method getIdentifierMethod;

    public ByteBuddyGroovyInterceptor(
            String entityName,
            Class<?> persistentClass,
            Class<?>[] interfaces,
            Object id,
            Method getIdentifierMethod,
            Method setIdentifierMethod,
            CompositeType componentIdType,
            SharedSessionContractImplementor session,
            boolean overridesEquals) {
        super(
                entityName,
                persistentClass,
                interfaces,
                id,
                getIdentifierMethod,
                setIdentifierMethod,
                componentIdType,
                session,
                overridesEquals);
        this.getIdentifierMethod = getIdentifierMethod;
    }

    @Override
    public Object intercept(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Check these BEFORE calling this.invoke() to avoid premature initialization in Hibernate 7
        if ((getIdentifierMethod != null && methodName.equals(getIdentifierMethod.getName())) ||
                GET_ID_METHOD.equals(methodName) ||
                GET_IDENTIFIER_METHOD.equals(methodName)) {
            return getIdentifier();
        }

        GroovyProxyInterceptorLogic.InterceptorState state = new GroovyProxyInterceptorLogic.InterceptorState(
                getEntityName(), getPersistentClass(), getIdentifier());

        if (isUninitialized()) {
            Object result = GroovyProxyInterceptorLogic.handleUninitialized(state, methodName, args);
            if (result != GroovyProxyInterceptorLogic.INVOKE_IMPLEMENTATION) { // NOPMD: sentinel comparison
                return result;
            }
        }

        return this.invoke(method, args, proxy);
    }
}
