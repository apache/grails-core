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
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.proxy.AbstractLazyInitializer;
import org.hibernate.proxy.AbstractSerializableProxy;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.CompositeType;

/**
 * Serialized form of an uninitialized proxy built by {@link ByteBuddyGroovyProxyFactory}.
 *
 * <p>Hibernate's own {@code SerializableProxy} rebuilds a deserialized proxy through the global
 * {@code BytecodeProviderImpl} and nothing else: it never consults the proxy factory the SessionFactory
 * was built with, so the result is a stock proxy whose Groovy MOP methods initialize it and whose
 * constructor fails for an entity whose trait declares default values. {@link ByteBuddyGroovyInterceptor}
 * writes this form instead, and {@link #readResolve()} rebuilds the proxy through
 * {@link ByteBuddyGroovyProxyFactory}.
 *
 * <p>Hibernate 5.6 does not record the originating SessionFactory in this form unless lazy loading
 * outside transactions is enabled, and {@link GroovyProxyClassBuilder} caches the Groovy proxy class per
 * entity and class loader anyway, so the proxy is rebuilt through one shared provider: the class it yields
 * is the one the SessionFactory uses. Mirrors the Hibernate 7 implementation in grails-data-hibernate7.
 *
 * @since 8.0.1
 */
public final class SerializableGroovyProxy extends AbstractSerializableProxy {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final GrailsBytecodeProvider BYTECODE_PROVIDER = new GrailsBytecodeProvider();

    private final Class<?> persistentClass;
    private final Class<?>[] interfaces;
    private final String identifierGetterMethodName;
    private final Class<?> identifierGetterMethodClass;
    private final String identifierSetterMethodName;
    private final Class<?> identifierSetterMethodClass;
    private final Class<?>[] identifierSetterMethodParams;
    private final CompositeType componentIdType;
    private final boolean lazyToString;

    public SerializableGroovyProxy(
            String entityName,
            Class<?> persistentClass,
            Class<?>[] interfaces,
            Serializable id,
            Boolean readOnly,
            String sessionFactoryUuid,
            boolean allowLoadOutsideTransaction,
            Method getIdentifierMethod,
            Method setIdentifierMethod,
            CompositeType componentIdType,
            boolean lazyToString) {
        super(entityName, id, readOnly, sessionFactoryUuid, allowLoadOutsideTransaction);
        this.persistentClass = persistentClass;
        this.interfaces = interfaces;
        this.identifierGetterMethodName = getIdentifierMethod == null ? null : getIdentifierMethod.getName();
        this.identifierGetterMethodClass = getIdentifierMethod == null ? null : getIdentifierMethod.getDeclaringClass();
        this.identifierSetterMethodName = setIdentifierMethod == null ? null : setIdentifierMethod.getName();
        this.identifierSetterMethodClass = setIdentifierMethod == null ? null : setIdentifierMethod.getDeclaringClass();
        this.identifierSetterMethodParams = setIdentifierMethod == null ? null : setIdentifierMethod.getParameterTypes();
        this.componentIdType = componentIdType;
        this.lazyToString = lazyToString;
    }

    /**
     * @return an uninitialized Groovy-aware proxy for the serialized identifier, ready to be attached to a session
     * @throws HibernateException when the proxy cannot be rebuilt
     */
    @Serial
    @SuppressWarnings("rawtypes")
    private Object readResolve() {
        final ByteBuddyGroovyProxyFactory proxyFactory =
                new ByteBuddyGroovyProxyFactory(BYTECODE_PROVIDER.getByteBuddyProxyHelper(), lazyToString);
        final Set<Class> proxyInterfaces = new LinkedHashSet<>(Arrays.asList(interfaces));
        proxyFactory.postInstantiate(
                getEntityName(),
                persistentClass,
                proxyInterfaces,
                identifierMethod(identifierGetterMethodClass, identifierGetterMethodName, null),
                identifierMethod(identifierSetterMethodClass, identifierSetterMethodName, identifierSetterMethodParams),
                componentIdType);
        final HibernateProxy proxy = proxyFactory.getProxy(getId(), null);
        afterDeserialization((AbstractLazyInitializer) proxy.getHibernateLazyInitializer());
        return proxy;
    }

    private Method identifierMethod(Class<?> declaringClass, String name, Class<?>[] parameterTypes) {
        if (name == null) {
            return null;
        }
        try {
            return declaringClass.getDeclaredMethod(name, parameterTypes);
        }
        catch (NoSuchMethodException e) {
            throw new HibernateException("Unable to deserialize proxy for " + getEntityName() +
                    ": identifier method " + name + " not found on " + declaringClass.getName(), e);
        }
    }
}
