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

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.services.DefaultTenantService
import org.grails.datastore.gorm.services.DefaultTransactionService

/**
 * Unit coverage for the reusable configuration behaviour in {@link AbstractDatastoreInitializer},
 * exercised through the {@link TestDatastoreInitializer} test double so no real datastore module
 * (Hibernate, MongoDB, Neo4j, ...) is required.
 */
class AbstractDatastoreInitializerSpec extends Specification {

    void 'the no-arg constructor uses sensible defaults'() {
        when:
        def initializer = new TestDatastoreInitializer()

        then:
        initializer.packages == []
        initializer.persistentClasses == []
        initializer.configuration instanceof StandardEnvironment
        initializer.originalConfiguration == null
    }

    void 'a package-name constructor records the given packages'() {
        when:
        def initializer = new TestDatastoreInitializer('com.example', 'com.other')

        then:
        initializer.packages == ['com.example', 'com.other']
    }

    void 'a persistent-class constructor records the given classes'() {
        when:
        def initializer = new TestDatastoreInitializer(String, Integer)

        then:
        initializer.persistentClasses == [String, Integer]
    }

    void 'a Map configuration constructor derives a PropertyResolver but retains the original Map'() {
        given:
        Map config = ['foo.bar': 'baz']

        when:
        def initializer = new TestDatastoreInitializer(config, [String])

        then:
        initializer.originalConfiguration.is(config)
        initializer.configuration.getRequiredProperty('foo.bar') == 'baz'
        initializer.persistentClasses == [String]
    }

    void 'a PropertyResolver configuration constructor keeps the resolver as-is with no original configuration'() {
        given:
        def resolver = new StandardEnvironment()

        when:
        def initializer = new TestDatastoreInitializer(resolver, [String])

        then:
        initializer.configuration.is(resolver)
        initializer.originalConfiguration == null
    }

    void 'findEventPublisher wraps the registry itself when it is a ConfigurableApplicationContext'() {
        given:
        def initializer = new TestDatastoreInitializer()
        def context = new GenericApplicationContext()

        expect:
        initializer.findEventPublisher(context) instanceof ConfigurableApplicationContextEventPublisher

        cleanup:
        context.close()
    }

    void 'findEventPublisher falls back to the resource loader when the registry is not a ConfigurableApplicationContext'() {
        given:
        def initializer = new TestDatastoreInitializer()
        def context = new GenericApplicationContext()
        initializer.setResourceLoader(context)

        expect:
        initializer.findEventPublisher(new DefaultListableBeanFactory()) instanceof ConfigurableApplicationContextEventPublisher

        cleanup:
        context.close()
    }

    void 'findEventPublisher defaults to a DefaultApplicationEventPublisher when neither source is available'() {
        given:
        def initializer = new TestDatastoreInitializer()

        expect:
        initializer.findEventPublisher(new DefaultListableBeanFactory()) instanceof DefaultApplicationEventPublisher
    }

    void 'findMessageSource uses the registry itself when it is a MessageSource'() {
        given:
        def initializer = new TestDatastoreInitializer()
        def context = new GenericApplicationContext()

        expect:
        initializer.findMessageSource(context).is(context)

        cleanup:
        context.close()
    }

    void 'findMessageSource falls back to the resource loader when the registry is not a MessageSource'() {
        given:
        def initializer = new TestDatastoreInitializer()
        def context = new GenericApplicationContext()
        initializer.setResourceLoader(context)

        expect:
        initializer.findMessageSource(new DefaultListableBeanFactory()).is(context)

        cleanup:
        context.close()
    }

    void 'findMessageSource defaults to a fresh StaticMessageSource when neither source is available'() {
        given:
        def initializer = new TestDatastoreInitializer()

        expect:
        initializer.findMessageSource(new DefaultListableBeanFactory()) instanceof StaticMessageSource
    }

    void 'setResourceLoader rebuilds the resource pattern resolver around the given loader'() {
        given:
        def initializer = new TestDatastoreInitializer()
        def loader = new GenericApplicationContext()

        when:
        initializer.setResourceLoader(loader)

        then:
        initializer.resourcePatternResolver.resourceLoader.is(loader)

        cleanup:
        loader.close()
    }

    void 'isMappedClass returns true only when the static mapWith property matches the datastore type'() {
        given:
        def initializer = new TestDatastoreInitializer()

        expect:
        initializer.isMappedClass('mongo', MongoEntity)
        !initializer.isMappedClass('sql', MongoEntity)
        !initializer.isMappedClass('mongo', UnmappedEntity)
    }

    void 'collectMappedClasses returns every persistent class when this is not a secondary datastore'() {
        given:
        def initializer = new TestDatastoreInitializer([MongoEntity, SqlEntity, UnmappedEntity])

        expect:
        initializer.collectMappedClasses('mongo') == [MongoEntity, SqlEntity, UnmappedEntity]
    }

    void 'collectMappedClasses filters to only the classes mapped to the given type for a secondary datastore'() {
        given:
        def initializer = new TestDatastoreInitializer([MongoEntity, SqlEntity, UnmappedEntity])
        initializer.setSecondaryDatastore(true)

        expect:
        initializer.collectMappedClasses('mongo') == [MongoEntity]
        initializer.collectMappedClasses('sql') == [SqlEntity]
    }

    void 'containsRegisteredBean returns true when the registry already contains a bean definition with that name'() {
        given:
        def registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition('fooBean', new RootBeanDefinition(Object))
        def initializer = new TestDatastoreInitializer()

        expect:
        initializer.containsRegisteredBean(new Object(), registry, 'fooBean')
    }

    void 'containsRegisteredBean falls back to a springConfig-aware builder when the registry does not know the bean'() {
        given:
        def registry = new DefaultListableBeanFactory()
        def builder = new BeanBuilderStub(springConfig: new SpringConfigStub(beanNames: ['fooBean'] as Set))
        def initializer = new TestDatastoreInitializer()

        expect:
        initializer.containsRegisteredBean(builder, registry, 'fooBean')
        !initializer.containsRegisteredBean(builder, registry, 'otherBean')
    }

    void 'containsRegisteredBean returns false when neither the registry nor the builder know the bean'() {
        given:
        def registry = new DefaultListableBeanFactory()
        def initializer = new TestDatastoreInitializer()

        expect:
        !initializer.containsRegisteredBean(new Object(), registry, 'fooBean')
    }

    void 'getCommonConfiguration returns a no-op closure by default'() {
        given:
        def initializer = new TestDatastoreInitializer()

        when:
        def closure = initializer.getCommonConfiguration(new DefaultListableBeanFactory(), 'foo')

        then:
        closure instanceof Closure
        closure() == null
    }

    void 'loadDataServices discovers the Service implementations declared for this module'() {
        given:
        def initializer = new TestDatastoreInitializer()

        when:
        def services = initializer.loadDataServices()

        then:
        services.defaultTransactionService == DefaultTransactionService
        services.defaultTenantService == DefaultTenantService
    }

    void 'loadDataServices namespaces service names under the secondary datastore type when given'() {
        given:
        def initializer = new TestDatastoreInitializer()

        when:
        def services = initializer.loadDataServices('foo')

        then:
        services.fooDefaultTransactionService == DefaultTransactionService
        services.fooDefaultTenantService == DefaultTenantService
    }

    void 'isGrailsPresent and getGrailsApplicationClass detect grails-core on the classpath'() {
        given:
        def initializer = new TestDatastoreInitializer()

        expect:
        initializer.isGrailsPresent()
        initializer.getGrailsApplicationClass() == DefaultGrailsApplication
    }

    void 'getGrailsValidatorClass is no longer supported'() {
        given:
        def initializer = new TestDatastoreInitializer()

        when:
        initializer.getGrailsValidatorClass()

        then:
        thrown(UnsupportedOperationException)
    }

    void 'getAdditionalBeansConfiguration registers a transaction manager, persistence interceptor, aggregator and every data service'() {
        given:
        def registry = new DefaultListableBeanFactory()
        def initializer = new TestDatastoreInitializer()

        when:
        def beanDefinitions = initializer.getAdditionalBeansConfiguration(registry, 'foo')
        AbstractDatastoreInitializer.GroovyBeanReaderInit.registerBeans(registry, beanDefinitions)

        then:
        registry.containsBeanDefinition('fooTransactionManager')
        registry.isAlias('transactionManager')
        registry.getAliases('fooTransactionManager') as Set == ['transactionManager'] as Set
        registry.containsBeanDefinition('fooPersistenceInterceptor')
        registry.containsBeanDefinition('fooPersistenceContextInterceptorAggregator')
        registry.containsBeanDefinition('defaultTransactionService')
        registry.containsBeanDefinition('defaultTenantService')
        !registry.containsBeanDefinition('fooOpenSessionInViewInterceptor')
    }

    void 'getAdditionalBeansConfiguration does not alias an already-registered transactionManager bean'() {
        given:
        def registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition('transactionManager', new RootBeanDefinition(Object))
        def initializer = new TestDatastoreInitializer()

        when:
        def beanDefinitions = initializer.getAdditionalBeansConfiguration(registry, 'foo')
        AbstractDatastoreInitializer.GroovyBeanReaderInit.registerBeans(registry, beanDefinitions)

        then:
        registry.containsBeanDefinition('fooTransactionManager')
        !registry.isAlias('transactionManager')
        registry.getBeanDefinition('transactionManager').beanClassName == Object.name
    }

    void 'GrailsBeanBuilderInit registers beans via grails.spring.BeanBuilder when used directly'() {
        given:
        def registry = new DefaultListableBeanFactory()
        def initializer = new TestDatastoreInitializer()

        expect:
        AbstractDatastoreInitializer.GrailsBeanBuilderInit.isAvailable()

        when:
        def beanDefinitions = initializer.getAdditionalBeansConfiguration(registry, 'foo')
        AbstractDatastoreInitializer.GrailsBeanBuilderInit.registerBeans(registry, beanDefinitions)

        then:
        registry.containsBeanDefinition('fooTransactionManager')
        registry.containsBeanDefinition('fooPersistenceInterceptor')
    }

    void 'configure builds a fully refreshed application context containing the declared beans'() {
        given:
        def initializer = new TestDatastoreInitializer()
        initializer.beanDefinitions = { -> "sampleBean"(String, 'hello') }

        when:
        def context = initializer.configure()

        then:
        context.isActive()
        context.getBean('sampleBean', String) == 'hello'

        cleanup:
        context.close()
    }

    static class MongoEntity {
        static mapWith = 'mongo'
    }

    static class SqlEntity {
        static mapWith = 'sql'
    }

    static class UnmappedEntity {
    }

    static class SpringConfigStub {
        Set<String> beanNames
        boolean containsBean(String name) { name in beanNames }
    }

    static class BeanBuilderStub {
        SpringConfigStub springConfig
    }
}
