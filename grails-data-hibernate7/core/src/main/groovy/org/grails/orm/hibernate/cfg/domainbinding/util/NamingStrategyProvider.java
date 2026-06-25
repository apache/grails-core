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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;

import org.grails.datastore.mapping.core.connections.ConnectionSource;

public class NamingStrategyProvider {

    private final ConcurrentHashMap<String, PhysicalNamingStrategy> physicalProviderMap;

    public NamingStrategyProvider() {
        physicalProviderMap = new ConcurrentHashMap<>();
        physicalProviderMap.put(ConnectionSource.DEFAULT, new PhysicalNamingStrategySnakeCaseImpl());
    }

    private static String getKey(String sessionFactoryBeanName) {
        if (Objects.isNull(sessionFactoryBeanName) || sessionFactoryBeanName.isBlank()) {
            return ConnectionSource.DEFAULT;
        }
        return "sessionFactory".equals(sessionFactoryBeanName) ?
                ConnectionSource.DEFAULT :
                sessionFactoryBeanName.substring("sessionFactory_".length());
    }

    /**
     * Configures the naming strategy for a given datasource.
     *
     * @param datasourceName the datasource name
     * @param strategy the naming strategy (instance, Class, or class name)
     * @throws ClassNotFoundException when the strategy class cannot be found
     * @throws IllegalAccessException when the strategy class cannot be accessed
     * @throws InstantiationException when the strategy class cannot be instantiated
     */
    public void configureNamingStrategy(final String datasourceName, final Object strategy)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {

        if (strategy == null) {
            throw new IllegalArgumentException("Naming strategy cannot be null");
        }

        var strategyClass = getStrategyClass(strategy);
        var strategyInstance = getStrategyInstance(strategy, strategyClass);

        if (strategyInstance instanceof PhysicalNamingStrategy physicalStrategy) {
            physicalProviderMap.put(datasourceName, physicalStrategy);
        } else {
            physicalProviderMap.put(datasourceName, new PhysicalNamingStrategySnakeCaseImpl());
        }
    }

    private Class<?> getStrategyClass(Object strategy) throws ClassNotFoundException {
        if (strategy instanceof Class<?>) {
            return (Class<?>) strategy;
        }
        if (strategy instanceof CharSequence) {
            return Thread.currentThread().getContextClassLoader().loadClass(strategy.toString());
        }
        return strategy.getClass();
    }

    private Object getStrategyInstance(Object strategy, Class<?> strategyClass)
            throws InstantiationException, IllegalAccessException {
        if (strategy instanceof PhysicalNamingStrategy) {
            return strategy;
        }
        //TODO Candidate for SneakyThrow
        return strategyClass.newInstance();
    }

    public PhysicalNamingStrategy getPhysicalNamingStrategy(String sessionFactoryBeanName) {
        String key = getKey(sessionFactoryBeanName);
        physicalProviderMap.putIfAbsent(key, new PhysicalNamingStrategySnakeCaseImpl());
        return physicalProviderMap.get(key);
    }
}
