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

import ch.qos.logback.classic.Level
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import org.bson.Document
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * The order of a compound index's keys is part of the index: MongoDB keeps {@code {a: 1, b: 1}} and
 * {@code {b: 1, a: 1}} as two indexes, and one cannot serve the other's sort. A declaration whose keys
 * come in a different order from an existing index is therefore a new index, and the summary has to say
 * it was created.
 */
class BuildIndexesKeyOrderSpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'keyOrderDb'

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    MongoClient setupClient

    @Shared
    CapturedLog log

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        String url = dbContainer.getReplicaSetUrl(DATABASE)
        setupClient = MongoClients.create(url)
        setupClient.getDatabase(DATABASE).getCollection('keyOrderThing').createIndex(new Document('a', 1).append('b', 1))

        log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        datastore = new MongoDatastore(['grails.mongodb.url': url] as Map, KeyOrderThing)
    }

    void cleanupSpec() {
        log?.close()
        setupClient?.close()
    }

    void "test a compound index declared in a different key order is created, not taken for the existing one"() {
        expect: "both orders now exist on the server, as two indexes"
        def keys = setupClient.getDatabase(DATABASE).getCollection('keyOrderThing').listIndexes()*.key*.keySet()*.toList()
        ['a', 'b'] in keys
        ['b', 'a'] in keys

        and: "the summary counts the reversed declaration as created and the matching one as already present"
        log.events*.formattedMessage.find { it.contains("database [$DATABASE]") }.contains('1 created, 1 already present')
    }
}

@Entity
class KeyOrderThing {
    Integer a
    Integer b

    static mapping = {
        version false
        collection 'keyOrderThing'
        compoundIndex b: 1, a: 1
        compoundIndex a: 1, b: 1
    }
}
