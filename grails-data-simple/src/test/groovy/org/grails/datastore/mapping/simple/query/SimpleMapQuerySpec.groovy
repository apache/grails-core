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

package org.grails.datastore.mapping.simple.query

import org.grails.datastore.gorm.multitenancy.MultiTenantEventListener
import grails.gorm.MultiTenant
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.simple.SimpleMapSession
import spock.lang.Specification
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.springframework.context.support.GenericApplicationContext
import groovy.transform.CompileStatic

class SimpleMapQuerySpec extends Specification {

    def "test getBackingMap in DISCRIMINATOR mode"() {
        given: "A datastore in DISCRIMINATOR mode"
        Map config = [
            'grails.gorm.multiTenancy.mode': MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        ]
        SimpleMapDatastore datastore = new SimpleMapDatastore(config, [TestEntity] as Class[])
        
        // Ensure mode is set on context
        datastore.mappingContext.setMultiTenancyMode(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR)
        PersistentEntity pe = datastore.mappingContext.addPersistentEntity(TestEntity)
        
        def settings = datastore.connectionSources.defaultConnectionSource.settings
        new GormEnhancer(datastore, datastore.transactionManager, settings).with {
            registerEntity(pe)
        }
        SimpleMapSession session = (SimpleMapSession) datastore.connect()

        when: "We get the backing map from the session"
        def backingMap = session.getBackingMap()

        then: "It should be the global ConcurrentHashMap, not a ScopedMap"
        backingMap.getClass().simpleName == 'ConcurrentHashMap'

        when: "We are inside a withTenant block"
        def mapInsideTenant = Tenants.withId("tenant2") {
            session.getBackingMap()
        }

        then: "It should still be the global ConcurrentHashMap in DISCRIMINATOR mode"
        mapInsideTenant.getClass().simpleName == 'ConcurrentHashMap'
        
        cleanup:
        datastore.close()
    }

    def "test query isolation in DISCRIMINATOR mode"() {
        given: "A datastore in DISCRIMINATOR mode"
        Map config = [
            'grails.gorm.multiTenancy.mode': MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        ]
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        
        SimpleMapDatastore datastore = new SimpleMapDatastore(config, [TestEntity] as Class[])
        datastore.applicationContext = ctx
        
        // IMPORTANT: Set mode and ensure entity is initialized
        datastore.mappingContext.setMultiTenancyMode(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR)
        PersistentEntity pe = datastore.mappingContext.getPersistentEntity(TestEntity.name)
        
        // Register the multi-tenancy listener explicitly in the context
        MultiTenantEventListener listener = new MultiTenantEventListener(datastore) {
            @Override
            public boolean supportsSourceType(Class<?> sourceType) {
                return true // Accept events from any datastore
            }
        }
        ctx.addApplicationListener(listener)
        
        def settings = datastore.connectionSources.defaultConnectionSource.settings
        new GormEnhancer(datastore, datastore.transactionManager, settings).with {
            registerEntity(pe)
        }

        when: "We save entities for different tenants"
        Tenants.withId("T1") {
            new TestEntity(name: "Book1").save(flush:true)
        }
        Tenants.withId("T2") {
            new TestEntity(name: "Book2").save(flush:true)
            new TestEntity(name: "Book3").save(flush:true)
        }

        then: "Global count is 3"
        datastore.sharedState.inmemoryData[TestEntity.name].size() == 3
        
        when: "We query for T1"
        int countT1 = (int)Tenants.withId("T1") {
            TestEntity.count()
        }

        then: "We only see 1 result"
        countT1 == 1

        when: "We query for T2"
        int countT2 = (int)Tenants.withId("T2") {
            TestEntity.count()
        }

        then: "We see 2 results"
        countT2 == 2

        cleanup:
        datastore.close()
        ctx.close()
    }
}

@Entity
class TestEntity implements GormEntity<TestEntity>, MultiTenant<TestEntity> {
    Long id
    Long version
    String name
    String tenantId

    static mapping = {
        multiTenancy strategy: 'DISCRIMINATOR'
    }
}
