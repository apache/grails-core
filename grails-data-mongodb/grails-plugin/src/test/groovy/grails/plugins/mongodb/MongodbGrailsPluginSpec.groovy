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

import org.springframework.context.support.GenericApplicationContext

import grails.config.Config
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.spring.BeanBuilder
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
        expect:
        plugin.license == 'Apache 2.0 License'
        plugin.title == 'GORM MongoDB'
        plugin.description
        plugin.documentation
        plugin.organization == [name: 'Grails', url: 'https://grails.apache.org/']
        plugin.issueManagement == [system: 'Github', url: 'https://github.com/apache/grails-core/issues']
        plugin.scm == [url: 'https://github.com/apache/grails-core']
        plugin.observe == ['services', 'domainClass']
        plugin.loadAfter == ['domainClass', 'hibernate', 'hibernate5', 'hibernate7', 'services']
    }

    void "doWithSpring registers the core MongoDB datastore bean definitions"() {
        given:
        plugin.pluginManager = Stub(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }

        when:
        def beanClosure = plugin.doWithSpring()
        def bb = new BeanBuilder()
        bb.beans(beanClosure)

        then:
        beanClosure != null
        bb.beanDefinitions.keySet().containsAll(['mongoDatastore', 'mongoMappingContext', 'mongoTransactionManager'])
    }

    void "doWithSpring registers the domain mapping context alias when Hibernate is not present"() {
        given:
        plugin.pluginManager = Stub(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }

        when:
        def bb = new BeanBuilder()
        bb.beans(plugin.doWithSpring())

        then:
        bb.springConfig.unrefreshedApplicationContext.isAlias('grailsDomainClassMappingContext')
    }

    void "doWithSpring does not register the domain mapping context alias when Hibernate is present"() {
        given:
        plugin.pluginManager = Stub(GrailsPluginManager) {
            getAllPlugins() >> ([Stub(GrailsPlugin) { getName() >> 'hibernate' }] as GrailsPlugin[])
        }

        when:
        def bb = new BeanBuilder()
        bb.beans(plugin.doWithSpring())

        then:
        !bb.springConfig.unrefreshedApplicationContext.isAlias('grailsDomainClassMappingContext')
    }

    void "doWithSpring aliases the mongo transaction manager as the default transactionManager bean"() {
        given:
        plugin.pluginManager = Stub(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }

        when:
        def bb = new BeanBuilder()
        bb.beans(plugin.doWithSpring())

        then:
        applicationContext.isAlias('transactionManager')
        applicationContext.getAliases('mongoTransactionManager').contains('transactionManager')
    }
}
