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

import java.lang.reflect.Method

import groovy.transform.CompileStatic
import org.hibernate.HibernateException
import org.hibernate.bytecode.spi.BytecodeProvider
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.internal.SessionFactoryRegistry
import org.hibernate.proxy.AbstractLazyInitializer
import org.hibernate.proxy.AbstractSerializableProxy
import org.hibernate.proxy.HibernateProxy
import org.hibernate.type.CompositeType

/**
 * Serialized form of an uninitialized proxy built by {@link ByteBuddyGroovyProxyFactory}.
 *
 * <p>Hibernate's own {@code SerializableProxy} rebuilds a deserialized proxy through the stock
 * {@code BytecodeProviderImpl} and nothing else: it never consults the proxy factory the SessionFactory
 * was built with, and it rejects {@link GrailsBytecodeProvider} with an {@code IllegalStateException}
 * because that is not the stock provider. {@link ByteBuddyGroovyInterceptor} writes this form instead,
 * and {@link #readResolve()} rebuilds the proxy through {@link ByteBuddyGroovyProxyFactory}, so a
 * deserialized proxy is the same Groovy-aware proxy a session hands out: {@code getMetaClass()} answers
 * before an interceptor is assigned, which trait initializers depend on, and the Groovy MOP methods do
 * not initialize it.
 *
 * <p>The proxy class comes from the {@link GrailsBytecodeProvider} of the originating SessionFactory when
 * that SessionFactory is registered in this JVM, so it is the class the SessionFactory already uses.
 * Otherwise it comes from a provider shared by every such deserialization, which caches one class per
 * entity.
 *
 * @since 8.0.1
 */
@CompileStatic
final class SerializableGroovyProxy extends AbstractSerializableProxy {

    @Serial
    private static final long serialVersionUID = 1L

    /** Used when the originating SessionFactory is not registered in this JVM; it caches one proxy class per entity. */
    private static final GrailsBytecodeProvider FALLBACK_BYTECODE_PROVIDER = new GrailsBytecodeProvider()

    private final Class<?> persistentClass
    private final Class<?>[] interfaces
    private final String identifierGetterMethodName
    private final Class<?> identifierGetterMethodClass
    private final String identifierSetterMethodName
    private final Class<?> identifierSetterMethodClass
    private final Class<?>[] identifierSetterMethodParams
    private final CompositeType componentIdType
    private final boolean lazyToString

    SerializableGroovyProxy(
            String entityName,
            Class<?> persistentClass,
            Class<?>[] interfaces,
            Object id,
            Boolean readOnly,
            String sessionFactoryUuid,
            String sessionFactoryName,
            boolean allowLoadOutsideTransaction,
            Method getIdentifierMethod,
            Method setIdentifierMethod,
            CompositeType componentIdType,
            boolean lazyToString) {
        super(entityName, id, readOnly, sessionFactoryUuid, sessionFactoryName, allowLoadOutsideTransaction)
        this.persistentClass = persistentClass
        this.interfaces = interfaces
        this.identifierGetterMethodName = getIdentifierMethod == null ? null : getIdentifierMethod.getName()
        this.identifierGetterMethodClass = getIdentifierMethod == null ? null : getIdentifierMethod.getDeclaringClass()
        this.identifierSetterMethodName = setIdentifierMethod == null ? null : setIdentifierMethod.getName()
        this.identifierSetterMethodClass = setIdentifierMethod == null ? null : setIdentifierMethod.getDeclaringClass()
        this.identifierSetterMethodParams = setIdentifierMethod == null ? null : setIdentifierMethod.getParameterTypes()
        this.componentIdType = componentIdType
        this.lazyToString = lazyToString
    }

    /**
     * @return an uninitialized Groovy-aware proxy for the serialized identifier, ready to be attached to a session
     * @throws HibernateException when the proxy cannot be rebuilt
     */
    @Serial
    private Object readResolve() {
        final GrailsBytecodeProvider bytecodeProvider = bytecodeProvider()
        final ByteBuddyGroovyProxyFactory proxyFactory = new ByteBuddyGroovyProxyFactory(
                bytecodeProvider.getProxyHelper(), bytecodeProvider.getByteBuddyState(), lazyToString)
        proxyFactory.postInstantiate(
                getEntityName(),
                persistentClass,
                new LinkedHashSet<>(Arrays.asList(interfaces)),
                identifierMethod(identifierGetterMethodClass, identifierGetterMethodName, null),
                identifierMethod(identifierSetterMethodClass, identifierSetterMethodName, identifierSetterMethodParams),
                componentIdType)
        final HibernateProxy proxy = proxyFactory.getProxy(getId(), null)
        afterDeserialization((AbstractLazyInitializer) proxy.getHibernateLazyInitializer())
        return proxy
    }

    private GrailsBytecodeProvider bytecodeProvider() {
        if (sessionFactoryUuid == null) {
            return FALLBACK_BYTECODE_PROVIDER
        }
        final SessionFactoryImplementor sessionFactory =
                SessionFactoryRegistry.INSTANCE.findSessionFactory(sessionFactoryUuid, sessionFactoryName)
        if (sessionFactory == null) {
            return FALLBACK_BYTECODE_PROVIDER
        }
        final BytecodeProvider bytecodeProvider = sessionFactory.getServiceRegistry().getService(BytecodeProvider)
        if (bytecodeProvider instanceof GrailsBytecodeProvider) {
            return (GrailsBytecodeProvider) bytecodeProvider
        }
        return FALLBACK_BYTECODE_PROVIDER
    }

    private Method identifierMethod(Class<?> declaringClass, String name, Class<?>[] parameterTypes) {
        if (name == null) {
            return null
        }
        try {
            return declaringClass.getDeclaredMethod(name, parameterTypes)
        }
        catch (NoSuchMethodException e) {
            throw new HibernateException('Unable to deserialize proxy for ' + getEntityName() +
                    ': identifier method ' + name + ' not found on ' + declaringClass.getName(), e)
        }
    }
}
