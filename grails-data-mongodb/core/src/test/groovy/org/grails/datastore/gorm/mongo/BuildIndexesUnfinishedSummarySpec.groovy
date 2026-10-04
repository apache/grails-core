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
import com.mongodb.MongoSocketReadException
import com.mongodb.ServerAddress
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * A build that stops partway - a dropped connection, a timeout - still reports how far it got. In a
 * background build that is all an operator has to go on besides the error itself: which indexes are
 * there, and which domain classes never had theirs applied.
 */
class BuildIndexesUnfinishedSummarySpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient realClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl('unfinishedDb'))
    }

    void cleanupSpec() {
        realClient?.close()
    }

    /**
     * Lets the first index through and loses the connection on the second, so the build stops after one
     * of the two domain classes.
     */
    private MongoClient connectionLostOnSecondIndex() {
        def created = new AtomicInteger()
        FailingMongoClient.wrap(realClient, 'createIndex') { Closure proceed ->
            if (created.incrementAndGet() > 1) {
                throw new MongoSocketReadException('Prematurely reached end of stream', new ServerAddress())
            }
            proceed()
        }
    }

    private static String unfinishedSummary(CapturedLog log, String database) {
        log.events.find {
            it.level == Level.WARN && it.formattedMessage.contains("database [$database] did not finish")
        }?.formattedMessage
    }

    void "test a background build that stops partway reports how far it got"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)

        when:
        def datastore = new MongoDatastore(connectionLostOnSecondIndex(), DatastoreUtils.createPropertyResolver([
                'grails.mongodb.databaseName'              : 'unfinishedAsyncDb',
                (MongoSettings.SETTING_BUILD_INDEXES_ASYNC): true
        ]), FirstUnfinishedThing, SecondUnfinishedThing)

        then: "the failure is reported, and so is what had been applied before it"
        conditions.eventually {
            assert unfinishedSummary(log, 'unfinishedAsyncDb') != null
        }
        unfinishedSummary(log, 'unfinishedAsyncDb').contains('at 1 of 2 domain class(es): 1 created, 0 already present')
        log.events.any { it.level == Level.ERROR && it.formattedMessage.contains('The background index build failed') }

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test a build on the calling thread that stops partway reports how far it got and still fails"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)

        when:
        new MongoDatastore(connectionLostOnSecondIndex(), DatastoreUtils.createPropertyResolver([
                'grails.mongodb.databaseName': 'unfinishedSyncDb'
        ]), FirstUnfinishedThing, SecondUnfinishedThing)

        then: "the exception still fails startup, as it always has"
        thrown(MongoSocketReadException)

        and: "but not before saying what was applied"
        unfinishedSummary(log, 'unfinishedSyncDb').contains('at 1 of 2 domain class(es): 1 created, 0 already present')

        cleanup:
        log?.close()
    }
}

@Entity
class FirstUnfinishedThing {
    String name

    static mapping = {
        version false
        collection 'firstUnfinishedThing'
        name index: true
    }
}

@Entity
class SecondUnfinishedThing {
    String name

    static mapping = {
        version false
        collection 'secondUnfinishedThing'
        name index: true
    }
}
