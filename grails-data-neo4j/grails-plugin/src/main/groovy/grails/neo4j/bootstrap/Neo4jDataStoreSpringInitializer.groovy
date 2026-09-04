
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
package grails.neo4j.bootstrap

import grails.neo4j.Neo4jEntity
import grails.spring.BeanBuilder
import groovy.transform.InheritConstructors
import org.apache.grails.common.aot.AheadOfTimeProcessing
import org.grails.datastore.gorm.bootstrap.AbstractDatastoreInitializer
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceFactory
import org.grails.datastore.gorm.plugin.support.PersistenceContextInterceptorAggregator
import org.grails.datastore.gorm.support.AbstractDatastorePersistenceContextInterceptor
import org.grails.datastore.gorm.support.DatastorePersistenceContextInterceptor
import org.grails.datastore.mapping.config.DatastoreServiceMethodInvokingFactoryBean
import org.grails.datastore.mapping.core.grailsversion.GrailsVersion
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.util.ClassUtils

/**
 * An {@link AbstractDatastoreInitializer} used for initializing GORM for Neo4j when using Spring.
 *
 * If you are not using the Spring container then using the constructors of {@link Neo4jDatastore} is preferable
 *
 * @author Graeme Rocher
 * @since 4.0
 */
@InheritConstructors
class Neo4jDataStoreSpringInitializer extends AbstractDatastoreInitializer {

    public static final String DATASTORE_TYPE = 'neo4j'

    protected Closure defaultMapping

    @Override
    protected Class<AbstractDatastorePersistenceContextInterceptor> getPersistenceInterceptorClass() {
        DatastorePersistenceContextInterceptor
    }

    @Override
    protected boolean isMappedClass(String datastoreType, Class cls) {
        return Neo4jEntity.isAssignableFrom(cls) || super.isMappedClass(datastoreType, cls)
    }

    /**
     * Refuses to generate code for a definition that would carry the build machine's settings.
     *
     * <p>The datastore below is given the resolved configuration rather than a reference to the
     * environment, because the method that names the environment instead was added to
     * {@code AbstractDatastoreInitializer} and this build resolves that class from a released
     * artifact rather than from the source beside it -- so the call compiles here and goes missing
     * at run time.</p>
     *
     * <p>Generating code for a definition means writing out what it holds, and what this one holds
     * is a {@code PropertyResolver}: the build machine's environment, its environment variables
     * among them, written into the application's generated source and shipped in it. Refusing is
     * the lesser outcome. An unsupported combination that says so can be worked around; one that
     * quietly ships whatever the machine that built it had cannot be noticed.</p>
     */
    private static void refuseWhereTheConfigurationWouldBeWrittenOut() {
        if (AheadOfTimeProcessing.generatingCode) {
            throw new IllegalStateException('Neo4j cannot yet be processed ahead of time. Its bean ' +
                    'definitions hold the resolved configuration, and generating code for them ' +
                    "would write this machine's environment into the application. Remove Spring " +
                    "Boot's AOT plugin, or the Neo4j datastore, until this is supported.")
        }
    }

    @Override
    Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) {
        refuseWhereTheConfigurationWouldBeWrittenOut()
        return { ->
            def callable = getCommonConfiguration(beanDefinitionRegistry, DATASTORE_TYPE)
            callable.delegate = delegate
            callable.call()

            // Registered rather than built here, so what the datastore holds is a reference the
            // container can build. Holding the publisher itself puts a live object in the
            // definition, and generating code for a definition means writing out what it holds --
            // which a publisher bound to a running context is not.
            //
            // The choice is made here rather than through AbstractDatastoreInitializer, because this
            // build resolves that class from a released grails-datamapping-core rather than from
            // the source beside it, and a method added there is not one this can call.
            // Both halves of the condition, because this bean is registered under one name by four
            // initializers and whichever drains last defines it for all of them. The other three
            // resolve the class through AbstractDatastoreInitializer.findEventPublisherClass, which
            // also accepts a resource loader that is a context -- so testing only the registry here
            // let a Hibernate-and-Neo4j application end up publishing Hibernate's events through
            // DefaultApplicationEventPublisher, which does nothing with them: GORM events stop
            // firing and auto-timestamping stops working, with nothing logged.
            grailsDatastoreEventPublisher(beanDefinitionRegistry instanceof ConfigurableApplicationContext
                    || resourcePatternResolver.resourceLoader instanceof ConfigurableApplicationContext
                    ? ConfigurableApplicationContextEventPublisher
                    : DefaultApplicationEventPublisher)
            final boolean isRecentGrailsVersion = GrailsVersion.isAtLeastMajorMinor(3, 3)
            neo4jConnectionSourceFactory(Neo4jConnectionSourceFactory) { bean ->
                bean.autowire = true
            }
            neo4jDatastore(Neo4jDatastore, configuration, ref('neo4jConnectionSourceFactory'), ref('grailsDatastoreEventPublisher'), collectMappedClasses(DATASTORE_TYPE))
            neo4jMappingContext(neo4jDatastore: 'getMappingContext')
            neo4jTransactionManager(neo4jDatastore: 'getTransactionManager')
            neo4jAutoTimestampEventListener(neo4jDatastore: 'getAutoTimestampEventListener')
            neo4jDriver(neo4jDatastore: 'getBoltDriver')
            neo4jPersistenceInterceptor(getPersistenceInterceptorClass(), ref('neo4jDatastore'))
            neo4jPersistenceContextInterceptorAggregator(PersistenceContextInterceptorAggregator)
            if (!secondaryDatastore) {
                if (delegate instanceof BeanBuilder) {
                    springConfig.addAlias 'grailsDomainClassMappingContext', 'neo4jMappingContext'
                } else if (delegate instanceof GroovyBeanDefinitionReader) {
                    registerAlias 'neo4jMappingContext', 'grailsDomainClassMappingContext'
                }
            }

            String transactionManagerBeanName = TRANSACTION_MANAGER_BEAN
            if (!containsRegisteredBean(delegate, beanDefinitionRegistry, transactionManagerBeanName)) {
                beanDefinitionRegistry.registerAlias('neo4jTransactionManager', transactionManagerBeanName)
            }

            ClassLoader classLoader = getClass().getClassLoader()
            if (isWebApplicationRegistry(beanDefinitionRegistry) && ClassUtils.isPresent(OSIV_CLASS_NAME, classLoader)) {
                String interceptorName = 'neo4jOpenSessionInViewInterceptor'
                "${interceptorName}"(ClassUtils.forName(OSIV_CLASS_NAME, classLoader)) {
                    datastore = ref('neo4jDatastore')
                }
            }

            loadDataServices(secondaryDatastore ? 'neo4j' : null).each { serviceName, serviceClass ->
                "$serviceName"(DatastoreServiceMethodInvokingFactoryBean, serviceClass) {
                    targetObject = ref('neo4jDatastore')
                    targetMethod = 'getService'
                    arguments = [serviceClass]
                }
            }
        }
    }

    /**
     * Determines whether the given registry belongs to a web application. The presence of the
     * {@code dispatcherServlet} bean definition is only a reliable signal after Spring Boot
     * auto-configuration has been processed; Grails 8 registers plugin bean definitions before
     * that, so the type of the registry (the web application context itself) is checked as well.
     *
     * <p>Mirrors {@code AbstractDatastoreInitializer#isWebApplicationRegistry} in
     * grails-datamapping-core; once this build depends on a version that ships it, this
     * override is redundant and can be removed.
     *
     * @param registry The bean definition registry
     * @return true if the registry belongs to a web application
     */
    protected boolean isWebApplicationRegistry(BeanDefinitionRegistry registry) {
        if (registry == null) {
            return false
        }
        if (registry.containsBeanDefinition('dispatcherServlet')) {
            return true
        }
        String webApplicationContextClassName = 'org.springframework.web.context.WebApplicationContext'
        ClassLoader classLoader = getClass().classLoader
        return ClassUtils.isPresent(webApplicationContextClassName, classLoader) &&
                ClassUtils.forName(webApplicationContextClassName, classLoader).isInstance(registry)
    }

    /**
     * Sets the default Neo4j GORM mapping configuration
     */
    @Deprecated
    void setDefaultMapping(Closure defaultMapping) {
        this.defaultMapping = defaultMapping
    }
}
