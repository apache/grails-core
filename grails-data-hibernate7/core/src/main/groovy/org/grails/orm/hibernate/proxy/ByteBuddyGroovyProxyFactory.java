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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.bytecode.internal.bytebuddy.ByteBuddyState;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.ProxyConfiguration;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyFactory;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper;
import org.hibernate.type.CompositeType;

import static org.hibernate.internal.util.collections.ArrayHelper.EMPTY_CLASS_ARRAY;

/**
 * A ProxyFactory implementation for ByteBuddy that uses {@link ByteBuddyGroovyInterceptor}.
 * The proxy class comes from {@link GroovyProxyClassBuilder}, and is generated and checked once, in
 * {@link #postInstantiate}, so {@link #getProxy} has nothing left to fail on but the instantiation itself.
 *
 * @since 8.0
 */
public class ByteBuddyGroovyProxyFactory extends ByteBuddyProxyFactory {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient GroovyProxyClassBuilder proxyClassBuilder;
    private Class<?> persistentClass;
    private String entityName;
    private Class<?>[] interfaces;
    private transient Method getIdentifierMethod;
    private transient Method setIdentifierMethod;
    private transient CompositeType componentIdType;
    private boolean overridesEquals;
    private Class<?> proxyClass;
    private transient Constructor<?> proxyConstructor;
    private final boolean lazyToString;

    public ByteBuddyGroovyProxyFactory(ByteBuddyProxyHelper byteBuddyProxyHelper) {
        this(byteBuddyProxyHelper, false);
    }

    public ByteBuddyGroovyProxyFactory(ByteBuddyProxyHelper byteBuddyProxyHelper, boolean lazyToString) {
        this(byteBuddyProxyHelper, new ByteBuddyState(), lazyToString);
    }

    /**
     * @param byteBuddyState the state the proxy classes are loaded through, so the Groovy wrapper is cached and
     * tracked with the proxy class {@code byteBuddyProxyHelper} builds
     */
    public ByteBuddyGroovyProxyFactory(ByteBuddyProxyHelper byteBuddyProxyHelper, ByteBuddyState byteBuddyState, boolean lazyToString) {
        super(byteBuddyProxyHelper);
        this.proxyClassBuilder = new GroovyProxyClassBuilder(byteBuddyProxyHelper, byteBuddyState);
        this.lazyToString = lazyToString;
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

        this.proxyClass = proxyClassBuilder.build(entityName, persistentClass, this.interfaces);
        this.proxyConstructor = resolveConstructor(entityName, proxyClass);

        // DO NOT call super.postInstantiate(entityName, ...)
        // because it will try to initialize the standard Hibernate ProxyFactory fields
        // which might conflict with your custom getProxy() logic.
    }

    private static Constructor<?> resolveConstructor(String entityName, Class<?> proxyClass) {
        try {
            return proxyClass.getDeclaredConstructor();
        }
        catch (NoSuchMethodException e) {
            throw new HibernateException("Unable to generate proxy for " + entityName +
                    ": the entity needs a default constructor", e);
        }
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
                    overridesEquals,
                    lazyToString);

            final HibernateProxy hibernateProxy = (HibernateProxy) proxyConstructor.newInstance();
            ((ProxyConfiguration) hibernateProxy).$$_hibernate_set_interceptor(interceptor);
            return hibernateProxy;
        } catch (Exception e) {
            throw new HibernateException("Unable to generate proxy for " + entityName, e);
        }
    }
}
