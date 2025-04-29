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
package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.mongodb.MongoEntity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.bson.types.ObjectId
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.Shared

/**
 * Created by graemerocher on 30/06/16.
 */
class MultipleConnectionsSpec extends AutoStartedMongoSpec {

    @Shared MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
            (MongoSettings.SETTING_URL)        : "mongodb://${mongoHost}:${mongoPort}/defaultDb" as String,
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

    void cleanupSpec() {
        datastore.close()
    }

    void "Test multiple datasources state"() {

        expect:
        CompanyA.DB.name == 'test1Db'
        CompanyA.test2.DB.name == 'test2Db'
    }

    void "Test query multiple data sources"() {
        setup:
        CompanyA.DB.drop()
        CompanyA.test2.DB.drop()

        when:"An entity is saved"
        new CompanyA(name:"One").save(flush:true)

        then:"The results are correct"
        CompanyA.count() == 1
        CompanyA.withConnection("test2") { count() } == 0

        when:"An entity is saved to another connection"
        new CompanyA(name:"Two").save(flush:true)
        CompanyA.withConnection("test2") {
            save(new CompanyA(name: "Three"), [flush:true])
        }

        then:"The results are correct"
        CompanyA.count() == 2
        CompanyA.first()
        CompanyA.withConnection("test2") { count() == 1 }
    }

    List getDomainClasses() {
        [CompanyA]
    }
}

/**
 * Created by graemerocher on 30/06/16.
 */
@Entity
class CompanyA implements MongoEntity<CompanyA> {
    ObjectId id
    String name
    static mapping = {
        connections "test1", "test2"
    }
}

