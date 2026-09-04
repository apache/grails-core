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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * Nothing waits on the background index build, so what it does when it goes wrong is only visible in the
 * log. A build that fails has to say so loudly, because it can no longer fail startup; a build abandoned
 * because the application is shutting down has to stay quiet, because nothing went wrong.
 */
class BuildIndexesBackgroundFailureSpec extends AutoStartedMongoSpec {

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
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl('backgroundFailureDb'))
        datastoreLogger = LoggerFactory.getLogger(AbstractDatastore) as Logger
        previousLevel = datastoreLogger.level
        datastoreLogger.level = Level.DEBUG
        logged.start()
        datastoreLogger.addAppender(logged)
    }

    void cleanupSpec() {
        datastoreLogger?.detachAppender(logged)
        datastoreLogger?.level = previousLevel
        realClient?.close()
    }

    private MongoDatastore asyncDatastoreOn(MongoClient client, String database, Class... classes) {
        new MongoDatastore(client, DatastoreUtils.createPropertyResolver([
                'grails.mongodb.databaseName'              : database,
                (MongoSettings.SETTING_BUILD_INDEXES_ASYNC): true
        ]), classes)
    }

    void "test a background build that fails reports the failure instead of losing it"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        MongoClient broken = FailingMongoClient.wrap(realClient, 'getCollection') {
            throw new MongoException('the connection went away mid-build')
        }

        when: "the datastore is created, which does not wait for the build"
        def datastore = asyncDatastoreOn(broken, 'backgroundFailureDb', FailedBackgroundThing)

        then: "startup was not held up by, and did not fail because of, the broken build"
        datastore.isBuildIndexesAsync()

        and: "the failure is reported at error level, since nothing else would surface it"
        conditions.eventually {
            assert logged.list.any {
                it.level == Level.ERROR && it.formattedMessage.contains('The background index build failed')
            }
        }

        cleanup:
        datastore?.close()
    }

    void "test a background build abandoned at shutdown is not reported as a failure"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        def buildReached = new CountDownLatch(1)
        def neverReleased = new CountDownLatch(1)
        // The appender is shared with the preceding feature, which logs an error of its own on purpose
        int errorsBefore = backgroundBuildFailures()
        MongoClient blocking = FailingMongoClient.wrap(realClient, 'createIndex') {
            buildReached.countDown()
            neverReleased.await()
        }

        when: "the build is under way on its background thread"
        def datastore = asyncDatastoreOn(blocking, 'backgroundFailureDb', AbandonedBackgroundThing)

        then:
        buildReached.await(30, TimeUnit.SECONDS)

        when: "the datastore is closed while the build is still running"
        datastore.close()

        then: "the interruption is reported as the shutdown it is, not as a failure"
        conditions.eventually {
            assert logged.list.any {
                it.level == Level.DEBUG && it.formattedMessage.contains('abandoned because the datastore is shutting down')
            }
        }

        and: "no error is logged for it"
        backgroundBuildFailures() == errorsBefore
    }

    private int backgroundBuildFailures() {
        logged.list.count {
            it.level == Level.ERROR && it.formattedMessage.contains('The background index build failed')
        }
    }
}

@Entity
class FailedBackgroundThing {
    String name

    static mapping = {
        version false
        collection 'failedBackgroundThing'
        name index: true
    }
}

@Entity
class AbandonedBackgroundThing {
    String name

    static mapping = {
        version false
        collection 'abandonedBackgroundThing'
        name index: true
    }
}
