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

import groovy.transform.CompileStatic

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

/**
 * Plugin that integrates Hibernate into a Grails application
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@GrailsPluginDescriptor(
        loadAfter = ['controllers', 'domainClass'],
        observe = ['domainClass'],
        watchedResources = ['file:./grails-app/conf/hibernate/**.xml'])
class HibernateGrailsPlugin extends Plugin {

    public static final String DEFAULT_DATA_SOURCE_NAME = HibernateDatastoreSpringInitializer.DEFAULT_DATA_SOURCE_NAME

    Set<String> dataSourceNames

    Closure doWithSpring() {
        { ->
            ConfigurableApplicationContext applicationContext = (ConfigurableApplicationContext) applicationContext

            GrailsApplication grailsApplication = grailsApplication
            Config config = grailsApplication.config
            ConfigSupport.prepareConfig(config, applicationContext)

            def domainClasses = grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)
                                                 .collect() { GrailsClass cls -> cls.clazz }

            def springInitializer = new HibernateDatastoreSpringInitializer((PropertyResolver) config, domainClasses)
            springInitializer.enableReload = Environment.isDevelopmentMode()
            dataSourceNames = springInitializer.dataSources
            def beans = springInitializer.getBeanDefinitions((BeanDefinitionRegistry) applicationContext)

            beans.delegate = delegate
            beans.call()
        }
    }

    @Override
    void onChange(Map<String, Object> event) {
        // TODO: rewrite onChange handling
    }

}
