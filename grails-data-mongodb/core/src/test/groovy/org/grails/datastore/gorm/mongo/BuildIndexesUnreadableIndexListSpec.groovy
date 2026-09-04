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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import com.mongodb.client.model.IndexOptions
import org.bson.Document
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * Telling an index this build created from one that was already there means reading the indexes the
 * collection already has, which a role holding {@code createIndex} but not {@code listIndexes} is not
 * allowed to do. Losing that breakdown must not cost the indexes themselves: the build goes ahead and the
 * summary says only how many declarations it applied.
 */
class BuildIndexesUnreadableIndexListSpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'unlistableIndexDb'

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    MongoClient realClient

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
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl(DATABASE))
        // A TTL index whose expiry differs from the one the mapping declares. MongoDB reports that as an
        // IndexOptionsConflict, which GORM reconciles in place - but reconciling means reading the indexes
        // that are there, which this role is not allowed to do.
        realClient.getDatabase(DATABASE).getCollection('unlistableConflictThing')
                .createIndex(new Document('created', 1), new IndexOptions().expireAfter(999L, TimeUnit.SECONDS))

        datastoreLogger = LoggerFactory.getLogger(AbstractDatastore) as Logger
        previousLevel = datastoreLogger.level
        datastoreLogger.level = Level.INFO
        logged.start()
        datastoreLogger.addAppender(logged)

        MongoClient cannotList = FailingMongoClient.wrap(realClient, 'listIndexes') {
            throw new MongoException('not authorized on unlistableIndexDb to execute command listIndexes')
        }
        datastore = new MongoDatastore(cannotList,
                DatastoreUtils.createPropertyResolver(['grails.mongodb.databaseName': DATABASE]),
                UnlistableIndexThing, UnlistableConflictThing)
    }

    void cleanupSpec() {
        datastoreLogger?.detachAppender(logged)
        datastoreLogger?.level = previousLevel
        realClient?.close()
    }

    void "test the declared indexes are still created when the existing ones cannot be listed"() {
        expect:
        realClient.getDatabase(DATABASE).getCollection('unlistableIndexThing').listIndexes()*.key.contains([name: 1])
    }

    void "test the summary falls back to reporting how many declarations were applied"() {
        given:
        def summary = logged.list.find { it.formattedMessage.contains("database [$DATABASE]") }

        expect: "the build reported itself"
        summary != null

        and: "without claiming which indexes it created, because that could not be established"
        summary.formattedMessage.contains('1 index declaration(s) applied')
        !summary.formattedMessage.contains('created')

        and: "the declaration that conflicted is counted as failed, since it could not be reconciled blind"
        summary.formattedMessage.contains('1 failed')
    }

    void "test a conflict that cannot be reconciled without the index list is reported, not swallowed"() {
        expect:
        logged.list.any {
            it.level == Level.ERROR &&
                    it.formattedMessage.contains('could not inspect existing indexes') &&
                    it.formattedMessage.contains('UnlistableConflictThing')
        }
    }
}

@Entity
class UnlistableConflictThing {
    Date created

    static mapping = {
        version false
        collection 'unlistableConflictThing'
        created index: true, indexAttributes: [expireAfterSeconds: 100]
    }
}

@Entity
class UnlistableIndexThing {
    String name

    static mapping = {
        version false
        collection 'unlistableIndexThing'
        name index: true
    }
}
