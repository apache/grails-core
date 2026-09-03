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

import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.aot.AbstractAotProcessor
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import spock.lang.Specification

import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher

/**
 * Covers what a datastore's bean definition holds in place of the configuration.
 *
 * <p>A definition holding the resolver is a definition holding everything that resolver can reach,
 * and generating code for it writes those values out -- the environment of whatever machine ran the
 * build, credentials among them, and settings that are meant to differ where the application runs.
 * So while code is being generated the environment is named instead. Every other time the resolver
 * is held as before, which is what a datastore brought up on its own depends on: its configuration
 * is whatever the caller passed, and no environment holds it.</p>
 */
class AbstractDatastoreInitializerConfigurationSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        System.clearProperty(AbstractAotProcessor.AOT_PROCESSING)
        context.close()
    }

    private void whileGeneratingCode(boolean generating) {
        generating ? System.setProperty(AbstractAotProcessor.AOT_PROCESSING, 'true')
                : System.clearProperty(AbstractAotProcessor.AOT_PROCESSING)
    }

    private Initializer initializerFor(Map configuration) {
        context.environment.propertySources.addFirst(new MapPropertySource('test', configuration))
        new Initializer(configuration)
    }

    void 'the configuration is held as it stands on an ordinary start'() {
        given:
            Initializer initializer = initializerFor(['grails.mongodb.databaseName': 'foo'])
            whileGeneratingCode(false)

        when:
            Object held = initializer.configurationReferenceFor(context)

        then: 'a datastore brought up on its own has no environment carrying its settings'
            held.is(initializer.configuration)
    }

    void 'the environment is named while code is being generated'() {
        given:
            Initializer initializer = initializerFor(['grails.mongodb.databaseName': 'foo'])
            whileGeneratingCode(true)

        when:
            Object held = initializer.configurationReferenceFor(context)

        then: 'so the lookup is made where the application runs, not written down where it was built'
            held instanceof RuntimeBeanReference
            ((RuntimeBeanReference) held).beanName == ConfigurableApplicationContext.ENVIRONMENT_BEAN_NAME
    }

    void 'a registry that is not a container keeps the configuration'() {
        given:
            Initializer initializer = new Initializer(['grails.mongodb.databaseName': 'foo'])
            whileGeneratingCode(true)

        when: 'a bare registry, with no environment to name'
            Object held = initializer.configurationReferenceFor(new DefaultListableBeanFactory())

        then:
            held.is(initializer.configuration)
    }

    void 'the classes a datastore maps are the array its constructor takes'() {
        given:
            Initializer initializer = new Initializer([:])
            initializer.persistentClasses = [String, Integer]

        expect: 'a collection does not answer to Class[], and the argument is then missed when the ' +
                'definition is read to generate code for it'
            initializer.mappedClassesFor('mongo') instanceof Class[]
            initializer.mappedClassesFor('mongo').toList() == [String, Integer]
    }

    void 'a datastore saying which classes are its own is still asked'() {
        given: 'the seam a datastore outside this repository overrides'
            Initializer initializer = new Overriding([:])
            initializer.persistentClasses = [String, Integer]

        expect: 'folding the array into it would leave such an override overriding nothing'
            initializer.mappedClassesFor('mongo').toList() == [Integer]
    }

    void 'inside a container the publisher class that publishes through it is named'() {
        given:
            Initializer initializer = new Initializer([:])

        expect: 'a class for the container to build, rather than a publisher built here -- which is ' +
                'a live object, and a definition holding one cannot be generated ahead of time'
            initializer.eventPublisherClassFor(context) == ConfigurableApplicationContextEventPublisher
    }

    void 'outside a container the publisher that publishes nowhere is named'() {
        given:
            Initializer initializer = new Initializer([:])

        expect: 'the context-aware one would never be given a context here, and would fail on publish'
            initializer.eventPublisherClassFor(new DefaultListableBeanFactory()) ==
                    DefaultApplicationEventPublisher
    }

    void 'the resource loader stands in for a registry that is not a container'() {
        given: 'how a datastore initialized against a bare registry still reaches the context'
            Initializer initializer = new Initializer([:])
            initializer.setResourceLoader(context)

        expect:
            initializer.eventPublisherClassFor(new DefaultListableBeanFactory()) ==
                    ConfigurableApplicationContextEventPublisher
    }

    /** Reaches the protected members through a subclass, the way a datastore's own initializer does. */
    static class Initializer extends AbstractDatastoreInitializer {

        Initializer(Map configuration) {
            super(configuration)
        }

        Object configurationReferenceFor(BeanDefinitionRegistry registry) {
            configurationReference(registry)
        }

        Class<? extends ApplicationEventPublisher> eventPublisherClassFor(BeanDefinitionRegistry registry) {
            findEventPublisherClass(registry)
        }

        Class[] mappedClassesFor(String datastoreType) {
            mappedClasses(datastoreType)
        }

        @Override
        Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) {
            return { }
        }

        @Override
        Class getPersistenceInterceptorClass() {
            Object
        }
    }

    /** A datastore that says which of the classes are its own, the way an implementation does. */
    static class Overriding extends Initializer {

        Overriding(Map configuration) {
            super(configuration)
        }

        @Override
        protected Collection<Class> collectMappedClasses(String datastoreType) {
            persistentClasses.findAll { Class cls -> cls != String }
        }
    }
}
