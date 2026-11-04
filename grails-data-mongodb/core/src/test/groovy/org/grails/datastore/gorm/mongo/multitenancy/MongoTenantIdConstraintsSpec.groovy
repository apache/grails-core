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
package org.grails.datastore.gorm.mongo.multitenancy

import org.bson.types.ObjectId
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver

/**
 * The constraints declared on a tenant id are checked through GORM for MongoDB, which validates an instance again as
 * it is inserted and cancels the insert when that fails, without an error. An associated object is therefore given
 * its tenant id before its owner is validated, so that a tenant id that breaks a constraint declared on it fails the
 * save of the owner instead.
 */
@Issue(['https://github.com/apache/grails-core/issues/14365', 'https://github.com/apache/grails-core/issues/16855'])
@RestoreSystemProperties
class MongoTenantIdConstraintsSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.gorm.multiTenancy.mode'               : 'DISCRIMINATOR',
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                (MongoSettings.SETTING_URL)                   : "mongodb://${mongoHost}:${mongoPort}/tenantIdConstraintsDb" as String,
        ]
        this.datastore = new MongoDatastore(config, TenantAccount, TenantOwner, TenantPet)
    }

    void setup() {
        TenantOwner.DB.drop()
    }

    void 'the constraints declared on a tenant id named tenantId are checked'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def account = new TenantAccount(name: 'Main')
        def saved = TenantAccount.withNewSession { account.save(flush: true) }

        then:
        saved
        account.tenantId == 'acme'
        TenantAccount.collection.countDocuments() == 1L

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'toolongtenant')
        account = new TenantAccount(name: 'Second')
        saved = TenantAccount.withNewSession { account.save(flush: true) }

        then:
        !saved
        account.errors.getFieldError('tenantId').code == 'maxSize.exceeded'
        TenantAccount.collection.countDocuments() == 1L
    }

    void 'a new associated object is saved with its owner for the current tenant'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def owner = new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex'))
        def saved = TenantOwner.withNewSession { owner.save(flush: true) }

        then:
        saved
        !owner.hasErrors()
        owner.pets*.firmId == ['acme']
        storedCounts() == [owners: 1L, pets: 1L]
    }

    void 'saving an owner fails when the current tenant id breaks a constraint declared on the tenant id of a new associated object'() {
        given: 'a current tenant id longer than the maxSize of the tenant id of a pet'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'toolongtenant')

        when:
        def owner = new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex'))
        def saved = TenantOwner.withNewSession { owner.save(flush: true) }

        then: 'the owner reports the tenant id of the pet'
        !saved
        owner.errors.getFieldError('pets[0].firmId').code == 'maxSize.exceeded'

        and: 'neither is stored'
        storedCounts() == [owners: 0L, pets: 0L]
    }

    void 'saving an owner fails when the current tenant id replaces a tenant id of an associated object with one that breaks a constraint'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'toolongtenant')

        when: 'the pet has a tenant id that the current tenant id replaces as the pet is inserted'
        def owner = new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex', firmId: 'abc'))
        def saved = TenantOwner.withNewSession { owner.save(flush: true) }

        then:
        !saved
        owner.errors.getFieldError('pets[0].firmId').code == 'maxSize.exceeded'
        storedCounts() == [owners: 0L, pets: 0L]
    }

    void 'saving an owner inside withoutId fails when a new associated object is not given a tenant id'() {
        when:
        def owner = new TenantOwner(name: 'Dave', tenantId: 'acme').addToPets(new TenantPet(name: 'Rex'))
        def saved = Tenants.withoutId(datastore) { TenantOwner.withNewSession { owner.save(flush: true) } }

        then: 'the pet does not get the tenant id of a tenant, so the owner reports the tenant id of the pet'
        !saved
        owner.errors.hasFieldErrors('pets[0].firmId')
        storedCounts() == [owners: 0L, pets: 0L]
    }

    void 'an owner and a new associated object are saved inside withoutId with the tenant ids they are given'() {
        when:
        def owner = new TenantOwner(name: 'Dave', tenantId: 'acme').addToPets(new TenantPet(name: 'Rex', firmId: 'abc'))
        def saved = Tenants.withoutId(datastore) { TenantOwner.withNewSession { owner.save(flush: true) } }

        then:
        saved
        !owner.hasErrors()
        owner.tenantId == 'acme'
        owner.pets*.firmId == ['abc']
        storedCounts() == [owners: 1L, pets: 1L]
    }

    private static Map<String, Long> storedCounts() {
        [owners: TenantOwner.collection.countDocuments(), pets: TenantPet.collection.countDocuments()]
    }
}

@Entity
class TenantAccount implements MultiTenant<TenantAccount>, MongoEntity<TenantAccount> {
    ObjectId id
    String name
    String tenantId

    static constraints = {
        tenantId nullable: false, maxSize: 5
    }
}

@Entity
class TenantOwner implements MultiTenant<TenantOwner>, MongoEntity<TenantOwner> {
    ObjectId id
    String name
    String tenantId

    static hasMany = [pets: TenantPet]
}

@Entity
class TenantPet implements MultiTenant<TenantPet>, MongoEntity<TenantPet> {
    ObjectId id
    String name
    String firmId

    static belongsTo = [owner: TenantOwner]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}
