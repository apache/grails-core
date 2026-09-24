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

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import ch.qos.logback.classic.Level
import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.IndexOptions
import grails.gorm.annotation.Entity
import org.bson.Document
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * A collection whose indexes cannot be listed - on its first listing, or on the re-listing a conflict
 * makes - cannot say which of its indexes were new. That is a reason not to classify that collection, not
 * to discard what the other collections' listings established.
 */
class BuildIndexesPartialClassificationSpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'partialClassificationDb'

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    MongoClient realClient

    @Shared
    String summary

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl(DATABASE))
        // Conflicts with the declared expiry, so its collection is listed a second time to reconcile it
        realClient.getDatabase(DATABASE).getCollection('relistedThing')
                .createIndex(new Document('created', 1), new IndexOptions().expireAfter(999L, TimeUnit.SECONDS))

        def relistings = new AtomicInteger()
        MongoClient partlyUnlistable = FailingMongoClient.wrap(realClient, 'listIndexes') { Closure proceed, MongoCollection collection ->
            String name = collection.namespace.collectionName
            if (name == 'unlistedThing') {
                throw new MongoException('not authorized on partialClassificationDb to execute command listIndexes')
            }
            if (name == 'relistedThing' && relistings.incrementAndGet() > 1) {
                throw new MongoException('connection reset while re-listing')
            }
            proceed()
        }

        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        datastore = new MongoDatastore(partlyUnlistable,
                DatastoreUtils.createPropertyResolver(['grails.mongodb.databaseName': DATABASE]),
                ListedThing, UnlistedThing, RelistedThing)
        summary = log.events*.formattedMessage.find { it.contains("database [$DATABASE]") }
        log.close()
    }

    void cleanupSpec() {
        realClient?.close()
    }

    void "test a collection that could not be listed is left unclassified without discarding the others"() {
        expect: "the listed collection's index is still counted as created"
        summary.contains('1 created, 0 already present')

        and: "the collection whose listing was refused is applied without being classified"
        summary.contains('1 applied without a listing')

        and: "the conflict that could not be re-listed is a failure, as before"
        summary.contains('1 failed')
    }

    void "test the unlisted collection's index is still created"() {
        expect:
        [name: 1] in realClient.getDatabase(DATABASE).getCollection('unlistedThing').listIndexes()*.key
    }
}

@Entity
class ListedThing {
    String name

    static mapping = {
        version false
        collection 'listedThing'
        name index: true
    }
}

@Entity
class UnlistedThing {
    String name

    static mapping = {
        version false
        collection 'unlistedThing'
        name index: true
    }
}

@Entity
class RelistedThing {
    Date created

    static mapping = {
        version false
        collection 'relistedThing'
        created index: true, indexAttributes: [expireAfterSeconds: 100]
    }
}
