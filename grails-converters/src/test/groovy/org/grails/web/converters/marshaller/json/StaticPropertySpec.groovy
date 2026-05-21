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
package org.grails.web.converters.marshaller.json

import spock.lang.Specification

import org.springframework.context.ApplicationContext

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.validation.Constrained
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer

class StaticPropertySpec extends Specification {
    void initJson() {
        final initializer = new ConvertersConfigurationInitializer()
        def grailsApplication = new DefaultGrailsApplication(MyGroovyBean)
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext("json")
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> {
                mappingContext
            }
        })
        grailsApplication.setMappingContext(mappingContext)
        initializer.grailsApplication = grailsApplication
        initializer.initialize()

    }

    void "static property should be excluded"() {
        given:
        initJson()

        when:
        MyGroovyBean bean = new MyGroovyBean(aProperty: 'testing')

        then:
        def jsonString = new JSON(bean).toString()
        jsonString == '{"aProperty":"testing"}'
    }
}

class MyGroovyBean {
    static Map<String, Constrained> getConstraintsMap() {
        [:]
    }

    String aProperty
}
