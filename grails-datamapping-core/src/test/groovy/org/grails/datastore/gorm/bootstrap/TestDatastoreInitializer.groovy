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
package org.grails.datastore.gorm.bootstrap

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.core.env.PropertyResolver

import org.grails.datastore.gorm.support.AbstractDatastorePersistenceContextInterceptor
import org.grails.datastore.mapping.core.Datastore

/**
 * Minimal concrete {@link AbstractDatastoreInitializer} used to exercise the abstract
 * class's own behaviour in unit tests without depending on a real datastore module
 * (Hibernate, MongoDB, Neo4j, ...).
 *
 * <p>{@link #beanDefinitions} and {@link #persistenceInterceptorClass} default to
 * harmless no-op implementations but can be overridden per-test.
 */
class TestDatastoreInitializer extends AbstractDatastoreInitializer {

    Closure beanDefinitions = { -> }
    Class<AbstractDatastorePersistenceContextInterceptor> persistenceInterceptorClass = TestPersistenceContextInterceptor

    TestDatastoreInitializer() {
        super()
    }

    TestDatastoreInitializer(ClassLoader classLoader, String... packages) {
        super(classLoader, packages)
    }

    TestDatastoreInitializer(String... packages) {
        super(packages)
    }

    TestDatastoreInitializer(Collection<Class> persistentClasses) {
        super(persistentClasses)
    }

    TestDatastoreInitializer(Class... persistentClasses) {
        super(persistentClasses)
    }

    TestDatastoreInitializer(PropertyResolver configuration, Collection<Class> persistentClasses) {
        super(configuration, persistentClasses)
    }

    TestDatastoreInitializer(PropertyResolver configuration, Class... persistentClasses) {
        super(configuration, persistentClasses)
    }

    TestDatastoreInitializer(PropertyResolver configuration, String... packages) {
        super(configuration, packages)
    }

    TestDatastoreInitializer(Map configuration, Collection<Class> persistentClasses) {
        super(configuration, persistentClasses)
    }

    TestDatastoreInitializer(Map configuration, Class... persistentClasses) {
        super(configuration, persistentClasses)
    }

    @Override
    Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) {
        beanDefinitions
    }

    @Override
    protected Class<AbstractDatastorePersistenceContextInterceptor> getPersistenceInterceptorClass() {
        persistenceInterceptorClass
    }

    static class TestPersistenceContextInterceptor extends AbstractDatastorePersistenceContextInterceptor {
        TestPersistenceContextInterceptor(Datastore datastore) {
            super(datastore)
        }
    }
}
