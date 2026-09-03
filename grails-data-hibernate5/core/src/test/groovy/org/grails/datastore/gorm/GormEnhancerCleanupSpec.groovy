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
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.core.connections.ConnectionSource

class GormEnhancerCleanupSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(CleanupEntity)
    }

    void "GormRegistry tracks entity-to-datastore mapping for registered entities"() {
        given:
        def registry = GormRegistry.instance

        expect: "CleanupEntity maps to the active datastore"
        registry.getDatastore(CleanupEntity, ConnectionSource.DEFAULT) == datastore

        and: "A static API is registered for CleanupEntity"
        registry.getStaticApi(CleanupEntity) != null
    }

    void "GormRegistry.removeDatastore clears entity and static API entries"() {
        given:
        def registry = GormRegistry.instance

        expect: "entries are present before removal"
        registry.getDatastore(CleanupEntity, ConnectionSource.DEFAULT) == datastore
        registry.getStaticApi(CleanupEntity) != null

        when: "the datastore is removed from the registry"
        registry.removeDatastore(datastore)

        then: "entity-to-datastore mapping is cleared"
        registry.getDatastore(CleanupEntity, ConnectionSource.DEFAULT) == null

        and: "static API is also cleared"
        registry.getStaticApi(CleanupEntity) == null
    }
}

@Entity
class CleanupEntity {
    String name
}
