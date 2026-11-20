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

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;

import net.bytebuddy.TypeCache;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import org.hibernate.HibernateException;
import org.hibernate.bytecode.internal.bytebuddy.ByteBuddyState;
import org.hibernate.proxy.ProxyConfiguration;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

/**
 * Builds the proxy class Hibernate instantiates for an entity.
 *
 * <p>The class is the stock Hibernate proxy class. For a Groovy entity it is wrapped in a subclass that
 * answers {@code getMetaClass()} through {@link GroovyProxyMetaClassDispatcher} while the interceptor has
 * not been assigned yet, which Groovy trait initializers running in the proxy constructor depend on.
 * The wrapper is loaded through the {@link ByteBuddyState} that loaded the stock proxy class, so both are
 * cached and tracked together.
 *
 * <p>Everything that can fail about the generated class fails here, when the proxy factory is
 * instantiated, rather than on the first lazy load.
 *
 * @since 8.0.1
 */
public final class GroovyProxyClassBuilder {

    private final ByteBuddyProxyHelper proxyHelper;
    private final ByteBuddyState byteBuddyState;

    public GroovyProxyClassBuilder(ByteBuddyProxyHelper proxyHelper, ByteBuddyState byteBuddyState) {
        this.proxyHelper = proxyHelper;
        this.byteBuddyState = byteBuddyState;
    }

    /**
     * @param entityName the name of the entity, used to say which entity a failure is about
     * @param persistentClass the entity class
     * @param interfaces the interfaces the proxy has to implement
     * @return the proxy class, which implements {@link ProxyConfiguration} so an interceptor can be assigned
     * @throws HibernateException when the proxy class cannot be generated or cannot take an interceptor
     */
    public Class<?> build(String entityName, Class<?> persistentClass, Class<?>[] interfaces) throws HibernateException {
        final Class<?> hibernateProxyClass = proxyHelper.buildProxy(persistentClass, interfaces);
        final boolean groovyEntity = GroovyObject.class.isAssignableFrom(persistentClass);
        final Class<?> proxyClass = groovyEntity ? wrap(entityName, persistentClass, hibernateProxyClass) : hibernateProxyClass;
        if (!ProxyConfiguration.class.isAssignableFrom(proxyClass)) {
            throw new HibernateException("Generated proxy class for " + entityName +
                    " does not implement ProxyConfiguration, so no interceptor can be installed: " + proxyClass.getName());
        }
        return proxyClass;
    }

    private Class<?> wrap(String entityName, Class<?> persistentClass, Class<?> hibernateProxyClass) {
        try {
            return byteBuddyState.loadProxy(
                    persistentClass,
                    new TypeCache.SimpleKey(hibernateProxyClass, GroovyProxyMetaClassDispatcher.class),
                    byteBuddy -> byteBuddy
                            .subclass(hibernateProxyClass, ConstructorStrategy.Default.IMITATE_SUPER_CLASS_OPENING)
                            // declared again so Class#getInterfaces() still shows HibernateProxy on the wrapper
                            .implement(hibernateProxyClass.getInterfaces())
                            .method(named("getMetaClass").and(returns(MetaClass.class)))
                            .intercept(MethodDelegation.to(new GroovyProxyMetaClassDispatcher(persistentClass))));
        }
        catch (RuntimeException e) {
            throw new HibernateException("Unable to generate Groovy proxy for " + entityName, e);
        }
    }
}
