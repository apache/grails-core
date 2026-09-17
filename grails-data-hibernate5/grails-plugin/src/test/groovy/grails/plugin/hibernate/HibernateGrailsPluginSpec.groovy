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
import org.grails.plugins.DefaultGrailsPlugin
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.grails.spring.RuntimeSpringConfiguration
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.context.support.GenericApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Drives {@link HibernateGrailsPlugin#doWithSpring()} the same way the real {@code GrailsPluginManager} does: via
 * {@link org.grails.plugins.DefaultGrailsPlugin#doWithRuntimeConfiguration}, followed by merging the accumulated
 * bean definitions into a fresh {@link GenericApplicationContext} and refreshing it - exactly what
 * {@link RuntimeSpringConfiguration#registerBeansWithContext} exists for, and how a real Grails Boot application
 * merges plugin-registered beans into the application's own context. Refreshing the plugin's own internal
 * {@code GrailsApplicationContext} directly doesn't work here: it renames Spring's "environment" bean to
 * "springEnvironment" (a long-standing workaround for GRAILS-7851) which breaks the SpEL datasource expressions
 * that {@code HibernateDatastoreConnectionSourcesRegistrar} registers.
 */
class HibernateGrailsPluginSpec extends Specification {

    @AutoCleanup
    GenericApplicationContext applicationContext

    void "doWithSpring registers the Hibernate beans and prepares the config for class conversion"() {
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

        and: "the plugin driven the same way the real GrailsPluginManager drives it"
        def grailsPlugin = new DefaultGrailsPlugin(HibernateGrailsPlugin, app)
        RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration()
        grailsPlugin.applicationContext = springConfig.unrefreshedApplicationContext

        expect: "the class value cannot be resolved before doWithSpring runs"
        config.getProperty('some.class', Class) == null

        when: "the plugin is configured, then its beans merged into the application's real context"
        grailsPlugin.doWithRuntimeConfiguration(springConfig)
        applicationContext = new GenericApplicationContext()
        springConfig.registerBeansWithContext(applicationContext)
        applicationContext.refresh()

        then: "the Hibernate beans registered by the plugin are present"
        applicationContext.getBean('sessionFactory', SessionFactory).metamodel.entities.size() == 1
        applicationContext.getBean(PlatformTransactionManager)

        and: "the config can now resolve a String value as a Class"
        config.getProperty('some.class', Class) == PluginSpecBook
    }
}

@Entity
class PluginSpecBook {
    String title
}
