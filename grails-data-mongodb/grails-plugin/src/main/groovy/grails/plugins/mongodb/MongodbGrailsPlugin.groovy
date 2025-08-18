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

import grails.core.GrailsClass
import grails.mongodb.bootstrap.MongoDbDataStoreSpringInitializer
import grails.plugins.GrailsPlugin
import grails.plugins.Plugin
import grails.util.Metadata
import groovy.transform.CompileStatic
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.gorm.plugin.support.ConfigSupport
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.PropertyResolver
import org.springframework.transaction.PlatformTransactionManager

class MongodbGrailsPlugin extends Plugin {
    def license = 'Apache 2.0 License'
    def organization = [name: 'Grails', url: 'https://grails.org/']
    def issueManagement = [system: 'Github', url: 'https://github.com/grails/gorm-mongodb']
    def scm = [url: 'https://github.com/grails/gorm-mongodb']
    def grailsVersion = '7.0.0 > *'
    def observe = ['services', 'domainClass']
    def loadAfter = ['domainClass', 'hibernate', 'hibernate5', 'hibernate6', 'services']
    def title = 'GORM MongoDB'
    def description = 'A plugin that integrates the MongoDB document datastore into the Grails framework, providing a GORM API onto it'
    def documentation = 'https://gorm.grails.org/latest/mongodb/manual'

    @Override
    @CompileStatic
    Closure doWithSpring() {
        ConfigSupport.prepareConfig(config, (ConfigurableApplicationContext) applicationContext)
        def initializer = new MongoDbDataStoreSpringInitializer((PropertyResolver) config, grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE).collect() { GrailsClass cls -> cls.clazz })
        initializer.registerApplicationIfNotPresent = false

        def applicationName = Metadata.getCurrent().getApplicationName()
        if(!applicationName.contains('@')) {
            initializer.databaseName = applicationName
        }
        initializer.setSecondaryDatastore(hasHibernatePlugin())

        return initializer.getBeanDefinitions((BeanDefinitionRegistry)applicationContext)
    }

    @CompileStatic
    protected boolean hasHibernatePlugin() {
        manager.allPlugins.any() { GrailsPlugin plugin -> plugin.name ==~ /hibernate\d*/}
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
