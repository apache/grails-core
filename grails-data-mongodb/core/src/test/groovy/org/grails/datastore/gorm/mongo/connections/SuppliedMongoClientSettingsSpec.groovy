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
package org.grails.datastore.gorm.mongo.connections

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode

/**
 * An application that hands GORM an existing {@code MongoClient} - which is what happens whenever a
 * {@code MongoClient} bean is already present, as with Spring Boot's MongoDB auto-configuration - must
 * still have its {@code grails.mongodb} settings applied. Only the connection details are taken from the
 * supplied client; everything describing how the datastore behaves still comes from the configuration.
 */
class SuppliedMongoClientSettingsSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    MongoClient mongoClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        mongoClient = MongoClients.create(dbContainer.getReplicaSetUrl('suppliedClientDb'))
        Map config = [
                'grails.mongodb.databaseName'        : 'suppliedClientDb',
                (MongoSettings.SETTING_BUILD_INDEXES): false,
                'grails.mongodb.transactional'       : true
        ]
        datastore = new MongoDatastore(mongoClient, DatastoreUtils.createPropertyResolver(config), SuppliedClientThing)
    }

    void cleanupSpec() {
        mongoClient?.close()
    }

    void "test the configured settings are applied to a datastore built on a supplied client"() {
        expect: "the index setting is taken from the configuration rather than left at its default"
        !datastore.isBuildIndexes()

        and: "so is any other datastore setting"
        datastore.isTransactionsEnabled()
    }

    void "test a supplied client applies #prefix multi-tenancy mode #mode"() {
        given:
        def configuration = DatastoreUtils.createPropertyResolver([
                'grails.mongodb.databaseName': 'suppliedTenantDb',
                ("${prefix}.multiTenancy.mode".toString()): mode.name()
        ])

        when:
        def tenantDatastore = new MongoDatastore(mongoClient, configuration, new Class[0])

        then: "both the datastore and the settings used by GORM static APIs agree"
        tenantDatastore.multiTenancyMode == mode
        tenantDatastore.connectionSources.defaultConnectionSource.settings.multiTenancy.mode == mode

        cleanup:
        tenantDatastore?.close()

        where:
        [prefix, mode] << [['grails.gorm', 'grails.mongodb'], MultiTenancyMode.values().toList()].combinations()
    }

    void "test MongoDB tenancy settings override the global GORM fallback for a supplied client"() {
        when:
        def tenantDatastore = new MongoDatastore(mongoClient, DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode': 'DISCRIMINATOR',
                'grails.mongodb.multiTenancy.mode': 'NONE'
        ]), new Class[0])

        then:
        tenantDatastore.multiTenancyMode == MultiTenancyMode.NONE
        tenantDatastore.connectionSources.defaultConnectionSource.settings.multiTenancy.mode == MultiTenancyMode.NONE

        cleanup:
        tenantDatastore?.close()
    }

    void "test configured tenant discrimination isolates data with a supplied client"() {
        given:
        def tenantDatastore = new MongoDatastore(mongoClient, DatastoreUtils.createPropertyResolver([
                'grails.mongodb.databaseName': 'suppliedTenantIsolationDb',
                'grails.gorm.multiTenancy.mode': 'DISCRIMINATOR'
        ]), SuppliedTenantThing)

        when:
        SuppliedTenantThing.withTenant('first') {
            new SuppliedTenantThing(name: 'First tenant').save(flush: true, failOnError: true)
        }
        SuppliedTenantThing.withTenant('second') {
            new SuppliedTenantThing(name: 'Second tenant').save(flush: true, failOnError: true)
        }

        then:
        SuppliedTenantThing.withTenant('first') { SuppliedTenantThing.list()*.name } == ['First tenant']
        SuppliedTenantThing.withTenant('second') { SuppliedTenantThing.list()*.name } == ['Second tenant']

        cleanup:
        tenantDatastore?.close()
    }

    void "test the configured settings take effect and not merely report"() {
        when: "a document is written so the collection certainly exists"
        SuppliedClientThing.withNewSession {
            new SuppliedClientThing(name: 'Fred').save(flush: true)
        }

        then: "the index declared in the mapping was not created, as configured"
        SuppliedClientThing.collection.listIndexes()*.key == [[_id: 1]]
    }
}

@Entity
class SuppliedClientThing {
    String name

    static mapping = {
        version false
        collection 'suppliedClientThing'
        name index: true
    }
}

@Entity
class SuppliedTenantThing implements MultiTenant<SuppliedTenantThing> {
    String tenantId
    String name
}
