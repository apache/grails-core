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

import java.io.Serial;
import java.lang.reflect.Method;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyFactory;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper;
import org.hibernate.type.CompositeType;

import static org.hibernate.internal.util.collections.ArrayHelper.EMPTY_CLASS_ARRAY;

/**
 * A ProxyFactory implementation for ByteBuddy that uses {@link ByteBuddyGroovyInterceptor}.
 *
 * @author Graeme Rocher
 * @since 7.0
 */
public class ByteBuddyGroovyProxyFactory extends ByteBuddyProxyFactory {


    @Serial
    private static final long serialVersionUID = 1L;

    private Class<?> persistentClass;
    private String entityName;
    private Class<?>[] interfaces;
    private Method getIdentifierMethod;
    private Method setIdentifierMethod;
    private CompositeType componentIdType;
    private boolean overridesEquals;

    private Class<?> proxyClass;
    private final ByteBuddyProxyHelper byteBuddyProxyHelper;

    public ByteBuddyGroovyProxyFactory(ByteBuddyProxyHelper byteBuddyProxyHelper) {
        super(byteBuddyProxyHelper);
        this.byteBuddyProxyHelper = byteBuddyProxyHelper;
    }

    @Override
    public void postInstantiate(
            String entityName,
            Class<?> persistentClass,
            Set<Class<?>> interfaces,
            Method getIdentifierMethod,
            Method setIdentifierMethod,
            CompositeType componentIdType)
            throws HibernateException {
        this.entityName = entityName;
        this.persistentClass = persistentClass;
        this.interfaces = interfaces == null ? EMPTY_CLASS_ARRAY : interfaces.toArray(EMPTY_CLASS_ARRAY);
        this.getIdentifierMethod = getIdentifierMethod;
        this.setIdentifierMethod = setIdentifierMethod;
        this.componentIdType = componentIdType;
        this.overridesEquals = ReflectHelper.overridesEquals(persistentClass);

        // Build the proxy class using the helper
        this.proxyClass = byteBuddyProxyHelper.buildProxy(persistentClass, this.interfaces);

        // DO NOT call super.postInstantiate(entityName, ...)
        // because it will try to initialize the standard Hibernate ProxyFactory fields
        // which might conflict with your custom getProxy() logic.
    }

    @Override
    public HibernateProxy getProxy(Object id, SharedSessionContractImplementor session) throws HibernateException {
        try {
            final ByteBuddyGroovyInterceptor interceptor = new ByteBuddyGroovyInterceptor(
                    entityName,
                    persistentClass,
                    interfaces,
                    id,
                    getIdentifierMethod,
                    setIdentifierMethod,
                    componentIdType,
                    session,
                    overridesEquals);

            // 1. Create the instance
            final HibernateProxy hibernateProxy =
                    (HibernateProxy) proxyClass.getDeclaredConstructor().newInstance();

            // 2. Cast to ProxyConfiguration to set the custom interceptor
            // Hibernate 7 proxies implement ProxyConfiguration
            if (hibernateProxy instanceof org.hibernate.proxy.ProxyConfiguration instance) {
                instance.$$_hibernate_set_interceptor(interceptor);
            }

            return hibernateProxy;
        } catch (Throwable t) {
            throw new HibernateException("Unable to generate proxy for " + entityName, t);
        }
    }
}
