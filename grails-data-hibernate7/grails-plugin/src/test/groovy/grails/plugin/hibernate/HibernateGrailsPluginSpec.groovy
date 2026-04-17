/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.plugin.hibernate

import grails.core.GrailsApplication
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import grails.spring.BeanBuilder
import org.grails.config.PropertySourcesConfig
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.SessionFactory
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MutablePropertySources
import spock.lang.Specification

class HibernateGrailsPluginSpec extends Specification {

    void "test doWithSpring registers essential beans"() {
        given:
        def plugin = new HibernateGrailsPlugin()
        
        def propertySources = new MutablePropertySources()
        def config = new PropertySourcesConfig(propertySources)
        // Set essential GORM/Hibernate config
        propertySources.addFirst(new org.springframework.core.env.MapPropertySource("test", [
            'dataSource.url': "jdbc:h2:mem:pluginTest;LOCK_TIMEOUT=10000",
            'dataSource.driverClassName': 'org.h2.Driver',
            'hibernate.dialect': 'org.hibernate.dialect.H2Dialect'
        ]))

        def grailsApplication = Mock(GrailsApplication)
        grailsApplication.getConfig() >> config
        grailsApplication.getArtefacts(_) >> []
        
        plugin.grailsApplication = grailsApplication
        
        def context = new GenericApplicationContext()
        plugin.applicationContext = context
        
        def beanBuilder = new BeanBuilder(context)
        
        when:
        def springConfig = plugin.doWithSpring()
        beanBuilder.beans(springConfig)
        beanBuilder.registerBeans(context)
        context.refresh()

        then:
        context.containsBean('hibernateDatastore')
        context.getBean('hibernateDatastore') instanceof HibernateDatastore
        context.containsBean('sessionFactory')
        context.getBean('sessionFactory') instanceof SessionFactory

        when: "Testing the converter"
        def conversionService = context.getEnvironment().getConversionService()
        def resolvedClass = conversionService.convert("java.lang.String", Class)

        then:
        resolvedClass == String

        cleanup:
        context.close()
    }
}
