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

import org.hibernate.bytecode.spi.BasicProxyFactory;
import org.hibernate.bytecode.spi.ProxyFactoryFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.proxy.ProxyFactory;

/**
 * A {@link ProxyFactoryFactory} implementation for Hibernate 5 that provides Groovy-aware
 * entity proxies. Basic (non-entity) proxies are delegated to the stock implementation.
 * Mirrors the Hibernate 7 implementation in grails-data-hibernate7.
 *
 * @since 8.0
 */
public class GrailsProxyFactoryFactory implements ProxyFactoryFactory, java.io.Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final GrailsBytecodeProvider grailsBytecodeProvider;
    private final transient ProxyFactoryFactory basicProxyDelegate;

    public GrailsProxyFactoryFactory(GrailsBytecodeProvider grailsBytecodeProvider, ProxyFactoryFactory basicProxyDelegate) {
        this.grailsBytecodeProvider = grailsBytecodeProvider;
        this.basicProxyDelegate = basicProxyDelegate;
    }

    @Override
    public ProxyFactory buildProxyFactory(SessionFactoryImplementor sessionFactory) {
        return new ByteBuddyGroovyProxyFactory(grailsBytecodeProvider.getByteBuddyProxyHelper(),
                isLazyToString(sessionFactory));
    }

    private static boolean isLazyToString(SessionFactoryImplementor sessionFactory) {
        Object value = sessionFactory.getProperties().get(GroovyProxyInterceptorLogic.PROPERTY_LAZY_TO_STRING);
        return value != null && Boolean.parseBoolean(value.toString());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public BasicProxyFactory buildBasicProxyFactory(Class superClass, Class[] interfaces) {
        return basicProxyDelegate.buildBasicProxyFactory(superClass, interfaces);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public BasicProxyFactory buildBasicProxyFactory(Class superClassOrInterface) {
        return basicProxyDelegate.buildBasicProxyFactory(superClassOrInterface);
    }
}
