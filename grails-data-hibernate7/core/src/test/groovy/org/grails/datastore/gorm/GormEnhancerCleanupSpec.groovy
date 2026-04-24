/* Copyright (C) 2026 the original author or authors.
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
package org.grails.datastore.gorm

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.core.Datastore
import spock.lang.Specification
import java.util.concurrent.ConcurrentHashMap

class GormEnhancerCleanupSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([CleanupEntity])
    }

    void "Test that GormEnhancer.close() removes datastore from registry"() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect: "The datastore is registered for the entity"
        registry.getDatastore(CleanupEntity.name, "default") == datastore

        when: "The datastore is closed"
        datastore.close()

        then: "The datastore reference is removed from the registry"
        registry.getDatastore(CleanupEntity.name, "default") == null
    }

    void "Test that GormEnhancer.close() does not mutate registry with extra maps"() {
        given:
        GormRegistry registry = GormRegistry.instance
        String unknownQualifier = "unknown_tenant_" + System.currentTimeMillis()
        
        expect: "The unknown qualifier is not in the map"
        !registry.datastoresByQualifier.containsKey(unknownQualifier)

        when: "Accessing an unknown qualifier"
        def ds = registry.getDatastore(CleanupEntity.name, unknownQualifier)

        then: "The datastore is not found but NO map was created for that qualifier"
        ds == null
        !registry.datastoresByQualifier.containsKey(unknownQualifier)
    }
}

@Entity
class CleanupEntity {
    String name
}
