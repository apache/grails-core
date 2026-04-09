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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor;
import org.hibernate.type.CompositeType;

import static org.hibernate.internal.util.ReflectHelper.isPublic;

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
        if (method == null) {
            return super.intercept(proxy, null, args);
        }
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

        final Object result = this.invoke(method, args, proxy);
        if (result != INVOKE_IMPLEMENTATION) { // NOPMD: sentinel comparison
            return result;
        }

        if (GroovyProxyInterceptorLogic.isGroovyMethod(methodName)) {
            if (isUninitialized()) {
                // If we reach here, it's a Groovy method but handleUninitialized didn't catch it.
                // We should still avoid getImplementation() if uninitialized.
                Object uninitializedResult = GroovyProxyInterceptorLogic.handleUninitialized(state, methodName, args);
                if (uninitializedResult != GroovyProxyInterceptorLogic.INVOKE_IMPLEMENTATION) {
                    return uninitializedResult;
                }
            }

            final Object target = getImplementation();
            try {
                if (!isPublic(getPersistentClass(), method)) {
                    method.setAccessible(true); // NOPMD: accessibility alteration
                }
                return method.invoke(target, args);
            } catch (InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        }
        return super.intercept(proxy, method, args);
    }
}
