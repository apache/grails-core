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
package org.grails.datastore.gorm.mongo.connections

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.mongodb.client.MongoClient
import grails.gorm.annotation.Entity
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.mongo.FailingMongoClient
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings

/**
 * Verifies that {@code buildIndexes} is resolved per connection: a connection inherits the top level
 * setting unless it declares its own, so index building can be switched off globally and left on for an
 * individual connection (or the other way round).
 */
class BuildIndexesPerConnectionSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.mongodb.url'                 : "mongodb://${mongoHost}:${mongoPort}/skippedDb" as String,
                (MongoSettings.SETTING_BUILD_INDEXES): false,
                'grails.mongodb.connections'         : [
                        'indexed': [
                                'url'         : "mongodb://${mongoHost}:${mongoPort}/indexedDb" as String,
                                'buildIndexes': true
                        ],
                        'inherits': [
                                'url': "mongodb://${mongoHost}:${mongoPort}/inheritsDb" as String
                        ]
                ]
        ]
        datastore = new MongoDatastore(config, PerConnectionThing)
    }

    void "test closing interrupts a child index build added at runtime: #addedAtRuntime"() {
        given:
        def buildReached = new CountDownLatch(1)
        def releaseBuild = new CountDownLatch(1)
        def worker = new AtomicReference<Thread>()
        Logger logger = LoggerFactory.getLogger('org.grails.datastore.mapping') as Logger
        Level previousLevel = logger.level
        def logged = new ListAppender<ILoggingEvent>()
        logger.level = Level.DEBUG
        logged.start()
        logger.addAppender(logged)
        def factory = new MongoConnectionSourceFactory() {
            @Override
            ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
                def source = super.create(name, settings)
                if (name != 'indexedAsync') {
                    return source
                }
                MongoClient blocking = FailingMongoClient.wrap(source.source, 'createIndex') {
                    worker.set(Thread.currentThread())
                    buildReached.countDown()
                    releaseBuild.await()
                }
                new DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings>(name, blocking, settings)
            }
        }
        Map childConfig = [url: dbContainer.getReplicaSetUrl('asyncChildDb'), buildIndexes: true]
        Map config = [
                'grails.mongodb.url': dbContainer.getReplicaSetUrl('asyncParentDb'),
                'grails.mongodb.buildIndexes': false,
                'grails.mongodb.buildIndexesAsync': true,
                'grails.mongodb.connections': addedAtRuntime ? [:] : [indexedAsync: childConfig]
        ]

        when:
        def parent = new MongoDatastore(DatastoreUtils.createPropertyResolver(config), factory,
                new DefaultApplicationEventPublisher(), AsyncPerConnectionThing)
        if (addedAtRuntime) {
            parent.connectionSources.addConnectionSource('indexedAsync', childConfig)
        }

        then:
        parent.getDatastoreForConnection('indexedAsync').isBuildIndexesAsync()
        buildReached.await(30, TimeUnit.SECONDS)
        worker.get().name == 'gorm-mongo-index-build-indexedAsync'

        when: "the parent closes while its child is still building"
        parent.close()
        worker.get().join(10000)

        then: "the child worker is interrupted and terminates"
        !worker.get().alive

        and: "the abandoned build is classified as shutdown rather than an error"
        logged.list.any {
            it.threadName == worker.get().name && it.level == Level.DEBUG &&
                    it.formattedMessage.contains('abandoned because the datastore is shutting down')
        }
        !logged.list.any {
            it.threadName == worker.get().name && it.level == Level.ERROR
        }

        cleanup:
        releaseBuild.countDown()
        parent?.close()
        worker.get()?.join(10000)
        logger.detachAppender(logged)
        logger.level = previousLevel
        logged.stop()

        where:
        addedAtRuntime << [false, true]
    }

    void "test a connection can override the global setting"() {
        expect: "the default connection is disabled by the top level setting"
        !datastore.isBuildIndexes()

        and: "the connection that declares its own setting builds indexes"
        datastore.getDatastoreForConnection('indexed').isBuildIndexes()

        and: "a connection that declares nothing inherits the top level setting"
        !datastore.getDatastoreForConnection('inherits').isBuildIndexes()
    }

    void "test only the connection with index building enabled has the declared index"() {
        when: "a document is written through each connection so every collection exists"
        PerConnectionThing.withNewSession {
            new PerConnectionThing(name: 'Fred').save(flush: true)
        }
        PerConnectionThing.indexed.withNewSession {
            new PerConnectionThing(name: 'Fred').save(flush: true)
        }

        then: "the disabled connection has only the implicit _id index"
        PerConnectionThing.collection.listIndexes()*.key == [[_id: 1]]

        and: "the enabled connection has the index declared in the mapping"
        [name: 1] in PerConnectionThing.indexed.collection.listIndexes()*.key
    }
}

@Entity
class PerConnectionThing {
    String name

    static mapping = {
        version false
        collection 'perConnectionThing'
        connection ConnectionSource.ALL
        name index: true
    }
}

@Entity
class AsyncPerConnectionThing {
    String name

    static mapping = {
        connection ConnectionSource.ALL
        name index: true
    }
}
