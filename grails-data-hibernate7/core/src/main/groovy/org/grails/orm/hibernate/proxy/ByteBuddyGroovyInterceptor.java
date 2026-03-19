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
        super(entityName, persistentClass, interfaces, id, getIdentifierMethod, setIdentifierMethod, componentIdType, session, overridesEquals);
    }

    @Override
    public Object intercept(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if (methodName.equals("getMetaClass") || methodName.equals("setMetaClass") || methodName.equals("getProperty") || methodName.equals("setProperty") || methodName.equals("invokeMethod")) {
            // Logic adapted from ByteBuddyInterceptor.intercept to handle Groovy methods without initialization
            final Object result = this.invoke( method, args, proxy );
            if ( result == INVOKE_IMPLEMENTATION ) {
                final Object target = getImplementation();
                try {
                    if ( isPublic( persistentClass, method ) ) {
                        return method.invoke( target, args );
                    }
                    else {
                        method.setAccessible( true );
                        return method.invoke( target, args );
                    }
                }
                catch (InvocationTargetException ite) {
                    throw ite.getTargetException();
                }
            }
            return result;
        }
        if (methodName.equals("toString") && args.length == 0) {
            return getEntityName() + ":" + getIdentifier();
        }
        return super.intercept(proxy, method, args);
    }
}
