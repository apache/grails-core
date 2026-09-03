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

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Structurally identical to {@link GormStaticApiRegistry} (item 20) - same gap, same fix: no
 * dedicated spec existed, and {@code GormRegistry.findValidationApi(...)} routes through a
 * different method ({@code resolveValidationApi}), never through this class's own instance method.
 */
class GormValidationApiRegistrySpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(GormValidationApiRegistryThing)

    void setup() {
        GormRegistry.instance.reset()
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    void "qualify returns the same api unchanged when no datastore resolves for the qualifier"() {
        given:
        def registry = GormRegistry.instance
        def validationApi = new GormValidationApi(GormValidationApiRegistryThing, datastore, registry)

        expect:
        registry.validationApiRegistry.qualify(validationApi, 'unknown-qualifier').is(validationApi)
    }

    void "findValidationApi throws IllegalStateException when the entity is not registered"() {
        given:
        def registry = GormRegistry.instance

        when:
        registry.validationApiRegistry.findValidationApi(String)

        then:
        thrown(IllegalStateException)
    }

    void "findValidationApi returns the registered api directly for a null or DEFAULT qualifier"() {
        given:
        def registry = GormRegistry.instance
        def validationApi = new GormValidationApi(GormValidationApiRegistryThing, datastore, registry)
        registry.validationApiRegistry.register(GormValidationApiRegistryThing.name, validationApi)

        expect:
        registry.validationApiRegistry.findValidationApi(GormValidationApiRegistryThing).is(validationApi)
        registry.validationApiRegistry.findValidationApi(GormValidationApiRegistryThing, ConnectionSource.DEFAULT).is(validationApi)
    }

    void "findValidationApi routes through forQualifier for a non-default qualifier"() {
        given:
        def registry = GormRegistry.instance
        def validationApi = new GormValidationApi(GormValidationApiRegistryThing, datastore, registry)
        registry.validationApiRegistry.register(GormValidationApiRegistryThing.name, validationApi)

        when:
        def result = registry.validationApiRegistry.findValidationApi(GormValidationApiRegistryThing, 'secondary')

        then: "a fresh, qualifier-scoped api was derived via forQualifier rather than the default api being returned as-is"
        result != null
        !result.is(validationApi)
    }
}

@Entity
class GormValidationApiRegistryThing {
    String name
}
