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
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl

import org.grails.datastore.mapping.core.connections.ConnectionSource

import java.util.concurrent.ConcurrentHashMap

@CompileStatic
class NamingStrategyProvider {

    private final ConcurrentHashMap<String, PhysicalNamingStrategy> physicalProviderMap

    NamingStrategyProvider() {
        physicalProviderMap = new ConcurrentHashMap<>()
        physicalProviderMap.put(ConnectionSource.DEFAULT, new PhysicalNamingStrategySnakeCaseImpl())
    }

    private static String getKey(String sessionFactoryBeanName) {
        if (sessionFactoryBeanName == null || sessionFactoryBeanName.isBlank()) {
            return ConnectionSource.DEFAULT
        }
        return 'sessionFactory' == sessionFactoryBeanName ?
                ConnectionSource.DEFAULT :
                sessionFactoryBeanName.substring('sessionFactory_'.length())
    }

    /**
     * Configures the naming strategy for a given datasource.
     *
     * @param datasourceName the datasource name
     * @param strategy the naming strategy (instance, Class, or class name)
     * @throws ClassNotFoundException when the strategy class cannot be found
     * @throws ReflectiveOperationException when the strategy class cannot be instantiated
     */
    void configureNamingStrategy(final String datasourceName, final Object strategy)
            throws ClassNotFoundException, ReflectiveOperationException {

        if (strategy == null) {
            throw new IllegalArgumentException('Naming strategy cannot be null')
        }

        Class<?> strategyClass = getStrategyClass(strategy)
        Object strategyInstance = getStrategyInstance(strategy, strategyClass)

        if (strategyInstance instanceof PhysicalNamingStrategy) {
            physicalProviderMap.put(datasourceName, (PhysicalNamingStrategy) strategyInstance)
        }
        else {
            physicalProviderMap.put(datasourceName, new PhysicalNamingStrategySnakeCaseImpl())
        }
    }

    private Class<?> getStrategyClass(Object strategy) throws ClassNotFoundException {
        if (strategy instanceof Class<?>) {
            return (Class<?>) strategy
        }
        if (strategy instanceof CharSequence) {
            return Thread.currentThread().contextClassLoader.loadClass(strategy.toString())
        }
        return strategy.class
    }

    private Object getStrategyInstance(Object strategy, Class<?> strategyClass)
            throws ReflectiveOperationException {
        if (strategy instanceof PhysicalNamingStrategy) {
            return strategy
        }
        return strategyClass.getDeclaredConstructor().newInstance()
    }

    PhysicalNamingStrategy getPhysicalNamingStrategy(String sessionFactoryBeanName) {
        String key = getKey(sessionFactoryBeanName)
        physicalProviderMap.putIfAbsent(key, new PhysicalNamingStrategySnakeCaseImpl())
        return physicalProviderMap.get(key)
    }

}
