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

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import ch.qos.logback.classic.Level
import com.mongodb.client.ListIndexesIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.IndexOptions
import grails.gorm.annotation.Entity
import org.bson.Document
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * The build lists a collection's indexes once, before creating any, to tell a created index from one that
 * was already there. An index that appears after that listing - created by another instance starting at
 * the same time, or another connection building concurrently - is what the server then reports as
 * conflicting, so reconciling the conflict has to look at the server again rather than at the listing.
 */
class BuildIndexesStaleSnapshotSpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'staleSnapshotDb'

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    MongoClient realClient

    @Shared
    CapturedLog log

    @Shared
    AtomicInteger listings = new AtomicInteger()

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    private static ListIndexesIterable<Document> noIndexes() {
        (ListIndexesIterable<Document>) Proxy.newProxyInstance(ListIndexesIterable.classLoader,
                [ListIndexesIterable] as Class<?>[], { Object proxy, Method method, Object[] args ->
            if (method.name == 'into') {
                return args[0]
            }
            throw new UnsupportedOperationException(method.name)
        } as InvocationHandler)
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl(DATABASE))
        // Already on the server with a different expiry, as another instance would have left it
        realClient.getDatabase(DATABASE).getCollection('staleSnapshotThing')
                .createIndex(new Document('created', 1), new IndexOptions().expireAfter(999L, TimeUnit.SECONDS))

        // The first listing predates that index; every later one is the server's answer
        MongoClient staleFirstListing = FailingMongoClient.wrap(realClient, 'listIndexes') { Closure proceed ->
            listings.incrementAndGet() == 1 ? noIndexes() : proceed()
        }

        // Classification only lists when the summary will be logged
        log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        datastore = new MongoDatastore(staleFirstListing,
                DatastoreUtils.createPropertyResolver(['grails.mongodb.databaseName': DATABASE]), StaleSnapshotThing)
    }

    void cleanupSpec() {
        log?.close()
        realClient?.close()
    }

    void "test a conflict is reconciled against the server, not the listing taken before it"() {
        expect: "the stale listing was used, and the conflict listed again"
        listings.get() == 2

        and: "the index that appeared afterwards was reconciled in place to the declared expiry"
        realClient.getDatabase(DATABASE).getCollection('staleSnapshotThing').listIndexes()
                .find { it.key == [created: 1] }.expireAfterSeconds == 100

        and: "it is reported as an index that was already there, with nothing failed"
        log.events*.formattedMessage.find { it.contains("database [$DATABASE]") }.contains('0 created, 1 already present')
        !log.events.any { it.level == Level.ERROR && it.formattedMessage.contains('StaleSnapshotThing') }
    }
}

@Entity
class StaleSnapshotThing {
    Date created

    static mapping = {
        version false
        collection 'staleSnapshotThing'
        created index: true, indexAttributes: [expireAfterSeconds: 100]
    }
}
