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

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.mongodb.MongoEntity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.bson.types.ObjectId
import org.grails.datastore.gorm.mongo.City
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import spock.lang.AutoCleanup
import spock.lang.Shared

import static com.mongodb.client.model.Filters.eq

/**
 * Created by graemerocher on 07/07/2016.
 */
class SingleTenancySpec extends AutoStartedMongoSpec {

    @Shared @AutoCleanup MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                "grails.gorm.multiTenancy.mode":"DATABASE",
                "grails.gorm.multiTenancy.tenantResolverClass":SystemPropertyTenantResolver,
                (MongoSettings.SETTING_URL): "mongodb://${mongoHost}:${mongoPort}/defaultDb" as String,
                (MongoSettings.SETTING_CONNECTIONS): [
                        test1: [
                                url: "mongodb://${mongoHost}:${mongoPort}/test1Db" as String
                        ],
                        test2: [
                                url: "mongodb://${mongoHost}:${mongoPort}/test2Db" as String
                        ]
                ]
        ]
        this.datastore = new MongoDatastore(config, getDomainClasses() as Class[])
    }

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    void "Test no tenant id"() {
        when:
        CompanyB.DB

        then:
        thrown(TenantNotFoundException)
    }

    void "Test multi tenancy state"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test1")
        expect:
        City.DB.name == "defaultDb"
        CompanyB.DB.name == 'test1Db'
    }

    void "Test persist and retrieve entities with multi tenancy"() {
        setup:
        CompanyB.eachTenant {
            CompanyB.DB.drop()
        }

        when:"A tenant id is present"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test1")

        then:"the correct tenant is used"
        CompanyB.count() == 0
        CompanyB.DB.name == 'test1Db'

        when:"An object is saved"
        new CompanyB(name: "Foo").save(flush:true)

        then:"The results are correct"
        CompanyB.count() == 1

        when:"An object is updated"
        CompanyB cb = CompanyB.findByName("Foo")
        cb.name = "Bar"
        cb.save(flush:true)

        then:
        !CompanyB.findByName("Foo")
        CompanyB.findByName("Bar")?.version == 1

        when:"The tenant id is switched"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test2")

        then:"the correct tenant is used"
        CompanyB.DB.name == 'test2Db'
        CompanyB.count() == 0
        !CompanyB.find(eq("name", "Foo")).first()
        !CompanyB.find(eq("name", "Bar")).first()

        CompanyB.withTenant("test1") { Serializable tenantId, Session s ->
            assert tenantId
            assert s
            CompanyB.count() == 1
        }

        when:"each tenant is iterated over"
        Map tenantIds = [:]
        CompanyB.eachTenant { String tenantId ->
            tenantIds.put(tenantId, CompanyB.count())
        }

        then:"The result is correct"
        tenantIds == [test1:1, test2:0]
    }

    void "Test tenant mapped to all"() {
        setup:
        CompanyD.eachTenant {
            CompanyD.DB.drop()
        }

        when:"each tenant is iterated over"
        Map tenantIds = [:]
        CompanyB.eachTenant { String tenantId ->
            tenantIds.put(tenantId, CompanyB.count())
        }

        then:"The result is correct"
        tenantIds == [test1:0, test2:0]

    }

    List getDomainClasses() {
        [City, CompanyB, CompanyD]
    }

}

@Entity
class CompanyB implements MongoEntity<CompanyB>, MultiTenant {
    ObjectId id
    String name
}

@Entity
class CompanyD implements MongoEntity<CompanyC>, MultiTenant {
    ObjectId id
    String name

    static mapping = {
        connections ConnectionSource.ALL
    }
}