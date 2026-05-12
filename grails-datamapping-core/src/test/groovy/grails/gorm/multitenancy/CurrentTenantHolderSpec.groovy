/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.gorm.multitenancy

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import spock.lang.Specification

class CurrentTenantHolderSpec extends Specification {

    void "test set, get, and remove for Datastore instance"() {
        given:
        Datastore mockDatastore = Mock(Datastore)

        when:
        CurrentTenantHolder.set(mockDatastore, "tenant1")

        then:
        CurrentTenantHolder.get(mockDatastore) == "tenant1"

        when:
        CurrentTenantHolder.remove(mockDatastore)

        then:
        CurrentTenantHolder.get(mockDatastore) == null
    }

    void "test set, get, and remove for Datastore class"() {
        given:
        Datastore mockDatastore = [:] as Datastore
        Class<? extends Datastore> mockDatastoreClass = mockDatastore.getClass()

        when:
        CurrentTenantHolder.set(mockDatastoreClass, "tenant2")

        then:
        CurrentTenantHolder.get(mockDatastore) == "tenant2"

        when:
        CurrentTenantHolder.remove(mockDatastoreClass)

        then:
        CurrentTenantHolder.get(mockDatastore) == null
    }

    void "test fallback to Datastore class when instance tenant is not found"() {
        given:
        Datastore mockDatastore = [:] as Datastore
        Class<? extends Datastore> datastoreClass = mockDatastore.getClass()

        when:
        CurrentTenantHolder.set(datastoreClass, "classTenant")

        then:
        CurrentTenantHolder.get(mockDatastore) == "classTenant"

        when:
        CurrentTenantHolder.set(mockDatastore, "instanceTenant")

        then:
        CurrentTenantHolder.get(mockDatastore) == "instanceTenant"

        cleanup:
        CurrentTenantHolder.remove(datastoreClass)
        CurrentTenantHolder.remove(mockDatastore)
    }

    void "test withTenant for Datastore instance"() {
        given:
        Datastore mockDatastore = [:] as Datastore
        CurrentTenantHolder.set(mockDatastore, "previousTenant")

        when:
        def result = CurrentTenantHolder.withTenant(mockDatastore, "newTenant") {
            return CurrentTenantHolder.get(mockDatastore)
        }

        then:
        result == "newTenant"
        CurrentTenantHolder.get(mockDatastore) == "previousTenant"

        cleanup:
        CurrentTenantHolder.remove(mockDatastore)
    }

    void "test withTenant for Datastore class"() {
        given:
        Datastore mockDatastore = [:] as Datastore
        Class<? extends Datastore> mockDatastoreClass = mockDatastore.getClass()
        CurrentTenantHolder.set(mockDatastoreClass, "previousClassTenant")

        when:
        def result = CurrentTenantHolder.withTenant(mockDatastoreClass, "newClassTenant") {
            return CurrentTenantHolder.get(mockDatastore)
        }

        then:
        result == "newClassTenant"
        CurrentTenantHolder.get(mockDatastore) == "previousClassTenant"

        cleanup:
        CurrentTenantHolder.remove(mockDatastoreClass)
    }

    void "test withoutTenant"() {
        given:
        Datastore mockDatastore = [:] as Datastore
        CurrentTenantHolder.set(mockDatastore, "currentTenant")

        when:
        def result = CurrentTenantHolder.withoutTenant(mockDatastore) {
            return CurrentTenantHolder.get(mockDatastore)
        }

        then:
        result == ConnectionSource.DEFAULT
        CurrentTenantHolder.get(mockDatastore) == "currentTenant"

        cleanup:
        CurrentTenantHolder.remove(mockDatastore)
    }
}
