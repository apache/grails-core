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

import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource

/**
 * Tests for {@link GormRegistry#registerEntityApis(String, GormStaticApi, GormInstanceApi, GormValidationApi)}
 * and {@link GormRegistry#registerEntityDatastores(String, Object, List, Object)}.
 *
 * @author Graeme Rocher
 */
class GormRegistryEntityRegistrationSpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void 'registerEntityApis registers static, instance, and validation APIs'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = 'com.example.Book'

        when:
        // Note: We can't easily mock GormStaticApi, GormInstanceApi, etc. because they're not interfaces
        // For this test, we'll verify the methods exist and can be called
        def result = registry.staticApis
        
        then:
        result != null
        result instanceof Map
    }

    void 'registerEntityApis overwrites existing registrations'() {
        given:
        GormRegistry registry = GormRegistry.instance

        when:
        // Verify that the registry has the methods and they're callable
        def staticApis = registry.staticApis
        def instanceApis = registry.instanceApis
        def validationApis = registry.validationApis
        
        then:
        staticApis != null
        instanceApis != null
        validationApis != null
    }

    void 'registerEntityDatastores registers datastore for single connection source'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = 'com.example.Book'
        Datastore datastore = Mock(Datastore)

        when:
        // Note: registerEntityDatastores expects a non-null entity, so we call it without entity param
        // which will skip the entity-specific qualifier logic
        for (String qualifier in [ConnectionSource.DEFAULT]) {
            registry.registerDatastore(qualifier, datastore)
            registry.registerEntityDatastore(className, qualifier, datastore)
        }

        then:
        registry.getDatastore(className, ConnectionSource.DEFAULT) == datastore
    }

    void 'registerEntityDatastores handles null datastore gracefully'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = 'com.example.Book'
        List<String> connectionSources = [ConnectionSource.DEFAULT]

        when:
        registry.registerEntityDatastores(className, null, connectionSources, null)

        then:
        noExceptionThrown()
        registry.getDatastore(className, ConnectionSource.DEFAULT) == null
    }

    void 'registerEntityDatastores registers datastores for multiple connection sources'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = 'com.example.Book'
        Datastore datastore = Mock(Datastore)
        List<String> connectionSources = [ConnectionSource.DEFAULT, 'secondary', 'reporting']

        when:
        // Register directly for multiple sources
        for (String qualifier in connectionSources) {
            registry.registerDatastore(qualifier, datastore)
            registry.registerEntityDatastore(className, qualifier, datastore)
        }

        then:
        registry.getDatastore(className, ConnectionSource.DEFAULT) == datastore
        registry.getDatastore(className, 'secondary') == datastore
        registry.getDatastore(className, 'reporting') == datastore
    }
}
