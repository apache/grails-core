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
package grails.persistence

import grails.artefact.DomainClass
import grails.core.DefaultGrailsApplication
import grails.util.Holders
import groovy.transform.Generated
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.springframework.context.ApplicationContext
import spock.lang.Issue
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * @author James Kleeh
 */
class DomainClassTraitSpec extends Specification {

    @Issue("GRAILS-9245")
    void "test getConstrainedProperties"() {
        given:
        def application = new DefaultGrailsApplication([Person] as Class[], getClass().classLoader)
        application.initialise()

        def context = new KeyValueMappingContext("domainclasstraitspec")
        context.addPersistentEntity(Person)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))
        application.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> {
                context
            }
        })
        application.setMappingContext(context)
        Holders.grailsApplication = application

        expect:
        !Person.constrainedProperties.name.blank
        new Person().constrainedProperties.name.inList == ['Joe']

        and: 'it is annotated as Generated'
        Person.class.getMethod('getConstrainedProperties').isAnnotationPresent(Generated)
    }

    @Entity
    class Person {
        String name
        
        static constraints = {
            name blank: false, inList: ['Joe']
        }
    }

}
