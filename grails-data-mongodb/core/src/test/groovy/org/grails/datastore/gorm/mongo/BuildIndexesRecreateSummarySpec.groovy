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
 * A declaration that {@code recreateOnConflict} satisfies by dropping the index that was there and building
 * the declared one costs a full build. The summary says so, instead of counting it with the indexes that
 * were already present, which is what a restart that changed nothing reports.
 */
class BuildIndexesRecreateSummarySpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'recreateSummaryDb'

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
        // A text index on another field. MongoDB allows one text index per collection, so the declaration
        // conflicts with it, and recreateOnConflict replaces it.
        setupClient.getDatabase(DATABASE).getCollection('recreatedThing').createIndex(new Document('title', 'text'))

        log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        datastore = new MongoDatastore(['grails.mongodb.url': url] as Map, RecreatedThing)
    }

    void cleanupSpec() {
        log?.close()
        setupClient?.close()
    }

    void "test an index dropped and built again is reported as recreated, not as already present"() {
        given:
        String summary = log.events*.formattedMessage.find { it.contains("database [$DATABASE]") }

        expect:
        summary.contains('0 created, 1 recreated, 0 already present')

        and: "the text index is now the one declared"
        setupClient.getDatabase(DATABASE).getCollection('recreatedThing').listIndexes()
                .find { it.key == [_fts: 'text', _ftsx: 1] }.weights == [body: 1]
    }
}

@Entity
class RecreatedThing {
    String title
    String body

    static mapping = {
        version false
        collection 'recreatedThing'
        body index: true, indexAttributes: [type: 'text', recreateOnConflict: true]
    }
}
