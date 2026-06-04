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
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.Datastore
import spock.lang.Specification

class GormApiFactoryRegistrySpec extends Specification {

    void "test register and resolve custom API factory"() {
        given:
        GormApiFactoryRegistry registry = new GormApiFactoryRegistry()
        GormApiFactory customFactory = Mock(GormApiFactory)
        Datastore customDatastore = Mock(CustomDatastore)

        expect: "defaults to DefaultGormApiFactory"
        registry.getApiFactory(customDatastore) instanceof DefaultGormApiFactory

        when: "registering a custom factory for the specific datastore class"
        registry.registerApiFactory(CustomDatastore, customFactory)

        then: "resolves to the custom factory"
        registry.getApiFactory(customDatastore) == customFactory
    }

    void "test resolve API factory by inheritance"() {
        given:
        GormApiFactoryRegistry registry = new GormApiFactoryRegistry()
        GormApiFactory customFactory = Mock(GormApiFactory)
        Datastore subDatastore = Mock(SubDatastore)

        when: "registering a custom factory for a parent class"
        registry.registerApiFactory(CustomDatastore, customFactory)

        then: "resolves to the custom factory by checking inheritance"
        registry.getApiFactory(subDatastore) == customFactory
    }

    void "test clear removes all custom factories"() {
        given:
        GormApiFactoryRegistry registry = new GormApiFactoryRegistry()
        GormApiFactory customFactory = Mock(GormApiFactory)
        Datastore customDatastore = Mock(CustomDatastore)

        when:
        registry.registerApiFactory(CustomDatastore, customFactory)
        registry.clear()

        then:
        registry.getApiFactory(customDatastore) instanceof DefaultGormApiFactory
    }

    void "test null values are handled gracefully"() {
        given:
        GormApiFactoryRegistry registry = new GormApiFactoryRegistry()

        expect:
        registry.getApiFactory(null) instanceof DefaultGormApiFactory

        when:
        registry.registerApiFactory(null, Mock(GormApiFactory))
        registry.registerApiFactory(CustomDatastore, null)

        then:
        noExceptionThrown()
    }

    static interface CustomDatastore extends Datastore {}
    static interface SubDatastore extends CustomDatastore {}
}
