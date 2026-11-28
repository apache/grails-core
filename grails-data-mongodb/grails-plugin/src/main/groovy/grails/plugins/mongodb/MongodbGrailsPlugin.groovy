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

package grails.plugins.mongodb

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertyResolver
import org.springframework.transaction.PlatformTransactionManager

import grails.core.GrailsClass
import grails.mongodb.bootstrap.MongoDbDataStoreSpringInitializer
import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor
import grails.util.Metadata
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.gorm.plugin.support.ConfigSupport
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.spring.BeanBuilderRegistryPostProcessor

@GrailsPluginDescriptor(
        after = ['domainClass', 'hibernate', 'services'],
        observe = ['services', 'domainClass'])
class MongodbGrailsPlugin extends Plugin {

    /** The name of the post-processor that registers the datastore's bean definitions. */
    public static final String BEAN_DEFINITIONS_POST_PROCESSOR = 'mongoBeanDefinitionsPostProcessor'

    /**
     * Registers the MongoDB datastore's beans through a post-processor, which Spring runs before it
     * reads the configuration classes, so an auto-configuration's {@code @ConditionalOnMissingBean}
     * backs off from them as it does from the beans of a registrar. It runs after the post-processor
     * of a Hibernate plugin loaded before this one, whose transaction manager it then finds.
     */
    @Override
    @CompileStatic
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, Environment environment ->
            registry.registerBean(BEAN_DEFINITIONS_POST_PROCESSOR, BeanBuilderRegistryPostProcessor) {
                it.infrastructure().supplier {
                    new BeanBuilderRegistryPostProcessor(grailsApplication.parentContext, grailsApplication.classLoader,
                            [:], { -> datastoreBeans() } as Supplier<Closure<?>>)
                }
            }
        } as BeanRegistrar
    }

    /**
     * The bean definitions of the MongoDB datastore, in the bean builder DSL the datastore's
     * initializer writes them in.
     */
    @CompileStatic
    protected Closure datastoreBeans() {
        ConfigSupport.prepareConfig(config, (ConfigurableApplicationContext) applicationContext)
        def initializer = new MongoDbDataStoreSpringInitializer((PropertyResolver) config, grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE).collect() { GrailsClass cls -> cls.clazz })

        def applicationName = Metadata.getCurrent().getApplicationName()
        if (!applicationName.contains('@')) {
            initializer.databaseName = applicationName
        }
        initializer.setSecondaryDatastore(hasHibernatePlugin())

        return initializer.getBeanDefinitions((BeanDefinitionRegistry) applicationContext)
    }

    @CompileStatic
    protected boolean hasHibernatePlugin() {
        manager.hasGrailsPlugin('hibernate')
    }

    @Override
    @CompileStatic
    void onChange(Map<String, Object> event) {

        def ctx = applicationContext
        event.application = grailsApplication
        event.ctx = applicationContext

        def mongoDatastore = ctx.getBean(MongoDatastore)
        def mongoTransactionManager = ctx.getBean('mongoTransactionManager', PlatformTransactionManager)
    }
}
