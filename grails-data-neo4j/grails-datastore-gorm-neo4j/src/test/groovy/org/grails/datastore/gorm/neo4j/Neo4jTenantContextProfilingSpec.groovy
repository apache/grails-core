/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.neo4j

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.multitenancy.TenantDelegatingGormOperations
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.gorm.neo4j.api.Neo4jGormStaticApi
import spock.lang.Specification

class Neo4jTenantContextProfilingSpec extends Specification {

    void setup() {
        GormRegistry.instance.reset()
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    void "profile neo4j tenant wrapping overhead"() {
        given:
        def mappingContext = Stub(MappingContext)
        def datastore = Stub(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getMappingContext() >> mappingContext
            getDatastoreForTenantId(_) >> { return it[0] == null ? delegate : delegate }
        }
        
        def registry = GormRegistry.instance
        registry.registerDatastore("default", datastore)
        
        def staticApi = new DummyNeo4jStaticApi(TenantEntity, datastore)
        def ops = new TenantDelegatingGormOperations<TenantEntity>((Datastore) datastore, "tenant1", staticApi)
        def qualifiedApi = staticApi.forQualifier("tenant1")
        
        int iterations = 1000

        when: "Calling operations repeatedly via TenantDelegatingGormOperations (wrapped every time)"
        long startWrapped = System.currentTimeMillis()
        for (int i = 0; i < iterations; i++) {
            ops.exists(1L)
        }
        long endWrapped = System.currentTimeMillis()

        and: "Calling operations via qualified API (unwrapped, but pre-bound)"
        long startQualified = System.currentTimeMillis()
        for (int i = 0; i < iterations; i++) {
            qualifiedApi.exists(1L)
        }
        long endQualified = System.currentTimeMillis()

        and: "Calling operations via closure block (wrapped once)"
        long startBlock = System.currentTimeMillis()
        Tenants.withId((MultiTenantCapableDatastore) datastore, "tenant1") {
            for (int i = 0; i < iterations; i++) {
                staticApi.exists(1L)
            }
        }
        long endBlock = System.currentTimeMillis()

        then:
        println "Neo4j Single block wrapped operations: ${endBlock - startBlock} ms"
        println "Neo4j Qualified API operations: ${endQualified - startQualified} ms"
        println "Neo4j Per-method wrapped operations: ${endWrapped - startWrapped} ms"
        
        true
    }

    static class TenantEntity implements MultiTenant<TenantEntity> {
        Long id
    }

    static class DummyNeo4jStaticApi extends Neo4jGormStaticApi<TenantEntity> {
        DummyNeo4jStaticApi(Class<TenantEntity> persistentClass, MultiTenantCapableDatastore datastore) {
            super(persistentClass, (Neo4jDatastore) datastore, [], new org.grails.datastore.gorm.DatastoreResolver() {
                @Override org.grails.datastore.mapping.core.Datastore resolve() { return (Datastore) datastore }
            })
        }

        @Override
        boolean exists(Serializable id) {
            return true
        }

        @Override
        org.grails.datastore.gorm.GormStaticApi<TenantEntity> forQualifier(String qualifier) {
            return this
        }
    }
}
