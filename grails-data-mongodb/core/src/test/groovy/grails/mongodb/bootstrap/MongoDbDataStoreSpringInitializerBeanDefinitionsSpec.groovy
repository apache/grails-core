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
package grails.mongodb.bootstrap

import grails.persistence.Entity
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

/**
 * Covers how the datastore receives its event publisher. The definitions are inspected without
 * refreshing the context, so no MongoDB is involved.
 */
class MongoDbDataStoreSpringInitializerBeanDefinitionsSpec extends Specification {

    private static final String PUBLISHER = 'grailsDatastoreEventPublisher'

    void 'inside an application context the publisher is registered as a definition'() {
        given:
            def registry = new GenericApplicationContext()

        when:
            new MongoDbDataStoreSpringInitializer(Person).configureForBeanDefinitionRegistry(registry)

        then: 'a definition rather than an already-constructed object, so it can be processed ahead of time'
            registry.containsBeanDefinition(PUBLISHER)
            registry.getBeanDefinition(PUBLISHER).beanClassName ==
                    ConfigurableApplicationContextEventPublisher.name

        cleanup:
            registry.close()
    }

    void 'the datastore refers to the publisher by name rather than holding an instance'() {
        given:
            def registry = new GenericApplicationContext()

        when:
            new MongoDbDataStoreSpringInitializer(Person).configureForBeanDefinitionRegistry(registry)
            def args = registry.getBeanDefinition('mongoDatastore').constructorArgumentValues

        then:
            args.genericArgumentValues*.value
                    .findAll { it instanceof RuntimeBeanReference }
                    .any { RuntimeBeanReference reference -> reference.beanName == PUBLISHER }

        cleanup:
            registry.close()
    }

    void 'outside an application context the no-op publisher is used instead'() {
        given: 'a registry that is not a context, as when GORM is bootstrapped standalone'
            def registry = new SimpleBeanDefinitionRegistry()

        when:
            new MongoDbDataStoreSpringInitializer(Person).configureForBeanDefinitionRegistry(registry)

        then: 'the context-aware publisher would never be given a context here, and would fail on publish'
            registry.getBeanDefinition(PUBLISHER).beanClassName == DefaultApplicationEventPublisher.name
    }

    @Entity
    static class Person {
        String name
    }
}
