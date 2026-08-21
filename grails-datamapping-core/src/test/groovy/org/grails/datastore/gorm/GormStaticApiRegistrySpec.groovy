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
 * Brand-new file in this PR. No dedicated spec existed - {@code GormRegistry.findStaticApi(...)}
 * (the static delegator already covered by {@code GormRegistryCoverageSpec}, item 3) routes through
 * a *different* method, {@code resolveStaticApi}, not through this class's own
 * {@code findStaticApi(Class, String)} - so this class's own instance method was never exercised.
 */
class GormStaticApiRegistrySpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(GormStaticApiRegistryThing)

    void setup() {
        GormRegistry.instance.reset()
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    void "qualify returns the same api unchanged when no datastore resolves for the qualifier"() {
        given:
        def registry = GormRegistry.instance
        def staticApi = new GormStaticApi(GormStaticApiRegistryThing, datastore, [])

        expect: "apiResolver.findDatastore(class, qualifier) resolves nothing for an unknown qualifier"
        registry.staticApiRegistry.qualify(staticApi, 'unknown-qualifier').is(staticApi)
    }

    void "findStaticApi throws IllegalStateException when the entity is not registered"() {
        given:
        def registry = GormRegistry.instance

        when:
        registry.staticApiRegistry.findStaticApi(String)

        then:
        thrown(IllegalStateException)
    }

    void "findStaticApi returns the registered api directly for a null or DEFAULT qualifier"() {
        given:
        def registry = GormRegistry.instance
        def staticApi = new GormStaticApi(GormStaticApiRegistryThing, datastore, [])
        registry.staticApiRegistry.register(GormStaticApiRegistryThing.name, staticApi)

        expect:
        registry.staticApiRegistry.findStaticApi(GormStaticApiRegistryThing).is(staticApi)
        registry.staticApiRegistry.findStaticApi(GormStaticApiRegistryThing, ConnectionSource.DEFAULT).is(staticApi)
    }

    void "findStaticApi routes through forQualifier for a non-default qualifier"() {
        given:
        def registry = GormRegistry.instance
        def staticApi = new GormStaticApi(GormStaticApiRegistryThing, datastore, [])
        registry.staticApiRegistry.register(GormStaticApiRegistryThing.name, staticApi)

        when:
        def result = registry.staticApiRegistry.findStaticApi(GormStaticApiRegistryThing, 'secondary')

        then:
        result != null
        result.qualifier == 'secondary'
    }
}

@Entity
class GormStaticApiRegistryThing {
    String name
}
