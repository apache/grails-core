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

package grails.plugin.hibernate

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.PropertyResolver

import grails.config.Config
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor
import grails.util.Environment
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.gorm.plugin.support.ConfigSupport
import org.grails.spring.BeanBuilderRegistryPostProcessor

/**
 * Plugin that integrates Hibernate into a Grails application
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@GrailsPluginDescriptor(
        after = ['controllers', 'domainClass'],
        observe = ['domainClass'],
        watchedResources = ['file:./grails-app/conf/hibernate/**.xml'])
class HibernateGrailsPlugin extends Plugin {

    public static final String DEFAULT_DATA_SOURCE_NAME = HibernateDatastoreSpringInitializer.DEFAULT_DATA_SOURCE_NAME

    /** The name of the post-processor that registers the datastore's bean definitions. */
    public static final String BEAN_DEFINITIONS_POST_PROCESSOR = 'hibernateBeanDefinitionsPostProcessor'

    Set<String> dataSourceNames

    /**
     * Registers the Hibernate datastore's beans through a post-processor, which Spring runs before it
     * reads the configuration classes, so an auto-configuration's {@code @ConditionalOnMissingBean}
     * backs off from them as it does from the beans of a registrar.
     */
    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, org.springframework.core.env.Environment environment ->
            registry.registerBean(BEAN_DEFINITIONS_POST_PROCESSOR, BeanBuilderRegistryPostProcessor) {
                it.infrastructure().supplier {
                    new BeanBuilderRegistryPostProcessor(grailsApplication.parentContext, grailsApplication.classLoader,
                            [:], { -> datastoreBeans() } as Supplier<Closure<?>>)
                }
            }
        } as BeanRegistrar
    }

    /**
     * The bean definitions of the Hibernate datastore, in the bean builder DSL the datastore's
     * initializer writes them in. Reads the configuration and records the data source names.
     */
    protected Closure datastoreBeans() {
        ConfigurableApplicationContext applicationContext = (ConfigurableApplicationContext) applicationContext

        GrailsApplication grailsApplication = grailsApplication
        Config config = grailsApplication.config
        ConfigSupport.prepareConfig(config, applicationContext)

        def domainClasses = grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)
                                             .collect() { GrailsClass cls -> cls.clazz }

        def springInitializer = new HibernateDatastoreSpringInitializer((PropertyResolver) config, domainClasses)
        springInitializer.enableReload = Environment.isDevelopmentMode()
        dataSourceNames = springInitializer.dataSources
        springInitializer.getBeanDefinitions((BeanDefinitionRegistry) applicationContext)
    }

    @Override
    void onChange(Map<String, Object> event) {
        // No plugin-level onChange handling is currently implemented here.
    }

}
