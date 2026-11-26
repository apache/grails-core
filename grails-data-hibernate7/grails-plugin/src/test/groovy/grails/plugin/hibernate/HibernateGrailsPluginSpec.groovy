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

import grails.core.DefaultGrailsApplication
import grails.gorm.annotation.Entity
import org.grails.config.PropertySourcesConfig
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.plugins.DefaultGrailsPlugin
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.support.GenericApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Drives {@link HibernateGrailsPlugin#beanRegistrar()} the way an application does: the registrar is applied
 * to the application's context, and the post-processor it registers registers the Hibernate bean definitions
 * when Spring runs it, before the context is refreshed.
 */
class HibernateGrailsPluginSpec extends Specification {

    @AutoCleanup
    GenericApplicationContext applicationContext

    void "the registrar registers the Hibernate beans and prepares the config for class conversion"() {
        given: "a Grails application with one domain class and an H2 test datasource"
        def app = new DefaultGrailsApplication([PluginSpecBook] as Class[], getClass().classLoader)
        app.initialise()
        def config = new PropertySourcesConfig((Map<String, Object>) [
                'dataSource.url'        : 'jdbc:h2:mem:hibernateGrailsPluginSpec;LOCK_TIMEOUT=10000',
                'dataSource.dialect'    : H2Dialect.name,
                'hibernate.hbm2ddl.auto': 'create-drop',
                'some.class'            : PluginSpecBook.name
        ])
        app.setConfig(config)

        and: "the plugin, as the real GrailsPluginManager creates it"
        def grailsPlugin = new DefaultGrailsPlugin(HibernateGrailsPlugin, app)
        applicationContext = new GenericApplicationContext()
        grailsPlugin.applicationContext = applicationContext

        expect: "the class value cannot be resolved before the plugin registers its beans"
        config.getProperty('some.class', Class) == null

        when: "the plugin's registrar is applied and the context refreshed, which runs the post-processor it registered"
        def registrar = grailsPlugin.beanRegistrar
        new BeanRegistryAdapter(applicationContext, applicationContext.beanFactory, applicationContext.environment, registrar.getClass())
                .register(registrar)
        applicationContext.refresh()

        then: "the post-processor the registrar registered is the one that registered the beans"
        applicationContext.containsBeanDefinition(HibernateGrailsPlugin.BEAN_DEFINITIONS_POST_PROCESSOR)

        and: "the Hibernate beans registered by the plugin are present"
        applicationContext.getBean('sessionFactory', SessionFactory).metamodel.entities.size() == 1
        applicationContext.getBean(PlatformTransactionManager)

        and: "the config can now resolve a String value as a Class"
        config.getProperty('some.class', Class) == PluginSpecBook

        and: "the plugin recorded the configured data source names"
        (grailsPlugin.pluginInstance as HibernateGrailsPlugin).dataSourceNames.contains(ConnectionSource.DEFAULT)
    }

    void "onChange is a no-op"() {
        given:
        HibernateGrailsPlugin plugin = new HibernateGrailsPlugin()

        when:
        plugin.onChange([:])

        then:
        noExceptionThrown()
    }
}

@Entity
class PluginSpecBook {
    String title
}
