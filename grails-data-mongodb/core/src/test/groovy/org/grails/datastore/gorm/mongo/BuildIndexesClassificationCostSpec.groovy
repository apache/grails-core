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

import java.util.concurrent.atomic.AtomicInteger

import ch.qos.logback.classic.Level
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * Telling a created index from one that was already there costs a {@code listIndexes} per collection that
 * declares indexes, and the only thing it feeds is the summary line. It is paid once per collection
 * however many domain classes map to it, and not at all when the summary is not logged.
 */
class BuildIndexesClassificationCostSpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'classificationCostDb'

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    MongoClient countingClient

    @Shared
    AtomicInteger listings = new AtomicInteger()

    @Shared
    int startupListings

    @Shared
    String startupSummary

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        countingClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(dbContainer.getReplicaSetUrl(DATABASE)))
                .addCommandListener(new CommandListener() {
                    @Override
                    void commandStarted(CommandStartedEvent event) {
                        if (event.commandName == 'listIndexes') {
                            listings.incrementAndGet()
                        }
                    }
                })
                .build())

        def log = new CapturedLog('org.grails.datastore.mapping.mongo', Level.INFO)
        datastore = new MongoDatastore(countingClient,
                DatastoreUtils.createPropertyResolver(['grails.mongodb.databaseName': DATABASE]),
                ClassifiedThing, FirstSharedCollectionThing, SecondSharedCollectionThing)
        startupListings = listings.get()
        startupSummary = log.events*.formattedMessage.find { it.contains("database [$DATABASE]") }
        log.close()
    }

    void cleanupSpec() {
        countingClient?.close()
    }

    void "test a collection shared by several domain classes is listed once"() {
        expect: "one listing for each of the two collections, not one for each of the three classes"
        startupListings == 2
    }

    void "test classes sharing a collection see each other's indexes"() {
        expect: "the second class declaring the keys the first has just created finds them already there"
        startupSummary.contains('2 created, 1 already present')
    }

    void "test the existing indexes are not listed when the summary will not be logged"() {
        given:
        int before = listings.get()
        def quiet = new CapturedLog('org.grails.datastore.mapping.mongo', Level.WARN)

        when:
        datastore.buildIndex()

        then:
        listings.get() == before

        cleanup:
        quiet?.close()
    }

    void "test an index applied outside a build is logged as applied, without being classified"() {
        given:
        int before = listings.get()
        def debug = new CapturedLog('org.grails.datastore.mapping.mongo', Level.DEBUG)

        when: "a domain class is registered after startup, when no summary will report the counts"
        datastore.persistentEntityAdded(datastore.mappingContext.getPersistentEntity(ClassifiedThing.name))

        then: "nothing is listed, and the per-index line does not claim the index was created"
        listings.get() == before
        debug.events*.formattedMessage.any { it.startsWith("Applied index for entity [${ClassifiedThing.name}]") }
        !debug.events*.formattedMessage.any { it.startsWith("Created index for entity [${ClassifiedThing.name}]") }

        cleanup:
        debug?.close()
    }
}

@Entity
class ClassifiedThing {
    String name

    static mapping = {
        version false
        collection 'classifiedThing'
        name index: true
    }
}

@Entity
class FirstSharedCollectionThing {
    String code

    static mapping = {
        version false
        collection 'sharedCollection'
        code index: true
    }
}

@Entity
class SecondSharedCollectionThing {
    String code

    static mapping = {
        version false
        collection 'sharedCollection'
        code index: true
    }
}
