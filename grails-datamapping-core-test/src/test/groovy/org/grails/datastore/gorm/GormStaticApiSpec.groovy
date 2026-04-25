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
import grails.gorm.MultiTenant
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import grails.gorm.api.GormStaticOperations

import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver
import org.grails.datastore.mapping.core.connections.ConnectionSource

import org.grails.datastore.mapping.core.DatastoreUtils

class GormStaticApiSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
        DatastoreUtils.createPropertyResolver([(Settings.SETTING_MULTI_TENANCY_MODE): "SCHEMA",
                                               (Settings.SETTING_MULTI_TENANT_RESOLVER): new FixedTenantResolver(ConnectionSource.DEFAULT)]),
        TestEntity
    )

    def setup() {
        GormEnhancer.setPreferredDatastore(datastore)
    }

    def cleanup() {
        GormEnhancer.clearPreferredDatastore()
        datastore.clearData()
    }

    void "test dynamic finder dispatch"() {
        given:
        TestEntity.withTenant(ConnectionSource.DEFAULT) {
            new TestEntity(name: "Homer").save(flush:true)
            new TestEntity(name: "Bart").save(flush:true)
        }

        expect:
        TestEntity.withTenant(ConnectionSource.DEFAULT) {
            TestEntity.count() == 2
            TestEntity.findByName("Homer") != null
            TestEntity.findByName("Bart") != null
            TestEntity.findByName("Lisa") == null
        }
    }

    void "test static property access for connection sources"() {
        expect:
        TestEntity.DEFAULT instanceof GormStaticOperations
    }

    void "test multi-tenant switching with stateless API"() {
        given:
        datastore.addTenantForSchema("tenant1")
        datastore.addTenantForSchema("tenant2")

        when:
        TestEntity.withTenant("tenant1") {
            new TestEntity(name: "Homer").save(flush:true)
        }
        TestEntity.withTenant("tenant2") {
            new TestEntity(name: "Bart").save(flush:true)
        }

        then:
        TestEntity.withTenant("tenant1") { TestEntity.count() } == 1
        TestEntity.withTenant("tenant2") { TestEntity.count() } == 1
        TestEntity.withTenant("tenant1") { TestEntity.findByName("Homer") != null }
        TestEntity.withTenant("tenant2") { TestEntity.findByName("Bart") != null }
        TestEntity.withTenant("tenant1") { TestEntity.findByName("Bart") == null }
        TestEntity.withTenant("tenant2") { TestEntity.findByName("Homer") == null }
    }
}

@Entity
class TestEntity implements MultiTenant<TestEntity> {
    String name
}
