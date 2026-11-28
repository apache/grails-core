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

import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.support.GenericApplicationContext

import grails.config.Config
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.plugins.metadata.GrailsPluginDescriptor
import spock.lang.Specification

class MongodbGrailsPluginSpec extends Specification {

    GrailsApplication grailsApplication = Stub(GrailsApplication) {
        getConfig() >> Stub(Config)
        getArtefacts(_) >> ([] as GrailsClass[])
        getClassLoader() >> getClass().classLoader
    }

    GenericApplicationContext applicationContext = new GenericApplicationContext()

    MongodbGrailsPlugin plugin = new MongodbGrailsPlugin(
            grailsApplication: grailsApplication,
            applicationContext: applicationContext
    )

    void "exposes the expected Grails plugin descriptor metadata"() {
        when:
        GrailsPluginDescriptor descriptor = MongodbGrailsPlugin.getAnnotation(GrailsPluginDescriptor)

        then:
        descriptor.observe() as List == ['services', 'domainClass']
        descriptor.after() as List == ['domainClass', 'hibernate', 'services']
    }

    void "the post-processor the registrar registers registers the core MongoDB datastore bean definitions"() {
        given:
        plugin.pluginManager = Stub(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }

        when:
        registerPluginBeans()

        then:
        applicationContext.containsBeanDefinition(MongodbGrailsPlugin.BEAN_DEFINITIONS_POST_PROCESSOR)
        ['mongoDatastore', 'mongoMappingContext', 'mongoTransactionManager'].every { applicationContext.containsBeanDefinition(it) }
    }

    void "the domain mapping context alias is registered when Hibernate is not present"() {
        given:
        plugin.pluginManager = Stub(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }

        when:
        registerPluginBeans()

        then:
        applicationContext.isAlias('grailsDomainClassMappingContext')
    }

    void "the domain mapping context alias is not registered when Hibernate is present"() {
        given:
        plugin.pluginManager = Stub(GrailsPluginManager) {
            hasGrailsPlugin('hibernate') >> true
        }

        when:
        registerPluginBeans()

        then:
        !applicationContext.isAlias('grailsDomainClassMappingContext')
    }

    void "the mongo transaction manager is aliased as the default transactionManager bean"() {
        given:
        plugin.pluginManager = Stub(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }

        when:
        registerPluginBeans()

        then:
        applicationContext.isAlias('transactionManager')
        applicationContext.getAliases('mongoTransactionManager').contains('transactionManager')
    }

    /**
     * Applies the plugin's registrar to the context, and runs the post-processor it registers, as
     * Spring does before it reads the configuration classes.
     */
    private void registerPluginBeans() {
        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(applicationContext, applicationContext.beanFactory, applicationContext.environment, registrar.getClass())
                .register(registrar)
        applicationContext.beanFactory.getBean(MongodbGrailsPlugin.BEAN_DEFINITIONS_POST_PROCESSOR, BeanDefinitionRegistryPostProcessor)
                .postProcessBeanDefinitionRegistry(applicationContext)
    }
}
