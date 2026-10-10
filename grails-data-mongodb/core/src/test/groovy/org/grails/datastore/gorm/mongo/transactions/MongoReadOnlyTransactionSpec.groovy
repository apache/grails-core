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
package org.grails.datastore.gorm.mongo.transactions

import grails.gorm.annotation.Entity

import org.apache.grails.testing.mongo.EmbeddedReplicaSetSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * A read-only transaction reads without a server transaction, so it honours the client's read
 * preference: the driver refuses any preference other than primary inside a transaction.
 */
class MongoReadOnlyTransactionSpec extends EmbeddedReplicaSetSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    void setupSpec() {
        String url = mongoUrl + (mongoUrl.contains('?') ? '&' : '?') + 'readPreference=secondaryPreferred'
        datastore = new MongoDatastore(['grails.mongodb.url': url, 'grails.mongodb.transactional': true] as Map,
                ReadOnlyThing)
    }

    void setup() {
        ReadOnlyThing.withNewSession {
            ReadOnlyThing.DB.drop()
            new ReadOnlyThing(name: "stored").save(flush: true)
        }
    }

    void "a read-only transaction reads through a client that prefers secondaries"() {
        expect:
        ReadOnlyThing.withTransaction([readOnly: true]) { ReadOnlyThing.count() } == 1
    }
}

@Entity
class ReadOnlyThing {
    String name
}
