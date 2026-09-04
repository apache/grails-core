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
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import org.bson.Document
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * An index the server refuses is not allowed to stop the application from starting: the failure is
 * reported and the rest of the build carries on. The summary then goes out at {@code WARN} and says how
 * many declarations failed, so a build that half worked cannot pass for a clean one.
 */
class BuildIndexesFailureSummarySpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'failedIndexDb'

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    MongoClient setupClient

    @Shared
    Logger datastoreLogger

    @Shared
    ListAppender<ILoggingEvent> logged = new ListAppender<>()

    @Shared
    Level previousLevel

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        String url = dbContainer.getReplicaSetUrl(DATABASE)
        setupClient = MongoClients.create(url)
        def database = setupClient.getDatabase(DATABASE)

        // An index on the same keys as the mapping declares, but without the unique option it asks for.
        // MongoDB reports IndexOptionsConflict for the declaration, and without recreateOnConflict GORM
        // is not authorised to drop the existing one.
        database.getCollection('conflictingThing').createIndex(new Document('name', 1))

        database.getCollection('rejectedThing').insertOne(new Document('code', 'first'))

        datastoreLogger = LoggerFactory.getLogger(AbstractDatastore) as Logger
        previousLevel = datastoreLogger.level
        datastoreLogger.level = Level.INFO
        logged.start()
        datastoreLogger.addAppender(logged)

        datastore = new MongoDatastore(['grails.mongodb.url': url] as Map, ConflictingThing, RejectedThing)
    }

    void cleanupSpec() {
        datastoreLogger?.detachAppender(logged)
        datastoreLogger?.level = previousLevel
        setupClient?.close()
    }

    private List<ILoggingEvent> eventsForThisDatabase() {
        logged.list.findAll { it.formattedMessage.contains("database [$DATABASE]") }
    }

    void "test an index declaration the server refuses does not stop the datastore from starting"() {
        expect: "the datastore is usable"
        RejectedThing.withNewSession { RejectedThing.count() } == 1

        and: "the conflicting declaration left the index that was already there untouched"
        ConflictingThing.withNewSession {
            ConflictingThing.collection.listIndexes().find { it.key == [name: 1] }.unique == null
        }

        and: "and the declaration the server refused outright created nothing"
        RejectedThing.withNewSession {
            RejectedThing.collection.listIndexes()*.key == [[_id: 1]]
        }
    }

    void "test the summary is logged at warn and reports how many declarations failed"() {
        given:
        def summary = eventsForThisDatabase().first()

        expect: "one summary, raised to WARN because not everything was applied"
        eventsForThisDatabase().size() == 1
        summary.level == Level.WARN

        and: "both failures are counted: the unresolved conflict and the declaration the server refused"
        summary.formattedMessage.contains('2 failed')

        and: "the failures themselves were reported individually, naming the entity each came from"
        logged.list.any { it.level == Level.ERROR && it.formattedMessage.contains('ConflictingThing') }
        logged.list.any { it.level == Level.ERROR && it.formattedMessage.contains('RejectedThing') }
    }
}

@Entity
class ConflictingThing {
    String name

    static mapping = {
        version false
        collection 'conflictingThing'
        name index: true, indexAttributes: [unique: true]
    }
}

@Entity
class RejectedThing {
    String code

    static mapping = {
        version false
        collection 'rejectedThing'
        code index: true, indexAttributes: [type: 'nosuchindexplugin']
    }
}
