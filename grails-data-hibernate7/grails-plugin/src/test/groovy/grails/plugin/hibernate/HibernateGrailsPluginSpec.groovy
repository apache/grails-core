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
import grails.core.GrailsApplication
import grails.gorm.annotation.Entity
import grails.spring.BeanBuilder
import org.grails.config.PropertySourcesConfig
import org.grails.core.artefact.DomainClassArtefactHandler
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.convert.support.ConfigurableConversionService
import spock.lang.Specification

class HibernateGrailsPluginSpec extends Specification {

    def "doWithSpring registers the Hibernate datastore beans"() {
        given:
        Map<String, BeanDefinition> beans = beanDefinitionsFor([
                'dataSource.url': 'jdbc:h2:mem:hibernateGrailsPluginSpecDb;LOCK_TIMEOUT=10000',
        ])

        expect: "the core Hibernate infrastructure beans are registered"
        beans.containsKey('hibernateDatastore')
        beans.containsKey('sessionFactory')
        beans.containsKey('transactionManager')
    }

    def "doWithSpring records the configured data source names on the plugin"() {
        given:
        HibernateGrailsPlugin plugin = pluginFor([
                'dataSource.url': 'jdbc:h2:mem:hibernateGrailsPluginSpecDsNames;LOCK_TIMEOUT=10000',
        ])

        when:
        new BeanBuilder().beans(plugin.doWithSpring())

        then: "the plugin's dataSourceNames were populated as a side effect"
        plugin.dataSourceNames.contains('default')
    }

    def "doWithSpring installs a Class converter on the application context's conversion service"() {
        given: "a PropertySourcesConfig (triggering the conversion-service-installation branch) and our own ApplicationContext, so we can inspect its ConversionService directly afterwards"
        PropertySourcesConfig config = new PropertySourcesConfig([
                'dataSource.url': 'jdbc:h2:mem:hibernateGrailsPluginSpecConverter;LOCK_TIMEOUT=10000',
        ])
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        HibernateGrailsPlugin plugin = pluginForConfig(config, applicationContext)
        ConfigurableConversionService conversionService = applicationContext.environment.conversionService

        expect: "no String->Class converter registered yet"
        !conversionService.canConvert(String, Class)

        when:
        new BeanBuilder().beans(plugin.doWithSpring())

        then: "the environment's conversion service can now convert a String to a Class"
        conversionService.canConvert(String, Class)
        conversionService.convert(HibernateGrailsPlugin.name, Class) == HibernateGrailsPlugin
    }

    def "onChange is a no-op"() {
        given:
        HibernateGrailsPlugin plugin = new HibernateGrailsPlugin()

        when:
        plugin.onChange([:])

        then:
        noExceptionThrown()
    }

    private Map<String, BeanDefinition> beanDefinitionsFor(Map<String, Object> config) {
        BeanBuilder beanBuilder = new BeanBuilder()
        beanBuilder.beans pluginFor(config).doWithSpring()
        beanBuilder.beanDefinitions
    }

    private HibernateGrailsPlugin pluginFor(Map<String, Object> config) {
        pluginForConfig(new PropertySourcesConfig(config), new GenericApplicationContext())
    }

    private HibernateGrailsPlugin pluginForConfig(PropertySourcesConfig config, GenericApplicationContext applicationContext) {
        GrailsApplication grailsApplication = new DefaultGrailsApplication()
        grailsApplication.config = config
        grailsApplication.registerArtefactHandler(new DomainClassArtefactHandler())
        grailsApplication.initialise()
        grailsApplication.addArtefact(DomainClassArtefactHandler.TYPE, HibernateGrailsPluginSpecEntity)

        HibernateGrailsPlugin plugin = new HibernateGrailsPlugin()
        plugin.grailsApplication = grailsApplication
        plugin.applicationContext = applicationContext
        plugin
    }
}

@Entity
class HibernateGrailsPluginSpecEntity {
    Long id
    String name
}
