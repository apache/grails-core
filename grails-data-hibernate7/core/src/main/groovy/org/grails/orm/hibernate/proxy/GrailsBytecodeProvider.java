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

import java.util.Map;

import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.bytecode.enhance.spi.Enhancer;
import org.hibernate.bytecode.spi.BytecodeProvider;
import org.hibernate.bytecode.spi.ProxyFactoryFactory;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper;

/**
 * A {@link BytecodeProvider} implementation for Hibernate 7 that provides Groovy-aware proxies.
 *
 * @author Walter Duque de Estrada
 * @since 8.0
 */
public class GrailsBytecodeProvider implements BytecodeProvider, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private final ByteBuddyProxyHelper proxyHelper;

    public GrailsBytecodeProvider() {
        this.proxyHelper = createProxyHelper();
    }

    protected ByteBuddyProxyHelper createProxyHelper() {
        return new ByteBuddyProxyHelper(new org.hibernate.bytecode.internal.bytebuddy.ByteBuddyState());
    }

    public ByteBuddyProxyHelper getProxyHelper() {
        return proxyHelper;
    }

    @Override
    public ProxyFactoryFactory getProxyFactoryFactory() {
        return new GrailsProxyFactoryFactory(this);
    }

    @Override
    public ReflectionOptimizer getReflectionOptimizer(
            Class clazz, String[] getterNames, String[] setterNames, Class[] types) {
        return null;
    }

    @Override
    public ReflectionOptimizer getReflectionOptimizer(Class<?> clazz, Map<String, PropertyAccess> propertyAccessMap) {
        return null;
    }

    @Override
    public Enhancer getEnhancer(EnhancementContext enhancementContext) {
        return null;
    }
}
