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
package org.grails.plugins.web.controllers.api

import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.Holders
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext

class ControllersDomainBindingApiSpec extends Specification {

    KeyValueMappingContext mappingContext = new KeyValueMappingContext('test')

    AutowireCapableBeanFactory beanFactory = Mock(AutowireCapableBeanFactory)

    ApplicationContext applicationContext = Stub(ApplicationContext) {
        getAutowireCapableBeanFactory() >> beanFactory
    }

    GrailsApplication grailsApplication = new DefaultGrailsApplication()

    void setup() {
        // Discovery strategies are static and consulted in registration order, and tests share a fork, so a
        // strategy left behind by an earlier test - pointing at an application context since closed - would be
        // asked first and throw before the one registered below is ever reached.
        Holders.clear()
        mappingContext.addPersistentEntity(Widget)
        grailsApplication.mappingContext = mappingContext
        Holders.addApplicationDiscoveryStrategy(new GrailsApplicationDiscoveryStrategy() {

            @Override
            GrailsApplication findGrailsApplication() {
                grailsApplication
            }

            @Override
            ApplicationContext findApplicationContext() {
                applicationContext
            }
        })
    }

    void cleanup() {
        Holders.clear()
    }

    private void setAutowire(boolean autowire) {
        mappingContext.getPersistentEntity(Widget.name).mapping.mappedForm.autowire = autowire
    }

    void 'a map constructor binds the named arguments and autowires the instance when its mapping asks for it'() {
        given:
        setAutowire(true)
        def widget = new Widget()

        when:
        ControllersDomainBindingApi.initialize(widget, [name: 'spanner'])

        then:
        widget.name == 'spanner'
        1 * beanFactory.autowireBeanProperties(widget, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
    }

    void 'a map constructor binds the named arguments without autowiring when the mapping does not ask for it'() {
        given:
        setAutowire(false)
        def widget = new Widget()

        when:
        ControllersDomainBindingApi.initialize(widget, [name: 'spanner'])

        then:
        widget.name == 'spanner'
        0 * beanFactory.autowireBeanProperties(_, _, _)
    }

    void 'an instance of a class that is not a persistent entity is bound but never autowired'() {
        given:
        setAutowire(true)
        def gadget = new Gadget()

        when:
        ControllersDomainBindingApi.initialize(gadget, [name: 'spanner'])

        then:
        gadget.name == 'spanner'
        0 * beanFactory.autowireBeanProperties(_, _, _)
    }

    void 'the no argument initializer autowires the instance when its mapping asks for it'() {
        given:
        setAutowire(true)
        def widget = new Widget()

        when:
        ControllersDomainBindingApi.initialize(widget)

        then:
        1 * beanFactory.autowireBeanProperties(widget, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
    }

    void 'named arguments are still bound when no application has been bound yet'() {
        given:
        Holders.clear()
        def widget = new Widget()

        when:
        ControllersDomainBindingApi.initialize(widget, [name: 'spanner'])

        then:
        widget.name == 'spanner'
        0 * beanFactory.autowireBeanProperties(_, _, _)
    }
}

class Widget {

    Long id
    Long version
    String name
}

class Gadget {

    String name
}
