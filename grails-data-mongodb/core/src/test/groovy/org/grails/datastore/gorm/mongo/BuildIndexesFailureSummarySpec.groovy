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
import ch.qos.logback.classic.spi.ILoggingEvent
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import org.bson.Document
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * An index the server refuses, whether by a command error or by a duplicate key, is not allowed to stop the
 * application from starting: the failure is reported and the rest of the build carries on. The summary then
 * goes out at {@code WARN} and says how many declarations failed, so a build that half worked cannot pass for
 * a clean one.
 */
class BuildIndexesFailureSummarySpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'failedIndexDb'

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
        def database = setupClient.getDatabase(DATABASE)

        // An index on the same keys as the mapping declares, but without the unique option it asks for.
        // MongoDB reports IndexOptionsConflict for the declaration, and without recreateOnConflict GORM
        // is not authorised to drop the existing one.
        database.getCollection('conflictingThing').createIndex(new Document('name', 1))

        database.getCollection('rejectedThing').insertOne(new Document('code', 'first'))

        // Documents that share the value a declared unique index is on, which the driver reports as a
        // DuplicateKeyException rather than a command error.
        database.getCollection('duplicatedThing').insertMany([new Document('code', 'same'), new Document('code', 'same')])

        // The same, where recreateOnConflict has the existing non-unique index dropped before the unique one fails.
        def recreated = database.getCollection('recreatedOverDuplicatesThing')
        recreated.insertMany([new Document('code', 'same'), new Document('code', 'same')])
        recreated.createIndex(new Document('code', 1))

        log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)

        datastore = new MongoDatastore(['grails.mongodb.url': url] as Map, ConflictingThing, RejectedThing,
                DuplicatedThing, RecreatedOverDuplicatesThing)
    }

    void cleanupSpec() {
        log?.close()
        setupClient?.close()
    }

    private List<ILoggingEvent> eventsForThisDatabase() {
        log.events.findAll { it.formattedMessage.contains("database [$DATABASE]") }
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

        and: "a unique index over duplicate values was not built, and the class's other declaration still was"
        DuplicatedThing.withNewSession {
            DuplicatedThing.collection.listIndexes()*.key as Set == [[_id: 1], [label: 1]] as Set
        }

        and: "recreating an index as unique over duplicate values dropped the old one and could not build the new"
        RecreatedOverDuplicatesThing.withNewSession {
            RecreatedOverDuplicatesThing.collection.listIndexes()*.key == [[_id: 1]]
        }
    }

    void "test the summary is logged at warn and reports how many declarations failed"() {
        given:
        def summary = eventsForThisDatabase().first()

        expect: "one summary, raised to WARN because not everything was applied"
        eventsForThisDatabase().size() == 1
        summary.level == Level.WARN

        and: "every failure is counted: the unresolved conflict, the declaration the server refused, and the two unique indexes over duplicate values"
        summary.formattedMessage.contains('4 failed')

        and: "the failures themselves were reported individually, naming the entity each came from"
        log.events.any { it.level == Level.ERROR && it.formattedMessage.contains('ConflictingThing') }
        log.events.any { it.level == Level.ERROR && it.formattedMessage.contains('RejectedThing') }
        log.events.any {
            it.level == Level.ERROR && it.formattedMessage.contains('DuplicatedThing') &&
                    it.formattedMessage.contains('E11000')
        }

        and: "the recreate that dropped the old index says so"
        log.events.any {
            it.level == Level.ERROR && it.formattedMessage.startsWith('Dropped index [code_1] on entity [') &&
                    it.formattedMessage.contains('RecreatedOverDuplicatesThing') &&
                    it.formattedMessage.contains('but it could not be built again')
        }
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

@Entity
class DuplicatedThing {
    String code
    String label

    static mapping = {
        version false
        collection 'duplicatedThing'
        code index: true, indexAttributes: [unique: true]
        label index: true
    }
}

@Entity
class RecreatedOverDuplicatesThing {
    String code

    static mapping = {
        version false
        collection 'recreatedOverDuplicatesThing'
        code index: true, indexAttributes: [unique: true, recreateOnConflict: true]
    }
}
